package com.sysadmindoc.nono.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.data.CatalogedApp
import com.sysadmindoc.nono.model.InsightAppCount
import com.sysadmindoc.nono.model.InsightDay
import com.sysadmindoc.nono.model.LocalInsights
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Insights screen renders what the aggregates found, and says so when they found nothing. */
@RunWith(AndroidJUnit4::class)
class InsightsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    private val populated = UiState(
        historyTotalCount = 12,
        rules = listOf(SignalRule(id = 1L, name = "Group chats"), SignalRule(id = 2L, name = "Deliveries")),
        // Deliberately not equal to any app count, so an assertion on one number cannot be
        // satisfied by the other card.
        ruleMatchCounts = mapOf(1L to 9),
        appCatalog = listOf(CatalogedApp(label = "Messages", packageName = "com.example.chat")),
        insights = LocalInsights(
            storedRecordCount = 12,
            totalCaptured = 10,
            excludedGroupSummaries = 2,
            topApps = listOf(
                InsightAppCount("com.example.chat", 7),
                InsightAppCount("com.example.shop", 3),
            ),
            hourlyCounts = List(24) { hour -> if (hour == 20) 6 else if (hour == 9) 4 else 0 },
            dailyTrend = listOf(
                InsightDay("2026-08-30", "Aug 30", 4),
                InsightDay("2026-08-31", "Aug 31", 6),
            ),
        ),
    )

    @Test
    fun everySectionOfAPopulatedScreenIsRendered() {
        composeRule.setContent { SignalTheme { InsightsScreen(populated, model) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Insights").assertIsDisplayed()
        composeRule.onNodeWithText("10").assertIsDisplayed()
        composeRule.onNodeWithText("From 12 stored records, excluding 2 group summaries.").assertIsDisplayed()
        composeRule.onNodeWithText("Busiest hour: 8 PM with 6 notifications").assertIsDisplayed()
        composeRule.onNodeWithText("By hour of day").assertIsDisplayed()
        composeRule.onNodeWithText("Last 2 days").assertIsDisplayed()
        composeRule.onNodeWithText("Busiest day: Aug 31 with 6 notifications").assertIsDisplayed()
    }

    @Test
    fun theChartsAnswerAsOneNodeRatherThanAsUnlabelledBars() {
        composeRule.setContent { SignalTheme { InsightsScreen(populated, model) } }
        composeRule.waitForIdle()

        composeRule
            .onNode(hasContentDescription("Notifications by hour of day. 10 in total. Busiest at 8 PM with 6."))
            .assertExists()
    }

    @Test
    fun theMostActiveAppsUseTheirInstalledLabelAndFallBackToThePackage() {
        composeRule.setContent { SignalTheme { InsightsScreen(populated, model) } }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Most active apps"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithText("com.example.chat").assertIsDisplayed()
        // Nothing in the catalog describes this one, so the package name stands in as the title.
        composeRule.onNodeWithText("com.example.shop").assertIsDisplayed()
    }

    @Test
    fun everySavedRuleReportsItsMatchCountIncludingZero() {
        composeRule.setContent { SignalTheme { InsightsScreen(populated, model) } }
        composeRule.waitForIdle()
        // The card sits below the charts, so on a phone-sized viewport it is not composed yet.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Deliveries"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Group chats").assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
        // A rule that has never matched still belongs on the list; dropping it reads as a rule
        // that does not exist rather than one that is idle.
        composeRule.onNodeWithText("Deliveries").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun anEmptyDatabaseExplainsItselfInsteadOfDrawingEmptyCharts() {
        composeRule.setContent { SignalTheme { InsightsScreen(UiState(), model) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nothing to count yet").assertIsDisplayed()
        composeRule.onNodeWithText("By hour of day").assertDoesNotExist()
    }
}
