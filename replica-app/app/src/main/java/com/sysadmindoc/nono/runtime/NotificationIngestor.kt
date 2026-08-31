package com.sysadmindoc.nono.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IngestionMetrics(
    val queued: Int = 0,
    val persisted: Long = 0L,
    val dropped: Long = 0L,
    val failed: Long = 0L,
    val lastFailureAtEpochMillis: Long? = null,
    /** Counts the user has already seen and dismissed. Only the durable record carries these. */
    val acknowledgedDropped: Long = 0L,
    val acknowledgedFailed: Long = 0L,
)

/**
 * What the health banner should report right now.
 *
 * A count that only ever grows means one bad minute keeps the warning on screen forever, so the
 * user learns to ignore it. Subtracting what they acknowledged leaves the banner reporting what
 * is happening rather than what once happened, without discarding the history.
 */
data class IngestionProblems(
    val dropped: Long,
    val failed: Long,
    val acknowledgedDropped: Long,
    val acknowledgedFailed: Long,
    val lastFailureAtEpochMillis: Long?,
) {
    val hasCurrentProblem: Boolean get() = dropped > 0L || failed > 0L
    val hasAcknowledgedHistory: Boolean get() = acknowledgedDropped > 0L || acknowledgedFailed > 0L
}

/**
 * @param live counters for this process, which reset when it restarts.
 * @param durable the accumulated record, which is what acknowledgement applies to.
 */
fun outstandingIngestionProblems(live: IngestionMetrics, durable: IngestionMetrics): IngestionProblems {
    // The durable record accumulates the live deltas, so it is normally the larger. Taking the
    // maximum covers the window before the first merge has been written.
    val dropped = maxOf(live.dropped, durable.dropped)
    val failed = maxOf(live.failed, durable.failed)
    return IngestionProblems(
        dropped = (dropped - durable.acknowledgedDropped).coerceAtLeast(0L),
        failed = (failed - durable.acknowledgedFailed).coerceAtLeast(0L),
        acknowledgedDropped = durable.acknowledgedDropped,
        acknowledgedFailed = durable.acknowledgedFailed,
        lastFailureAtEpochMillis = durable.lastFailureAtEpochMillis,
    )
}

/**
 * Bounded hand-off from the main-thread listener callback to storage. The callback performs no
 * disk I/O and has a fixed upper bound on retained work. A full queue drops the newest event and
 * increments a diagnostic counter rather than blocking the system callback.
 *
 * @param persist returns true when the item reached storage. A policy that declines to store an
 * item is not a failure, but counting it as persisted would report rows that are not there.
 */
class NotificationIngestor<T>(
    private val scope: CoroutineScope,
    capacity: Int = 64,
    private val persist: suspend (T) -> Boolean,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val queue = Channel<T>(capacity)
    private val _metrics = MutableStateFlow(IngestionMetrics())
    val metrics: StateFlow<IngestionMetrics> = _metrics.asStateFlow()

    private val worker = scope.launch {
        for (item in queue) {
            updateMetrics { it.copy(queued = (it.queued - 1).coerceAtLeast(0)) }
            runCatching { persist(item) }
                .onSuccess { stored -> if (stored) updateMetrics { it.copy(persisted = it.persisted + 1) } }
                .onFailure { updateMetrics { it.copy(failed = it.failed + 1) } }
        }
    }

    fun offer(item: T): Boolean {
        val result: ChannelResult<Unit> = queue.trySend(item)
        if (result.isSuccess) {
            updateMetrics { it.copy(queued = it.queued + 1) }
            return true
        }
        updateMetrics { it.copy(dropped = it.dropped + 1) }
        return false
    }

    suspend fun close() {
        queue.close()
        worker.join()
    }

    private fun updateMetrics(transform: (IngestionMetrics) -> IngestionMetrics) {
        // The callback and worker are single-threaded in normal operation. This synchronized
        // section also makes the diagnostic snapshot deterministic in JVM burst tests.
        synchronized(this) { _metrics.value = transform(_metrics.value) }
    }
}
