package com.sysadmindoc.nono.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.HistoryLoadState
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.categoryLabel
import com.sysadmindoc.nono.model.historyFilterCatalog
import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.InsightHourCount
import com.sysadmindoc.nono.model.formatInsightHour
import com.sysadmindoc.nono.model.NO_DEVICE_ACTION_LABEL
import com.sysadmindoc.nono.data.formatStoredTime
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RemovalReason
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.field
import com.sysadmindoc.nono.model.importanceLabel
import com.sysadmindoc.nono.runtime.MetadataConditionFailure
import com.sysadmindoc.nono.runtime.NotificationPayload
import com.sysadmindoc.nono.runtime.evaluateMetadataConditions

@Composable
fun HistoryScreen(state: UiState, model: MainViewModel) {
    if (state.historySearchActive) {
        SearchHistory(state, model)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                SignalIconButton(Icons.Rounded.Search, "Search history", model::openHistorySearch)
                SignalIconButton(Icons.Rounded.FilterAlt, "Filter history metadata", { model.showOverlay(Overlay.HISTORY_FILTERS) })
            }
        }
        item { HistoryOverview(state.historyTotalCount, state.historyFilteredCount, state.historyFilter, state.historyHourCounts) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                historyFilterCatalog.forEach { filter ->
                    HistoryFilterButton(
                        // "Rule-triggered" is the stored value; this is what it is called on screen.
                        if (filter == "Rule-triggered") "Matched rules" else filter,
                        state.historyFilter == filter,
                        Modifier.weight(1f),
                    ) { model.setHistoryFilter(filter) }
                }
            }
        }
        val metadataFilterSummary = historyMetadataSummary(state)
        if (metadataFilterSummary.isNotBlank()) {
            item { SignalStatusPanel("Metadata filters", metadataFilterSummary, icon = Icons.Rounded.FilterAlt) }
        }
        item { SignalSectionHeading("Recent metadata", "Notification content is never stored.") }
        when (state.historyLoadState) {
            HistoryLoadState.LOADING -> item { HistoryStatePanel("Loading notification history", "Reading the local metadata store.", Icons.Rounded.History) }
            HistoryLoadState.ERROR -> item {
                Column {
                    HistoryStatePanel("History is unavailable", state.historyError ?: "The local metadata store could not be read.", Icons.Rounded.Info)
                    SignalOutlineButton("Retry", model::retryHistory, Modifier.fillMaxWidth())
                }
            }
            HistoryLoadState.READY -> if (state.history.isEmpty()) {
                item { EmptyHistory(state) }
            } else {
                item {
                    SignalGroupedSurface(Modifier.fillMaxWidth()) {
                        state.history.forEachIndexed { index, item ->
                            HistoryRecordRow(item, state.rules) { model.showHistoryOverlay(item.id) }
                            if (index != state.history.lastIndex) SignalDivider()
                        }
                    }
                }
                if (state.hasMoreHistory) {
                    item {
                        SignalOutlineButton(
                            "Load more (${state.history.size} of ${state.historyFilteredCount})",
                            model::loadMoreHistory,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SearchHistory(state: UiState, model: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { requestKeyboardFocus(focusRequester, keyboard) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.historySearch,
                onValueChange = model::setHistorySearch,
                placeholder = { Text("Search history") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { keyboard?.hide(); model.closeHistorySearch() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close search")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(SignalMetrics.controlRadius),
                colors = historyTextFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (state.history.isEmpty()) {
            item { HistoryStatePanel("No matching notifications", "Try another search term.", Icons.Rounded.Search) }
        } else {
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    state.history.forEachIndexed { index, item ->
                        HistoryRecordRow(item, state.rules) { model.showHistoryOverlay(item.id) }
                        if (index != state.history.lastIndex) SignalDivider()
                    }
                }
            }
        }
    }
}

/**
 * The two counts the screen can honestly report.
 *
 * The big number used to be the size of the loaded page, labelled "notifications today". It was
 * neither a total nor today's: capped at the page limit, and covering however far back retention
 * reaches.
 */
@Composable
private fun HistoryOverview(total: Int, filtered: Int, filter: String, hours: List<InsightHourCount>) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(filtered.toString(), style = MaterialTheme.typography.displayLarge)
            Text(
                historyCountCaption(total, filtered, filter),
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyLarge,
            )
            HistoryActivityChart(hours, Modifier.fillMaxWidth().height(106.dp).padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12AM", "6AM", "12PM", "6PM", "12AM").forEach {
                    Text(it, color = SignalColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            // The chart covers everything retained, and the number above may be filtered; this
            // line is what keeps the two honest next to each other.
            Text(
                "All retained, by hour",
                color = SignalColors.Muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Says what the number counts, and names the total when a filter is narrowing it. */
internal fun historyCountCaption(total: Int, filtered: Int, filter: String): String {
    val noun = if (filtered == 1) "record" else "records"
    return when {
        filter == "All" && filtered == total -> "$noun retained"
        filter == "Starred" -> "starred, of $total retained"
        filter == "Rule-triggered" -> "matched a rule, of $total retained"
        else -> "$noun shown, of $total retained"
    }
}

/** One slot per hour of day, summed from the aggregate rows and clamped to a real clock. */
internal fun historyHourTotals(hours: List<InsightHourCount>): List<Int> {
    val totals = MutableList(24) { 0 }
    hours.forEach { row ->
        if (row.hour in totals.indices && row.count > 0) totals[row.hour] += row.count
    }
    return totals
}

/**
 * What the chart says, spoken as one node in the Insights chart's style: a screen reader
 * stepping through twenty-four unlabelled bars learns nothing.
 */
internal fun historyChartDescription(totals: List<Int>): String {
    val peak = totals.maxOrNull() ?: 0
    if (peak <= 0) return "No retained notifications yet."
    val busiest = totals.indexOf(peak)
    return "All retained notifications by hour of day. Busiest at ${formatInsightHour(busiest)} with $peak."
}

@Composable
private fun HistoryActivityChart(hours: List<InsightHourCount>, modifier: Modifier = Modifier) {
    val totals = historyHourTotals(hours)
    val description = historyChartDescription(totals)
    Canvas(modifier.semantics { contentDescription = description }) {
        val baseline = size.height - 10.dp.toPx()
        drawLine(SignalColors.Border, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        val peak = totals.max()
        if (peak > 0) {
            // Hour 0 sits under the left 12AM label and hour 23 just before the right one, so
            // the bars and the axis describe the same clock.
            val slot = size.width / totals.size
            totals.forEachIndexed { index, value ->
                if (value <= 0) return@forEachIndexed
                val x = slot * (index + 0.5f)
                drawLine(
                    color = SignalColors.Yellow,
                    start = Offset(x, baseline),
                    end = Offset(x, baseline - (size.height - 20.dp.toPx()) * (value.toFloat() / peak)),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .background(if (selected) SignalColors.SurfaceSelected else SignalColors.Surface, RoundedCornerShape(SignalMetrics.controlRadius))
            .border(1.dp, if (selected) SignalColors.Yellow else SignalColors.ControlOutline, RoundedCornerShape(SignalMetrics.controlRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) SignalColors.White else SignalColors.Secondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (selected) 6.dp else 0.dp))
    }
}

@Composable
private fun EmptyHistory(state: UiState) {
    val narrowed = state.historySearch.isNotBlank() || state.historyFilter != "All" || historyMetadataSummary(state).isNotBlank()
    val title = when {
        state.historySearch.isNotBlank() -> "No matching notifications"
        narrowed -> "No metadata matches these filters"
        else -> "No history yet"
    }
    val body = when {
        state.historySearch.isNotBlank() -> "Try another search term."
        state.historyFilter == "Dismissed" -> "Nothing here was recorded as removed by you. Android only says why a notification went from Android 8 onward, and it does not always say."
        narrowed -> "Clear a filter to widen the local metadata query."
        else -> "Redacted notification metadata will appear here after access is enabled."
    }
    HistoryStatePanel(title, body, Icons.Rounded.History)
}

@Composable
private fun HistoryStatePanel(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
            Text(body, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun HistoryRecordRow(
    item: HistoryRecord,
    rules: List<SignalRule>,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(SignalColors.Background, RoundedCornerShape(10.dp)).border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.White) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(item.app, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.time, color = SignalColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.body, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.isGroupSummary) {
                Text(
                    describeGroupSummary(item.groupSummaryOrigin),
                    color = SignalColors.Muted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            describeMatchedRules(item, rules)?.let {
                Text(it, color = SignalColors.Yellow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Icon(Icons.Rounded.MoreVert, contentDescription = "History item actions", tint = SignalColors.Secondary)
    }
}

private fun historyMetadataSummary(state: UiState): String = listOfNotNull(
    state.historyPackageFilter?.let { "package=$it" },
    state.historyChannelFilter?.let { "channel=$it" },
    state.historyGroupFilter?.let { "group=$it" },
    state.historyContentStateFilter?.let { "content=${it.name}" },
    "summaries".takeIf { state.historyGroupSummaryOnly },
    importanceLabel(state.historyImportanceFilter)?.let { "importance $it" },
    "conversations".takeIf { state.historyConversationFilter == true },
).joinToString(" · ")

@Composable
fun HistoryActivityScreen(state: UiState, model: MainViewModel) {
    val selected = state.history.firstOrNull { it.id == state.selectedHistoryId }
    val attribution = selected?.let { captureAttribution(it, state.rules) }
    val metadataChecks = remember(selected, state.rules) {
        selected?.let { activityMetadataChecks(it, state.rules) }.orEmpty()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SignalTopBar("Notification activity", onBack = { model.selectRoot(RootTab.HISTORY) }) }
        if (selected == null || attribution == null) {
            item { HistoryStatePanel("History entry unavailable", "The selected record is no longer in the bounded history query.", Icons.Rounded.History) }
            return@LazyColumn
        }
        item { ActivityMetadataHeader(selected) }
        item {
            Row(Modifier.fillMaxWidth().border(1.dp, SignalColors.ControlOutline, RoundedCornerShape(SignalMetrics.controlRadius))) {
                ActivityTab("Rules", state.historyActivityTab == "Rules", Modifier.weight(1f)) { model.setHistoryActivityTab("Rules") }
                ActivityTab("Changes", state.historyActivityTab == "Changes", Modifier.weight(1f)) { model.setHistoryActivityTab("Changes") }
            }
        }
        item {
            SignalStatusPanel(
                title = attribution.headline,
                description = "Recorded when this arrived. No action was executed.",
                icon = Icons.Rounded.Shield,
            )
        }
        if (state.historyActivityTab == "Rules") {
            item { Text("EVALUATION", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item { AttributionTrace(attribution) }
            if (attribution.rules.isNotEmpty()) {
                item { Text("RULES THAT MATCHED", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
                item {
                    SignalGroupedSurface(Modifier.fillMaxWidth()) {
                        attribution.rules.forEachIndexed { index, rule ->
                            ActivityRow(
                                rule.name,
                                if (rule.deleted) {
                                    "Rule ${rule.id} no longer exists. It matched when this arrived."
                                } else {
                                    "Rule ${rule.id}"
                                },
                            )
                            if (index != attribution.rules.lastIndex) SignalDivider()
                        }
                    }
                }
            }
            item { Text("CAPTURED METADATA", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item { CapturedMetadata(selected) }
            if (metadataChecks.isNotEmpty()) {
                item { Text("CURRENT METADATA CHECK", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
                item {
                    SignalGroupedSurface(Modifier.fillMaxWidth()) {
                        metadataChecks.forEachIndexed { index, check ->
                            ActivityRow(check.ruleName, check.detail)
                            if (index != metadataChecks.lastIndex) SignalDivider()
                        }
                    }
                }
                item {
                    Text(
                        "This comparison uses the rules saved now. Capture attribution above remains the historical record.",
                        color = SignalColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            item { Text("CHANGES", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    ActivityRow("Notification posted", "Captured locally without storing private payloads.")
                    SignalDivider()
                    ActivityRow("Action preview only", "No device action: no notification, sound, or setting was changed.")
                }
            }
        }
        item { SignalOutlineButton("Create rule from this", model::createRuleFromSelectedHistory, Modifier.fillMaxWidth(), Icons.Rounded.Add) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ActivityMetadataHeader(record: HistoryRecord) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(record.title, style = MaterialTheme.typography.titleLarge)
                Text(record.app, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyLarge)
                Text(record.time, color = SignalColors.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun ActivityTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).background(if (selected) SignalColors.SurfaceSelected else SignalColors.Surface)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) SignalColors.Yellow else SignalColors.Secondary, style = MaterialTheme.typography.labelLarge)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(SignalColors.Yellow))
    }
}

@Composable
private fun AttributionTrace(attribution: CaptureAttribution) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        ActivityStep(1, "Rules", attribution.evaluationDetail)
        SignalDivider()
        ActivityStep(2, "Content", attribution.contentDetail)
        SignalDivider()
        ActivityStep(3, "Action", NO_DEVICE_ACTION_LABEL)
    }
}

@Composable
private fun ActivityStep(step: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        SignalStepNumber(step)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun CapturedMetadata(record: HistoryRecord) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        MetadataRow("Package", record.appPackageName ?: "Not available")
        SignalDivider()
        MetadataRow("Channel", record.channelId ?: "Not available")
        SignalDivider()
        MetadataRow("Group", record.groupKey ?: "Not available")
        SignalDivider()
        MetadataRow("Group Android imposed", record.overrideGroupKey ?: "None")
        SignalDivider()
        MetadataRow("Group summary", if (record.isGroupSummary) describeGroupSummary(record.groupSummaryOrigin) else "No")
        SignalDivider()
        MetadataRow("Importance", importanceLabel(record.importance) ?: "Not available")
        SignalDivider()
        MetadataRow("Category", record.category?.let(::categoryLabel) ?: "Not available")
        SignalDivider()
        MetadataRow("Conversation", record.isConversation?.let { if (it) "Yes" else "No" } ?: "Not available")
        SignalDivider()
        MetadataRow("Ongoing", if (record.isOngoing) "Yes" else "No")
        SignalDivider()
        MetadataRow("Content storage", "Not stored")
        SignalDivider()
        MetadataRow("Left the shade", describeRemoval(record))
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActivityRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}


internal data class ActivityMetadataCheck(
    val ruleId: Long,
    val ruleName: String,
    val matched: Boolean,
    val detail: String,
)

/** Compares current typed conditions with the metadata the selected record actually retained. */
internal fun activityMetadataChecks(
    record: HistoryRecord,
    rules: List<SignalRule>,
): List<ActivityMetadataCheck> {
    val payload = NotificationPayload(
        title = null,
        text = null,
        appLabel = record.app,
        packageName = record.appPackageName,
        contentStateOverride = record.contentState,
        channelId = record.channelId,
        importance = record.importance,
        category = record.category,
        isConversation = record.isConversation,
        isOngoing = record.isOngoing,
        isGroupSummary = record.isGroupSummary,
    )
    return rules.filter { it.metadataConditions.isNotEmpty() }.map { rule ->
        val traces = evaluateMetadataConditions(rule.metadataConditions, payload)
        val failures = traces.filterNot { it.matched }
        val detail = if (failures.isEmpty()) {
            if (traces.size == 1) {
                "The current metadata condition matches this record."
            } else {
                "All ${traces.size} current metadata conditions match this record."
            }
        } else {
            failures.joinToString(separator = "; ") { trace ->
                when (trace.failure) {
                    MetadataConditionFailure.VALUE_MISMATCH ->
                        "${trace.condition.field.label}: expected ${trace.expectedValue}, recorded ${trace.actualValue ?: "not available"}"
                    MetadataConditionFailure.METADATA_NOT_AVAILABLE ->
                        "${trace.condition.field.label}: expected ${trace.expectedValue}, but this record has no value"
                    MetadataConditionFailure.INVALID_CONDITION ->
                        "${trace.condition.field.label}: the saved condition value is invalid"
                    null -> "${trace.condition.field.label}: matches"
                }
            } + "."
        }
        ActivityMetadataCheck(rule.id, rule.name, failures.isEmpty(), detail)
    }
}

/**
 * Says what a summary row is, and how much is actually known about where it came from.
 *
 * Unknown is the honest common case: Android names no author for a summary, so the two grouping
 * signals it does publish only sometimes settle it.
 */
/**
 * A rule a record was attributed to when it was captured.
 *
 * @property deleted true when the rule no longer exists. Its id is still shown: a record that
 * quietly dropped the rule it matched would misrepresent what happened at capture time.
 */
internal data class AttributedRule(val id: Long, val name: String, val deleted: Boolean)

/** Everything the Activity screen says about a record, read from what capture stored. */
internal data class CaptureAttribution(
    val matchState: RuleMatchState,
    val rules: List<AttributedRule>,
    val headline: String,
    val evaluationDetail: String,
    val contentDetail: String,
)

/**
 * Reads a record's stored attribution. Nothing is re-evaluated.
 *
 * A stored row is metadata only: it replays with no title and no text whatever content it
 * originally carried. Running the current rules against that answered a different question from
 * the one the screen asks, and could contradict the ids capture actually recorded.
 */
internal fun captureAttribution(record: HistoryRecord, rules: List<SignalRule>): CaptureAttribution {
    val attributed = record.matchedRuleIds.map { id ->
        val rule = rules.firstOrNull { it.id == id }
        AttributedRule(id = id, name = rule?.name ?: "Deleted rule $id", deleted = rule == null)
    }
    // Derived from the stored state, not just from whether ids are present. "No rule matched"
    // is a claim that the rules were checked, which is untrue for three of the five states, and
    // a state that did produce ids must not be summarised as if it had not.
    val headline = when {
        attributed.size == 1 -> "Matched ${attributed.single().name}"
        attributed.isNotEmpty() -> "Matched ${attributed.size} rules"
        record.matchState == RuleMatchState.GROUP_SUMMARY -> "Group summary: no rule was tested"
        record.matchState == RuleMatchState.GROUP_SUMMARY_EVALUATED -> "Group summary: no explicit rule matched"
        record.matchState == RuleMatchState.NOT_EVALUATED -> "No rules were saved yet"
        record.matchState == RuleMatchState.RULES_NOT_LOADED -> "Arrived before the rules were read"
        record.matchState == RuleMatchState.CONTENT_HIDDEN -> "No content arrived to test"
        else -> "No rule matched"
    }
    val evaluationDetail = when (record.matchState) {
        RuleMatchState.NOT_EVALUATED -> "No rules were saved when this arrived."
        RuleMatchState.RULES_NOT_LOADED ->
            "This arrived before the saved rules had been read from disk, so none were tested."
        RuleMatchState.GROUP_SUMMARY ->
            "A group summary stands for its group rather than being an arrival of its own."
        RuleMatchState.GROUP_SUMMARY_EVALUATED -> if (attributed.isEmpty()) {
            "Checked against rules that explicitly test summary state. None matched."
        } else {
            "Checked against rules that explicitly test summary state."
        }
        RuleMatchState.CONTENT_HIDDEN ->
            "No content arrived, so any rule testing a phrase could not be checked."
        RuleMatchState.EVALUATED -> if (attributed.isEmpty()) {
            "Checked against the rules saved at the time. None matched."
        } else {
            "Checked against the rules saved at the time."
        }
    }
    return CaptureAttribution(
        matchState = record.matchState,
        rules = attributed,
        headline = headline,
        evaluationDetail = evaluationDetail,
        contentDetail = describeStoredContent(record.contentState),
    )
}

/**
 * The metadata a record actually holds, as plain text.
 *
 * The title and body History shows are UI copy derived from the content-state enum, not anything
 * the notification said, so they are deliberately absent: putting them on the clipboard would
 * invite the reader to treat them as captured content.
 */
internal fun historyMetadataClipboardText(record: HistoryRecord): String = buildString {
    appendLine("Package: ${record.appPackageName ?: record.app}")
    appendLine("Posted at (epoch millis): ${record.postedAtEpochMillis}")
    appendLine("Notification key: ${record.notificationKey}")
    appendLine("Channel: ${record.channelId ?: "not available"}")
    appendLine("Group: ${record.groupKey ?: "not available"}")
    appendLine("Group Android imposed: ${record.overrideGroupKey ?: "none"}")
    appendLine("Group summary: ${if (record.isGroupSummary) record.groupSummaryOrigin.name else "no"}")
    appendLine("Content: ${record.contentState.name}")
    appendLine("Match state: ${record.matchState.name}")
    appendLine("Matched rule ids: ${record.matchedRuleIds.joinToString(", ").ifBlank { "none" }}")
    appendLine("Importance: ${record.importance?.toString() ?: "not available"}")
    appendLine("Conversation: ${record.isConversation?.toString() ?: "not available"}")
    appendLine("Category: ${record.category ?: "not available"}")
    appendLine("Ongoing: ${record.isOngoing}")
    appendLine("Starred: ${record.starred}")
    append("Left the shade: ${describeRemoval(record)}")
}

/**
 * What is known about the notification leaving the shade.
 *
 * Three distinct answers, and they must stay distinct: it is still there, it went and Android said
 * why, or it went and Android did not say. The last is the common one below Android 8, and
 * flattening it into either of the others would be a claim the device never made.
 */
internal fun describeRemoval(record: HistoryRecord): String {
    val removedAt = record.removedAtEpochMillis ?: return "Still posted, or gone without Android saying"
    val moment = formatStoredTime(removedAt)
    return if (record.removalReason == RemovalReason.UNKNOWN) {
        "$moment. Android did not say why."
    } else {
        "$moment. ${record.removalReason.label}."
    }
}

/** What the record says about its own content, rather than what a fresh look would say. */
internal fun describeStoredContent(state: NotificationContentState): String = when (state) {
    NotificationContentState.AVAILABLE -> "Content reached the matcher and was not stored."
    NotificationContentState.HIDDEN_BY_SYSTEM ->
        "Recorded as hidden by an earlier build, which inferred that without platform confirmation."
    NotificationContentState.NOT_AVAILABLE -> "This notification arrived with no title and no text."
    NotificationContentState.NOT_STORED -> "Metadata only. Content was never persisted."
}

internal fun describeGroupSummary(origin: GroupSummaryOrigin): String = when (origin) {
    GroupSummaryOrigin.APP -> "Group summary, from the app's own group. Not counted as a notification."
    GroupSummaryOrigin.SYSTEM -> "Group summary, from a group Android imposed. Not counted as a notification."
    GroupSummaryOrigin.UNKNOWN -> "Group summary, origin unknown. Not counted as a notification."
}

/**
 * The one-line summary on a history row.
 *
 * Stored ids come first: a rule matching on the app alone matches a notification that carried no
 * text, so a CONTENT_HIDDEN record can legitimately have them. Reading the state first made this
 * row say "not matched" about a record the Activity screen named two matching rules for.
 */
internal fun describeMatchedRules(record: HistoryRecord, rules: List<SignalRule>): String? = when {
    record.matchedRuleIds.isNotEmpty() -> {
        val names = record.matchedRuleIds.map { id -> rules.firstOrNull { it.id == id }?.name ?: "deleted rule $id" }
        "Would match: " + names.joinToString(", ")
    }
    record.matchState == RuleMatchState.CONTENT_HIDDEN -> "Not matched: no content arrived to test"
    else -> null
}

internal const val NO_DISMISSAL_STATE = "this build runs no actions, so nothing ever records a dismissal"

@Composable
private fun historyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SignalColors.Surface,
    unfocusedContainerColor = SignalColors.Surface,
    focusedBorderColor = SignalColors.Yellow,
    unfocusedBorderColor = SignalColors.ControlOutline,
    cursorColor = SignalColors.Yellow,
)
