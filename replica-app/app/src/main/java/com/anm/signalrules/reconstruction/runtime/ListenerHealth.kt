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
     * A listener that has reported itself connected needs nothing. Treating that case as revoked -
     * which an earlier revision did, because the healthy case fell through to the else branch -
     * made every resume publish a disconnected listener and raise the health banner over a
     * listener that was working.
     *
     * UNKNOWN is not healthy. It means access is granted but the service has not called back in
     * this process, which is exactly the state an OEM background kill or an app update leaves
     * behind. Nothing else in the app moves the state out of UNKNOWN, so a resume that did nothing
     * here would leave capture silently dead with the app still reporting itself fine. Asking for
     * a rebind is safe when the listener is already bound.
     *
     * Revocation is announced once per loss rather than once per resume, tracked separately from
     * the connection state: the platform can disconnect a listener whose access is still granted,
     * so DISCONNECTED alone does not mean the user took access away.
     */
    fun capabilityAction(accessGranted: Boolean, connection: Connection): CapabilityAction = when {
        !accessGranted && revocationAnnounced -> CapabilityAction.NONE
        !accessGranted -> CapabilityAction.MARK_REVOKED
        connection == Connection.CONNECTED -> CapabilityAction.NONE
        else -> CapabilityAction.REQUEST_REBIND
    }

    @Volatile
    private var revocationAnnounced = false

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
        revocationAnnounced = false
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
        revocationAnnounced = true
        _connection.value = Connection.DISCONNECTED
        SignalObservability.emit(SignalEvent(SignalEventType.ACCESS_REVOKED))
    }

    fun reset() {
        revocationAnnounced = false
        _connection.value = Connection.UNKNOWN
        _lastEventAt.value = null
        _eventCount.value = 0L
        _ingestionMetrics.value = IngestionMetrics()
        _durableIngestionMetrics.value = IngestionMetrics()
    }
}
