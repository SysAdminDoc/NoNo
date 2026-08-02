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
