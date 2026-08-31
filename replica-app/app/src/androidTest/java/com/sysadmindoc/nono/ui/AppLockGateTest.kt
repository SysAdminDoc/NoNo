package com.sysadmindoc.nono.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.runtime.APP_LOCK_SETTING
import com.sysadmindoc.nono.runtime.CaptureGate
import com.sysadmindoc.nono.runtime.SignalWidgetProvider
import com.sysadmindoc.nono.runtime.WidgetScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app lock, on a device with no screen lock enrolled.
 *
 * That is not a gap in the fixture, it is the dangerous case. A lock that turns itself on where
 * there is no credential to satisfy it takes the user's rules with it and offers no way back, so
 * this is the path that most needs proving. The unlock itself goes through Android's own
 * confirm-credential screen and needs an enrolled credential; `Roadmap_Blocked.md` records that.
 */
@RunWith(AndroidJUnit4::class)
class AppLockGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test
    fun theLockScreenShowsNothingAboutWhatIsBehindIt() {
        var unlockRequested = false
        composeRule.setContent { SignalTheme { AppLockScreen(onUnlock = { unlockRequested = true }) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("NoNo is locked").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock with your screen lock to see your rules and history.").assertIsDisplayed()
        composeRule.onNodeWithText("That did not unlock it. NoNo stays locked.").assertDoesNotExist()
        composeRule.onNodeWithText("Unlock").performClick()

        assertTrue("the unlock control must reach the credential prompt", unlockRequested)
    }

    @Test
    fun aRefusedUnlockSaysSoOnTheLockScreenItself() {
        // A snackbar cannot carry this: the host lives behind the gate, so the message would be
        // invisible now and would surface out of nowhere after a later successful unlock.
        composeRule.setContent { SignalTheme { AppLockScreen(onUnlock = {}, refused = true) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("That did not unlock it. NoNo stays locked.").assertIsDisplayed()
    }

    @Test
    fun aDeviceWithNoScreenLockIsNeverLockedOut() {
        composeRule.runOnUiThread {
            model.setSetting(APP_LOCK_SETTING, "On")
            model.refreshAppLock()
            model.refreshAppLock(leftForeground = true)
            model.refreshAppLock()
        }
        composeRule.waitForIdle()

        assertEquals(false, model.state.value.deviceCredentialAvailable)
        assertEquals(false, model.state.value.appLocked)
    }

    @Test
    fun aResumeWithNoTripAwayDoesNotLockTheAppInTheUsersHands() {
        // A permission dialog or a multi-window focus change resumes the Activity without a
        // preceding stop. Answering "was it away long enough?" from a stamp left over from an
        // earlier trip locks the app while the user is still looking at it.
        composeRule.runOnUiThread {
            model.refreshAppLock(leftForeground = true)
            model.onAppUnlocked()
            model.refreshAppLock()
            model.refreshAppLock()
        }
        composeRule.waitForIdle()

        assertEquals(false, model.state.value.appLocked)
    }

    @Test
    fun theSettingIsRefusedRatherThanTakenWhenThereIsNothingToUnlockWith() {
        composeRule.runOnUiThread {
            model.refreshAppLock()
            model.setSetting(APP_LOCK_SETTING, "On")
        }
        composeRule.waitForIdle()

        // Taking the setting and then failing to honour it would leave a row saying the app is
        // locked while it plainly is not.
        assertEquals("Off", model.state.value.settings[APP_LOCK_SETTING])
        assertTrue(
            model.state.value.transientMessage.orEmpty(),
            model.state.value.transientMessage.orEmpty().contains("Set a screen lock"),
        )
    }

    @Test
    fun theCaptureGateAndTheWidgetDoNotConsultTheLock() = runBlocking {
        // Neither exposes any content, and a user who relies on the tile to silence a phone should
        // not have to unlock the app first.
        CaptureGate.setPaused(context, true)
        assertTrue("the tile must be able to pause capture", CaptureGate.isPaused())
        CaptureGate.setPaused(context, false)

        val dao = SignalDatabase.get(context).notificationDao()
        val views = SignalWidgetProvider.widgetViews(
            context,
            WidgetScope.ALL_CAPTURED,
            dao.readWidgetCount(),
            dao.readGroupSummaryCount(),
            null,
            null,
        )

        assertTrue("the widget must still build", views.layoutId != 0)
    }
}
