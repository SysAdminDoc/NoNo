package com.anm.signalrules.reconstruction.runtime

import com.anm.signalrules.reconstruction.model.NotificationContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupingTest {

    @Test
    fun `group children share an evaluation key but summaries are not evaluated`() {
        val child = SanitizedNotification("child", "messages", 1L, NotificationContentState.NOT_STORED, "chat")
        val summary = SanitizedNotification("summary", "messages", 2L, NotificationContentState.NOT_STORED, "chat", true)

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
}
