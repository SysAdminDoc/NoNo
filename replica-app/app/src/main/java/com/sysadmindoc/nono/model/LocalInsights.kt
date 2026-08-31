package com.sysadmindoc.nono.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val INSIGHT_DAY_COUNT = 14
const val INSIGHT_TOP_APP_LIMIT = 5

/**
 * Rules listed on the Insights screen.
 *
 * A rule list is user-sized and an import may carry ten thousand of them. The card is one item in
 * a lazy list, so every row it emits is composed and measured at once; capping it is what keeps
 * opening the screen cheap for someone with a large rule set.
 */
const val INSIGHT_TOP_RULE_LIMIT = 10

/** Exact stored-row totals returned by Room. */
data class InsightTotals(
    val storedRecordCount: Int = 0,
    val totalCaptured: Int = 0,
    val excludedGroupSummaries: Int = 0,
)

data class InsightAppCount(val packageName: String, val count: Int)
data class InsightHourCount(val hour: Int, val count: Int)
data class InsightDayCount(val dayKey: String, val count: Int)
data class InsightDay(val dayKey: String, val label: String, val count: Int)

/**
 * A complete, display-ready snapshot assembled from bounded Room aggregate rows.
 *
 * @property loaded false until the aggregates have answered. The queries are whole-table scans
 * started when the screen opens, so the default is what the screen holds for the first frames and
 * calling that "nothing captured" would state something false about a full history.
 */
data class LocalInsights(
    val loaded: Boolean = false,
    val storedRecordCount: Int = 0,
    val totalCaptured: Int = 0,
    val excludedGroupSummaries: Int = 0,
    val topApps: List<InsightAppCount> = emptyList(),
    val hourlyCounts: List<Int> = List(24) { 0 },
    val dailyTrend: List<InsightDay> = emptyList(),
) {
    /**
     * True only when there is nothing stored at all.
     *
     * Not `totalCaptured == 0`: a history holding nothing but group summaries has rows the user
     * can see in History, and telling them there is nothing to count would contradict the screen
     * they just came from. That case has its own line instead.
     */
    val isEmpty: Boolean get() = storedRecordCount == 0

    /** Rows are stored, but every one of them is a group summary, which the counts leave out. */
    val onlyGroupSummaries: Boolean get() = storedRecordCount > 0 && totalCaptured == 0

    val busiestHour: Int?
        get() {
            val busiestCount = hourlyCounts.maxOrNull() ?: 0
            return hourlyCounts.indexOf(busiestCount).takeIf { busiestCount > 0 }
        }

    val busiestHourCount: Int get() = busiestHour?.let(hourlyCounts::get) ?: 0

    /**
     * How many records History holds, which is the same number History itself shows.
     *
     * Both are `COUNT(*)` over `notification_history`, so they reconcile by construction rather
     * than by a check. An earlier version compared this against the separately collected History
     * total and reported a mismatch every time a notification arrived between the two flows
     * emitting, which is a race in the check rather than a disagreement in the data.
     */
    val historyRecordCount: Int get() = storedRecordCount
}

/**
 * Fills sparse SQL groups into fixed charts and normalizes their order.
 *
 * Room does the database scan on its query executor. This function only handles the bounded rows
 * returned by those aggregate queries.
 */
fun buildLocalInsights(
    totals: InsightTotals,
    appCounts: List<InsightAppCount>,
    hourCounts: List<InsightHourCount>,
    dayCounts: List<InsightDayCount>,
    nowEpochMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): LocalInsights {
    val hours = MutableList(24) { 0 }
    hourCounts.forEach { row ->
        if (row.hour in hours.indices && row.count > 0) {
            hours[row.hour] = addCounts(hours[row.hour], row.count)
        }
    }

    val countsByDay = dayCounts
        .filter { it.dayKey.isNotBlank() && it.count > 0 }
        .groupingBy { it.dayKey }
        .fold(0) { total, row -> addCounts(total, row.count) }

    val dayWindow = insightDayWindow(nowEpochMillis, zone, locale).map { (key, label) ->
        InsightDay(key, label, countsByDay[key] ?: 0)
    }

    val topApps = appCounts
        .filter { it.packageName.isNotBlank() && it.count > 0 }
        .groupBy { it.packageName }
        .map { (packageName, rows) ->
            InsightAppCount(packageName, rows.fold(0) { total, row -> addCounts(total, row.count) })
        }
        .sortedWith(compareByDescending<InsightAppCount> { it.count }.thenBy { it.packageName })
        .take(INSIGHT_TOP_APP_LIMIT)

    return LocalInsights(
        loaded = true,
        storedRecordCount = totals.storedRecordCount.coerceAtLeast(0),
        totalCaptured = totals.totalCaptured.coerceAtLeast(0),
        excludedGroupSummaries = totals.excludedGroupSummaries.coerceAtLeast(0),
        topApps = topApps,
        hourlyCounts = hours,
        dailyTrend = dayWindow,
    )
}

/** Local midnight at the start of the fourteen-day chart. */
fun insightsStartEpochMillis(
    nowEpochMillis: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Long = Calendar.getInstance(zone).apply {
    timeInMillis = nowEpochMillis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -(INSIGHT_DAY_COUNT - 1))
}.timeInMillis

fun formatInsightHour(hour: Int): String {
    if (hour !in 0..23) return "Unknown hour"
    val suffix = if (hour < 12) "AM" else "PM"
    val display = when (val clockHour = hour % 12) {
        0 -> 12
        else -> clockHour
    }
    return "$display $suffix"
}

private fun insightDayWindow(
    nowEpochMillis: Long,
    zone: TimeZone,
    locale: Locale,
): List<Pair<String, String>> {
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = insightsStartEpochMillis(nowEpochMillis, zone)
    }
    val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }
    val labelFormat = SimpleDateFormat("MMM d", locale).apply { timeZone = zone }
    return List(INSIGHT_DAY_COUNT) {
        val date = Date(calendar.timeInMillis)
        (keyFormat.format(date) to labelFormat.format(date)).also {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

private fun addCounts(first: Int, second: Int): Int =
    (first.toLong() + second.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
