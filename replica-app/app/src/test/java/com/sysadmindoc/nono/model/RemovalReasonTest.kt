package com.sysadmindoc.nono.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping from Android's removal codes.
 *
 * The whole value of this data is that it is what the platform said, so the tests that matter are
 * the ones about not saying more than it did: an old platform that supplies no code, a code this
 * build has never seen, and lockdown, which is not a removal at all.
 */
class RemovalReasonTest {

    private val api26 = 26
    private val api36 = 36
    private val api37 = 37
    private val bundleDismissedCode = 24

    @Test
    fun `a platform below api 26 supplies no reason and none is recorded`() {
        // The three-argument callback does not exist there, and an OEM that back-ports it is not
        // a source this build trusts to use the same numbers.
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(2, sdkInt = 25))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(2, sdkInt = 24))
    }

    @Test
    fun `a missing code is unknown on every level`() {
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(null, api26))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(null, api37))
    }

    @Test
    fun `the api 26 codes map to what they mean`() {
        assertEquals(RemovalReason.CLICKED, RemovalReason.fromPlatform(1, api26))
        assertEquals(RemovalReason.DISMISSED, RemovalReason.fromPlatform(2, api26))
        assertEquals(RemovalReason.DISMISSED_ALL, RemovalReason.fromPlatform(3, api26))
        assertEquals(RemovalReason.PLATFORM_ERROR, RemovalReason.fromPlatform(4, api26))
        assertEquals(RemovalReason.WITHDRAWN_BY_APP, RemovalReason.fromPlatform(8, api26))
        assertEquals(RemovalReason.WITHDRAWN_BY_APP, RemovalReason.fromPlatform(9, api26))
        assertEquals(RemovalReason.CANCELLED_BY_LISTENER, RemovalReason.fromPlatform(10, api26))
        assertEquals(RemovalReason.CANCELLED_BY_LISTENER, RemovalReason.fromPlatform(11, api26))
        assertEquals(RemovalReason.GROUP_MANAGEMENT, RemovalReason.fromPlatform(12, api26))
        assertEquals(RemovalReason.GROUP_MANAGEMENT, RemovalReason.fromPlatform(13, api26))
        assertEquals(RemovalReason.SNOOZED, RemovalReason.fromPlatform(18, api26))
        assertEquals(RemovalReason.TIMED_OUT, RemovalReason.fromPlatform(19, api26))
    }

    @Test
    fun `the codes added after api 26 map on a current platform`() {
        assertEquals(RemovalReason.GROUP_MANAGEMENT, RemovalReason.fromPlatform(16, api37))
        assertEquals(RemovalReason.APP_STATE_CHANGED, RemovalReason.fromPlatform(17, api37))
        assertEquals(RemovalReason.APP_STATE_CHANGED, RemovalReason.fromPlatform(20, api37))
        assertEquals(RemovalReason.APP_STATE_CHANGED, RemovalReason.fromPlatform(21, api37))
        assertEquals(RemovalReason.ASSISTANT, RemovalReason.fromPlatform(22, api37))
    }

    @Test
    fun `bundle dismissal maps only on api 37 and does not claim a user action`() {
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(bundleDismissedCode, api26))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(bundleDismissedCode, api36))

        val reason = RemovalReason.fromPlatform(bundleDismissedCode, api37)
        assertEquals(RemovalReason.BUNDLE_DISMISSED, reason)
        assertEquals("Cleared with its bundle", reason.label)
        assertFalse(reason.userDismissed)
    }

    @Test
    fun `every state of the app underneath reads as the app changing, not as the user acting`() {
        val appState = listOf(5, 6, 7, 14, 15, 17, 20, 21)
        for (code in appState) {
            val reason = RemovalReason.fromPlatform(code, api37)
            assertEquals("code $code", RemovalReason.APP_STATE_CHANGED, reason)
            assertFalse("code $code must not read as the user dismissing", reason.userDismissed)
        }
    }

    @Test
    fun `lockdown is not recorded as a removal reason`() {
        // Lockdown hides notifications. The notification is not gone, and writing "the device was
        // locked down at this time" into an exportable history would be a second problem.
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromPlatform(23, api37))
    }

    @Test
    fun `a code this build has never seen stays unknown`() {
        for (code in listOf(0, -1, 25, 99, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("code $code", RemovalReason.UNKNOWN, RemovalReason.fromPlatform(code, api37))
        }
    }

    @Test
    fun `only the three user actions count as a dismissal`() {
        val dismissals = RemovalReason.entries.filter { it.userDismissed }
        assertEquals(
            listOf(RemovalReason.CLICKED, RemovalReason.DISMISSED, RemovalReason.DISMISSED_ALL),
            dismissals,
        )
        assertFalse("an unknown reason is not a dismissal", RemovalReason.UNKNOWN.userDismissed)
        assertFalse("the app withdrawing it is not the user", RemovalReason.WITHDRAWN_BY_APP.userDismissed)
        assertFalse("a timeout is not the user", RemovalReason.TIMED_OUT.userDismissed)
    }

    @Test
    fun `a stored name written by a build that knew more reads as unknown`() {
        assertEquals(RemovalReason.DISMISSED, RemovalReason.fromStored("DISMISSED"))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromStored("SOMETHING_LATER"))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromStored(null))
        assertEquals(RemovalReason.UNKNOWN, RemovalReason.fromStored(""))
    }

    @Test
    fun `every reason has a label a person can read`() {
        for (reason in RemovalReason.entries) {
            assertTrue(reason.name, reason.label.isNotBlank())
            assertFalse("$reason reads like a constant", reason.label == reason.name)
        }
    }
}
