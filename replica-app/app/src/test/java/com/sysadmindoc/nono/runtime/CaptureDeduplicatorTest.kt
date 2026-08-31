package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RuleMatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDeduplicatorTest {

    private fun capture(
        key: String = "n-1",
        contentState: NotificationContentState = NotificationContentState.AVAILABLE,
        postedAt: Long = 1_000L,
        isOngoing: Boolean = false,
    ) = SanitizedNotification(
        notificationKey = key,
        packageName = "com.example.chat",
        postedAtEpochMillis = postedAt,
        contentState = contentState,
        isOngoing = isOngoing,
    )

    @Test
    fun `the posting time is not part of the fingerprint`() {
        // A repost always carries a new time. Including it would mean no repost ever matched the
        // one before it, which is the whole thing being detected here.
        assertEquals(
            captureFingerprint(capture(postedAt = 1_000L), emptyList(), RuleMatchState.EVALUATED),
            captureFingerprint(capture(postedAt = 9_999L), emptyList(), RuleMatchState.EVALUATED),
        )
    }

    @Test
    fun `a changed field changes the fingerprint`() {
        val base = captureFingerprint(capture(), emptyList(), RuleMatchState.EVALUATED)

        assertNotEquals(base, captureFingerprint(capture(isOngoing = true), emptyList(), RuleMatchState.EVALUATED))
        assertNotEquals(base, captureFingerprint(capture(), listOf(7L), RuleMatchState.EVALUATED))
        assertNotEquals(base, captureFingerprint(capture(), emptyList(), RuleMatchState.CONTENT_HIDDEN))
        assertNotEquals(
            base,
            captureFingerprint(capture(contentState = NotificationContentState.NOT_AVAILABLE), emptyList(), RuleMatchState.EVALUATED),
        )
    }

    @Test
    fun `matched rule order does not change the fingerprint`() {
        assertEquals(
            captureFingerprint(capture(), listOf(9L, 7L), RuleMatchState.EVALUATED),
            captureFingerprint(capture(), listOf(7L, 9L), RuleMatchState.EVALUATED),
        )
    }

    @Test
    fun `an unchanged burst inside the window is one capture`() {
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)

        assertTrue(deduplicator.shouldCapture("n-1", "same", 0L))
        assertFalse(deduplicator.shouldCapture("n-1", "same", 500L))
        assertFalse(deduplicator.shouldCapture("n-1", "same", 1_999L))
    }

    @Test
    fun `the same notification is captured again once the window has passed`() {
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)

        assertTrue(deduplicator.shouldCapture("n-1", "same", 0L))
        assertTrue(deduplicator.shouldCapture("n-1", "same", 2_000L))
    }

    @Test
    fun `a changed post is always captured, however fast it arrives`() {
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)

        assertTrue(deduplicator.shouldCapture("n-1", "first", 0L))
        assertTrue(deduplicator.shouldCapture("n-1", "second", 1L))
        // And the new fingerprint is what the next post is compared against.
        assertFalse(deduplicator.shouldCapture("n-1", "second", 2L))
    }

    @Test
    fun `different notifications never suppress one another`() {
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)

        assertTrue(deduplicator.shouldCapture("n-1", "same", 0L))
        assertTrue(deduplicator.shouldCapture("n-2", "same", 1L))
    }

    @Test
    fun `a clock that jumps backwards does not suppress a capture`() {
        // Wall-clock time can move backwards on a device. Losing a notification to that would be
        // worse than recording a duplicate.
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)

        assertTrue(deduplicator.shouldCapture("n-1", "same", 10_000L))
        assertTrue(deduplicator.shouldCapture("n-1", "same", 5_000L))
    }

    @Test
    fun `the map cannot grow without limit`() {
        val deduplicator = CaptureDeduplicator(windowMillis = 60_000L, maxEntries = 4)

        repeat(10) { index -> assertTrue(deduplicator.shouldCapture("n-$index", "same", index.toLong())) }

        // The oldest keys were evicted, so they are treated as unseen rather than retained.
        assertTrue(deduplicator.shouldCapture("n-0", "same", 11L))
        // The newest ones are still remembered.
        assertFalse(deduplicator.shouldCapture("n-9", "same", 12L))
    }
}
