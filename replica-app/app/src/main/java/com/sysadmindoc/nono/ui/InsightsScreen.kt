package com.sysadmindoc.nono.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.INSIGHT_TOP_RULE_LIMIT
import com.sysadmindoc.nono.model.InsightDay
import com.sysadmindoc.nono.model.LocalInsights
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.formatInsightHour

/** Tall enough to read a shape from, short enough that fourteen of them fit a compact screen. */
private val CHART_HEIGHT = 96.dp

/**
 * What the stored metadata adds up to.
 *
 * Every number here comes from a Room aggregate over rows the app already holds. Nothing new is
 * captured to draw it, and no notification text is involved: the screen counts packages, hours and
 * days, which is all a history record keeps.
 */
@Composable
fun InsightsScreen(state: UiState, model: MainViewModel) {
    val insights = state.insights
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SignalTopBar("Insights", onBack = { model.selectRoot(RootTab.EXPLORE) }) }
        if (insights.isEmpty || insights.onlyGroupSummaries) {
            item {
                InsightsEmptyState(
                    insights,
                    Modifier.padding(horizontal = SignalMetrics.pageHorizontal),
                )
            }
            return@LazyColumn
        }
        item {
            TotalsCard(insights, Modifier.padding(horizontal = SignalMetrics.pageHorizontal))
        }
        item {
            HourlyCard(insights, Modifier.padding(horizontal = SignalMetrics.pageHorizontal))
        }
        item {
            DailyCard(insights.dailyTrend, Modifier.padding(horizontal = SignalMetrics.pageHorizontal))
        }
        item {
            TopAppsCard(state, Modifier.padding(horizontal = SignalMetrics.pageHorizontal))
        }
        item {
            RuleMatchesCard(state, Modifier.padding(horizontal = SignalMetrics.pageHorizontal))
        }
    }
}

