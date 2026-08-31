package com.sysadmindoc.nono.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDiagnosticsTest {
    @Test
    fun reportContainsOnlyTheRequestedOperationalFields() {
        val report = buildCaptureDiagnosticsReport(
            CaptureDiagnosticsSnapshot(
                appVersion = "1.4.1-reconstruction",
                accessGranted = true,
                connection = ListenerHealth.Connection.CONNECTED,
                metrics = IngestionMetrics(queued = 2, persisted = 18, dropped = 1, failed = 3),
                lastCaptureAtEpochMillis = 1_000L,
                nowEpochMillis = 121_000L,
            ),
        )

        assertEquals(
            """NoNo capture diagnostics
App version: 1.4.1-reconstruction
Notification access: Granted
Listener connection: Connected
Ingestion queued: 2
Ingestion persisted: 18
Ingestion dropped: 1
Ingestion failed: 3
Last capture age: 2 minutes
Privacy: no notification content or posting-app, channel, group, or rule identifiers are included.""",
            report,
        )
        assertFalse(report.contains("packageName"))
        assertFalse(report.contains("notificationKey"))
        assertFalse(report.contains("title="))
        assertFalse(report.contains("text="))
    }

    @Test
    fun durableTotalsAndLiveQueueProduceOneCurrentSnapshot() {
        val combined = combinedIngestionMetrics(
            live = IngestionMetrics(queued = 4, persisted = 2, dropped = 3, failed = 1),
            durable = IngestionMetrics(
                persisted = 20,
                dropped = 2,
                failed = 5,
                acknowledgedDropped = 1,
                acknowledgedFailed = 4,
            ),
        )

        assertEquals(4, combined.queued)
        assertEquals(20L, combined.persisted)
        assertEquals(3L, combined.dropped)
        assertEquals(5L, combined.failed)
        assertEquals(1L, combined.acknowledgedDropped)
        assertEquals(4L, combined.acknowledgedFailed)
    }

    @Test
    fun captureAgeIsRelativeAndHandlesClockChanges() {
        assertEquals("Never recorded", captureAge(null, 100L))
        assertEquals("Less than a minute", captureAge(50_000L, 100_000L))
        assertEquals("1 minute", captureAge(40_000L, 100_000L))
        assertEquals("1 hour", captureAge(0L, 3_600_000L))
        assertEquals("2 days", captureAge(0L, 172_800_000L))
        assertTrue(captureAge(200L, 100L).contains("clock moved backwards"))
    }

    @Test
    fun failureGuidanceIncludesTheManufacturerStep() {
        val guidance = captureSelfTestFailureGuidance("Xiaomi", 35)

        assertTrue(guidance.contains("unrestricted battery use"))
        assertTrue(guidance.contains("Autostart"))
    }
}
