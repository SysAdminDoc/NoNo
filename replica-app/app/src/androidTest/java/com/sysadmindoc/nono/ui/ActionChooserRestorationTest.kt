package com.sysadmindoc.nono.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The action chooser is the one screen here whose filter lives in the composition rather than in
 * the view model, so a recreation is the only thing that can lose it.
 */
@RunWith(AndroidJUnit4::class)
class ActionChooserRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    /** "Flash" matches exactly one entry in the catalog, so the filter either held or it did not. */
    @Test
    fun theTypedFilterAndItsResultsSurviveARecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { ActionSelectorScreen(UiState(), model) }

        composeRule.onNode(hasSetTextAction()).performTextInput("Flash")
        composeRule.onNodeWithText("Flash").assertIsDisplayed()
        composeRule.onNodeWithText("Flashlight").assertIsDisplayed()
        composeRule.onNodeWithText("Cooldown").assertDoesNotExist()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Flash").assertIsDisplayed()
        composeRule.onNodeWithText("Flashlight").assertIsDisplayed()
        composeRule.onNodeWithText("Cooldown").assertDoesNotExist()
    }
}
