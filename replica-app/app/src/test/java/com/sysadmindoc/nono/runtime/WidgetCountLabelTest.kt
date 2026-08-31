package com.sysadmindoc.nono.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's number stands for "notifications that arrived", which is not the same as "rows
 * stored". Leaving that difference silent made the widget disagree with History for no reason
 * the user could see. The scope adds a second way to be misread, so the label names it.
 */
class WidgetCountLabelTest {

    @Test
    fun anEmptyStoreSaysSoRatherThanReportingZero() {
        assertEquals("No metadata captured", SignalWidgetProvider.countLabel(WidgetScope.ALL_CAPTURED, 0, 0))
    }

    @Test
    fun withNoSummariesTheCountStandsAlone() {
        assertEquals("12 notifications", SignalWidgetProvider.countLabel(WidgetScope.ALL_CAPTURED, 12, 0))
    }

    @Test
    fun storedSummariesAreNamedAndExcludedFromTheCount() {
        val label = SignalWidgetProvider.countLabel(WidgetScope.ALL_CAPTURED, 12, 3)

        assertTrue(label.startsWith("12 notifications"))
        assertTrue("the label must say what it left out", label.contains("3 group summaries"))
        assertTrue(label.contains("not counted"))
    }

    @Test
    fun aStoreOfNothingButSummariesDoesNotReadAsEmpty() {
        // Otherwise the widget says "No metadata captured" while History shows three rows.
        val label = SignalWidgetProvider.countLabel(WidgetScope.ALL_CAPTURED, 0, 3)

        assertTrue(label.contains("3 group summaries"))
        assertTrue("no notifications arrived, and that is what it should say", label.contains("no notifications"))
    }

    @Test
    fun everyScopeNamesWhatItCounted() {
        // The same number means three different things. A bare "12" cannot say which.
        assertEquals("12 rule matches", SignalWidgetProvider.countLabel(WidgetScope.RULE_MATCHED, 12, 0))
        assertEquals("12 starred notifications", SignalWidgetProvider.countLabel(WidgetScope.STARRED, 12, 0))
        assertEquals("No rule matches", SignalWidgetProvider.countLabel(WidgetScope.RULE_MATCHED, 0, 0))
        assertEquals("No starred notifications", SignalWidgetProvider.countLabel(WidgetScope.STARRED, 0, 0))
    }

    @Test
    fun oneOfSomethingIsSingular() {
        assertEquals("1 rule match", SignalWidgetProvider.countLabel(WidgetScope.RULE_MATCHED, 1, 0))
        assertEquals("1 starred notification", SignalWidgetProvider.countLabel(WidgetScope.STARRED, 1, 0))
    }

    @Test
    fun eachScopeReadsTheCountItsLabelNames() {
        // Wiring a scope to the wrong query would put a plausible number under a label meaning
        // something else, which is the one failure nobody looking at the widget could detect.
        assertEquals(7, countFor(WidgetScope.ALL_CAPTURED, allCaptured = 7, ruleMatched = 3, starred = 1))
        assertEquals(3, countFor(WidgetScope.RULE_MATCHED, allCaptured = 7, ruleMatched = 3, starred = 1))
        assertEquals(1, countFor(WidgetScope.STARRED, allCaptured = 7, ruleMatched = 3, starred = 1))
    }

    @Test
    fun noTwoScopesReadTheSameCount() {
        val counts = WidgetScope.entries.map { countFor(it, allCaptured = 7, ruleMatched = 3, starred = 1) }

        assertEquals(WidgetScope.entries.size, counts.distinct().size)
    }

    @Test
    fun theRuleScopeIsNamedAsHistoryNamesTheSameFilter() {
        // History calls it "Rule-triggered". Two names for one idea reads as two ideas.
        assertEquals("Rule-triggered", WidgetScope.RULE_MATCHED.label)
    }

    @Test
    fun aNarrowedScopeDoesNotRepeatTheSummaryNote() {
        // Group summaries are excluded from every scope. Saying so beside a rule-match count would
        // suggest some of those matches were summaries, which they never are.
        val matched = SignalWidgetProvider.countLabel(WidgetScope.RULE_MATCHED, 12, 3)
        val starred = SignalWidgetProvider.countLabel(WidgetScope.STARRED, 12, 3)

        assertEquals("12 rule matches", matched)
        assertEquals("12 starred notifications", starred)
    }

    @Test
    fun everyScopeReadsDifferentlyForTheSameNumbers() {
        val labels = WidgetScope.entries.map { SignalWidgetProvider.countLabel(it, 12, 0) }

        assertEquals(WidgetScope.entries.size, labels.distinct().size)
    }

    @Test
    fun anUnknownStoredScopeFallsBackToCountingEverything() {
        // An older build could persist a label this one does not offer, and a widget that silently
        // counted nothing would look like capture had stopped.
        assertEquals(WidgetScope.ALL_CAPTURED, widgetScope(null))
        assertEquals(WidgetScope.ALL_CAPTURED, widgetScope(""))
        assertEquals(WidgetScope.ALL_CAPTURED, widgetScope("Filtered"))
    }

    @Test
    fun aStoredScopeResolvesWhateverItsSpacingOrCase() {
        assertEquals(WidgetScope.ALL_CAPTURED, widgetScope("All captured"))
        assertEquals(WidgetScope.RULE_MATCHED, widgetScope(" rule-triggered "))
        assertEquals(WidgetScope.STARRED, widgetScope("STARRED"))
    }

    @Test
    fun noLabelUsesADash() {
        val labels = WidgetScope.entries.flatMap { scope ->
            listOf(0, 1, 12).map { SignalWidgetProvider.countLabel(scope, it, 3) }
        }

        assertTrue(labels.none { label -> label.any { it == '—' || it == '–' } })
    }
}
