package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.HISTORY_PAGE_SIZE
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.InsightHourCount
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.historyFilterCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCountsTest {

    private fun page(size: Int) = (1..size).map { HistoryRecord(id = it.toLong()) }

    @Test
    fun theCaptionSaysWhatTheNumberCounts() {
        // The screen used to show the size of the loaded page labelled "notifications today",
        // which was neither a total nor today's.
        assertEquals("records retained", historyCountCaption(total = 250, filtered = 250, filter = "All"))
        assertEquals("record retained", historyCountCaption(total = 1, filtered = 1, filter = "All"))
        assertEquals("starred, of 250 retained", historyCountCaption(total = 250, filtered = 3, filter = "Starred"))
        assertEquals(
            "matched a rule, of 250 retained",
            historyCountCaption(total = 250, filtered = 40, filter = "Rule-triggered"),
        )
    }

    @Test
    fun aNarrowedListNamesTheTotalItWasNarrowedFrom() {
        // Metadata filters narrow the same "All" segment, so the caption still has to say the
        // number on screen is not everything.
        assertEquals("records shown, of 250 retained", historyCountCaption(total = 250, filtered = 12, filter = "All"))
    }

    @Test
    fun moreIsOfferedOnlyWhenSomethingIsNotLoaded() {
        val loaded = UiState(history = page(HISTORY_PAGE_SIZE), historyFilteredCount = 250)

        assertTrue(loaded.hasMoreHistory)
        assertFalse(loaded.copy(historyFilteredCount = HISTORY_PAGE_SIZE).hasMoreHistory)
        assertFalse(UiState(history = emptyList(), historyFilteredCount = 0).hasMoreHistory)
    }

    @Test
    fun changingWhatTheListSelectsStartsTheWindowOver() {
        // Three pages deep, then a filter change. Keeping the window would load three pages of the
        // new query and read as though that were all of it.
        val deep = UiState(historyLimit = HISTORY_PAGE_SIZE * 3)

        assertEquals(HISTORY_PAGE_SIZE, deep.resetHistoryWindow().historyLimit)
    }

    @Test
    fun theChartDrawsTheHoursTheDatabaseCounted() {
        // The overview chart used to render seven hardcoded bar heights under a real time axis,
        // whatever the hours actually were. These pin it to the aggregate rows.
        val oneBusyHour = historyHourTotals(listOf(InsightHourCount(hour = 20, count = 6)))

        assertEquals(24, oneBusyHour.size)
        assertEquals(6, oneBusyHour[20])
        assertEquals(0, oneBusyHour.sum() - oneBusyHour[20])
        // Out-of-range and non-positive rows cannot invent a bar.
        assertEquals(List(24) { 0 }, historyHourTotals(listOf(InsightHourCount(24, 5), InsightHourCount(-1, 5), InsightHourCount(3, 0))))
    }

    @Test
    fun theChartSpeaksRealValuesOrNothing() {
        assertEquals(
            "All retained notifications by hour of day. Busiest at 8 PM with 6.",
            historyChartDescription(historyHourTotals(listOf(InsightHourCount(20, 6), InsightHourCount(9, 4)))),
        )
        assertEquals("No retained notifications yet.", historyChartDescription(historyHourTotals(emptyList())))
    }

    @Test
    fun everyOfferedFilterIsOneTheQueryImplements() {
        // The SQL branches on these exact strings, so a label added here without a branch there
        // would silently select everything. Dismissed joined the list when the query gained the
        // branch for it, in the same change; the instrumented RemovalRecordTest proves that branch
        // selects only what the platform called a user removal.
        assertEquals(listOf("All", "Rule-triggered", "Starred", "Dismissed"), historyFilterCatalog)
    }
}
