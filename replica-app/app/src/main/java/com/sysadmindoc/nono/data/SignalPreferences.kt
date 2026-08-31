package com.sysadmindoc.nono.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Preference storage for the reconstruction.
 *
 * DataStore contractually requires the caller to handle [androidx.datastore.core.CorruptionException];
 * without a handler a truncated or partially written preferences file throws on every read and, because
 * the file survives the crash, bricks the app permanently. Recovery replaces the unreadable file with
 * defaults and reports that it happened so the UI can tell the user rather than silently losing settings.
 */
object SignalPreferences {

    const val STORE_NAME = "nono"

    /** Read by the view model and by the listener, which evaluates rules as notifications arrive. */
    val RULES_KEY = stringPreferencesKey("rules_v1")

    /** Setting labels the listener reads directly, without waiting for an Activity. */
    const val HISTORY_RETENTION_SETTING = "History retention"
    const val HISTORY_STORAGE_SETTING = "Notification history"

    /**
     * Derives the preference key for a settings-screen label.
     *
     * Shared rather than private to the view model, because the listener has to read the same
     * keys the settings screen writes and a second copy of this rule would drift.
     */
    fun settingKey(label: String) = stringPreferencesKey(
        "setting_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
    )

    private const val STORE_FILE = "$STORE_NAME.preferences_pb"
    private const val STORE_DIRECTORY = "datastore"

    /**
     * Resolves the store under `noBackupFilesDir`.
     *
     * Everything this app persists is derived from other apps' notification content, so it
     * must not ride along in an automatic cloud backup or device transfer. Placing the file
     * outside the backed-up tree enforces that independently of the manifest backup rules,
     * which older platform versions and OEM backup agents honour inconsistently.
     *
     * @param legacyFile the previous location inside `filesDir`, moved across on first run.
     */
    fun resolveStoreFile(noBackupFilesDir: File, legacyFile: File): File {
        val directory = File(noBackupFilesDir, STORE_DIRECTORY)
        val target = File(directory, STORE_FILE)
        if (!target.exists() && legacyFile.exists()) {
            directory.mkdirs()
            // renameTo can fail across filesystems; a failed migration must not be fatal,
            // it just means the user starts from defaults.
            if (!legacyFile.renameTo(target)) {
                runCatching {
                    legacyFile.copyTo(target, overwrite = true)
                    legacyFile.delete()
                }
            }
        }
        return target
    }

    @Volatile
    private var instance: DataStore<Preferences>? = null

    /**
     * Held so the file can be released again.
     *
     * DataStore registers the backing file in a process-wide set and only removes it when the
     * store scope completes. Deleting the file is not enough: reopening the same path while the
     * old scope is alive throws. Nothing in shipping code releases the store, so this exists for
     * [resetForTest] alone.
     */
    @Volatile
    private var storeScope: CoroutineScope? = null

    @Volatile
    private var recoveredFromCorruption = false

    /**
     * The process-wide preference store.
     *
     * DataStore permits exactly one active instance per file per process and throws on the second,
     * so the notification listener and the view model cannot each build their own. The listener
     * needs the rules to evaluate a notification as it arrives, and the platform can start it with
     * no Activity ever having run, so reading them through the view model is not an option either.
     *
     * The scope belongs to the store rather than to any one owner, because a view model that is
     * cleared must not take the listener's store down with it.
     */
    fun get(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
        instance ?: create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { storeScope = it },
            produceFile = {
                resolveStoreFile(
                    noBackupFilesDir = context.applicationContext.noBackupFilesDir,
                    legacyFile = context.applicationContext.preferencesDataStoreFile(STORE_NAME),
                )
            },
            onCorruption = { recoveredFromCorruption = true },
        ).also { instance = it }
    }

    /**
     * Discards the process-wide store and the file behind it.
     *
     * A test that exercises the view model has to start from a known preference state, and the
     * store is deliberately a singleton, so without this every test would inherit whatever the
     * previous one saved. DataStore permits one instance per file per process, so the file has to
     * go along with the reference: reopening the same path while the old instance is alive throws.
     *
     * Never called from shipping code, and there is no path that reaches it from the UI.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun resetForTest(context: Context) {
        synchronized(this) {
            // The scope has to finish before the path is free again, hence the join.
            storeScope?.coroutineContext?.get(Job)?.let { job -> runBlocking { job.cancelAndJoin() } }
            storeScope = null
            instance = null
            recoveredFromCorruption = false
            val noBackup = context.applicationContext.noBackupFilesDir
            File(noBackup, STORE_DIRECTORY).resolve(STORE_FILE).delete()
            context.applicationContext.preferencesDataStoreFile(STORE_NAME).delete()
        }
    }

    /**
     * True once if the store on disk was unreadable and defaults were substituted.
     *
     * Consuming the flag matters because the store is now process-wide: without it a view model
     * built after the first one would repeat a message about a recovery that happened minutes ago
     * and has already been reported.
     */
    fun consumeCorruptionRecovery(): Boolean {
        if (!recoveredFromCorruption) return false
        recoveredFromCorruption = false
        return true
    }

    /**
     * Builds a preference store that recovers from an unreadable backing file.
     *
     * @param onCorruption invoked when the stored file could not be parsed and defaults were substituted.
     */
    fun create(
        scope: CoroutineScope,
        produceFile: () -> File,
        onCorruption: () -> Unit,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            onCorruption()
            // Some Windows and OEM filesystems cannot atomically replace an existing
            // unreadable payload. Remove it before DataStore writes the recovered defaults.
            runCatching { produceFile().delete() }
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )
}
