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
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.importanceLabel
import com.sysadmindoc.nono.runtime.EvaluationReason
import com.sysadmindoc.nono.runtime.RuleEvaluationTrace
import com.sysadmindoc.nono.runtime.evaluateHistoryRecord

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
        item { HistoryOverview(state.history.size) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryFilterButton("All", state.historyFilter == "All", Modifier.weight(1f)) { model.setHistoryFilter("All") }
                HistoryFilterButton("Matched rules", state.historyFilter == "Rule-triggered", Modifier.weight(1f)) { model.setHistoryFilter("Rule-triggered") }
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

@Composable
private fun HistoryOverview(count: Int) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.displayLarge)
            Text(if (count == 1) "notification today" else "notifications today", color = SignalColors.Secondary, style = MaterialTheme.typography.bodyLarge)
            HistoryActivityChart(count, Modifier.fillMaxWidth().height(106.dp).padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12AM", "6AM", "12PM", "6PM", "12AM").forEach {
                    Text(it, color = SignalColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HistoryActivityChart(count: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val baseline = size.height - 10.dp.toPx()
        drawLine(SignalColors.Border, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        if (count > 0) {
            val bars = listOf(0.22f, 0.38f, 0.18f, 0.62f, 0.34f, 0.78f, 0.46f)
            val gap = size.width / (bars.size + 2)
            bars.forEachIndexed { index, fraction ->
                val x = gap * (index + 1.5f)
                drawLine(
                    color = SignalColors.Yellow,
                    start = Offset(x, baseline),
                    end = Offset(x, baseline - (size.height - 20.dp.toPx()) * fraction),
                    strokeWidth = 5.dp.toPx(),
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
            .border(1.dp, if (selected) SignalColors.Yellow else SignalColors.Border, RoundedCornerShape(SignalMetrics.controlRadius))
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
        state.historyFilter == "Dismissed" -> "This build runs no actions, so it never records a dismissal."
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
    val trace = selected?.let { evaluateHistoryRecord(state.rules, it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SignalTopBar("Notification activity", onBack = { model.selectRoot(RootTab.HISTORY) }) }
        if (selected == null || trace == null) {
            item { HistoryStatePanel("History entry unavailable", "The selected record is no longer in the bounded history query.", Icons.Rounded.History) }
            return@LazyColumn
        }
        item { ActivityMetadataHeader(selected) }
        item {
            Row(Modifier.fillMaxWidth().border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.controlRadius))) {
                ActivityTab("Rules", state.historyActivityTab == "Rules", Modifier.weight(1f)) { model.setHistoryActivityTab("Rules") }
                ActivityTab("Changes", state.historyActivityTab == "Changes", Modifier.weight(1f)) { model.setHistoryActivityTab("Changes") }
            }
        }
        item {
            SignalStatusPanel(
                title = trace.matchedRuleId?.let { id -> "Would match ${state.rules.firstOrNull { it.id == id }?.name ?: "rule $id"}" } ?: "No rule would match",
                description = "Preview only. No action was executed.",
                icon = Icons.Rounded.Shield,
            )
        }
        if (state.historyActivityTab == "Rules") {
            item { Text("EVALUATION", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item { EvaluationTrace(trace) }
            item { Text("CAPTURED METADATA", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item { CapturedMetadata(selected) }
        } else {
            item { Text("CHANGES", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    ActivityRow("Notification posted", "Captured locally without storing private payloads.")
                    SignalDivider()
                    ActivityRow("Action preview only", "${trace.actionResult}: no notification, sound, or setting was changed.")
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
private fun EvaluationTrace(trace: RuleEvaluationTrace) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        val matched = trace.matchedRuleId != null
        ActivityStep(1, "App", if (matched) "App condition matched" else "No enabled rule matched the app")
        SignalDivider()
        ActivityStep(2, "Content", contentStateLabel(trace))
        SignalDivider()
        ActivityStep(3, "Action", trace.actionResult.toString())
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
        MetadataRow("Importance", importanceLabel(record.importance) ?: "Not available")
        SignalDivider()
        MetadataRow("Content storage", "Not stored")
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActivityRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun contentStateLabel(trace: RuleEvaluationTrace): String = when (trace.contentState) {
    NotificationContentState.AVAILABLE -> "Content available to the matcher"
    NotificationContentState.HIDDEN_BY_SYSTEM -> "Content hidden by Android"
    NotificationContentState.NOT_AVAILABLE -> "Content unavailable"
    NotificationContentState.NOT_STORED -> "Content not stored"
}

private fun EvaluationReason.displayName(): String = when (this) {
    EvaluationReason.DISABLED -> "disabled"
    EvaluationReason.APP_MISMATCH -> "app mismatch"
    EvaluationReason.CONTENT_HIDDEN_BY_SYSTEM -> "content hidden by system"
    EvaluationReason.CONTENT_NOT_AVAILABLE -> "content unavailable"
    EvaluationReason.PHRASE_MISMATCH -> "phrase mismatch"
    EvaluationReason.EXTRA_FILTER_UNSUPPORTED -> "extra filter unsupported"
}

internal fun describeMatchedRules(record: HistoryRecord, rules: List<SignalRule>): String? = when {
    record.matchState == RuleMatchState.CONTENT_HIDDEN -> "Not matched: the system hid this content"
    record.matchedRuleIds.isEmpty() -> null
    else -> {
        val names = record.matchedRuleIds.map { id -> rules.firstOrNull { it.id == id }?.name ?: "deleted rule $id" }
        "Would match: " + names.joinToString(", ")
    }
}

internal const val NO_DISMISSAL_STATE = "this build runs no actions, so nothing ever records a dismissal"

@Composable
private fun historyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SignalColors.Surface,
    unfocusedContainerColor = SignalColors.Surface,
    focusedBorderColor = SignalColors.Yellow,
    unfocusedBorderColor = SignalColors.Border,
    cursorColor = SignalColors.Yellow,
)
