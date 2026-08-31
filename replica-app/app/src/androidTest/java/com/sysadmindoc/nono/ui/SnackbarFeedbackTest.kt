package com.sysadmindoc.nono.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The snackbar actually shows the app's messages.
 *
 * The effect that shows it is keyed on a counter rather than the message text, which is what lets
 * two identical messages in a row both appear. That only works while every message goes through
 * the one function that advances the counter: a message set with a plain copy leaves the key
 * unchanged, the effect does not restart, and the message is silently dropped. Roughly thirty of
 * them were being dropped that way, including the only report that a rule failed to save, and no
 * test noticed because every test asserted on the state rather than on the screen.
 */
@RunWith(AndroidJUnit4::class)
class SnackbarFeedbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savingARuleTellsTheUserItWasSaved() {
        val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent { SignalTheme { SignalApp(model) } }
        // The model reads its stores asynchronously and rewrites the route when that lands, so
        // acting before it does gets the action undone rather than tested.
        composeRule.waitUntil(5_000L) { model.state.value.rulesLoaded }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            model.completeOnboarding()
            model.newRule()
            model.updateDraft { it.copy(name = "Saved rule", app = "com.example.app", action = RECORD_ONLY_ACTION) }
            model.saveRule()
        }

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTextSafe("Rule saved").isNotEmpty()
        }
        composeRule.onAllNodes(hasText("Rule saved", substring = true)).onFirst().assertExists()
    }

    @Test
    fun aRuleThatCannotBeSavedSaysWhy() {
        val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent { SignalTheme { SignalApp(model) } }
        // The model reads its stores asynchronously and rewrites the route when that lands, so
        // acting before it does gets the action undone rather than tested.
        composeRule.waitUntil(5_000L) { model.state.value.rulesLoaded }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            model.completeOnboarding()
            model.newRule()
            // No app chosen, which is the first thing the validator objects to.
            model.updateDraft { it.copy(app = "", action = RECORD_ONLY_ACTION) }
            model.saveRule()
        }

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTextSafe("Choose an app.").isNotEmpty()
        }
        composeRule.onAllNodes(hasText("Choose an app.", substring = true)).onFirst().assertExists()
    }
}

/** Fetches matching nodes without throwing when there are none yet. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(
    text: String,
): List<androidx.compose.ui.semantics.SemanticsNode> = runCatching {
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes()
}.getOrDefault(emptyList())
