package com.sysadmindoc.nono.runtime

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
    fun `a resume acts on every state except a listener that reported itself connected`() {
        val expected = mapOf(
            (true to ListenerHealth.Connection.CONNECTED) to ListenerHealth.CapabilityAction.NONE,
            // Granted but never called back: what an OEM kill or an app update leaves behind.
            (true to ListenerHealth.Connection.UNKNOWN) to ListenerHealth.CapabilityAction.REQUEST_REBIND,
            (true to ListenerHealth.Connection.DISCONNECTED) to ListenerHealth.CapabilityAction.REQUEST_REBIND,
            (false to ListenerHealth.Connection.CONNECTED) to ListenerHealth.CapabilityAction.MARK_REVOKED,
            (false to ListenerHealth.Connection.UNKNOWN) to ListenerHealth.CapabilityAction.MARK_REVOKED,
            (false to ListenerHealth.Connection.DISCONNECTED) to ListenerHealth.CapabilityAction.MARK_REVOKED,
        )

        expected.forEach { (input, action) ->
            ListenerHealth.reset()
            val (granted, connection) = input
            assertEquals(
                "granted=$granted connection=$connection",
                action,
                ListenerHealth.capabilityAction(granted, connection),
            )
        }
    }

    @Test
    fun `a granted connected listener survives repeated resumes without an event`() {
        val events = mutableListOf<SignalEvent>()
        ListenerHealth.onConnected()
        SignalObservability.register { event -> events += event }

        repeat(3) {
            val action = ListenerHealth.capabilityAction(true, ListenerHealth.connection.value)
            assertEquals(ListenerHealth.CapabilityAction.NONE, action)
        }

        assertEquals(ListenerHealth.Connection.CONNECTED, ListenerHealth.connection.value)
        assertEquals(emptyList<SignalEvent>(), events)
    }

    @Test
    fun `a listener that never called back is asked to rebind rather than left for dead`() {
        // Nothing else moves the state out of UNKNOWN, so a resume that did nothing here would
        // leave capture dead while the app still reported itself healthy.
        assertEquals(ListenerHealth.Connection.UNKNOWN, ListenerHealth.connection.value)

        repeat(3) {
            assertEquals(
                ListenerHealth.CapabilityAction.REQUEST_REBIND,
                ListenerHealth.capabilityAction(true, ListenerHealth.connection.value),
            )
        }
    }

    @Test
    fun `losing access announces itself once, not on every resume`() {
        val revocations = mutableListOf<SignalEvent>()
        ListenerHealth.onConnected()
        SignalObservability.register { event ->
            if (event.type == SignalEventType.ACCESS_REVOKED) revocations += event
        }

        repeat(3) {
            val action = ListenerHealth.capabilityAction(false, ListenerHealth.connection.value)
            if (action == ListenerHealth.CapabilityAction.MARK_REVOKED) ListenerHealth.onAccessRevoked()
        }

        assertEquals(ListenerHealth.Connection.DISCONNECTED, ListenerHealth.connection.value)
        assertEquals(1, revocations.size)
    }

    @Test
    fun `revocation is announced even when the platform disconnected the listener first`() {
        val revocations = mutableListOf<SignalEvent>()
        ListenerHealth.onConnected()
        // The platform can unbind a listener whose access the user still granted.
        ListenerHealth.onDisconnected()
        SignalObservability.register { event ->
            if (event.type == SignalEventType.ACCESS_REVOKED) revocations += event
        }

        val action = ListenerHealth.capabilityAction(false, ListenerHealth.connection.value)
        if (action == ListenerHealth.CapabilityAction.MARK_REVOKED) ListenerHealth.onAccessRevoked()

        assertEquals(ListenerHealth.CapabilityAction.MARK_REVOKED, action)
        assertEquals(1, revocations.size)
    }

    @Test
    fun `regaining access re-arms the revocation notice`() {
        ListenerHealth.onAccessRevoked()
        assertEquals(ListenerHealth.CapabilityAction.NONE, ListenerHealth.capabilityAction(false, ListenerHealth.connection.value))

        ListenerHealth.onConnected()
        ListenerHealth.onDisconnected()

        assertEquals(
            ListenerHealth.CapabilityAction.MARK_REVOKED,
            ListenerHealth.capabilityAction(false, ListenerHealth.connection.value),
        )
    }

    @Test
    fun `a second revocation is announced even when the listener never bound in between`() {
        // Revoke, re-grant, and let the platform fail to bind. Nothing calls onConnected here,
        // which is the path that used to leave the notice permanently spent.
        ListenerHealth.onAccessRevoked()
        assertEquals(
            ListenerHealth.CapabilityAction.REQUEST_REBIND,
            ListenerHealth.capabilityAction(true, ListenerHealth.connection.value),
        )

        assertEquals(
            ListenerHealth.CapabilityAction.MARK_REVOKED,
            ListenerHealth.capabilityAction(false, ListenerHealth.connection.value),
        )
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
