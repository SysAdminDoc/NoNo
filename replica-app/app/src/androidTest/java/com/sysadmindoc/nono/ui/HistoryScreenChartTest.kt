package com.sysadmindoc.nono.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.HistoryLoadState
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.InsightHourCount
import com.sysadmindoc.nono.model.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The overview chart used to draw seven hardcoded bar heights under a real time axis. What it
 * renders and speaks now has to come from the hourly aggregate, and the card has to say the
 * chart covers everything retained even while the number above it is filtered.
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    private val seeded = UiState(
        history = listOf(HistoryRecord(id = 1L)),
        historyLoadState = HistoryLoadState.READY,
        historyFilteredCount = 1,
        historyTotalCount = 6,
        historyHourCounts = listOf(InsightHourCount(hour = 20, count = 6)),
    )

    @Test
    fun theChartSpeaksTheHoursTheDatabaseCounted() {
        composeRule.setContent { SignalTheme { HistoryScreen(seeded, model) } }
        composeRule.waitForIdle()

        composeRule
            .onNode(hasContentDescription("All retained notifications by hour of day. Busiest at 8 PM with 6."))
            .assertExists()
        composeRule.onNodeWithText("All retained, by hour").assertIsDisplayed()
    }
}
