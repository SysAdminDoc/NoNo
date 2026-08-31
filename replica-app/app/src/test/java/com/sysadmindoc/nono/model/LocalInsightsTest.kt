package com.sysadmindoc.nono.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aggregation math, independent of Room.
 *
 * Every input here is what a bounded aggregate query returns: sparse groups, in whatever order
 * SQLite produced them. The screen needs fixed-length charts, so the gap between the two is what
 * these cover.
 */
class LocalInsightsTest {

    private val zone = TimeZone.getTimeZone("UTC")

    /** 2026-08-31 14:00 UTC. */
    private val now = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.AUGUST, 31, 14, 0, 0)
    }.timeInMillis

    private fun build(
        totals: InsightTotals = InsightTotals(),
        apps: List<InsightAppCount> = emptyList(),
        hours: List<InsightHourCount> = emptyList(),
        days: List<InsightDayCount> = emptyList(),
    ) = buildLocalInsights(totals, apps, hours, days, now, zone, Locale.US)

    @Test
    fun anEmptyDatabaseProducesFixedLengthChartsRatherThanNothing() {
        val insights = build()

        assertTrue(insights.isEmpty)
        assertFalse(insights.onlyGroupSummaries)
        assertEquals(24, insights.hourlyCounts.size)
        assertEquals(INSIGHT_DAY_COUNT, insights.dailyTrend.size)
        assertTrue(insights.dailyTrend.all { it.count == 0 })
        assertNull(insights.busiestHour)
        assertEquals(0, insights.busiestHourCount)
    }

    @Test
    fun sparseHourGroupsFillTheMissingHoursWithZero() {
        val insights = build(hours = listOf(InsightHourCount(9, 4), InsightHourCount(21, 11)))

        assertEquals(24, insights.hourlyCounts.size)
        assertEquals(4, insights.hourlyCounts[9])
        assertEquals(11, insights.hourlyCounts[21])
        assertEquals(0, insights.hourlyCounts[10])
        assertEquals(21, insights.busiestHour)
        assertEquals(11, insights.busiestHourCount)
    }

    @Test
    fun anHourOutsideTheClockIsDroppedRatherThanShiftingTheChart() {
        // strftime cannot produce this, but a corrupt or migrated row should not crash the screen
        // or silently land in another hour's bar.
        val insights = build(hours = listOf(InsightHourCount(24, 9), InsightHourCount(-1, 9), InsightHourCount(3, 2)))

        assertEquals(24, insights.hourlyCounts.size)
        assertEquals(2, insights.hourlyCounts.sum())
        assertEquals(3, insights.busiestHour)
    }

    @Test
    fun theBusiestHourIsTheEarliestOfATie() {
        val insights = build(hours = listOf(InsightHourCount(18, 5), InsightHourCount(6, 5)))

        assertEquals(6, insights.busiestHour)
        assertEquals(5, insights.busiestHourCount)
    }

    @Test
    fun theDayWindowEndsOnTodayAndCoversTheWholeSpan() {
        val insights = build(days = listOf(InsightDayCount("2026-08-31", 3)))

        assertEquals(INSIGHT_DAY_COUNT, insights.dailyTrend.size)
        assertEquals("2026-08-18", insights.dailyTrend.first().dayKey)
        assertEquals("2026-08-31", insights.dailyTrend.last().dayKey)
        assertEquals(3, insights.dailyTrend.last().count)
        assertEquals("Aug 18", insights.dailyTrend.first().label)
    }

    @Test
    fun aDayOutsideTheWindowIsNotCharted() {
        // The query carries a cutoff, but the window is rebuilt from the same clock, so a row that
        // arrives from an earlier day must not be folded into the first bar.
        val insights = build(days = listOf(InsightDayCount("2026-08-01", 40), InsightDayCount("2026-08-20", 6)))

        assertEquals(6, insights.dailyTrend.sumOf { it.count })
        assertEquals(6, insights.dailyTrend.single { it.dayKey == "2026-08-20" }.count)
    }

    @Test
    fun theDayWindowIsBuiltInTheRequestedZone() {
        // 2026-08-31 16:00 UTC is already 2026-09-01 in Tokyo, so the window has to end there.
        val evening = Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.AUGUST, 31, 16, 0, 0)
        }.timeInMillis
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo")

        val utcWindow = buildLocalInsights(InsightTotals(), emptyList(), emptyList(), emptyList(), evening, zone, Locale.US)
        val tokyoWindow = buildLocalInsights(InsightTotals(), emptyList(), emptyList(), emptyList(), evening, tokyo, Locale.US)

        assertEquals("2026-08-31", utcWindow.dailyTrend.last().dayKey)
        assertEquals("2026-09-01", tokyoWindow.dailyTrend.last().dayKey)
        assertEquals("2026-08-19", tokyoWindow.dailyTrend.first().dayKey)
    }

    @Test
    fun theQueryCutoffIsLocalMidnightAtTheStartOfTheWindow() {
        val start = insightsStartEpochMillis(now, zone)
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = start }

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, calendar.get(Calendar.MONTH))
        assertEquals(18, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
        assertTrue(start < now)
    }

    @Test
    fun topAppsAreRankedByCountThenPackageAndCapped() {
        val apps = (1..8).map { InsightAppCount("com.example.app$it", it) } +
            InsightAppCount("com.example.aaa", 8)
        val insights = build(apps = apps)

        assertEquals(INSIGHT_TOP_APP_LIMIT, insights.topApps.size)
        assertEquals("com.example.aaa", insights.topApps.first().packageName)
        assertEquals("com.example.app8", insights.topApps[1].packageName)
        assertEquals(listOf(8, 8, 7, 6, 5), insights.topApps.map { it.count })
    }

    @Test
    fun anAppWithNoCountsIsNotListed() {
        val insights = build(apps = listOf(InsightAppCount("com.example.quiet", 0), InsightAppCount("  ", 4)))

        assertTrue(insights.topApps.isEmpty())
    }

    @Test
    fun theStoredCountIsTheNumberHistoryShows() {
        // Both are COUNT(*) over the same table, so they reconcile by construction. The captured
        // and excluded numbers are read from the same row, which is why nothing here can drift.
        val insights = build(totals = InsightTotals(storedRecordCount = 10, totalCaptured = 8, excludedGroupSummaries = 2))

        assertEquals(8, insights.totalCaptured)
        assertEquals(2, insights.excludedGroupSummaries)
        assertEquals(10, insights.historyRecordCount)
        assertFalse(insights.isEmpty)
        assertFalse(insights.onlyGroupSummaries)
    }

    @Test
    fun anUnansweredSnapshotIsNotTreatedAsAnEmptyOne() {
        // The queries are whole-table scans started when the screen opens. Reporting the default
        // as "nothing captured" states something false about a full history for as long as they
        // take to answer.
        assertFalse("the default must not claim to have answered", LocalInsights().loaded)
        assertTrue(build().loaded)
    }

    @Test
    fun aHistoryOfNothingButGroupSummariesIsNotEmpty() {
        // History lists those rows. Reporting the screen as empty would contradict it.
        val insights = build(totals = InsightTotals(storedRecordCount = 40, totalCaptured = 0, excludedGroupSummaries = 40))

        assertFalse(insights.isEmpty)
        assertTrue(insights.onlyGroupSummaries)
        assertEquals(40, insights.historyRecordCount)
    }

    @Test
    fun everyHourFormatsToADistinctReadableLabel() {
        val labels = (0..23).map(::formatInsightHour)

        assertEquals(24, labels.distinct().size)
        assertEquals("12 AM", labels[0])
        assertEquals("11 AM", labels[11])
        assertEquals("12 PM", labels[12])
        assertEquals("11 PM", labels[23])
        assertTrue(labels.none { label -> label.any { it == '—' || it == '–' } })
        assertEquals("Unknown hour", formatInsightHour(24))
    }
}
