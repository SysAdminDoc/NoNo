package com.sysadmindoc.nono.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationIngestorTest {

    @Test
    fun `burst is persisted by one worker and exposes queue metrics`() = runTest {
        val persisted = mutableListOf<Int>()
        val workerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ingestor = NotificationIngestor<Int>(
            scope = workerScope,
            capacity = 4,
            persist = { persisted += it; true },
        )

        assertTrue(ingestor.offer(1))
        assertTrue(ingestor.offer(2))
        advanceUntilIdle()

        assertEquals(listOf(1, 2), persisted)
        assertEquals(2L, ingestor.metrics.value.persisted)
        assertEquals(0L, ingestor.metrics.value.dropped)
        workerScope.cancel()
    }

    @Test
    fun `full queue drops without blocking the callback`() = runTest {
        val workerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ingestor = NotificationIngestor<Int>(
            scope = workerScope,
            capacity = 1,
            persist = { true },
        )

        assertTrue(ingestor.offer(1))
        assertFalse(ingestor.offer(2))
        assertEquals(1L, ingestor.metrics.value.dropped)
        advanceUntilIdle()
        assertEquals(1L, ingestor.metrics.value.persisted)
        workerScope.cancel()
    }
}
