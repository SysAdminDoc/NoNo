package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.runtime.APP_LOCK_SETTING
import com.sysadmindoc.nono.runtime.NO_DEVICE_CREDENTIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the lock row says, in each of the three states it can be in. */
class AppLockCopyTest {

    @Test
    fun aDeviceWithNoScreenLockIsToldWhyTheSettingWillNotTake() {
        assertEquals(NO_DEVICE_CREDENTIAL, describeAppLock(UiState(deviceCredentialAvailable = false)))
        assertTrue(NO_DEVICE_CREDENTIAL.contains("Set a screen lock"))
    }

    @Test
    fun anEnabledLockNamesWhatKeepsWorkingWhileItIsOn() {
        // Somebody relying on the tile or the widget needs to know before they turn this on, not
        // after they find the app asking for a PIN.
        val line = describeAppLock(
            UiState(deviceCredentialAvailable = true, settings = mapOf(APP_LOCK_SETTING to "On")),
        )

        assertTrue(line, line.contains("Quick Settings tile"))
        assertTrue(line, line.contains("widget"))
        assertTrue(line, line.contains("neither shows any content"))
    }

    @Test
    fun anOffLockSaysWhatTurningItOnWouldDo() {
        val line = describeAppLock(
            UiState(deviceCredentialAvailable = true, settings = mapOf(APP_LOCK_SETTING to "Off")),
        )

        assertEquals("Ask for your screen lock before showing any rule or record.", line)
    }

    @Test
    fun noneOfTheCopyUsesADash() {
        val lines = listOf(
            describeAppLock(UiState(deviceCredentialAvailable = false)),
            describeAppLock(UiState(deviceCredentialAvailable = true, settings = mapOf(APP_LOCK_SETTING to "On"))),
            describeAppLock(UiState(deviceCredentialAvailable = true, settings = mapOf(APP_LOCK_SETTING to "Off"))),
        )

        assertTrue(lines.none { line -> line.any { it == '—' || it == '–' } })
    }
}
