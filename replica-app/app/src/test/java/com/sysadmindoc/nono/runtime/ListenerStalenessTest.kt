package com.sysadmindoc.nono.runtime

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
        val generic = oemListenerChecklist("some-new-brand", sdkInt = 34)
        assertTrue(generic.any { it.contains("autostart", ignoreCase = true) })

        // Every brand named in the acceptance criterion needs a step no other brand supplies,
        // otherwise deleting its branch silently falls back to the generic list.
        val expected = mapOf(
            "samsung" to "put to sleep",
            "xiaomi" to "Autostart",
            "huawei" to "App launch",
            "oneplus" to "Auto-launch",
            "oppo" to "Auto-launch",
            "vivo" to "Auto-start",
        )
        expected.forEach { (brand, marker) ->
            val steps = oemListenerChecklist(brand, sdkInt = 34)
            assertTrue("$brand is missing its own step", steps.any { it.contains(marker, ignoreCase = true) })
            assertTrue("$brand fell back to the generic list", steps != generic)
            assertTrue(
                "$brand should not also carry the generic fallback line",
                steps.none { it.contains("Look for an autostart", ignoreCase = true) },
            )
        }
    }
}
