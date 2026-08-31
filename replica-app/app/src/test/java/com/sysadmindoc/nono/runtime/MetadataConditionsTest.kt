package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.CategoryCondition
import com.sysadmindoc.nono.model.ChannelCondition
import com.sysadmindoc.nono.model.ConversationCondition
import com.sysadmindoc.nono.model.ImportanceCondition
import com.sysadmindoc.nono.model.MetadataCondition
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.OngoingCondition
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.SummaryCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataConditionsTest {
    private val matchingPayload = NotificationPayload(
        title = null,
        text = null,
        appLabel = "Messages",
        packageName = "com.example.messages",
        channelId = "channel-7d2f",
        importance = 4,
        category = "msg",
        isConversation = true,
        isOngoing = false,
        isGroupSummary = false,
    )

    private val allConditions: List<MetadataCondition> = listOf(
        ChannelCondition("channel-7d2f"),
        ImportanceCondition(4),
        CategoryCondition("msg"),
        ConversationCondition(true),
        OngoingCondition(false),
        SummaryCondition(false),
    )

    @Test
    fun everySupportedMetadataConditionMatchesInOneDryRun() {
        val trace = evaluateRules(
            rules = listOf(SignalRule(id = 9L, metadataConditions = allConditions)),
            payload = matchingPayload,
            sdkInt = 36,
            traceId = "metadata-all",
        )

        val condition = trace.conditions.single()
        assertEquals(9L, trace.matchedRuleId)
        assertTrue(condition.matched)
        assertEquals(6, condition.metadataConditions.size)
        assertTrue(condition.metadataConditions.all { it.matched && it.failure == null })
        assertFalse(EvaluationReason.EXTRA_FILTER_UNSUPPORTED in condition.reasons)
    }

    @Test
    fun eachMismatchingFieldKeepsItsOwnConditionTrace() {
        val mismatches = listOf(
            ChannelCondition("another-channel"),
            ImportanceCondition(1),
            CategoryCondition("email"),
            ConversationCondition(false),
            OngoingCondition(true),
            SummaryCondition(true),
        )

        val trace = evaluateRules(
            rules = listOf(SignalRule(id = 10L, metadataConditions = mismatches)),
            payload = matchingPayload,
            sdkInt = 36,
        ).conditions.single()

        assertFalse(trace.matched)
        assertEquals(mismatches, trace.metadataConditions.map { it.condition })
        assertTrue(trace.metadataConditions.all { it.failure == MetadataConditionFailure.VALUE_MISMATCH })
        assertEquals(listOf(EvaluationReason.METADATA_MISMATCH), trace.reasons)
        assertEquals(
            listOf("channel-7d2f", "High", "Message", "Yes", "No", "No"),
            trace.metadataConditions.map { it.actualValue },
        )
    }

    @Test
    fun missingMetadataIsNotMistakenForAFalseValue() {
        val traces = evaluateMetadataConditions(
            allConditions,
            NotificationPayload(title = null, text = null, appLabel = null),
        )

        assertTrue(traces.none { it.matched })
        assertTrue(traces.all { it.failure == MetadataConditionFailure.METADATA_NOT_AVAILABLE })
        assertTrue(traces.all { it.actualValue == null })
    }

    @Test
    fun invalidTypedValuesFailClosedAndExplainWhy() {
        val rule = SignalRule(
            id = 11L,
            metadataConditions = listOf(
                ChannelCondition(""),
                ImportanceCondition(99),
                CategoryCondition(""),
            ),
        )

        val trace = evaluateRules(listOf(rule), matchingPayload, sdkInt = 36).conditions.single()

        assertTrue(EvaluationReason.INVALID_METADATA_CONDITION in trace.reasons)
        assertTrue(trace.metadataConditions.all { it.failure == MetadataConditionFailure.INVALID_CONDITION })
        assertNull(evaluateRules(listOf(rule), matchingPayload, sdkInt = 36).matchedRuleId)
    }

    @Test
    fun legacyExtrasStayUnsupportedWhileTypedConditionsRemainEvaluated() {
        val trace = evaluateRules(
            rules = listOf(
                SignalRule(
                    id = 12L,
                    metadataConditions = listOf(ConversationCondition(true)),
                    extras = listOf("Image"),
                ),
            ),
            payload = matchingPayload,
            sdkInt = 36,
        ).conditions.single()

        assertTrue(trace.metadataConditions.single().matched)
        assertEquals(listOf(EvaluationReason.EXTRA_FILTER_UNSUPPORTED), trace.reasons)
        assertFalse(trace.matched)
    }

    @Test
    fun captureComparesAgainstSanitizedMetadata() {
        val sanitized = SanitizedNotification(
            notificationKey = "key",
            packageName = "com.example.messages",
            postedAtEpochMillis = 1L,
            contentState = NotificationContentState.NOT_STORED,
            channelId = "channel-7d2f",
            importance = 4,
            isConversation = true,
            category = "msg",
            isOngoing = false,
        )
        val rule = SignalRule(id = 13L, metadataConditions = allConditions)

        val result = captureEvaluationFor(
            sanitized = sanitized,
            rules = listOf(rule),
            payload = NotificationPayload(null, null, "Messages", packageName = "com.example.messages"),
            sdkInt = 36,
        )

        assertEquals(RuleMatchState.EVALUATED, result.state)
        assertEquals(listOf(13L), result.matchedRuleIds)
    }

    @Test
    fun summaryEvaluationIsOptInAndRecordsADistinctState() {
        val summary = SanitizedNotification(
            notificationKey = "summary",
            packageName = "com.example.messages",
            postedAtEpochMillis = 1L,
            contentState = NotificationContentState.NOT_STORED,
            isGroupSummary = true,
        )
        val oldRule = SignalRule(id = 1L)
        val summaryRule = SignalRule(id = 2L, metadataConditions = listOf(SummaryCondition(true)))
        val payload = NotificationPayload(null, null, "Messages", packageName = "com.example.messages")

        val oldResult = captureEvaluationFor(summary, listOf(oldRule), payload, sdkInt = 36)
        val explicitResult = captureEvaluationFor(summary, listOf(oldRule, summaryRule), payload, sdkInt = 36)

        assertEquals(RuleMatchState.GROUP_SUMMARY, oldResult.state)
        assertEquals(emptyList<Long>(), oldResult.matchedRuleIds)
        assertEquals(RuleMatchState.GROUP_SUMMARY_EVALUATED, explicitResult.state)
        assertEquals(listOf(2L), explicitResult.matchedRuleIds)
    }
}
