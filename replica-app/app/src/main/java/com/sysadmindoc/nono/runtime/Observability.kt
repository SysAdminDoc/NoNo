package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.NotificationContentState
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class SignalEventType {
    LISTENER_CONNECTED,
    LISTENER_DISCONNECTED,
    ACCESS_REVOKED,
    NOTIFICATION_CAPTURED,
    QUEUE_METRICS,
    DATABASE_WRITE,
    EVALUATION_TRACE,
    ACTION_RESULT,
}

/**
 * Release-safe event schema. There is deliberately no message, package, title, body, token, or
 * arbitrary map field: callers can report operational state but cannot accidentally log payloads.
 */
data class SignalEvent(
    val type: SignalEventType,
    val atEpochMillis: Long = System.currentTimeMillis(),
    val traceId: String? = null,
    val queued: Int? = null,
    val persisted: Long? = null,
    val dropped: Long? = null,
    val failed: Long? = null,
    val contentState: NotificationContentState? = null,
    val success: Boolean? = null,
)

fun SignalEvent.toSafeLogLine(): String = buildString {
    append("type=").append(type.name)
    append(" at=").append(atEpochMillis)
    traceId?.let { append(" trace=").append(it) }
    queued?.let { append(" queued=").append(it) }
    persisted?.let { append(" persisted=").append(it) }
    dropped?.let { append(" dropped=").append(it) }
    failed?.let { append(" failed=").append(it) }
    contentState?.let { append(" contentState=").append(it.name) }
    success?.let { append(" success=").append(it) }
}

fun newTraceId(): String = UUID.randomUUID().toString()

fun interface SignalEventSink {
    fun onEvent(event: SignalEvent)
}

object SignalObservability {
    private val sinks = CopyOnWriteArrayList<SignalEventSink>()

    fun register(sink: SignalEventSink) {
        sinks += sink
    }

    fun unregister(sink: SignalEventSink) {
        sinks -= sink
    }

    fun emit(event: SignalEvent) {
        sinks.forEach { sink -> runCatching { sink.onEvent(event) } }
    }

    fun clearForTests() {
        sinks.clear()
    }
}
