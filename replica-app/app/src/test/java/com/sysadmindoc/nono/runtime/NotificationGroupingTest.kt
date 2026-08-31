package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RuleMatchState
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
    fun `a summary in a group only Android supplied is attributed to Android`() {
        assertEquals(
            GroupSummaryOrigin.SYSTEM,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = false, overrideGroupKey = "0|pkg|auto"),
        )
    }

    @Test
    fun `an ambiguous summary stays unknown rather than being guessed at`() {
        // Both signals present: the app declared a group and the platform reorganised it anyway.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = "0|pkg|auto"),
        )
        // Neither signal present, which is every device below API 26 and many above it.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = false, overrideGroupKey = null),
        )
        // A blank override key is not a group the platform imposed.
        assertEquals(
            GroupSummaryOrigin.UNKNOWN,
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = false, overrideGroupKey = "   "),
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
    fun `the evaluation policy and the counting policy agree about summaries`() {
        // One rule, applied in two places: the listener does not evaluate a summary, and the
        // widget count does not include one. Letting these drift is how History and the widget
        // ended up disagreeing.
        val summary = SanitizedNotification(
            "summary",
            "messages",
            2L,
            NotificationContentState.NOT_STORED,
            groupKey = "chat",
            isGroupSummary = true,
        )
        val child = SanitizedNotification("child", "messages", 1L, NotificationContentState.NOT_STORED, "chat")

        assertFalse(groupingFor(summary).shouldEvaluate)
        assertTrue(groupingFor(child).shouldEvaluate)
        // A summary that is not evaluated must not read as "your rules were checked and none
        // matched", which is what NOT_EVALUATED means.
        assertEquals(
            RuleMatchState.GROUP_SUMMARY,
            RuleMatchState.valueOf("GROUP_SUMMARY"),
        )
    }

    @Test
    fun `the classification never consults siblings`() {
        // The signature is the guard: there is nowhere to pass a sibling in. A summary with
        // children and one without cannot be told apart here, which is the point.
        assertEquals(
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = null),
            groupSummaryOrigin(isGroupSummary = true, appDeclaredGroup = true, overrideGroupKey = null),
        )
    }
}
