package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupingTest {

    @Test
    fun `group children share an evaluation key but summaries are not evaluated`() {
        val child = SanitizedNotification("child", "messages", 1L, NotificationContentState.NOT_STORED, "chat")
        val summary = SanitizedNotification(
            "summary",
            "messages",
            2L,
            NotificationContentState.NOT_STORED,
            groupKey = "chat",
            isGroupSummary = true,
        )

        assertEquals("chat", groupingFor(child).evaluationKey)
        assertTrue(groupingFor(child).shouldEvaluate)
        assertEquals("chat", groupingFor(summary).evaluationKey)
        assertFalse(groupingFor(summary).shouldEvaluate)
    }

    @Test
    fun `ungrouped events remain individually eligible`() {
        val grouping = groupingFor(SanitizedNotification("single", "messages", 1L, NotificationContentState.NOT_STORED))

        assertEquals("single", grouping.evaluationKey)
        assertTrue(grouping.shouldEvaluate)
    }

    @Test
    fun `a summary in the app's own group is attributed to the app`() {
        assertEquals(
            GroupSummaryOrigin.APP,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = null),
        )
    }

    @Test
    fun `the platform's own auto-group summary is never claimed for the app`() {
        // AOSP builds its auto-group summary with a group of its own and posts it with the same
        // value as the override key, so both signals are set. Reading that as APP would put the
        // platform's summary under the app's name.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = "ranker_group"),
        )
    }

    @Test
    fun `SYSTEM is never inferred, because nothing public identifies it`() {
        // An app-posted summary that never called setGroup used to land here and be labelled as
        // Android's, which named the wrong author in the only case the label appeared.
        val everyCombination = listOf(true, false).flatMap { declared ->
            listOf(null, "", "   ", "ranker_group").map { override ->
                groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = declared, overrideGroupKey = override)
            }
        }

        assertTrue(everyCombination.none { it == GroupSummaryOrigin.SYSTEM })
    }

    @Test
    fun `an ambiguous summary stays unknown rather than being guessed at`() {
        // Neither signal present, which is every device below API 26 and many above it.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = false, overrideGroupKey = null),
        )
        // A blank override key is not a group the platform imposed, but no app group means the
        // summary still cannot be attributed.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = false, overrideGroupKey = "   "),
        )
        // The app declared a group and the platform left it alone: the one decidable case.
        assertEquals(
            GroupSummaryOrigin.APP,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = "   "),
        )
    }

    @Test
    fun `a notification that is not a summary is never given an origin`() {
        listOf(true, false).forEach { declared ->
            listOf(null, "0|pkg|auto").forEach { override ->
                assertEquals(
                    GroupSummaryOrigin.UNKNOWN,
                    groupSummaryOrigin(isGroupSummary = false, appDeclaredGroup = declared, overrideGroupKey = override),
                )
            }
        }
    }

    @Test
    fun `a rule without a summary condition is not tested against a summary`() {
        // The rule matches this app and needs no content, so it would match if it were tested.
        // The summary must still not be evaluated, because it is not an arrival of its own.
        val matchAll = SignalRule(id = 1L, app = "any app", phrase = "anything", action = "Mute")
        val summary = SanitizedNotification(
            "summary",
            "com.example.chat",
            2L,
            NotificationContentState.NOT_STORED,
            groupKey = "chat",
            isGroupSummary = true,
        )
        val payload = NotificationPayload(null, null, "chat", packageName = "com.example.chat")

        val evaluation = captureEvaluationFor(summary, listOf(matchAll), payload, sdkInt = 36)

        assertEquals(RuleMatchState.GROUP_SUMMARY, evaluation.state)
        assertEquals(emptyList<Long>(), evaluation.matchedRuleIds)
    }

    @Test
    fun `a child of the same group is evaluated normally`() {
        val matchAll = SignalRule(id = 1L, app = "any app", phrase = "anything", action = "Mute")
        val child = SanitizedNotification("child", "com.example.chat", 1L, NotificationContentState.NOT_STORED, "chat")
        val payload = NotificationPayload(null, null, "chat", packageName = "com.example.chat")

        val evaluation = captureEvaluationFor(child, listOf(matchAll), payload, sdkInt = 36)

        assertEquals(RuleMatchState.EVALUATED, evaluation.state)
        assertEquals(listOf(1L), evaluation.matchedRuleIds)
    }

    @Test
    fun `a summary that arrives before the rules are loaded is still a summary`() {
        // The summary branch has to come first: RULES_NOT_LOADED would suggest the rules were the
        // reason nothing matched, when the record was never going to be evaluated at all.
        val summary = SanitizedNotification(
            "summary",
            "com.example.chat",
            2L,
            NotificationContentState.NOT_STORED,
            groupKey = "chat",
            isGroupSummary = true,
        )

        val evaluation = captureEvaluationFor(summary, null, NotificationPayload(null, null, null), sdkInt = 36)

        assertEquals(RuleMatchState.GROUP_SUMMARY, evaluation.state)
    }

    @Test
    fun `a child that arrives before the rules are loaded says so`() {
        val child = SanitizedNotification("child", "com.example.chat", 1L, NotificationContentState.NOT_STORED)

        val evaluation = captureEvaluationFor(child, null, NotificationPayload(null, null, null), sdkInt = 36)

        assertEquals(RuleMatchState.RULES_NOT_LOADED, evaluation.state)
    }

    @Test
    fun `the classification carries no state between calls`() {
        // There is nowhere to pass a sibling in, and nowhere to accumulate one either: the same
        // summary classified before and after a run of other notifications gives the same answer.
        val first = groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = null)

        repeat(50) { index ->
            groupSummaryOrigin(
                isGroupSummary = index % 2 == 0,
                appDeclaredGroup = index % 3 == 0,
                overrideGroupKey = if (index % 4 == 0) "group-$index" else null,
            )
        }

        assertEquals(first, groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = null))
        assertEquals(GroupSummaryOrigin.APP, first)
    }
}
