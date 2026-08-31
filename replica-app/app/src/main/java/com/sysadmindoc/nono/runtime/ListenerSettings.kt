package com.sysadmindoc.nono.runtime

import androidx.datastore.preferences.core.Preferences
import com.sysadmindoc.nono.data.SignalPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

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
 * Holds the ingestion worker until the persisted settings have been read once.
 *
 * Without it a cold-started listener prunes with the process default while the user's saved
 * period sits unread on disk. With an unbounded wait it would be worse: a preference read that
 * never completes parks the worker forever, the bounded queue fills, every later capture is
 * counted as dropped, and the service cannot even shut down, because closing the ingestor joins
 * a worker that never returns. So the wait is bounded and falls back to the defaults, which is
 * what an unreadable store means anyway.
 */
class ListenerSettingsGate(private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {

    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var gaveUp = false

    /** Publishes a fresh read and releases anything waiting on the first one. */
    fun publish(settings: ListenerSettings) {
        applyListenerSettings(settings)
        loaded.complete(Unit)
    }

    /** True once a read has been published. Waiting callers are released. */
    val isLoaded: Boolean get() = loaded.isCompleted

    /** True once the wait has timed out at least once and stopped being paid. */
    val hasGivenUp: Boolean get() = gaveUp

    /**
     * @return the published settings, or the defaults if none arrived within the timeout.
     *
     * The timeout is paid at most once. Re-entering the wait for every queued capture would
     * throttle ingestion to one item per timeout, fill the bounded queue, and make the service's
     * own shutdown wait the timeout for each item still in it.
     */
    suspend fun awaitSettings(): ListenerSettings {
        if (!loaded.isCompleted && !gaveUp) {
            if (withTimeoutOrNull(timeoutMillis) { loaded.await() } == null) gaveUp = true
        }
        return currentListenerSettings()
    }

    companion object {
        /** Long enough for a DataStore read, short enough that ingestion is not held hostage. */
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}

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
