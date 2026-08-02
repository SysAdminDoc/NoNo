package com.anm.signalrules.reconstruction.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
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

    const val STORE_NAME = "signal_rules"

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
            emptyPreferences()
        },
        scope = scope,
        produceFile = produceFile,
    )
}
