package com.sysadmindoc.nono.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nono.BuildConfig
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.R
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.UiState

@Composable
fun SettingsScreen(state: UiState, model: MainViewModel) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) model.exportCancelled() else model.writeExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.showMessage("Import cancelled.") else model.beginImport(uri)
    }
    val historyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) model.exportCancelled() else model.writeExport(uri)
    }
    LaunchedEffect(state.transferExportRequest) {
        if (state.transferExportRequest == 0) return@LaunchedEffect
        if (state.transferExportIsHistory) {
            historyExportLauncher.launch("nono-history.csv")
        } else {
            exportLauncher.launch("nono-rules.json")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SignalPageHeader("Settings") }
        item { SignalStatusPanel("Local by design", "No internet permission · Metadata only", icon = Icons.Rounded.Shield) }

        item { SettingsSectionLabel("BEHAVIOR") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.AutoMirrored.Rounded.VolumeOff, "Mute mode", value = state.settings["Mute mode"], onClick = { model.showOverlay(Overlay.MUTE_MODE) })
                SignalDivider()
                PersistentSwitchRow(
                    state,
                    model,
                    Icons.Rounded.Notifications,
                    "Dismiss fixed notifications",
                    "Off in preview-only builds.",
                    unavailable = NO_ACTION_ENGINE,
                )
                SignalDivider()
                PreferenceRow(Icons.Rounded.Tune, "Mute importance", value = state.settings["Mute importance"], onClick = { model.showOverlay(Overlay.MUTE_IMPORTANCE) })
            }
        }

        item { SettingsSectionLabel("HISTORY & DATA") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.Rounded.History, "Notification history", value = state.settings["Notification history"], onClick = { model.showOverlay(Overlay.HISTORY_STORAGE) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Schedule, "Keep history for", value = state.settings["History retention"], onClick = { model.showOverlay(Overlay.HISTORY_RETENTION) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Download, "Export metadata", value = "CSV", onClick = model::beginHistoryExport)
                SignalDivider()
                PreferenceRow(Icons.Rounded.Backup, "Automatic backups", value = "Off", unavailable = NO_AUTOMATIC_BACKUPS)
                SignalDivider()
                PreferenceRow(
                    if (state.capturePaused) Icons.Rounded.PauseCircle else Icons.Rounded.Notifications,
                    "Notification capture",
                    value = if (state.capturePaused) "Paused" else "Active",
                    onClick = { model.setCapturePaused(!state.capturePaused) },
                )
                SignalDivider()
                PreferenceRow(Icons.Rounded.VisibilityOff, "Why is content hidden?", "Android may redact sensitive notifications before NoNo reads metadata.", onClick = { model.showOverlay(Overlay.CONTENT_HIDDEN) })
            }
        }

        item { SettingsSectionLabel("APPEARANCE") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.Rounded.DarkMode, "Theme", value = state.settings["Theme"], onClick = { model.showOverlay(Overlay.THEME) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Language, "Language", value = "System default", unavailable = "This build follows the system locale.")
            }
        }

        item { SettingsSectionLabel("RULES & TRANSFER") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.AutoMirrored.Rounded.Shortcut, "Create shortcut", "Prepare a launcher shortcut for a saved rule.", onClick = { model.navigate(Route.SHORTCUT_EDITOR) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.ImportExport, "Import rules", "Read encrypted NoNo rules JSON.", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Download, "Export rules", "Create an encrypted rules file.", onClick = model::beginExport)
                SignalDivider()
                PreferenceRow(Icons.Rounded.DeleteForever, "Delete all rules", "Removes every saved rule from this device.", destructive = true, onClick = model::deleteAllRules)
            }
        }

        item { SettingsSectionLabel("HELP") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.AutoMirrored.Rounded.HelpOutline, "Rules are not triggering?", "Check listener access and device restrictions.", onClick = { model.showOverlay(Overlay.LISTENER_CHECKLIST) })
                SignalDivider()
                PreferenceRow(Icons.AutoMirrored.Rounded.Article, "Guide and FAQs", onClick = { openUrl(context, "https://developer.android.com/develop/ui/views/notifications") })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Forum, "Contact support", unavailable = NO_SUPPORT_CHANNEL)
                SignalDivider()
                PreferenceRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open notification settings", onClick = { openListenerSettings(context) })
            }
        }

        item {
            Text(
                "NoNo ${BuildConfig.VERSION_NAME}",
                color = SignalColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

private const val NO_SUPPORT_CHANNEL = "This reconstruction has no support channel."
private const val NO_AUTOMATIC_BACKUPS = "Use encrypted export instead."
private const val NO_ACTION_ENGINE = "No notification action engine is present."

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(label, color = SignalColors.Yellow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    SignalGroupedSurface(Modifier.fillMaxWidth(), content)
}

@Composable
private fun PreferenceRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    unavailable: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val enabled = unavailable == null && onClick != null
    val titleColor = when {
        destructive -> SignalColors.Error
        !enabled && onClick == null -> SignalColors.Muted
        else -> SignalColors.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (enabled) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .semantics {
                if (unavailable != null) {
                    disabled()
                    contentDescription = "$title. Unavailable: $unavailable"
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = when { destructive -> SignalColors.Error; enabled -> SignalColors.White; else -> SignalColors.Muted })
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.titleMedium)
            val supporting = unavailable ?: summary
            if (supporting != null) Text(supporting, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, color = if (enabled) SignalColors.Secondary else SignalColors.Muted, style = MaterialTheme.typography.bodyMedium)
        if (enabled) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = SignalColors.Secondary, modifier = Modifier.size(20.dp).padding(start = 4.dp))
    }
}

