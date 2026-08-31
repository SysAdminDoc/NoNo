package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.HistoryRecord
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
