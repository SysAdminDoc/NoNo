package com.anm.signalrules.reconstruction.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.HistoryLoadState
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.RuleMatchState
import com.anm.signalrules.reconstruction.model.importanceLabel
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.runtime.EvaluationReason
import com.anm.signalrules.reconstruction.runtime.RuleEvaluationTrace
import com.anm.signalrules.reconstruction.runtime.evaluateHistoryRecord
import kotlinx.coroutines.delay

@Composable
fun HistoryScreen(state: UiState, model: MainViewModel) {
    val searching = state.historySearchActive
    if (searching) {
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(searching) {
            requestKeyboardFocus(focusRequester, keyboard)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.historySearch,
                onValueChange = model::setHistorySearch,
                placeholder = { Text("Search history…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SignalColors.Yellow) },
                trailingIcon = {
                    IconButton(onClick = model::closeHistorySearch) {
                        Text("×", color = SignalColors.Secondary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SignalColors.Surface,
                    unfocusedContainerColor = SignalColors.Surface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).focusRequester(focusRequester),
            )
            HistoryResults(state, model, Modifier.weight(1f))
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::openHistorySearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search history", tint = SignalColors.Secondary)
            }
            IconButton(onClick = { model.showOverlay(Overlay.HISTORY_FILTERS) }) {
                Icon(Icons.Rounded.FilterAlt, contentDescription = "Filter history metadata", tint = SignalColors.Secondary)
            }
        }
        Text(state.history.size.toString(), style = MaterialTheme.typography.displayLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Notifications ", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("in history", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SignalColors.Yellow)
        }
        Row(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12AM", "6AM", "12PM", "6PM", "12AM").forEach { Text(it, fontWeight = FontWeight.Bold) }
        }
        Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            drawLine(
                color = SignalColors.White,
                start = androidx.compose.ui.geometry.Offset(4.dp.toPx(), size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width - 4.dp.toPx(), size.height / 2),
                strokeWidth = 6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 8.dp.toPx())),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HistoryFilterButton(if (state.historyFilter == "Rule-triggered") "Rule-triggered" else "All", state.historyFilter in listOf("All", "Rule-triggered"), Modifier.weight(1f)) {
                model.setHistoryFilter(if (state.historyFilter == "All") "Rule-triggered" else "All")
            }
            HistoryFilterButton(if (state.historyFilter == "Dismissed") "Dismissed" else "Sent at", state.historyFilter != "Dismissed", Modifier.weight(1f)) {
                model.setHistoryFilter(if (state.historyFilter == "Dismissed") "All" else "Dismissed")
            }
        }
        val metadataFilterSummary = listOfNotNull(
            state.historyPackageFilter?.let { "package=$it" },
            state.historyChannelFilter?.let { "channel=$it" },
            state.historyGroupFilter?.let { "group=$it" },
            state.historyContentStateFilter?.let { "content=${it.name}" },
            "summaries".takeIf { state.historyGroupSummaryOnly },
            importanceLabel(state.historyImportanceFilter)?.let { "importance $it" },
            "conversations".takeIf { state.historyConversationFilter == true },
        ).joinToString(" · ")
        if (metadataFilterSummary.isNotBlank()) {
            Text(
                "Metadata filters: $metadataFilterSummary",
                color = SignalColors.Secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        HistoryResults(state, model, Modifier.weight(1f))
    }
}

@Composable
private fun HistoryResults(state: UiState, model: MainViewModel, modifier: Modifier) {
    when (state.historyLoadState) {
        HistoryLoadState.LOADING -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Loading notification history…", color = SignalColors.Secondary, fontWeight = FontWeight.Bold)
        }
        HistoryLoadState.ERROR -> Column(
            modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = SignalColors.Error, modifier = Modifier.size(48.dp))
            Text("History is unavailable", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                state.historyError ?: "The local metadata store could not be read.",
                color = SignalColors.Secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Retry",
                color = SignalColors.Yellow,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp).clickable(role = Role.Button) { model.retryHistory() },
            )
        }
        HistoryLoadState.READY -> if (state.history.isEmpty()) {
            val narrowed = state.historySearch.isNotBlank() || state.historyFilter != "All" ||
                state.historyPackageFilter != null || state.historyChannelFilter != null ||
                state.historyGroupFilter != null || state.historyContentStateFilter != null ||
                state.historyGroupSummaryOnly || state.historyImportanceFilter != null ||
                state.historyConversationFilter != null
            Column(
                modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(72.dp).background(SignalColors.Secondary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(46.dp))
                }
                Text(
                    if (state.historySearch.isNotBlank()) "No matching notifications"
                    else if (narrowed) "No notifications match this filter"
                    else "Notification history",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    if (state.historySearch.isNotBlank()) "Try another search term."
                    else if (state.historyFilter == "Dismissed") "Nothing records a dismissal yet, because this build runs no actions."
                    else if (narrowed) "No stored metadata matches every filter you have set."
                    else "Notifications will appear here as local metadata. Notification content is never persisted.",
                    color = SignalColors.Secondary,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 24.dp),
            ) {
                items(state.history, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).background(SignalColors.Surface, RoundedCornerShape(18.dp))
                            .clickable { model.showHistoryOverlay(item.id) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(46.dp).background(SignalColors.RuleBlue, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Background)
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(item.app, color = SignalColors.Secondary, fontSize = 14.sp)
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(item.body, color = SignalColors.Secondary, fontSize = 14.sp)
                            val metadata = listOfNotNull(
                                item.channelId?.let { "channel=$it" },
                                item.groupKey?.let { "group=$it" },
                                "summary".takeIf { item.isGroupSummary },
                                importanceLabel(item.importance)?.let { "importance=$it" },
                                "conversation".takeIf { item.isConversation == true },
                                item.category?.let { "category=$it" },
                                "ongoing".takeIf { item.isOngoing },
                            ).joinToString(" · ")
                            if (metadata.isNotBlank()) {
                                Text(metadata, color = SignalColors.Secondary, fontSize = 12.sp)
                            }
                            val matched = describeMatchedRules(item, state.rules)
                            if (matched != null) {
                                Text(matched, color = SignalColors.Yellow, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { model.showHistoryOverlay(item.id) }) { Icon(Icons.Rounded.MoreVert, "History item actions") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.background(if (selected) SignalColors.SurfaceSelected else SignalColors.Surface, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.background(Color.Transparent).padding(3.dp) else Modifier.padding(3.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(18.dp))
        Text(label, color = if (selected) SignalColors.White else SignalColors.Secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = if (selected) 6.dp else 0.dp))
    }
}

@Composable
fun HistoryActivityScreen(state: UiState, model: MainViewModel) {
    val selected = state.history.firstOrNull { it.id == state.selectedHistoryId }
    val trace = selected?.let { evaluateHistoryRecord(state.rules, it) }
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Notification activity", onBack = { model.selectRoot(com.anm.signalrules.reconstruction.model.RootTab.HISTORY) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Rules", "Changes").forEach { tab ->
                Row(
                    Modifier.weight(1f).background(if (state.historyActivityTab == tab) SignalColors.Yellow else SignalColors.Surface, RoundedCornerShape(16.dp))
                        .clickable { model.setHistoryActivityTab(tab) }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { Text(tab, color = if (state.historyActivityTab == tab) SignalColors.Background else SignalColors.White, fontWeight = FontWeight.Bold) }
            }
        }
        if (trace == null) {
            ActivityRow("History entry unavailable", "The selected metadata record is no longer in the bounded history query.")
        } else if (state.historyActivityTab == "Rules") {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item {
                    ActivityRow(
                        "Metadata preview",
                        "${selected.app} · ${contentStateLabel(trace)}. No notification content was reconstructed.",
                    )
                }
                if (trace.matchedRuleId == null) {
                    item { ActivityRow("No rule matched", "Every enabled rule has at least one unmet condition.") }
                } else {
                    item { ActivityRow("Rule ${trace.matchedRuleId} would match", "The highest-priority matching rule is selected for preview only.") }
                }
                items(trace.conditions, key = { it.ruleId }) { condition ->
                    val reason = if (condition.matched) "All represented conditions matched." else condition.reasons.joinToString { it.displayName() }
                    ActivityRow("Rule ${condition.ruleId}", reason)
                }
                if (trace.conflictPairs.isNotEmpty()) {
                    item {
                        ActivityRow(
                            "Conflict resolution",
                            trace.conflictPairs.joinToString { "${it.leftRuleId}/${it.rightRuleId} → ${it.winningRuleId}" },
                        )
                    }
                }
                if (trace.priorityOverrides.isNotEmpty()) {
                    item { ActivityRow("Priority overrides", trace.priorityOverrides.joinToString { "Rule ${it.ruleId}: ${it.priority}" }) }
                }
            }
        } else {
            ActivityRow("Notification posted", "Captured locally without storing private payloads.")
            ActivityRow("Action preview only", "${trace.actionResult}: no notification, sound, setting, or PendingIntent was changed.")
        }
    }
}

