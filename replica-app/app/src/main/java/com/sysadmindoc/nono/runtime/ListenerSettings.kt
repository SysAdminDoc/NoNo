package com.sysadmindoc.nono.runtime

import androidx.datastore.preferences.core.Preferences
import com.sysadmindoc.nono.data.SignalPreferences

/**
 * The persisted settings the listener needs before it writes anything.
 *
 * The platform can start the listener with no Activity ever having run, so these cannot come
 * from the view model. Reading them from the same store the settings screen writes is what
 * stops a cold-started service from pruning with the process default while the user's saved
 * choice sits unread on disk.
 */
data class ListenerSettings(
    val retention: HistoryRetention = HistoryRetention.THIRTY_DAYS,
    val storage: HistoryStorage = HistoryStorage.METADATA_ONLY,
)

fun listenerSettings(preferences: Preferences): ListenerSettings = ListenerSettings(
    retention = historyRetention(preferences[SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING)]),
    storage = historyStorage(preferences[SignalPreferences.settingKey(SignalPreferences.HISTORY_STORAGE_SETTING)]),
)

/** Publishes [settings] to the process-wide bridges the listener and view model both read. */
fun applyListenerSettings(settings: ListenerSettings) {
    HistoryRetentionSettings.set(settings.retention)
    HistoryStorageSettings.set(settings.storage)
}

/** Reads the bridges back as one snapshot, which is what the ingestion worker acts on. */
fun currentListenerSettings(): ListenerSettings = ListenerSettings(
    retention = HistoryRetentionSettings.get(),
    storage = HistoryStorageSettings.get(),
)

/**
 * Applies the storage policy to one capture.
 *
 * [write] receives the retention cutoff derived from the persisted period, so the prune that
 * rides along with an insert can never use a period the user did not choose.
 *
 * @return true when the capture was written.
 */
suspend fun persistCapture(
    settings: ListenerSettings,
    nowEpochMillis: Long,
    write: suspend (cutoffEpochMillis: Long) -> Unit,
): Boolean {
    if (settings.storage == HistoryStorage.OFF) return false
    write(retentionCutoffEpochMillis(settings.retention, nowEpochMillis))
    return true
}
