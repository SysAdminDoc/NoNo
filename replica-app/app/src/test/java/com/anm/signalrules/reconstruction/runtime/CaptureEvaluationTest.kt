package com.anm.signalrules.reconstruction.runtime

import com.anm.signalrules.reconstruction.data.decodeMatchedRuleIds
import com.anm.signalrules.reconstruction.data.encodeMatchedRuleIds
import com.anm.signalrules.reconstruction.model.RuleMatchState
import com.anm.signalrules.reconstruction.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Capture-time evaluation records which rules matched. It never executes one. */
class CaptureEvaluationTest {

    private val chatRule = SignalRule(
        id = 7L,
        name = "Chat",
        app = "Messages",
        appPackageName = "com.example.chat",
        phrase = "anything",
        action = "Mute",
    )
    private val invoiceRule = SignalRule(
        id = 9L,
        name = "Invoices",
        app = "any app",
        phrase = "invoice",
        action = "Mute",
    )

    private fun payload(title: String?, text: String?, packageName: String, sensitive: Boolean = false) =
        NotificationPayload(
            title = title,
            text = text,
            appLabel = null,
            systemMarkedSensitive = sensitive,
            packageName = packageName,
        )

    @Test
    fun aMatchingPackageRuleIsRecordedById() {
        val evaluation = evaluateCapture(
            rules = listOf(chatRule, invoiceRule),
            payload = payload("Ada", "lunch?", "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(listOf(7L), evaluation.matchedRuleIds)
        assertEquals(RuleMatchState.EVALUATED, evaluation.state)
    }

    @Test
    fun everyMatchingRuleIsRecorded() {
        val evaluation = evaluateCapture(
            rules = listOf(chatRule, invoiceRule),
            payload = payload("Ada", "your invoice is ready", "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(listOf(7L, 9L), evaluation.matchedRuleIds)
    }

    @Test
    fun contentTheSystemHidIsMarkedAndMatchesNothing() {
        val evaluation = evaluateCapture(
            rules = listOf(chatRule, invoiceRule),
            payload = payload("Sensitive notification content hidden", null, "com.example.chat", sensitive = true),
            sdkInt = 35,
        )

        assertEquals(RuleMatchState.CONTENT_HIDDEN, evaluation.state)
        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }

    @Test
    fun withNoSavedRulesNothingIsClaimedToHaveBeenEvaluated() {
        val evaluation = evaluateCapture(
            rules = emptyList(),
            payload = payload("Ada", "lunch?", "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(RuleMatchState.NOT_EVALUATED, evaluation.state)
        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }

    @Test
    fun aDisabledRuleNeverCounts() {
        val evaluation = evaluateCapture(
            rules = listOf(chatRule.copy(enabled = false)),
            payload = payload("Ada", "lunch?", "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }

    @Test
    fun storedIdsRoundTripAndAnEmptyListStoresNothing() {
        assertNull(encodeMatchedRuleIds(emptyList()))
        assertEquals("7,9", encodeMatchedRuleIds(listOf(9L, 7L)))
        assertEquals(listOf(7L, 9L), decodeMatchedRuleIds("7,9"))
        assertEquals(emptyList<Long>(), decodeMatchedRuleIds(null))
        assertEquals(emptyList<Long>(), decodeMatchedRuleIds(""))
        // A value written by some future build must not crash a reader.
        assertEquals(listOf(4L), decodeMatchedRuleIds("4,not-an-id"))
    }
}
