package com.anm.signalrules.reconstruction.runtime

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
)

/**
 * Bounded hand-off from the main-thread listener callback to storage. The callback performs no
 * disk I/O and has a fixed upper bound on retained work. A full queue drops the newest event and
 * increments a diagnostic counter rather than blocking the system callback.
 */
class NotificationIngestor<T>(
    private val scope: CoroutineScope,
    capacity: Int = 64,
    private val persist: suspend (T) -> Unit,
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
                .onSuccess { updateMetrics { it.copy(persisted = it.persisted + 1) } }
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
