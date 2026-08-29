package com.anm.signalrules.reconstruction.ui

import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.RuleMatchState
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.model.filterHistory
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
    fun aRecordTheSystemHidExplainsItselfInsteadOfLookingUnmatched() {
        val line = describeMatchedRules(
            HistoryRecord(id = 1L, matchState = RuleMatchState.CONTENT_HIDDEN),
            rules,
        )

        assertEquals("Not matched: the system hid this content", line)
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
