package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.INSIGHT_TOP_RULE_LIMIT
import com.sysadmindoc.nono.model.InsightTotals
import com.sysadmindoc.nono.model.LocalInsights
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.buildLocalInsights
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Insights entry point, the totals card, and the two empty states say. */
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
            describeStoredRecords(insights(10, 8, 2)),
        )
    }

    @Test
    fun aSingleSummaryReadsAsOne() {
        assertEquals(
            "From 2 stored records, excluding 1 group summary.",
            describeStoredRecords(insights(2, 1, 1)),
        )
    }

    @Test
    fun withNoSummariesTheLineDoesNotMentionAnExclusion() {
        assertEquals("From 5 stored records.", describeStoredRecords(insights(5, 5, 0)))
    }

    @Test
    fun aSingleRecordIsNotCalledRecords() {
        assertEquals("From 1 stored record.", describeStoredRecords(insights(1, 1, 0)))
    }

    @Test
    fun aHistoryOfNothingButSummariesIsNotCalledEmpty() {
        // History visibly lists those records. Telling the user there is nothing here would
        // contradict the screen they just came from.
        val summariesOnly = insights(40, 0, 40)

        assertTrue(summariesOnly.onlyGroupSummaries)
        assertTrue(!summariesOnly.isEmpty)
        assertEquals("Only group summaries so far", emptyInsightsTitle(summariesOnly))
        assertTrue(emptyInsightsDetail(summariesOnly).contains("40 group summaries"))
    }

    @Test
    fun aTrulyEmptyHistorySaysNothingHasBeenCaptured() {
        val nothing = insights(0, 0, 0)

        assertTrue(nothing.isEmpty)
        assertTrue(!nothing.onlyGroupSummaries)
        assertEquals("Nothing to count yet", emptyInsightsTitle(nothing))
        assertTrue(emptyInsightsDetail(nothing).contains("Once capture has recorded some"))
    }

    @Test
    fun theExploreRowExplainsItselfBeforeThereIsAnythingToCount() {
        assertEquals(
            "Counts appear once History has something in it.",
            describeInsightsEntry(UiState()),
        )
    }

    @Test
    fun theExploreRowReadsTheHistoryTotalBecauseTheAggregatesAreNotCollectedThere() {
        // The insight aggregates only run while the Insights screen is open, so on Explore they
        // are always zero and a row built from them would always claim nothing was captured.
        assertEquals(
            "12 stored records, and what they add up to.",
            describeInsightsEntry(UiState(historyTotalCount = 12, insights = LocalInsights())),
        )
        assertEquals(
            "1 stored record, and what it adds up to.",
            describeInsightsEntry(UiState(historyTotalCount = 1)),
        )
    }

    @Test
    fun aCutRuleListSaysItWasCut() {
        // A rule missing from the list would otherwise read as a rule that never matched.
        assertNull(describeHiddenRules(INSIGHT_TOP_RULE_LIMIT))
        assertNull(describeHiddenRules(0))
        assertEquals(
            "1 more rule is saved. This list shows the $INSIGHT_TOP_RULE_LIMIT with the most matches.",
            describeHiddenRules(INSIGHT_TOP_RULE_LIMIT + 1),
        )
        assertTrue(
            describeHiddenRules(INSIGHT_TOP_RULE_LIMIT + 40)!!.startsWith("40 more rules are saved."),
        )
    }

    @Test
    fun noneOfTheCopyUsesADash() {
        val lines = listOfNotNull(
            describeStoredRecords(insights(10, 8, 2)),
            describeStoredRecords(insights(5, 5, 0)),
            emptyInsightsTitle(insights(40, 0, 40)),
            emptyInsightsDetail(insights(40, 0, 40)),
            emptyInsightsTitle(insights(0, 0, 0)),
            emptyInsightsDetail(insights(0, 0, 0)),
            describeInsightsEntry(UiState()),
            describeInsightsEntry(UiState(historyTotalCount = 12)),
            describeHiddenRules(INSIGHT_TOP_RULE_LIMIT + 3),
        )

        assertTrue(lines.none { line -> line.any { it == '—' || it == '–' } })
    }
}
