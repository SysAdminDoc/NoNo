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
}
