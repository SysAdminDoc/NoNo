package com.anm.signalrules.reconstruction.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerStalenessTest {

    private val now = 1_000_000_000_000L
    private val threshold = DEFAULT_LISTENER_STALE_AFTER_MILLIS

    private fun activity(
        accessGranted: Boolean = true,
        connection: ListenerHealth.Connection = ListenerHealth.Connection.CONNECTED,
        capturePaused: Boolean = false,
        lastEventAtEpochMillis: Long? = now - threshold,
    ) = listenerActivity(
        accessGranted = accessGranted,
        connection = connection,
        capturePaused = capturePaused,
        lastEventAtEpochMillis = lastEventAtEpochMillis,
        nowEpochMillis = now,
        staleAfterMillis = threshold,
    )

    @Test
    fun silenceIsReportedOnlyOnceItReachesTheThreshold() {
        assertEquals(ListenerActivity.HEALTHY, activity(lastEventAtEpochMillis = now - threshold + 1))
        assertEquals(ListenerActivity.STALE, activity(lastEventAtEpochMillis = now - threshold))
        assertEquals(ListenerActivity.STALE, activity(lastEventAtEpochMillis = now - threshold - 1))
    }

    @Test
    fun aListenerThatIsAlreadyKnownToBeDisconnectedHasItsOwnMessage() {
        assertEquals(
            ListenerActivity.HEALTHY,
            activity(connection = ListenerHealth.Connection.DISCONNECTED, lastEventAtEpochMillis = 0L),
        )
    }

    @Test
    fun silenceWithoutAccessIsNotAListenerProblem() {
        assertEquals(ListenerActivity.HEALTHY, activity(accessGranted = false, lastEventAtEpochMillis = 1L))
    }

    @Test
    fun silenceTheUserAskedForIsNotReported() {
        assertEquals(ListenerActivity.HEALTHY, activity(capturePaused = true, lastEventAtEpochMillis = 1L))
    }

    @Test
    fun anInstallThatHasNeverCapturedAnythingIsNotCalledStale() {
        // There is no evidence it ever worked, so silence proves nothing.
        assertEquals(ListenerActivity.HEALTHY, activity(lastEventAtEpochMillis = null))
    }

    @Test
    fun aClockThatMovedBackwardsIsIgnored() {
        assertEquals(ListenerActivity.HEALTHY, activity(lastEventAtEpochMillis = now + 60_000L))
    }

    @Test
    fun everyBrandGetsStepsAndTheGenericListIsNeverEmpty() {
        listOf("samsung", "Xiaomi", "HUAWEI", "OnePlus", "vivo", "Fairphone", "").forEach { brand ->
            val steps = oemListenerChecklist(brand, sdkInt = 34)
            assertTrue("no steps for $brand", steps.size >= 3)
            assertTrue("battery step missing for $brand", steps.first().contains("battery", ignoreCase = true))
            assertTrue("em dash in copy for $brand", steps.none { step -> step.any { it == '—' || it == '–' } })
        }
    }

    @Test
    fun theRestrictedSettingStepAppearsOnlyWhereAndroidHasThatBlock() {
        val modern = oemListenerChecklist("samsung", sdkInt = 33)
        val older = oemListenerChecklist("samsung", sdkInt = 32)

        assertTrue(modern.any { it.contains("restricted settings", ignoreCase = true) })
        assertTrue(older.none { it.contains("restricted settings", ignoreCase = true) })
    }

    @Test
    fun brandStepsAreSpecificRatherThanTheGenericFallback() {
        val xiaomi = oemListenerChecklist("xiaomi", sdkInt = 34)
        val unknown = oemListenerChecklist("some-new-brand", sdkInt = 34)

        assertTrue(xiaomi.any { it.contains("Autostart") })
        assertTrue(unknown.any { it.contains("autostart", ignoreCase = true) })
        assertTrue(xiaomi != unknown)
    }
}