private fun contentStateLabel(trace: RuleEvaluationTrace): String = when (trace.contentState) {
    com.anm.signalrules.reconstruction.model.NotificationContentState.AVAILABLE -> "content available to matcher"
    com.anm.signalrules.reconstruction.model.NotificationContentState.HIDDEN_BY_SYSTEM -> "content hidden by Android"
    com.anm.signalrules.reconstruction.model.NotificationContentState.NOT_AVAILABLE -> "content unavailable"
    com.anm.signalrules.reconstruction.model.NotificationContentState.NOT_STORED -> "content not stored"
}

private fun EvaluationReason.displayName(): String = when (this) {
    EvaluationReason.DISABLED -> "disabled"
    EvaluationReason.APP_MISMATCH -> "app mismatch"
    EvaluationReason.CONTENT_HIDDEN_BY_SYSTEM -> "content hidden by system"
    EvaluationReason.CONTENT_NOT_AVAILABLE -> "content unavailable"
    EvaluationReason.PHRASE_MISMATCH -> "phrase mismatch"
    EvaluationReason.EXTRA_FILTER_UNSUPPORTED -> "extra filter unsupported"
}

@Composable
private fun ActivityRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).background(SignalColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = SignalColors.Secondary, modifier = Modifier.padding(top = 5.dp))
    }
}

/**
 * Names the saved rules that matched a record when it arrived.
 *
 * Nothing was executed: the rule engine is not part of this build, so the line says what would
 * have matched rather than what happened. A rule deleted since the capture leaves its id behind,
 * which is reported honestly rather than silently dropped.
 */
internal fun describeMatchedRules(record: HistoryRecord, rules: List<SignalRule>): String? = when {
    record.matchState == RuleMatchState.CONTENT_HIDDEN ->
        "Not matched: the system hid this content"
    record.matchedRuleIds.isEmpty() -> null
    else -> {
        val names = record.matchedRuleIds.map { id ->
            rules.firstOrNull { it.id == id }?.name ?: "deleted rule $id"
        }
        "Would match: " + names.joinToString(", ")
    }
}
