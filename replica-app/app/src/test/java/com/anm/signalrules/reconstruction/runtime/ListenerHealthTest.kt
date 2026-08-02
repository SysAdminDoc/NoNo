package com.anm.signalrules.reconstruction.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ListenerHealthTest {

    @Before
    fun reset() {
        ListenerHealth.reset()
        SignalObservability.clearForTests()
    }

    @Test
    fun `starts unknown rather than claiming to be connected`() {
        assertEquals(ListenerHealth.Connection.UNKNOWN, ListenerHealth.connection.value)
        assertNull(ListenerHealth.lastEventAt.value)
        assertEquals(0L, ListenerHealth.eventCount.value)
    }

    @Test
    fun `connection callbacks drive the published state`() {
        ListenerHealth.onConnected()
        assertEquals(ListenerHealth.Connection.CONNECTED, ListenerHealth.connection.value)

        ListenerHealth.onDisconnected()
        assertEquals(ListenerHealth.Connection.DISCONNECTED, ListenerHealth.connection.value)
    }

    @Test
    fun `revoked access reads as disconnected`() {
        ListenerHealth.onConnected()
        ListenerHealth.onAccessRevoked()

        assertEquals(ListenerHealth.Connection.DISCONNECTED, ListenerHealth.connection.value)
    }

    @Test
    fun `events record a timestamp and a count`() {
        ListenerHealth.recordEvent(1_000L)
        ListenerHealth.recordEvent(2_500L)

        assertNotNull(ListenerHealth.lastEventAt.value)
        assertEquals(2_500L, ListenerHealth.lastEventAt.value)
        assertEquals(2L, ListenerHealth.eventCount.value)
    }

    @Test
    fun `ingestion diagnostics are published and reset`() {
        ListenerHealth.updateIngestionMetrics(IngestionMetrics(queued = 2, persisted = 4, dropped = 1, failed = 1))

        assertEquals(1L, ListenerHealth.ingestionMetrics.value.dropped)
        assertEquals(1L, ListenerHealth.ingestionMetrics.value.failed)

        ListenerHealth.reset()
        assertEquals(IngestionMetrics(), ListenerHealth.ingestionMetrics.value)
    }

    @Test
    fun `durable ingestion diagnostics can be restored after process restart`() {
        val restored = IngestionMetrics(
            persisted = 12L,
            dropped = 3L,
            failed = 2L,
            lastFailureAtEpochMillis = 9_000L,
        )

        ListenerHealth.restoreDurableIngestionMetrics(restored)

        assertEquals(restored, ListenerHealth.durableIngestionMetrics.value)
        ListenerHealth.reset()
        assertEquals(IngestionMetrics(), ListenerHealth.durableIngestionMetrics.value)
    }

    @Test
    fun `health events contain operational fields but no payload fields`() {
        val events = mutableListOf<SignalEvent>()
        val sink = SignalEventSink { events += it }
        SignalObservability.register(sink)

        ListenerHealth.onConnected()
        ListenerHealth.updateIngestionMetrics(IngestionMetrics(queued = 1, dropped = 2))

        assertEquals(listOf(SignalEventType.LISTENER_CONNECTED, SignalEventType.QUEUE_METRICS), events.map { it.type })
        assertEquals("type=QUEUE_METRICS at=${events[1].atEpochMillis} queued=1 persisted=0 dropped=2 failed=0", events[1].toSafeLogLine())
        SignalObservability.unregister(sink)
    }
}
