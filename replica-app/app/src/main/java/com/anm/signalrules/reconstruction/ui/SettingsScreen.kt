package com.anm.signalrules.reconstruction.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.UiState

@Composable
fun SettingsScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { }
    val listState = rememberLazyListState()
    val target = when (state.auditState.substringBefore('_').toIntOrNull()) { 17 -> 7; 18 -> 13; 19 -> 20; 20 -> 28; else -> 0 }
    LaunchedEffect(state.auditState) { listState.scrollToItem(target) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { SectionLabel("Help") }
        item { PreferenceRow("Contact support", onClick = { openUrl(context, "mailto:support@example.invalid") }) }
        item { PreferenceRow("Guide and FAQs", onClick = { openUrl(context, "https://developer.android.com/develop/ui/views/notifications") }) }
        item { PreferenceRow("Open community", onClick = { openUrl(context, "https://www.reddit.com/r/androidapps/") }) }
        item { PreferenceRow("Rules are not triggering?", onClick = { openUrl(context, "https://developer.android.com/reference/android/service/notification/NotificationListenerService") }) }

        item { SectionLabel("Settings") }
        item { PreferenceRow("Mute mode", "Configure how aggressively the mute should be.", state.settings["Mute mode"], onClick = { model.showOverlay(Overlay.MUTE_MODE) }) }
        item { PersistentSwitchRow(state, model, "Allow dismissing fixed notifications", "Allow rules to dismiss notifications that cannot normally be swiped away.") }

        item { SectionLabel("Mute actions") }
        item { PreferenceRow("Mute importance level", "How important a notification must be to trigger the rule.", state.settings["Mute importance"], onClick = { model.showOverlay(Overlay.MUTE_IMPORTANCE) }) }

        item { SectionLabel("Unsilence actions") }
        item { PersistentSwitchRow(state, model, "Adjust silent ringer mode for calls", "Temporarily change the ringer when an unsilence rule runs.") }

        item { SectionLabel("History") }
        item { PreferenceRow("Notification history", "Choose what content is retained locally.", state.settings["Notification history"], onClick = { model.showOverlay(Overlay.HISTORY_STORAGE) }) }
        item { PreferenceRow("Keep history for", "Older entries are removed automatically.", state.settings["History retention"], onClick = { model.showOverlay(Overlay.HISTORY_RETENTION) }) }

        item { SectionLabel("Shortcuts") }
        item { PreferenceRow("Create shortcut", "Create a launcher shortcut for a safe rule action.", onClick = { model.navigate(Route.SHORTCUT_EDITOR) }) }
        item { PreferenceRow("Clear shortcuts", "Remove shortcuts created by Signal Rules.", onClick = { model.showMessage("No shortcuts to clear") }) }

        item { SectionLabel("Backup") }
        item { PreferenceRow("Import rules", "Choose a local backup file.", onClick = { folderPicker.launch(null) }) }
        item { PreferenceRow("Export rules", "Save a versioned local backup.", onClick = { folderPicker.launch(null) }) }
        item { PreferenceRow("Automatic backups", "Choose a folder for periodic local backups.", state.settings["Automatic backups"], onClick = { folderPicker.launch(null) }) }

        item { SectionLabel("Advanced") }
        item { PersistentSwitchRow(state, model, "Privacy mode", "Hide notification text in history and diagnostics.") }
        item { PreferenceRow("Theme", value = state.settings["Theme"], onClick = { model.showOverlay(Overlay.THEME) }) }
        item { PreferenceRow("Language", value = state.settings["Language"], onClick = { model.showOverlay(Overlay.LANGUAGE) }) }
        item { PreferenceRow("Translate", "Help improve independently written translations.", onClick = { openUrl(context, "https://developer.android.com/guide/topics/resources/localization") }) }
        item { PersistentSwitchRow(state, model, "Hide popups when muting", "Avoid heads-up popups for notifications matched by a mute rule.") }
        item { PreferenceRow("Open notification settings", "Review Android notification access and channels.", onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) }
        item { PreferenceRow("Restore batch", "Restore a locally simulated notification batch.", onClick = { model.showMessage("No saved batch to restore") }) }
        item { PersistentSwitchRow(state, model, "Restore batches after reboot", "Rebuild scheduled local batches after device restart.") }
        item { PersistentSwitchRow(state, model, "Android 15+ icon workaround", "Use a compatibility path for notification icons.") }
        item { PersistentSwitchRow(state, model, "Notification grouping workaround", "Use a compatibility path for grouped notifications.") }
        item { PreferenceRow("Delete all rules", "Destructive operation is disabled in audit validation.", destructive = true, onClick = { model.showMessage("Delete all rules is disabled in this reconstruction") }) }
        item { Text("Signal Rules reconstruction 1.0", color = SignalColors.Secondary, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun PreferenceRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) SignalColors.Error else SignalColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (summary != null) Text(summary, color = SignalColors.Secondary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            if (value != null) Text(value, color = SignalColors.Yellow, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(if (destructive) Icons.Rounded.DeleteForever else Icons.Rounded.ChevronRight, contentDescription = null, tint = if (destructive) SignalColors.Error else SignalColors.Secondary)
    }
}

@Composable
private fun PersistentSwitchRow(state: UiState, model: MainViewModel, title: String, summary: String) {
    val checked = state.settings[title] == "On"
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { model.setSetting(title, if (checked) "Off" else "On") }.padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(summary, color = SignalColors.Secondary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = { model.setSetting(title, if (it) "On" else "Off") },
            colors = SwitchDefaults.colors(checkedTrackColor = SignalColors.Yellow, checkedThumbColor = SignalColors.Background, uncheckedTrackColor = SignalColors.Border),
        )
    }
}

@Composable
fun ShortcutEditorScreen(state: UiState, model: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Create shortcut", onBack = { model.selectRoot(com.anm.signalrules.reconstruction.model.RootTab.SETTINGS) }, actionIcon = Icons.Rounded.Check, actionDescription = "Save shortcut", onAction = { model.selectRoot(com.anm.signalrules.reconstruction.model.RootTab.SETTINGS) })
        Text("Shortcut name", color = SignalColors.Secondary, modifier = Modifier.padding(start = 24.dp, top = 24.dp))
        SurfaceCard(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("Choose a rule and action to make available from the launcher.", color = SignalColors.Secondary)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Yellow)
            Text("Add shortcut action", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
