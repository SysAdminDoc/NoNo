package com.sysadmindoc.nono.runtime

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

/** The one periodic job name, so a cadence change replaces the schedule rather than stacking one. */
private const val BACKUP_WORK_NAME = "nono-rule-backup"

/** Kept apart from the schedule, so an immediate run cannot displace the periodic one. */
private const val BACKUP_NOW_WORK_NAME = "nono-rule-backup-now"

/** Said alongside a success, because the backup was written and only the tidying was not done. */
const val ROTATION_INCOMPLETE = "Older backups could not be removed from that folder."

/**
 * Records what the run did, and never throws.
 *
 * A full disk is the realistic failure for a job whose whole purpose is writing files, and it is
 * exactly when the status matters most. An IOException out of this write used to leave `doWork`,
 * so WorkManager recorded the run as failed and Settings kept whatever it last said — over a
 * backup that had in fact been written. Failing to say what happened is not a reason to also
 * misreport it.
 */
internal suspend fun writeBackupStatus(store: DataStore<Preferences>, status: BackupStatus) {
    runCatching {
        store.edit { it[SignalPreferences.BACKUP_STATUS] = encodeBackupStatus(status) }
    }
}

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
        // Distinguished from an empty store: a damaged preferences file would otherwise read as
        // cadence off, and the schedule would report nothing while writing nothing, forever.
        val values = runCatching { store.data.first() }.getOrElse {
            return recordFailure(context, "The app's settings could not be read for this backup.")
        }
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
        val name = backupFileName(now)
        val written = runCatching {
            BackupFolder.writeDocument(resolver, folder, name, payload.toByteArray(Charsets.UTF_8))
        }
        if (written.isFailure) {
            return recordFailure(context, "The backup file could not be written to that folder.")
        }

        // Rotation runs only after a successful write, so a failed run never costs the user the
        // copy it could not replace. The backup itself is on disk either way, so a folder that
        // cannot be listed is reported as a rotation problem rather than as a failed backup.
        val rotated = runCatching {
            val present = BackupFolder.listNames(resolver, folder) ?: return@runCatching false
            // fold rather than all: `all` stops at the first refusal, so one file that cannot be
            // removed would leave every older one untried and the folder growing for ever.
            expiredBackupFileNames(present, justWritten = name).fold(true) { removed, expired ->
                BackupFolder.deleteByName(resolver, folder, expired) && removed
            }
        }.getOrDefault(false)

        // The backup is on disk by this point. An IOException out of the status write — a full
        // disk is the realistic one for a job whose whole purpose is writing files — would throw
        // out of doWork, WorkManager would record the run as failed, and Settings would keep
        // whatever it last said over a backup that did in fact happen.
        writeBackupStatus(
            store,
            BackupStatus(BackupOutcome.SUCCEEDED, now, rules.size, if (rotated) "" else ROTATION_INCOMPLETE),
        )
        return Result.success()
    }

    /**
     * @return success, deliberately. A retry would repeat a failure the user has to act on, and
     * the next scheduled run is soon enough once they have.
     */
    private suspend fun recordFailure(context: Context, detail: String): Result {
        writeBackupStatus(
            SignalPreferences.get(context),
            BackupStatus(BackupOutcome.FAILED, System.currentTimeMillis(), 0, detail),
        )
        return Result.success()
    }
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
