package com.anm.signalrules.reconstruction.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection state of the notification listener.
 *
 * The listener runs in this app's process, so a process-wide holder is enough. The point of
 * publishing it is that a listener which has been unbound - by an app update, a service crash,
 * or an OEM background killer - is otherwise indistinguishable from one that simply has not
 * seen a notification yet. That ambiguity is the single most common failure report against
 * apps in this category, so the state is surfaced rather than inferred.
 */
object ListenerHealth {

    enum class Connection { UNKNOWN, CONNECTED, DISCONNECTED }

    /** What a capability refresh should do about the listener. */
    enum class CapabilityAction { NONE, REQUEST_REBIND, MARK_REVOKED }

    /**
     * Decides how a resume should react to the platform's access state.
     *
     * A healthy listener is one the user has granted access to and which has reported itself
     * connected: it needs nothing. Only a granted-but-disconnected listener is inside the window
     * the platform documents `requestRebind` for, and only an ungranted one has actually lost
     * access. Treating any other combination as revoked - which an earlier revision did, because
     * the healthy case fell through to the else branch - made every resume publish a
     * disconnected listener and raise the health banner over a listener that was working.
     *
     * Access that is already recorded as gone stays gone without re-announcing itself, so a
     * user who leaves the app disabled does not accumulate an ACCESS_REVOKED event per resume.
     */
    fun capabilityAction(accessGranted: Boolean, connection: Connection): CapabilityAction = when {
        accessGranted && connection == Connection.DISCONNECTED -> CapabilityAction.REQUEST_REBIND
        accessGranted -> CapabilityAction.NONE
        connection == Connection.DISCONNECTED -> CapabilityAction.NONE
        else -> CapabilityAction.MARK_REVOKED
    }

    private val _connection = MutableStateFlow(Connection.UNKNOWN)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    /** Elapsed-realtime stamp of the last notification seen, or null if none this process. */
    private val _lastEventAt = MutableStateFlow<Long?>(null)
    val lastEventAt: StateFlow<Long?> = _lastEventAt.asStateFlow()

    private val _eventCount = MutableStateFlow(0L)
    val eventCount: StateFlow<Long> = _eventCount.asStateFlow()

    private val _ingestionMetrics = MutableStateFlow(IngestionMetrics())
    val ingestionMetrics: StateFlow<IngestionMetrics> = _ingestionMetrics.asStateFlow()

    private val _durableIngestionMetrics = MutableStateFlow(IngestionMetrics())
    val durableIngestionMetrics: StateFlow<IngestionMetrics> = _durableIngestionMetrics.asStateFlow()

    fun onConnected() {
        _connection.value = Connection.CONNECTED
        SignalObservability.emit(SignalEvent(SignalEventType.LISTENER_CONNECTED))
    }

    fun onDisconnected() {
        _connection.value = Connection.DISCONNECTED
        SignalObservability.emit(SignalEvent(SignalEventType.LISTENER_DISCONNECTED))
    }

    /** Records that a notification arrived. Cheap enough to call on the callback thread. */
    fun recordEvent(atElapsedRealtime: Long) {
        _lastEventAt.value = atElapsedRealtime
        _eventCount.value = _eventCount.value + 1
    }

    fun updateIngestionMetrics(metrics: IngestionMetrics) {
        _ingestionMetrics.value = metrics
        SignalObservability.emit(
            SignalEvent(
                type = SignalEventType.QUEUE_METRICS,
                queued = metrics.queued,
                persisted = metrics.persisted,
                dropped = metrics.dropped,
                failed = metrics.failed,
            ),
        )
    }

    /** Restores redacted counters from Room after the process starts. */
    fun restoreDurableIngestionMetrics(metrics: IngestionMetrics) {
        _durableIngestionMetrics.value = metrics
    }

    /** Called when the OS reports access was revoked while the app was not running. */
    fun onAccessRevoked() {
        _connection.value = Connection.DISCONNECTED
        SignalObservability.emit(SignalEvent(SignalEventType.ACCESS_REVOKED))
    }

    fun reset() {
        _connection.value = Connection.UNKNOWN
        _lastEventAt.value = null
        _eventCount.value = 0L
        _ingestionMetrics.value = IngestionMetrics()
        _durableIngestionMetrics.value = IngestionMetrics()
    }
}
