package com.sysadmindoc.nono.runtime

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureSelfTestProbeTest {
    @Before
    fun setUp() = CaptureSelfTestProbe.resetForTest()

    @After
    fun tearDown() = CaptureSelfTestProbe.resetForTest()

    @Test
    fun onlyTheExactArmedSelfNotificationIsAcceptedOnce() {
        val expected = CaptureSelfTestKey("com.sysadmindoc.nono", "test-71", 23)
        val observed = CaptureSelfTestProbe.arm(expected)

        assertNotNull(observed)
        assertFalse(CaptureSelfTestProbe.acknowledge(expected.copy(packageName = "com.example.other")))
        assertFalse(CaptureSelfTestProbe.acknowledge(expected.copy(tag = "test-72")))
        assertFalse(CaptureSelfTestProbe.acknowledge(expected.copy(notificationId = 24)))
        assertFalse(observed!!.isCompleted)

        assertTrue(CaptureSelfTestProbe.acknowledge(expected))
        assertTrue(observed.isCompleted)
        assertFalse("a duplicate callback must return to normal rejection", CaptureSelfTestProbe.acknowledge(expected))
    }

    @Test
    fun aSecondTestCannotReplaceThePendingKey() {
        val first = CaptureSelfTestKey("com.sysadmindoc.nono", "first", 23)
        val second = first.copy(tag = "second")

        assertNotNull(CaptureSelfTestProbe.arm(first))
        assertNull(CaptureSelfTestProbe.arm(second))

        CaptureSelfTestProbe.cancel(first)
        assertNotNull(CaptureSelfTestProbe.arm(second))
    }

    @Test
    fun cancellingOneKeyCannotCancelAnother() {
        val expected = CaptureSelfTestKey("com.sysadmindoc.nono", "expected", 23)
        val observed = CaptureSelfTestProbe.arm(expected)!!

        CaptureSelfTestProbe.cancel(expected.copy(tag = "wrong"))

        assertFalse(observed.isCancelled)
        assertTrue(CaptureSelfTestProbe.acknowledge(expected))
    }
}
