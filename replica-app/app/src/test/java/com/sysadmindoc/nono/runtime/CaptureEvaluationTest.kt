package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.data.decodeMatchedRuleIds
import com.sysadmindoc.nono.data.encodeMatchedRuleIds
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
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

    private fun payload(title: String?, text: String?, packageName: String) =
        NotificationPayload(
            title = title,
            text = text,
            appLabel = null,
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
    fun aRuleTestingAPhraseCannotMatchANotificationThatCarriedNoText() {
        val evaluation = evaluateCapture(
            rules = listOf(invoiceRule),
            payload = payload(null, null, "com.example.chat"),
            sdkInt = 35,
        )

        assertEquals(RuleMatchState.EVALUATED, evaluation.state)
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

    @Test
    fun aRuleThatTestsNoPhraseMatchesANotificationCarryingNoText() {
        // Custom layouts, foreground-service notifications and summaries often carry neither
        // title nor text. An app-only rule asks nothing of the content, so it still applies.
        val evaluation = evaluateCapture(
            rules = listOf(chatRule),
            payload = payload(title = null, text = null, packageName = "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(listOf(7L), evaluation.matchedRuleIds)
        assertEquals(RuleMatchState.EVALUATED, evaluation.state)
    }

    @Test
    fun aRuleThatTestsAPhraseStillNeedsContent() {
        val evaluation = evaluateCapture(
            rules = listOf(invoiceRule),
            payload = payload(title = null, text = null, packageName = "com.example.chat"),
            sdkInt = 34,
        )

        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }

    @Test
    fun anAppOnlyRuleMatchesANotificationThatCarriedNoText() {
        // The rule tests no phrase, so absent content is no reason to refuse it.
        val evaluation = evaluateCapture(
            rules = listOf(chatRule),
            payload = payload(null, null, "com.example.chat"),
            sdkInt = 35,
        )

        assertEquals(listOf(7L), evaluation.matchedRuleIds)
        assertEquals(RuleMatchState.EVALUATED, evaluation.state)
    }

    @Test
    fun aNegatedPhraseMatchesTextThatDoesNotContainIt() {
        val negated = invoiceRule.copy(id = 11L, matchType = "doesn't contain")

        val absent = evaluateCapture(
            rules = listOf(negated),
            payload = payload("Ada", "lunch?", "com.example.chat"),
            sdkInt = 35,
        )
        val present = evaluateCapture(
            rules = listOf(negated),
            payload = payload("Ada", "your invoice is ready", "com.example.chat"),
            sdkInt = 35,
        )

        assertEquals(listOf(11L), absent.matchedRuleIds)
        assertEquals(emptyList<Long>(), present.matchedRuleIds)
    }

    @Test
    fun aNegatedPhraseStillRefusesANotificationWithNoText() {
        // Absence cannot be asserted about text the app never saw.
        val negated = invoiceRule.copy(id = 11L, matchType = "doesn't contain")

        val evaluation = evaluateCapture(
            rules = listOf(negated),
            payload = payload(null, null, "com.example.chat"),
            sdkInt = 35,
        )

        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }
}
