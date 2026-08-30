package com.sysadmindoc.nono.runtime

import com.sysadmindoc.nono.model.NotificationContentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservabilityTest {
    @Test
    fun `safe log line cannot contain a notification payload`() {
        val line = SignalEvent(
            type = SignalEventType.NOTIFICATION_CAPTURED,
            contentState = NotificationContentState.HIDDEN_BY_SYSTEM,
        ).toSafeLogLine()

        assertTrue(line.contains("contentState=HIDDEN_BY_SYSTEM"))
        assertFalse(line.contains("title="))
        assertFalse(line.contains("body="))
        assertFalse(line.contains("package="))
        assertFalse(line.contains("token="))
    }
}