@Composable
private fun InsightsEmptyState(insights: LocalInsights, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            Text(emptyInsightsTitle(insights), style = MaterialTheme.typography.titleMedium)
            Text(
                emptyInsightsDetail(insights),
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Two different kinds of nothing.
 *
 * A history holding only group summaries is not an empty history, and saying it is would
 * contradict the History screen the user just came from.
 */
internal fun emptyInsightsTitle(insights: LocalInsights): String =
    if (insights.onlyGroupSummaries) "Only group summaries so far" else "Nothing to count yet"

internal fun emptyInsightsDetail(insights: LocalInsights): String = when {
    insights.onlyGroupSummaries -> {
        val stored = insights.excludedGroupSummaries
        "History holds $stored group ${if (stored == 1) "summary" else "summaries"} and nothing " +
            "else. A summary stands for its group rather than being a notification of its own, so " +
            "the counts here leave it out, exactly as every other count in the app does."
    }
    else -> "Insights are built from the notifications already in History. Once capture has " +
        "recorded some, the counts appear here."
}

@Composable
private fun TotalsCard(insights: LocalInsights, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            Text("Captured", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium)
            Text(
                insights.totalCaptured.toString(),
                color = SignalColors.Yellow,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                describeStoredRecords(insights),
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            val busiest = insights.busiestHour
            if (busiest != null) {
                Text(
                    "Busiest hour: ${formatInsightHour(busiest)} with ${insights.busiestHourCount} " +
                        pluralNotifications(insights.busiestHourCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * States the relationship between the two numbers rather than leaving them to contradict.
 *
 * History shows group summaries as records; the counts here exclude them, exactly as the rest of
 * the app's counting does. Without this line the Insights total reads as a smaller, wrong version
 * of the History total. All three numbers come from one row of one query, so they cannot disagree
 * merely because two reads landed at different moments.
 */
internal fun describeStoredRecords(insights: LocalInsights): String {
    val stored = insights.storedRecordCount
    val summaries = insights.excludedGroupSummaries
    return when {
        !insights.reconciles -> "Counts are still being read."
        summaries == 0 -> "From $stored stored ${pluralRecords(stored)}."
        else -> "From $stored stored ${pluralRecords(stored)}, excluding $summaries group " +
            if (summaries == 1) "summary." else "summaries."
    }
}

private fun pluralRecords(count: Int): String = if (count == 1) "record" else "records"

private fun pluralNotifications(count: Int): String = if (count == 1) "notification" else "notifications"

@Composable
private fun HourlyCard(insights: LocalInsights, modifier: Modifier = Modifier) {
    val counts = insights.hourlyCounts
    val total = counts.sumOf { it.toLong() }
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            SignalSectionHeading("By hour of day", "When notifications arrive, over everything stored.")
            BarChart(
                values = counts,
                description = "Notifications by hour of day. $total in total. " +
                    (insights.busiestHour?.let { "Busiest at ${formatInsightHour(it)} with ${insights.busiestHourCount}." } ?: ""),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                AxisLabel("12 AM", Modifier.weight(1f))
                AxisLabel("12 PM", Modifier.weight(1f), Alignment.CenterHorizontally)
                AxisLabel("11 PM", Modifier.weight(1f), Alignment.End)
            }
        }
    }
}

@Composable
private fun DailyCard(days: List<InsightDay>, modifier: Modifier = Modifier) {
    val busiest = days.maxByOrNull { it.count }
    val total = days.sumOf { it.count.toLong() }
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            SignalSectionHeading("Last ${days.size} days", "One bar per day, oldest first.")
            BarChart(
                values = days.map { it.count },
                description = "Notifications per day. $total across the window." +
                    (busiest?.takeIf { it.count > 0 }?.let { " Busiest on ${it.label} with ${it.count}." } ?: ""),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                AxisLabel(days.firstOrNull()?.label.orEmpty(), Modifier.weight(1f))
                AxisLabel(days.lastOrNull()?.label.orEmpty(), Modifier.weight(1f), Alignment.End)
            }
            if (busiest != null && busiest.count > 0) {
                Text(
                    "Busiest day: ${busiest.label} with ${busiest.count} ${pluralNotifications(busiest.count)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TopAppsCard(state: UiState, modifier: Modifier = Modifier) {
    val apps = state.insights.topApps
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            SignalSectionHeading("Most active apps", "Counted across everything stored.")
            if (apps.isEmpty()) {
                Text(
                    "No app has posted a captured notification yet.",
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            apps.forEach { app ->
                val label = state.appCatalog.firstOrNull { it.packageName == app.packageName }?.label
                    ?: app.packageName
                CountRow(label, app.packageName.takeIf { it != label }, app.count)
            }
        }
    }
}

@Composable
private fun RuleMatchesCard(state: UiState, modifier: Modifier = Modifier) {
    // A rule list is user-sized and an import can carry ten thousand. This card is one item in a
    // lazy list, so every row it emits is composed at once and the list cannot virtualize any of
    // them. Sorting is remembered for the same reason: recomposition must not re-sort the lot.
    val ranked = remember(state.rules, state.ruleMatchCounts) {
        state.rules
            .sortedWith(compareByDescending<SignalRule> { state.ruleMatchCounts[it.id] ?: 0 }.thenBy { it.name })
            .take(INSIGHT_TOP_RULE_LIMIT)
    }
    SurfaceCard(modifier.fillMaxWidth()) {
        Column {
            SignalSectionHeading("Rule matches", "How often each saved rule would have fired.")
            if (ranked.isEmpty()) {
                Text(
                    "No rules are saved, so nothing has been matched.",
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ranked.forEach { rule ->
                CountRow(rule.name, null, state.ruleMatchCounts[rule.id] ?: 0)
            }
            describeHiddenRules(state.rules.size)?.let { line ->
                Text(
                    line,
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Says that the list was cut, so a missing rule does not read as a rule that never matched. */
internal fun describeHiddenRules(total: Int): String? {
    val hidden = total - INSIGHT_TOP_RULE_LIMIT
    if (hidden <= 0) return null
    return "$hidden more ${if (hidden == 1) "rule is" else "rules are"} saved. This list shows the " +
        "$INSIGHT_TOP_RULE_LIMIT with the most matches."
}

@Composable
private fun CountRow(title: String, detail: String?, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(detail, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            count.toString(),
            color = SignalColors.Yellow,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AxisLabel(
    label: String,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label, color = SignalColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The bars carry no text of their own, so the whole chart answers as one node.
 *
 * A screen reader stepping through twenty-four unlabelled boxes learns nothing; the summary in
 * [description] is what the chart actually says.
 */
@Composable
private fun BarChart(values: List<Int>, description: String, modifier: Modifier = Modifier) {
    val peak = values.maxOrNull() ?: 0
    Row(
        modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            // A zero-count slot still draws a floor, so the axis stays readable where nothing
            // arrived instead of leaving a gap that reads as missing data.
            val fraction = if (peak <= 0) 0f else value.toFloat() / peak.toFloat()
            Column(
                Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { },
                verticalArrangement = Arrangement.Bottom,
            ) {
                Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.02f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(fraction.coerceAtLeast(0.02f))
                        .background(
                            if (value > 0) SignalColors.Yellow else SignalColors.Border,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}
