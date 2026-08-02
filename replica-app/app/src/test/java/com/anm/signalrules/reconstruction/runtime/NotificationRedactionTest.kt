package com.anm.signalrules.reconstruction.runtime

import com.anm.signalrules.reconstruction.model.NotificationContentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRedactionTest {

    @Test
    fun `android 15 marker is hidden and never matchable`() {
        val payload = NotificationPayload(
            title = "Messages",
            text = "Sensitive notification content hidden",
            appLabel = "Messages",
        )

        assertEquals(
            NotificationContentState.HIDDEN_BY_SYSTEM,
            classifyNotificationContent(payload, sdkInt = 35),
        )
        assertNull(matchableNotificationText(payload, sdkInt = 35))
    }

    @Test
    fun `explicit platform provenance wins even when fields look ordinary`() {
        val payload = NotificationPayload(
            title = "Messages",
            text = "Notification received",
            appLabel = "Messages",
            systemMarkedSensitive = true,
        )

        assertEquals(NotificationContentState.HIDDEN_BY_SYSTEM, classifyNotificationContent(payload, 35))
        assertNull(matchableNotificationText(payload, 35))
    }

    @Test
    fun `ordinary content remains eligible for future matching`() {
        val payload = NotificationPayload("Build", "Tests passed", "CI")

        assertEquals(NotificationContentState.AVAILABLE, classifyNotificationContent(payload, 35))
        assertEquals("Build Tests passed", matchableNotificationText(payload, 35))
    }

    @Test
    fun `empty fields are not treated as redaction without provenance`() {
        val payload = NotificationPayload(null, null, "System")

        assertEquals(NotificationContentState.NOT_AVAILABLE, classifyNotificationContent(payload, 35))
        assertTrue(matchableNotificationText(payload, 35) == null)
    }

    @Test
    fun `redaction matrix remains conservative across supported API levels`() {
        listOf(24, 35, 36).forEach { sdkInt ->
            val ordinary = NotificationPayload("Build", "Tests passed", "CI", packageName = "com.example.ci")
            assertEquals(NotificationContentState.AVAILABLE, classifyNotificationContent(ordinary, sdkInt))
            assertEquals("Build Tests passed", matchableNotificationText(ordinary, sdkInt))

            val empty = ordinary.copy(title = null, text = null)
            assertEquals(NotificationContentState.NOT_AVAILABLE, classifyNotificationContent(empty, sdkInt))
            assertNull(matchableNotificationText(empty, sdkInt))

            val explicit = ordinary.copy(systemMarkedSensitive = true)
            assertEquals(NotificationContentState.HIDDEN_BY_SYSTEM, classifyNotificationContent(explicit, sdkInt))
            assertNull(matchableNotificationText(explicit, sdkInt))

            val marker = ordinary.copy(text = "Sensitive notification content hidden")
            val markerState = if (sdkInt >= 35) {
                NotificationContentState.HIDDEN_BY_SYSTEM
            } else {
                NotificationContentState.AVAILABLE
            }
            assertEquals(markerState, classifyNotificationContent(marker, sdkInt))
        }
    }

    @Test
    fun `package identity remains separate from display labels in redaction traces`() {
        val rule = com.anm.signalrules.reconstruction.model.SignalRule(
            id = 9L,
            app = "Messages",
            appPackageName = "com.example.messages",
            phrase = "verification",
            action = "Mute",
        )
        val trace = evaluateRules(
            rules = listOf(rule),
            payload = NotificationPayload(
                title = "Messages",
                text = "verification",
                appLabel = "Messages",
                packageName = "com.example.other",
            ),
            sdkInt = 36,
            traceId = "redaction-package",
        )

        assertEquals(NotificationContentState.AVAILABLE, trace.contentState)
        assertEquals(listOf(EvaluationReason.APP_MISMATCH), trace.conditions.single().reasons)
        assertEquals(null, trace.matchedRuleId)
    }

    @Test
    fun `sanitized records retain grouping metadata without retaining content`() {
        val notification = SanitizedNotification(
            notificationKey = "key",
            packageName = "com.example.messages",
            postedAtEpochMillis = 10L,
            contentState = NotificationContentState.NOT_STORED,
            groupKey = "conversation",
            isGroupSummary = true,
        )

        assertEquals("conversation", groupingFor(notification).groupKey)
        assertTrue(groupingFor(notification).isGroupSummary)
        assertTrue(!groupingFor(notification).shouldEvaluate)
    }
}
