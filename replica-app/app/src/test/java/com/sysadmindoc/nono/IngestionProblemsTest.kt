package com.sysadmindoc.nono

import com.sysadmindoc.nono.model.StatusMessages
import com.sysadmindoc.nono.runtime.IngestionMetrics
import com.sysadmindoc.nono.runtime.outstandingIngestionProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner has to distinguish "this is happening" from "this happened once, months ago".
 * Everything here is arithmetic the user never sees directly, which is exactly why it needs
 * pinning: a wrong subtraction shows up only as a warning that will not go away.
 */
class IngestionProblemsTest {

    private fun durable(
        dropped: Long = 0L,
        failed: Long = 0L,
        acknowledgedDropped: Long = 0L,
        acknowledgedFailed: Long = 0L,
        lastFailureAtEpochMillis: Long? = null,
    ) = IngestionMetrics(
        dropped = dropped,
        failed = failed,
        acknowledgedDropped = acknowledgedDropped,
        acknowledgedFailed = acknowledgedFailed,
        lastFailureAtEpochMillis = lastFailureAtEpochMillis,
    )

    @Test
    fun acknowledgedCountsAreNotReportedAsCurrent() {
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(),
            durable = durable(dropped = 7L, failed = 3L, acknowledgedDropped = 7L, acknowledgedFailed = 3L),
        )
        assertEquals(0L, problems.dropped)
        assertEquals(0L, problems.failed)
        assertFalse(problems.hasCurrentProblem)
        assertTrue("the history is kept, not erased", problems.hasAcknowledgedHistory)
    }

    @Test
    fun aFailureAfterAcknowledgementRaisesTheBannerAgain() {
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(),
            durable = durable(dropped = 7L, failed = 4L, acknowledgedDropped = 7L, acknowledgedFailed = 3L),
        )
        assertEquals(0L, problems.dropped)
        assertEquals(1L, problems.failed)
        assertTrue(problems.hasCurrentProblem)
    }

    @Test
    fun liveCountsShowBeforeTheFirstMergeIsWritten() {
        // The worker counts in memory and flushes to the database periodically. In that window the
        // durable row is behind, and reading it alone would hide a burst that is happening now.
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(dropped = 5L, failed = 2L),
            durable = durable(),
        )
        assertEquals(5L, problems.dropped)
        assertEquals(2L, problems.failed)
    }

    @Test
    fun acknowledgementSurvivesAProcessRestart() {
        // Live counters reset to zero with the process; the acknowledgement must still hold.
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(),
            durable = durable(dropped = 9L, failed = 9L, acknowledgedDropped = 9L, acknowledgedFailed = 9L),
        )
        assertFalse(problems.hasCurrentProblem)
    }

    @Test
    fun anAcknowledgementAheadOfTheTotalsNeverGoesNegative() {
        // Reachable if a merge is rolled back after the acknowledgement was written.
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(),
            durable = durable(dropped = 1L, failed = 1L, acknowledgedDropped = 4L, acknowledgedFailed = 4L),
        )
        assertEquals(0L, problems.dropped)
        assertEquals(0L, problems.failed)
    }

    @Test
    fun theLastFailureTimeComesFromTheDurableRecord() {
        val problems = outstandingIngestionProblems(
            live = IngestionMetrics(failed = 1L),
            durable = durable(failed = 1L, lastFailureAtEpochMillis = 1_700_000_000_000L),
        )
        assertEquals(1_700_000_000_000L, problems.lastFailureAtEpochMillis)
    }

    @Test
    fun aFailedWriteNeverProducesASuccessSentence() {
        val failures = listOf(
            StatusMessages.starOutcome(updated = false, starred = true),
            StatusMessages.starOutcome(updated = false, starred = false),
            StatusMessages.deleteOutcome(removed = false),
            StatusMessages.restoreOutcome(restored = false),
            StatusMessages.acknowledgementOutcome(acknowledged = false),
            StatusMessages.exportFailure(partialRemoved = true),
            StatusMessages.exportFailure(partialRemoved = false),
        )
        for (message in failures) {
            assertFalse("a failure must say something", message.isNullOrBlank())
            assertFalse(
                "\"$message\" reads as a success",
                StatusMessages.successPhrases.any { message == it },
            )
        }
    }

    @Test
    fun aConfirmedWriteIsTheOnlyRouteToASuccessSentence() {
        assertEquals("Kept until you unstar it.", StatusMessages.starOutcome(updated = true, starred = true))
        assertEquals("No longer kept.", StatusMessages.starOutcome(updated = true, starred = false))
        assertEquals("Record deleted.", StatusMessages.deleteOutcome(removed = true))
        // Silence is the success case for these two: nothing was asked for, so nothing is said.
        assertEquals(null, StatusMessages.restoreOutcome(restored = true))
        assertEquals(null, StatusMessages.acknowledgementOutcome(acknowledged = true))
    }

    @Test
    fun aFailedExportSaysWhatIsAtTheDestination() {
        // "nothing on this device was changed" used to be said whatever happened, over a file the
        // user can see sitting there half written.
        val removed = StatusMessages.exportFailure(partialRemoved = true)
        val left = StatusMessages.exportFailure(partialRemoved = false)

        assertTrue(removed, removed.contains("removed"))
        assertFalse("a removed file must not be described as possibly incomplete", removed.contains("incomplete"))
        assertTrue(left, left.contains("may be incomplete"))
    }

    @Test
    fun pausingCaptureIsAnnouncedRatherThanSilent() {
        val paused = StatusMessages.captureOutcome(paused = true)
        val resumed = StatusMessages.captureOutcome(paused = false)

        assertTrue(paused, paused.contains("paused", ignoreCase = true))
        assertTrue("the consequence has to be stated: $paused", paused.contains("Nothing is being recorded"))
        assertTrue(resumed, resumed.contains("resumed", ignoreCase = true))
    }

    @Test
    fun anImportSaysWhatItActuallyDid() {
        // Replacing five conflicting rules and adding none used to read "Imported 0 new rule(s).",
        // which describes a no-op over a change to every rule the file touched.
        assertEquals(
            "Replaced 5 rules. Notification history was not imported.",
            StatusMessages.importOutcome(added = 0, replaced = 5, channelReselections = 0),
        )
        assertEquals(
            "Imported 2 new rules and replaced 5 rules. Notification history was not imported.",
            StatusMessages.importOutcome(added = 2, replaced = 5, channelReselections = 0),
        )
        assertEquals(
            "Imported 1 new rule. Notification history was not imported.",
            StatusMessages.importOutcome(added = 1, replaced = 0, channelReselections = 0),
        )
        assertEquals(
            "Nothing was imported. Notification history was not imported.",
            StatusMessages.importOutcome(added = 0, replaced = 0, channelReselections = 0),
        )
    }

    @Test
    fun theChannelReminderIsCountedRatherThanBracketed() {
        val one = StatusMessages.importOutcome(added = 1, replaced = 0, channelReselections = 1)
        val several = StatusMessages.importOutcome(added = 1, replaced = 0, channelReselections = 3)

        assertTrue(one, one.endsWith("Select 1 channel filter again before those rules can match."))
        assertTrue(several, several.endsWith("Select 3 channel filters again before those rules can match."))
    }
}
