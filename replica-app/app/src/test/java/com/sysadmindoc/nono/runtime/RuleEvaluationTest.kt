package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluationTest {

    private val payload = NotificationPayload(
        title = "Build failed",
        text = "The main branch is red",
        appLabel = "CI",
        packageName = "com.example.ci",
    )

    @Test
    fun `specific rule wins a normal-priority conflict`() {
        val trace = evaluateRules(
            rules = listOf(
                SignalRule(id = 1, app = "any app", phrase = "anything", action = "Mute"),
                SignalRule(
                    id = 2,
                    app = "CI",
                    appPackageName = "com.example.ci",
                    phrase = "main branch",
                    action = "Alarm",
                ),
            ),
            payload = payload,
            sdkInt = 35,
            traceId = "trace-specific",
        )

        assertEquals("trace-specific", trace.traceId)
        assertEquals(NotificationContentState.AVAILABLE, trace.contentState)
        assertEquals(2L, trace.matchedRuleId)
        assertEquals(1, trace.conflictPairs.size)
        assertEquals(2L, trace.conflictPairs.single().winningRuleId)
        assertEquals(DryRunActionResult.NOT_EXECUTED, trace.actionResult)
    }

    @Test
    fun `explicit priority wins over specificity and is exposed`() {
        val trace = evaluateRules(
            rules = listOf(
                SignalRule(
                    id = 1,
                    app = "CI",
                    appPackageName = "com.example.ci",
                    phrase = "main branch",
                    priority = "Low",
                    action = "Alarm",
                ),
                SignalRule(id = 2, app = "any app", phrase = "anything", priority = "Highest", action = "Mute"),
            ),
            payload = payload,
            sdkInt = 35,
        )

        assertEquals(2L, trace.matchedRuleId)
        assertEquals(listOf(2L, 1L), trace.priorityOverrides.map { it.ruleId })
        assertEquals("Highest", trace.priorityOverrides.first().priority)
    }

    @Test
    fun `absent content blocks a phrase rule and explains the unmet condition neutrally`() {
        val trace = evaluateRules(
            rules = listOf(SignalRule(id = 7, app = "CI", phrase = "code", action = "Mute")),
            payload = payload.copy(title = null, text = null),
            sdkInt = 35,
        )

        assertEquals(NotificationContentState.NOT_AVAILABLE, trace.contentState)
        assertEquals(null, trace.matchedRuleId)
        assertTrue(EvaluationReason.CONTENT_NOT_AVAILABLE in trace.conditions.single().reasons)
        // The neutral reason is the point: nothing here proves Android redacted anything.
        assertTrue(EvaluationReason.CONTENT_HIDDEN_BY_SYSTEM !in trace.conditions.single().reasons)
    }

    @Test
    fun `disabled and unsupported rules remain in the activity trace`() {
        val trace = evaluateRules(
            rules = listOf(
                SignalRule(id = 1, enabled = false),
                SignalRule(id = 2, extras = listOf("Image"), action = "Mute"),
            ),
            payload = payload,
            sdkInt = 35,
        )

        assertEquals(listOf(1L, 2L), trace.conditions.map { it.ruleId })
        assertEquals(listOf(EvaluationReason.DISABLED), trace.conditions[0].reasons)
        assertTrue(EvaluationReason.EXTRA_FILTER_UNSUPPORTED in trace.conditions[1].reasons)
        assertEquals(null, trace.matchedRuleId)
    }

    @Test
    fun `metadata history preview preserves provenance and never executes`() {
        val trace = evaluateHistoryRecord(
            rules = listOf(
                SignalRule(
                    id = 11,
                    app = "Messages",
                    appPackageName = "com.google.android.apps.messaging",
                    phrase = "verification",
                    action = "Copy verification code",
                ),
            ),
            record = HistoryRecord(
                id = 42,
                app = "Messages",
                appPackageName = "com.google.android.apps.messaging",
                contentState = NotificationContentState.NOT_STORED,
            ),
            sdkInt = 35,
        )

        assertEquals("history-42", trace.traceId)
        assertEquals(NotificationContentState.NOT_STORED, trace.contentState)
        assertTrue(EvaluationReason.CONTENT_NOT_AVAILABLE in trace.conditions.single().reasons)
        assertEquals(null, trace.matchedRuleId)
        assertEquals(DryRunActionResult.NOT_EXECUTED, trace.actionResult)
    }
}