@Composable
private fun PersistentSwitchRow(
    state: UiState,
    model: MainViewModel,
    icon: ImageVector,
    title: String,
    summary: String,
    unavailable: String? = null,
) {
    val enabled = unavailable == null
    val checked = enabled && state.settings[title] == "On"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { model.setSetting(title, if (it) "On" else "Off") },
            )
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                contentDescription = title
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (enabled) SignalColors.White else SignalColors.Muted)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = if (enabled) SignalColors.White else SignalColors.Muted, style = MaterialTheme.typography.titleMedium)
            Text(unavailable ?: summary, color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedTrackColor = SignalColors.Yellow,
                checkedThumbColor = SignalColors.Background,
                uncheckedTrackColor = SignalColors.Border,
            ),
        )
    }
}

@Composable
fun ShortcutEditorScreen(state: UiState, model: MainViewModel) {
    val rule = state.rules.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SignalTopBar("Create shortcut", onBack = { model.selectRoot(RootTab.SETTINGS) }) }
        item { SignalSectionHeading("Choose a saved rule", "The shortcut opens NoNo with that rule ready to review.") }
        item { SignalStatusPanel("Preview workflow", "The shortcut never runs a notification action.", icon = Icons.Rounded.Shield) }
        item { SettingsSectionLabel("SAVED RULES") }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                if (rule == null) {
                    SignalListRow(Icons.Rounded.Tune, "No saved rules", "Create a rule before making a shortcut.")
                } else {
                    SignalListRow(
                        Icons.Rounded.Tune,
                        rule.name,
                        "${rule.app} · ${rule.matchType} ${rule.phrase} · ${rule.action}",
                        selected = true,
                    )
                }
            }
        }
        item { SettingsSectionLabel("SHORTCUT DETAILS") }
        item {
            OutlinedTextField(
                value = rule?.name.orEmpty(),
                onValueChange = { },
                readOnly = true,
                label = { Text("Name") },
                shape = RoundedCornerShape(SignalMetrics.controlRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SignalColors.Surface,
                    unfocusedContainerColor = SignalColors.Surface,
                    focusedBorderColor = SignalColors.Border,
                    unfocusedBorderColor = SignalColors.Border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (rule != null) {
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = "NoNo app icon",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text("Home screen preview", color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item { SignalPrimaryButton("Create shortcut", onClick = { }, enabled = false) }
        item {
            Text(
                "Shortcut publication is not available in this build.",
                color = SignalColors.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            SignalOutlineButton("Cancel", { model.selectRoot(RootTab.SETTINGS) }, Modifier.fillMaxWidth())
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
