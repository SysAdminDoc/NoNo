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
            StatusMessages.restoreOutcome(StatusMessages.RestoreOutcome.ALREADY_PRESENT),
            StatusMessages.restoreOutcome(StatusMessages.RestoreOutcome.FAILED),
            StatusMessages.acknowledgementOutcome(acknowledged = false),
            StatusMessages.exportFailure(reachedTheFile = false, partialRemoved = false),
            StatusMessages.exportFailure(reachedTheFile = true, partialRemoved = true),
            StatusMessages.exportFailure(reachedTheFile = true, partialRemoved = false),
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
        assertEquals(null, StatusMessages.restoreOutcome(StatusMessages.RestoreOutcome.RESTORED))
        assertEquals(null, StatusMessages.acknowledgementOutcome(acknowledged = true))
    }

    @Test
    fun aRestoreThatThrewIsNotReportedAsARecordThatIsBack() {
        // The DAO returns false both when the key was already present (the app reposted it, so
        // the record genuinely is back) and, through runCatching, when the insert threw (the
        // record is gone). One sentence covered both and told the second case the opposite of
        // the truth. This is why the old single-string assertion had to change.
        val collision = StatusMessages.restoreOutcome(StatusMessages.RestoreOutcome.ALREADY_PRESENT)
        val failure = StatusMessages.restoreOutcome(StatusMessages.RestoreOutcome.FAILED)

        assertEquals("That record is already back on this device.", collision)
        assertEquals("That record could not be restored.", failure)
        assertFalse("a failure must not claim the record is present: $failure", failure!!.contains("back on this device"))
    }

    @Test
    fun aFailedExportSaysWhatIsAtTheDestination() {
        // Three outcomes, and the first is the one that matters most: a write that never opened
        // the file left the user's existing export exactly as it was, and nothing should suggest
        // otherwise or go near it.
        val untouched = StatusMessages.exportFailure(reachedTheFile = false, partialRemoved = false)
        val removed = StatusMessages.exportFailure(reachedTheFile = true, partialRemoved = true)
        val left = StatusMessages.exportFailure(reachedTheFile = true, partialRemoved = false)

        assertTrue(untouched, untouched.contains("unchanged"))
        assertFalse("nothing was written, so nothing was removed", untouched.contains("removed"))
        assertTrue(removed, removed.contains("removed"))
        assertFalse("a removed file cannot also be sitting there incomplete", removed.contains("incomplete"))
        assertTrue(left, left.contains("incomplete"))
        assertEquals("each outcome needs its own sentence", 3, listOf(untouched, removed, left).distinct().size)
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
    fun keepingTheExistingRulesSaysHowManyTheFileHeld() {
        // The mirror of the replace case. "Imported 0 new rules." over a file of five rules the
        // user chose to skip describes an empty file rather than a decision they made.
        assertEquals(
            "Nothing was imported. 5 rules in the file already existed and were left alone. " +
                "Notification history was not imported.",
            StatusMessages.importOutcome(added = 0, replaced = 0, kept = 5),
        )
        assertEquals(
            "Imported 2 new rules. 1 rule in the file already existed and was left alone. " +
                "Notification history was not imported.",
            StatusMessages.importOutcome(added = 2, replaced = 0, kept = 1),
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
