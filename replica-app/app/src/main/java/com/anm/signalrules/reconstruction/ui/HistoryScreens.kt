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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.filterHistory
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
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = model::openHistorySearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Search history", tint = SignalColors.Secondary)
            }
        }
        Text(state.history.size.toString(), style = MaterialTheme.typography.displayLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Notifications ", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("today", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SignalColors.Yellow)
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
        if (state.history.isEmpty()) {
            Spacer(Modifier.weight(0.9f))
            Box(Modifier.size(72.dp).align(Alignment.CenterHorizontally).background(SignalColors.Secondary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(46.dp))
            }
            Text(
                if (state.historySearch.isNotBlank()) "No matching notifications" else "Notification history",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
            )
            Text(
                if (state.historySearch.isNotBlank()) "Try another search term." else "Notifications will appear here along with any rules that were triggered. You can configure this in settings.",
                color = SignalColors.Secondary, fontWeight = FontWeight.Bold, lineHeight = 22.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 32.dp, vertical = 12.dp),
            )
            Spacer(Modifier.weight(1.1f))
        } else {
            filterHistory(state.history, state.historySearch, state.historyFilter).forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.dp).background(SignalColors.Surface, RoundedCornerShape(18.dp)).clickable { model.showHistoryOverlay(item.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).background(SignalColors.RuleBlue, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Background)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(item.app, color = SignalColors.Secondary, fontSize = 14.sp)
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(item.body, color = SignalColors.Secondary, fontSize = 14.sp)
                    }
                    IconButton(onClick = { model.showHistoryOverlay(item.id) }) { Icon(Icons.Rounded.MoreVert, "History item actions") }
                }
            }
            Spacer(Modifier.weight(1f))
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
        if (state.historyActivityTab == "Rules") {
            ActivityRow("No rule was triggered", "The test notification did not match an enabled rule.")
        } else {
            ActivityRow("Notification posted", "Captured locally without storing private payloads.")
            ActivityRow("No changes made", "This reconstruction simulates potentially destructive actions.")
        }
    }
}

@Composable
private fun ActivityRow(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).background(SignalColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = SignalColors.Secondary, modifier = Modifier.padding(top = 5.dp))
    }
}
