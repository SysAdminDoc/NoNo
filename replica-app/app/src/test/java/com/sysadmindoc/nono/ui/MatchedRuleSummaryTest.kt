package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.filterHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchedRuleSummaryTest {

    private val rules = listOf(
        SignalRule(id = 7L, name = "Chat"),
        SignalRule(id = 9L, name = "Invoices"),
    )

    @Test
    fun aRecordWithNoMatchesSaysNothing() {
        assertNull(describeMatchedRules(HistoryRecord(id = 1L), rules))
    }

    @Test
    fun matchedRulesAreNamedAndNeverClaimToHaveRun() {
        val line = describeMatchedRules(
            HistoryRecord(id = 1L, matchedRuleIds = listOf(7L, 9L), matchState = RuleMatchState.EVALUATED),
            rules,
        )

        assertEquals("Would match: Chat, Invoices", line)
        assertTrue(line!!.none { it in setOf('—', '–') })
    }

    @Test
    fun aRuleDeletedSinceTheCaptureIsReportedRatherThanDropped() {
        val line = describeMatchedRules(
            HistoryRecord(id = 1L, matchedRuleIds = listOf(7L, 42L), matchState = RuleMatchState.EVALUATED),
            rules,
        )

        assertEquals("Would match: Chat, deleted rule 42", line)
    }

    @Test
    fun activityReadsTheStoredMatchRatherThanReEvaluating() {
        // A stored row replays with no text, so re-running the current rules against it answers a
        // different question from the one the screen asks, and can contradict what capture saw.
        val record = HistoryRecord(
            id = 1L,
            matchedRuleIds = listOf(7L, 9L),
            matchState = RuleMatchState.EVALUATED,
            contentState = NotificationContentState.NOT_STORED,
        )

        val attribution = captureAttribution(record, rules)

        assertEquals(listOf(7L, 9L), attribution.rules.map { it.id })
        assertEquals(listOf("Chat", "Invoices"), attribution.rules.map { it.name })
        assertEquals("Matched 2 rules", attribution.headline)
        assertTrue(attribution.rules.none { it.deleted })
    }

    @Test
    fun aDeletedRuleIsShownByIdRatherThanDisappearing() {
        val record = HistoryRecord(id = 1L, matchedRuleIds = listOf(7L, 42L), matchState = RuleMatchState.EVALUATED)

        val attribution = captureAttribution(record, rules)

        assertEquals(listOf(7L, 42L), attribution.rules.map { it.id })
        val deleted = attribution.rules.single { it.deleted }
        assertEquals(42L, deleted.id)
        assertEquals("Deleted rule 42", deleted.name)
    }

    @Test
    fun attributionSurvivesARenamedRule() {
        // Editing a rule changes what it is called, not what it did when the record arrived.
        val record = HistoryRecord(id = 1L, matchedRuleIds = listOf(7L), matchState = RuleMatchState.EVALUATED)
        val renamed = rules.map { if (it.id == 7L) it.copy(name = "Chat, renamed") else it }

        val attribution = captureAttribution(record, renamed)

        assertEquals(listOf(7L), attribution.rules.map { it.id })
        assertEquals("Matched Chat, renamed", attribution.headline)
    }

    @Test
    fun aRuleEditedToStopMatchingStillShowsOnTheRecordItMatched() {
        // The rule no longer matches anything like this record. The record still says it did.
        val record = HistoryRecord(id = 1L, matchedRuleIds = listOf(7L), matchState = RuleMatchState.EVALUATED)
        val narrowed = rules.map { if (it.id == 7L) it.copy(phrase = "something else entirely") else it }

        assertEquals(listOf(7L), captureAttribution(record, narrowed).rules.map { it.id })
    }

    @Test
    fun eachStoredMatchStateExplainsItselfDistinctly() {
        val details = RuleMatchState.entries.map { state ->
            captureAttribution(HistoryRecord(id = 1L, matchState = state), rules).evaluationDetail
        }

        assertEquals("every state needs its own explanation", RuleMatchState.entries.size, details.distinct().size)
        assertTrue(details.none { it.isBlank() })
    }

    @Test
    fun theHeadlineNeverClaimsRulesWereCheckedWhenTheyWereNot() {
        // "No rule matched" asserts the rules were run. That is untrue for three of the five
        // states, and the detail line two rows below said so while the headline contradicted it.
        val notEvaluated = captureAttribution(HistoryRecord(id = 1L, matchState = RuleMatchState.NOT_EVALUATED), rules)
        val notLoaded = captureAttribution(HistoryRecord(id = 1L, matchState = RuleMatchState.RULES_NOT_LOADED), rules)
        val hidden = captureAttribution(HistoryRecord(id = 1L, matchState = RuleMatchState.CONTENT_HIDDEN), rules)

        assertEquals("No rules were saved yet", notEvaluated.headline)
        assertEquals("Arrived before the rules were read", notLoaded.headline)
        assertEquals("No content arrived to test", hidden.headline)
        // Only the state that really did check them may say so.
        assertEquals(
            "No rule matched",
            captureAttribution(HistoryRecord(id = 1L, matchState = RuleMatchState.EVALUATED), rules).headline,
        )
    }

    @Test
    fun aRecordWithMatchesReadsTheSameOnBothScreens() {
        // A rule matching on the app alone matches a notification that carried no text, so this
        // state can legitimately have ids. The row said "not matched" while the Activity screen
        // named two matching rules.
        val record = HistoryRecord(
            id = 1L,
            matchState = RuleMatchState.CONTENT_HIDDEN,
            matchedRuleIds = listOf(7L, 9L),
        )

        assertEquals("Matched 2 rules", captureAttribution(record, rules).headline)
        assertEquals("Would match: Chat, Invoices", describeMatchedRules(record, rules))
    }

    @Test
    fun aGroupSummarySaysNoRuleWasTestedRatherThanNoneMatched() {
        val summary = HistoryRecord(id = 1L, matchState = RuleMatchState.GROUP_SUMMARY, isGroupSummary = true)

        val attribution = captureAttribution(summary, rules)

        assertEquals("Group summary: no rule was tested", attribution.headline)
        assertTrue(attribution.rules.isEmpty())
    }

    @Test
    fun theContentLineReportsWhatWasStoredNotAFreshLook() {
        NotificationContentState.entries.forEach { state ->
            val detail = captureAttribution(HistoryRecord(id = 1L, contentState = state), rules).contentDetail
            assertTrue("$state has no explanation", detail.isNotBlank())
        }
        assertEquals(
            NotificationContentState.entries.size,
            NotificationContentState.entries.map { describeStoredContent(it) }.distinct().size,
        )
    }

    @Test
    fun aRecordTheSystemHidExplainsItselfInsteadOfLookingUnmatched() {
        val line = describeMatchedRules(
            HistoryRecord(id = 1L, matchState = RuleMatchState.CONTENT_HIDDEN),
            rules,
        )

        assertEquals("Not matched: no content arrived to test", line)
    }

    /**
     * Covers the in-memory helper the audit fixtures use. The screen itself filters in SQL, and
     * that path is covered by SignalDatabaseTest.theRuleTriggeredFilterSelectsRecordsWhoseRulesMatched.
     */
    @Test
    fun theInMemoryRuleTriggeredHelperSelectsRecordsThatMatched() {
        val matched = HistoryRecord(id = 1L, matchedRuleIds = listOf(7L))
        val unmatched = HistoryRecord(id = 2L)

        val filtered = filterHistory(listOf(matched, unmatched), query = "", filter = "Rule-triggered")

        assertEquals(listOf(1L), filtered.map { it.id })
    }
}
