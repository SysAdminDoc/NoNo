package com.anm.signalrules.reconstruction

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.accessibility.AccessibilityChecks
import org.junit.BeforeClass
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplicaSmokeTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityCreatesAnAccessibleComposeRoot() {
        composeRule.waitForIdle()
        assertNotNull(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }
}
