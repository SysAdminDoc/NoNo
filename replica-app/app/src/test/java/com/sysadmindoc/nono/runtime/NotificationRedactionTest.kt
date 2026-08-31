package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.normalizedNotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRedactionTest {
    @Test
    fun `only documented notification categories cross the sanitizer boundary`() {
        assertEquals("msg", normalizedNotificationCategory("msg"))
        assertNull(normalizedNotificationCategory("account=matt@example.com"))
        assertNull(normalizedNotificationCategory(null))
    }


    @Test
    fun `english placeholder text is not treated as proof the system redacted anything`() {
        // Android publishes no supported signal for its redaction, and this string is localized.
        // An app that legitimately posts it would otherwise have its notification filed under a
        // provenance the platform never reported.
        val payload = NotificationPayload(
            title = "Messages",
            text = "Sensitive notification content hidden",
            appLabel = "Messages",
        )

        assertEquals(
            NotificationContentState.AVAILABLE,
            classifyNotificationContent(payload, sdkInt = 35),
        )
        assertEquals("Messages Sensitive notification content hidden", matchableNotificationText(payload, sdkInt = 35))
    }

    @Test
    fun `a stored provenance is preserved rather than reclassified`() {
        // History rows written by an earlier build carry the state they were stored with, which
        // is data rather than a fresh inference.
        val payload = NotificationPayload(
            title = null,
            text = null,
            appLabel = "Messages",
            contentStateOverride = NotificationContentState.HIDDEN_BY_SYSTEM,
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
    fun `classification is the same on every supported API level`() {
        // No branch on sdkInt survives: the platform behaves differently across releases, but
        // nothing it exposes lets a listener tell which behaviour produced a given payload.
        listOf(24, 35, 36, 37).forEach { sdkInt ->
            val ordinary = NotificationPayload("Build", "Tests passed", "CI", packageName = "com.example.ci")
            assertEquals(NotificationContentState.AVAILABLE, classifyNotificationContent(ordinary, sdkInt))
            assertEquals("Build Tests passed", matchableNotificationText(ordinary, sdkInt))

            val empty = ordinary.copy(title = null, text = null)
            assertEquals(NotificationContentState.NOT_AVAILABLE, classifyNotificationContent(empty, sdkInt))
            assertNull(matchableNotificationText(empty, sdkInt))

            val marker = ordinary.copy(text = "Sensitive notification content hidden")
            assertEquals(NotificationContentState.AVAILABLE, classifyNotificationContent(marker, sdkInt))
        }
    }

    @Test
    fun `package identity remains separate from display labels in redaction traces`() {
        val rule = com.sysadmindoc.nono.model.SignalRule(
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
