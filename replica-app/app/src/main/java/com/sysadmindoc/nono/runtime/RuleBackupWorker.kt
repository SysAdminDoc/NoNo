package com.sysadmindoc.nono.runtime

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sysadmindoc.nono.data.BackupFolder
import com.sysadmindoc.nono.data.DeviceBackupKey
import com.sysadmindoc.nono.data.RuleTransfer
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.data.decodeRuleStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/** The one periodic job name, so a cadence change replaces the schedule rather than stacking one. */
private const val BACKUP_WORK_NAME = "nono-rule-backup"

/** Kept apart from the schedule, so an immediate run cannot displace the periodic one. */
private const val BACKUP_NOW_WORK_NAME = "nono-rule-backup-now"

/**
 * Writes an encrypted copy of the saved rules into the folder the user picked.
 *
 * It runs with no Activity, so everything it needs comes from the same DataStore the settings
 * screen writes. Nothing about notification history is included: this backs up rules, and the
 * history CSV stays a separate, explicit export.
 */
class RuleBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = SignalPreferences.get(context)
        val values = store.data.catchToEmpty().first()
        val cadence = backupCadence(values[SignalPreferences.settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING)])
        if (!cadence.enabled) {
            // The schedule was turned off between this run being queued and it starting. Nothing
            // to report and nothing to write.
            return Result.success()
        }

        val folder = values[SignalPreferences.BACKUP_FOLDER_URI]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (folder == null) {
            return recordFailure(context, "No backup folder is selected.")
        }
        // Checked before anything is written: a grant the user withdrew, or a volume that is gone,
        // must be reported rather than leaving the schedule silently doing nothing.
        if (!BackupFolder.hasWriteGrant(context, folder)) {
            return recordFailure(context, "Access to the backup folder was withdrawn. Pick it again.")
        }
        val key = DeviceBackupKey.get()
            ?: return recordFailure(context, "This device's keystore did not provide a backup key.")

        val rules = decodeRuleStore(values[SignalPreferences.RULES_KEY])?.rules.orEmpty()
        val payload = runCatching { RuleTransfer.exportRulesForDevice(rules, key) }.getOrElse {
            return recordFailure(context, "The rules could not be encrypted for backup.")
        }

        val now = System.currentTimeMillis()
        val resolver = context.contentResolver
        val written = runCatching {
            BackupFolder.writeDocument(resolver, folder, backupFileName(now), payload.toByteArray(Charsets.UTF_8))
        }
        if (written.isFailure) {
            return recordFailure(context, "The backup file could not be written to that folder.")
        }

        // Rotation runs only after a successful write, so a failed run never costs the user the
        // copy it could not replace.
        runCatching {
            expiredBackupFileNames(BackupFolder.listNames(resolver, folder)).forEach { name ->
                BackupFolder.deleteByName(resolver, folder, name)
            }
        }

        store.edit {
            it[SignalPreferences.BACKUP_STATUS] = encodeBackupStatus(
                BackupStatus(BackupOutcome.SUCCEEDED, now, rules.size),
            )
        }
        return Result.success()
    }

    /**
     * @return success, deliberately. A retry would repeat a failure the user has to act on, and
     * the next scheduled run is soon enough once they have.
     */
    private suspend fun recordFailure(context: Context, detail: String): Result {
        SignalPreferences.get(context).edit {
            it[SignalPreferences.BACKUP_STATUS] = encodeBackupStatus(
                BackupStatus(BackupOutcome.FAILED, System.currentTimeMillis(), 0, detail),
            )
        }
        return Result.success()
    }

    private fun Flow<Preferences>.catchToEmpty(): Flow<Preferences> = catch { emit(emptyPreferences()) }
}

/**
 * Puts the periodic job in place, or takes it away.
 *
 * WorkManager persists its own schedule and restores it after a reboot, which is the reason the
 * job is a periodic work request rather than an alarm this app would have to re-register itself.
 */
object BackupScheduler {

    fun apply(context: Context, cadence: BackupCadence) {
        val work = WorkManager.getInstance(context)
        if (!cadence.enabled) {
            work.cancelUniqueWork(BACKUP_WORK_NAME)
            return
        }
        work.enqueueUniquePeriodicWork(
            BACKUP_WORK_NAME,
            // Replacing rather than keeping, so changing Daily to Weekly takes effect. KEEP would
            // leave the old interval running and the setting would be a lie.
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<RuleBackupWorker>(cadence.repeatIntervalHours, TimeUnit.HOURS).build(),
        )
    }

    /**
     * Runs the job once, now.
     *
     * A periodic schedule can be a day away, so without this the user turns the setting on and has
     * nothing to look at. Whether the folder actually works is the thing they need to find out
     * while they are still standing in Settings.
     */
    fun runOnce(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKUP_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RuleBackupWorker>().build(),
        )
    }
}
