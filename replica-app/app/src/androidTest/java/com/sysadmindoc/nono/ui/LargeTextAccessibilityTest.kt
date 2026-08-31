package com.sysadmindoc.nono.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The layouts at the smallest viewport Android supports and the largest font scale its settings
 * offer.
 *
 * 320dp wide is the compact reference; 2.0 is the top of the system font-size slider. Content
 * that cannot be scrolled to there is content the user cannot reach, and a control smaller than
 * 48dp is one they cannot reliably hit. Both are measured on the composed tree, so a layout
 * change that breaks either fails here rather than needing someone to look at a screenshot.
 */
@RunWith(AndroidJUnit4::class)
class LargeTextAccessibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    private val populated = UiState(
        route = Route.ROOT,
        rootTab = RootTab.RULES,
        rules = listOf(
            SignalRule(
                id = 1L,
                name = "A rule with a deliberately long name that has to wrap at this width",
                action = RECORD_ONLY_ACTION,
            ),
            SignalRule(id = 2L, name = "Second rule", action = RECORD_ONLY_ACTION),
        ),
        history = listOf(HistoryRecord(id = 1L), HistoryRecord(id = 2L)),
        historyTotalCount = 2,
        historyFilteredCount = 2,
    )

    private fun setCompactLargeText(theme: String = "Dark", content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                SignalTheme(theme) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Scrolls the page's list until [text] is on screen, then asserts it is.
     *
     * A LazyColumn does not compose what is off screen, so a plain lookup finds nothing and
     * cannot tell "the layout clips it" from "it has not been composed yet". Scrolling the list
     * to the node distinguishes the two: it succeeds only if the content is reachable.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    /** Every clickable node measures at least 48dp on both axes. */
    private fun assertTouchTargets() {
        val nodes = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("no clickable node was found, so nothing was asserted", nodes.isNotEmpty())
        val density = composeRule.density
        val undersized = nodes.mapNotNull { node ->
            val width = with(density) { node.size.width.toDp() }
            val height = with(density) { node.size.height.toDp() }
            if (width >= 47.5.dp && height >= 47.5.dp) return@mapNotNull null
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                ?: "unlabelled node"
            "$label is ${width}x$height"
        }
        assertTrue("touch targets below 48dp: $undersized", undersized.isEmpty())
    }

    @Test
    fun theRulesScreenScrollsToItsLastControlAtTwiceTheFontSize() {
        setCompactLargeText { RulesHomeScreen(populated, model) }

        // Below the fold at this size. Reaching it proves the page scrolls rather than clipping.
        scrollTo("Create rule")
        assertTouchTargets()
    }

    @Test
    fun onboardingScrollsToItsLastControlAtTwiceTheFontSize() {
        setCompactLargeText {
            OnboardingScreen(populated.copy(route = Route.ONBOARDING, listenerAccessGranted = false), model)
        }

        scrollTo("Open notification settings")
        assertTouchTargets()
    }

    @Test
    fun theHistoryScreenScrollsAndKeepsItsTargetsAtTwiceTheFontSize() {
        setCompactLargeText { HistoryScreen(populated.copy(rootTab = RootTab.HISTORY), model) }

        scrollTo("Starred")
        assertTouchTargets()
    }

    @Test
    fun theSettingsScreenScrollsAndKeepsItsTargetsAtTwiceTheFontSize() {
        setCompactLargeText { SettingsScreen(populated.copy(rootTab = RootTab.SETTINGS), model) }

        scrollTo("Delete all rules")
        assertTouchTargets()
    }

    @Test
    fun theLightThemeHoldsUpAtTwiceTheFontSizeToo() {
        // Light mode kept dark hard-coded values for a while, so it is checked in its own right
        // rather than assumed to follow from dark.
        setCompactLargeText(theme = "Light") { RulesHomeScreen(populated, model) }

        scrollTo("Create rule")
        assertTouchTargets()
    }
}
