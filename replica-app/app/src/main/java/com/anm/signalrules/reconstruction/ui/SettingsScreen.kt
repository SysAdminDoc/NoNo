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
import com.anm.signalrules.reconstruction.BuildConfig
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.UiState

@Composable
fun SettingsScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) model.exportCancelled() else model.writeExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.showMessage("Import cancelled.") else model.beginImport(uri)
    }
    LaunchedEffect(state.transferExportRequest) {
        if (state.transferExportRequest > 0) exportLauncher.launch("signal-rules.json")
    }
    val listState = rememberLazyListState()
    val target = when (state.auditState.substringBefore('_').toIntOrNull()) { 17 -> 7; 18 -> 13; 19 -> 20; 20 -> 28; else -> 0 }
    LaunchedEffect(state.auditState) { listState.scrollToItem(target) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { SectionLabel("Help") }
        item { PreferenceRow("Contact support", unavailable = NO_SUPPORT_CHANNEL) }
        item { PreferenceRow("Guide and FAQs", onClick = { openUrl(context, "https://developer.android.com/develop/ui/views/notifications") }) }
        item { PreferenceRow("Open community", unavailable = NO_SUPPORT_CHANNEL) }
        item { PreferenceRow("Rules are not triggering?", onClick = { openUrl(context, "https://developer.android.com/reference/android/service/notification/NotificationListenerService") }) }

        item { SectionLabel("Settings") }
        item { PreferenceRow("Mute mode", "Configure how aggressively the mute should be.", state.settings["Mute mode"], onClick = { model.showOverlay(Overlay.MUTE_MODE) }) }
        item { PersistentSwitchRow(state, model, "Allow dismissing fixed notifications", "Allow rules to dismiss notifications that cannot normally be swiped away.", unavailable = NO_ACTION_ENGINE) }

        item { SectionLabel("Mute actions") }
        item { PreferenceRow("Mute importance level", "How important a notification must be to trigger the rule.", state.settings["Mute importance"], onClick = { model.showOverlay(Overlay.MUTE_IMPORTANCE) }) }

        item { SectionLabel("Unsilence actions") }
        item { PersistentSwitchRow(state, model, "Adjust silent ringer mode for calls", "Temporarily change the ringer when an unsilence rule runs.", unavailable = NO_ACTION_ENGINE) }

        item { SectionLabel("History") }
        item { PreferenceRow("Notification history", "Choose what content is retained locally.", state.settings["Notification history"], onClick = { model.showOverlay(Overlay.HISTORY_STORAGE) }) }
        item { PreferenceRow("Keep history for", "Older entries are removed automatically.", state.settings["History retention"], onClick = { model.showOverlay(Overlay.HISTORY_RETENTION) }) }

        item { SectionLabel("Shortcuts") }
        item { PreferenceRow("Create shortcut", "Create a launcher shortcut for a safe rule action.", onClick = { model.navigate(Route.SHORTCUT_EDITOR) }) }
        item { PreferenceRow("Clear shortcuts", unavailable = NOT_RECONSTRUCTED) }

        item { SectionLabel("Backup") }
        item { PreferenceRow("Import rules", "Encrypted Signal Rules JSON; notification history is never imported.", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) }
        item { PreferenceRow("Export rules", "Create an encrypted file through Android storage access.", onClick = model::beginExport) }
        item { PreferenceRow("Automatic backups", unavailable = NO_AUTOMATIC_BACKUPS) }

        item { SectionLabel("Advanced") }
        item { PersistentSwitchRow(state, model, "Privacy mode", "Hide notification text in history and diagnostics.", unavailable = NO_ACTION_ENGINE) }
        item { PreferenceRow("Theme", unavailable = "Only the dark theme is implemented; light and follow-system are not.") }
        item { PreferenceRow("Language", unavailable = "This build ships no translated resources, so it follows the system locale only.") }
        item { PreferenceRow("Translate", unavailable = NOT_RECONSTRUCTED) }
        item { PersistentSwitchRow(state, model, "Hide popups when muting", "Avoid heads-up popups for notifications matched by a mute rule.", unavailable = NO_ACTION_ENGINE) }
        item { PreferenceRow("Open notification settings", "Review Android notification access and channels.", onClick = { openListenerSettings(context) }) }
        item { PreferenceRow("Restore batch", unavailable = NO_ACTION_ENGINE) }
        item { PersistentSwitchRow(state, model, "Restore batches after reboot", "Rebuild scheduled local batches after device restart.", unavailable = NO_ACTION_ENGINE) }
        item { PersistentSwitchRow(state, model, "Android 15+ icon workaround", "Use a compatibility path for notification icons.", unavailable = NO_ACTION_ENGINE) }
        item { PersistentSwitchRow(state, model, "Notification grouping workaround", "Use a compatibility path for grouped notifications.", unavailable = NO_ACTION_ENGINE) }
        item { PreferenceRow("Delete all rules", "Removes every rule on this device. This cannot be undone.", destructive = true, onClick = model::deleteAllRules) }
        item { Text("Signal Rules reconstruction ${BuildConfig.VERSION_NAME}", color = SignalColors.Secondary, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
    }
}

/** Reasons a control exists for fidelity but cannot do anything in this build. */
private const val NOT_RECONSTRUCTED = "the original behaviour was not observable during the audit."
private const val NO_SUPPORT_CHANNEL = "this reconstruction has no support channel or community."
private const val NO_AUTOMATIC_BACKUPS = "automatic backup scheduling is not implemented; use encrypted export instead."
private const val NO_ACTION_ENGINE = "this build has no notification action engine, so the setting would have no effect."

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun PreferenceRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    unavailable: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val enabled = unavailable == null && onClick != null
    val titleColor = when {
        !enabled -> SignalColors.Muted
        destructive -> SignalColors.Error
        else -> SignalColors.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(role = Role.Button) { onClick?.invoke() } else Modifier)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (summary != null) Text(summary, color = SignalColors.Secondary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            if (value != null && enabled) Text(value, color = SignalColors.Yellow, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
            if (unavailable != null) UnavailableNote(unavailable)
        }
        if (enabled) {
            Icon(if (destructive) Icons.Rounded.DeleteForever else Icons.Rounded.ChevronRight, contentDescription = null, tint = if (destructive) SignalColors.Error else SignalColors.Secondary)
        }
    }
}

/** Inline marker so the UI never advertises behaviour this build does not have. */
@Composable
private fun UnavailableNote(reason: String) {
    Text(
        "Unavailable - " + reason,
        color = SignalColors.Muted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(top = 3.dp),
    )
}

@Composable
private fun PersistentSwitchRow(
    state: UiState,
    model: MainViewModel,
    title: String,
    summary: String,
    unavailable: String? = null,
) {
    val checked = state.settings[title] == "On"
    val enabled = unavailable == null
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(role = Role.Switch) { model.setSetting(title, if (checked) "Off" else "On") } else Modifier)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) SignalColors.White else SignalColors.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(summary, color = SignalColors.Secondary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
            if (unavailable != null) UnavailableNote(unavailable)
        }
        Switch(
            checked = checked,
            enabled = enabled,
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
            Text(
                "Unavailable - launcher shortcut publication is not reconstructed, so nothing is created when you save.",
                color = SignalColors.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Yellow)
            Text("Add shortcut action", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
