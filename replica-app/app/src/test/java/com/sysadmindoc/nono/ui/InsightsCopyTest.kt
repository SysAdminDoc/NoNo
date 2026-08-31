package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.InsightTotals
import com.sysadmindoc.nono.model.LocalInsights
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.buildLocalInsights
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Insights entry point and totals card say, given what the aggregates found. */
class InsightsCopyTest {

    private fun insights(stored: Int, captured: Int, summaries: Int): LocalInsights =
        buildLocalInsights(
            InsightTotals(storedRecordCount = stored, totalCaptured = captured, excludedGroupSummaries = summaries),
            emptyList(),
            emptyList(),
            emptyList(),
            0L,
            TimeZone.getTimeZone("UTC"),
            Locale.US,
        )

    @Test
    fun theTotalsLineNamesTheSummariesItLeftOut() {
        assertEquals(
            "From 10 stored records, excluding 2 group summaries.",
            describeStoredRecords(insights(10, 8, 2), 10),
        )
    }

    @Test
    fun asingleSummaryReadsAsOne() {
        assertEquals(
            "From 2 stored records, excluding 1 group summary.",
            describeStoredRecords(insights(2, 1, 1), 2),
        )
    }

    @Test
    fun withNoSummariesTheLineDoesNotMentionAnExclusion() {
        val line = describeStoredRecords(insights(5, 5, 0), 5)

        assertEquals("From 5 stored records.", line)
    }

    @Test
    fun aSingleRecordIsNotCalledRecords() {
        assertEquals("From 1 stored record.", describeStoredRecords(insights(1, 1, 0), 1))
    }

    @Test
    fun aTotalThatDisagreesWithHistorySaysSoRatherThanStatingAWrongNumber() {
        // The two counts are separate reads. A capture landing between them is normal, and the
        // screen should say the numbers are moving instead of presenting a contradiction.
        assertEquals(
            "Counts are still catching up with History.",
            describeStoredRecords(insights(10, 8, 2), 11),
        )
    }

    @Test
    fun theExploreRowExplainsItselfBeforeThereIsAnythingToCount() {
        assertEquals(
            "Counts appear once History has something in it.",
            describeInsightsEntry(UiState()),
        )
    }

    @Test
    fun theExploreRowReportsTheCapturedTotalOnceThereIsOne() {
        val state = UiState(insights = insights(10, 8, 2))

        assertEquals("8 captured, and what they add up to.", describeInsightsEntry(state))
    }

    @Test
    fun noneOfTheCopyUsesADash() {
        val lines = listOf(
            describeStoredRecords(insights(10, 8, 2), 10),
            describeStoredRecords(insights(5, 5, 0), 5),
            describeStoredRecords(insights(10, 8, 2), 11),
            describeInsightsEntry(UiState()),
            describeInsightsEntry(UiState(insights = insights(10, 8, 2))),
        )

        assertTrue(lines.none { line -> line.any { it == '—' || it == '–' } })
    }
}
