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
}
