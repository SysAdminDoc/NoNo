package com.sysadmindoc.nono.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WorkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState

private data class GuideItem(val title: String, val description: String, val icon: ImageVector)

private val guides = listOf(
    GuideItem("Stay focused", "Reduce distracting notifications", Icons.AutoMirrored.Rounded.Article),
    GuideItem("Protect quiet hours", "Keep work alerts out of personal time", Icons.Rounded.WorkOff),
)

private data class StarterItem(
    val title: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val draft: SignalRule,
)

private val starters = listOf(
    StarterItem(
        "Track noisy group chats",
        "COMMUNICATION",
        "Record when an active message thread posts.",
        Icons.AutoMirrored.Rounded.VolumeOff,
        SignalRule(name = "Group chats", app = "Messages", phrase = "group", action = RECORD_ONLY_ACTION),
    ),
    StarterItem(
        "Follow delivery updates",
        "DELIVERIES",
        "Record repetitive delivery updates as they arrive.",
        Icons.Rounded.Inventory2,
        SignalRule(name = "Delivery updates", app = "any app", phrase = "delivery", action = RECORD_ONLY_ACTION),
    ),
    StarterItem(
        "Watch one app closely",
        "WELLBEING",
        "Record everything from a chosen app, then read it back.",
        Icons.Rounded.Bedtime,
        SignalRule(name = "Everything from one app", app = "any app", phrase = "anything", action = RECORD_ONLY_ACTION),
    ),
)

@Composable
fun ExploreScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SignalPageHeader(
                title = "Explore",
                actionIcon = Icons.Rounded.Search,
                actionDescription = "Search ideas",
                onAction = { model.showMessage("Explore search is not available yet.") },
            )
        }
        item { SignalSectionHeading("Ideas for quieter days", "Learn the patterns, then adapt a rule.") }
        item { Text("GUIDES", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                guides.forEachIndexed { index, guide ->
                    SignalListRow(guide.icon, guide.title, guide.description) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.android.com/develop/ui/views/notifications")))
                        }
                    }
                    if (index != guides.lastIndex) SignalDivider()
                }
            }
        }
        item { SignalSectionHeading("Rule starters", "Preview first. Nothing runs automatically.") }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                starters.forEachIndexed { index, starter ->
                    StarterRow(starter) { model.startRuleFromSuggestion(starter.draft) }
                    if (index != starters.lastIndex) SignalDivider()
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StarterRow(starter: StarterItem, onPreview: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalListIcon(starter.icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(starter.title, style = MaterialTheme.typography.titleMedium)
            Text(starter.category, color = SignalColors.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
            Text(starter.description, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
        Text(
            "Preview",
            color = SignalColors.Yellow,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(role = Role.Button, onClick = onPreview).padding(horizontal = 8.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SignalListIcon(icon: ImageVector) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .heightIn(min = 40.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = SignalColors.White)
    }
}
