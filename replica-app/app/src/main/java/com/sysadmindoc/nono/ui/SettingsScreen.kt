package com.sysadmindoc.nono.ui

import android.Manifest
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
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
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
import com.sysadmindoc.nono.CaptureSelfTestAction
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.R
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.CaptureSelfTestStatus
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.DISMISS_FIXED_SETTING
import com.sysadmindoc.nono.model.counted
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.runtime.APP_LOCK_SETTING
import com.sysadmindoc.nono.runtime.NO_DEVICE_CREDENTIAL
import com.sysadmindoc.nono.runtime.BackupOutcome
import com.sysadmindoc.nono.runtime.BackupStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        model::onCaptureSelfTestPermissionResult,
    )
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) model.showMessage("Backup folder unchanged.") else model.setBackupFolder(uri)
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
                // All three say the same thing, because all three are the same thing. Two of them
                // used to accept and store a choice with nothing behind it, beside a sibling that
                // was honest about it.
                PreferenceRow(
                    Icons.AutoMirrored.Rounded.VolumeOff,
                    "Mute mode",
                    value = state.settings["Mute mode"],
                    unavailable = NO_ACTION_ENGINE,
                    onClick = { model.showOverlay(Overlay.MUTE_MODE) },
                )
                SignalDivider()
                PersistentSwitchRow(
                    state,
                    model,
                    Icons.Rounded.Notifications,
                    "Dismiss fixed notifications",
                    "Off in preview-only builds.",
                    unavailable = NO_ACTION_ENGINE,
                    settingKey = DISMISS_FIXED_SETTING,
                )
                SignalDivider()
                PreferenceRow(
                    Icons.Rounded.Tune,
                    "Mute importance",
                    value = state.settings["Mute importance"],
                    unavailable = NO_ACTION_ENGINE,
                    onClick = { model.showOverlay(Overlay.MUTE_IMPORTANCE) },
                )
            }
        }

        item { SettingsSectionLabel("HISTORY & DATA") }
        item {
            SettingsGroup {
                PreferenceRow(Icons.Rounded.History, "Notification history", value = state.settings["Notification history"], onClick = { model.showOverlay(Overlay.HISTORY_STORAGE) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Schedule, "Keep history for", value = state.settings["History retention"], onClick = { model.showOverlay(Overlay.HISTORY_RETENTION) })
                SignalDivider()
                PreferenceRow(Icons.Rounded.Download, "Export metadata", "Every retained record, as CSV.", value = "CSV", onClick = model::beginHistoryExport)
                SignalDivider()
                PreferenceRow(
                    Icons.Rounded.Backup,
                    "Automatic backups",
                    describeBackupStatus(state.backupStatus),
                    value = state.settings[SignalPreferences.AUTOMATIC_BACKUP_SETTING],
                    onClick = { model.showOverlay(Overlay.BACKUP_CADENCE) },
                )
                SignalDivider()
                PreferenceRow(
                    Icons.Rounded.FolderOpen,
                    "Backup folder",
                    BACKUP_FOLDER_EXPLANATION,
                    value = state.backupFolderLabel ?: "Not set",
                    onClick = { backupFolderLauncher.launch(null) },
                )
                if (state.backupFolderLabel != null) {
                    SignalDivider()
                    PreferenceRow(
                        Icons.Rounded.FolderOff,
                        "Clear backup folder",
                        "Stops backups and hands the folder access back to Android.",
                        destructive = true,
                        onClick = model::clearBackupFolder,
                    )
                }
                SignalDivider()
                // A switch, because that is what it does. As a preference row it looked like
                // something that opens a screen and TalkBack heard a button with a static value,
                // so pausing all capture was one mistaken tap in a list of rows that open dialogs.
                SwitchRow(
                    icon = if (state.capturePaused) Icons.Rounded.PauseCircle else Icons.Rounded.Notifications,
                    title = "Notification capture",
                    summary = if (state.capturePaused) {
                        "Paused. Nothing is being recorded."
                    } else {
                        "Active. Metadata is recorded as notifications arrive."
                    },
                    checked = !state.capturePaused,
                    onCheckedChange = { model.setCapturePaused(!it) },
                )
                SignalDivider()
                PersistentSwitchRow(
                    state,
                    model,
                    Icons.Rounded.Lock,
                    APP_LOCK_SETTING,
                    describeAppLock(state),
                )
                SignalDivider()
                PreferenceRow(
                    Icons.Rounded.Widgets,
                    "Widget count",
                    "What the home-screen widget's number counts.",
                    value = state.settings[SignalPreferences.WIDGET_COUNT_SETTING],
                    onClick = { model.showOverlay(Overlay.WIDGET_SCOPE) },
                )
                SignalDivider()
                PreferenceRow(Icons.Rounded.VisibilityOff, "Why do some records have no content?", "Some notifications arrive with no text at all, and Android redacts sensitive ones.", onClick = { model.showOverlay(Overlay.CONTENT_HIDDEN) })
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

        item { SettingsSectionLabel("CAPTURE HEALTH") }
        item {
            SettingsGroup {
                PreferenceRow(
                    Icons.Rounded.Notifications,
                    "Run capture self-test",
                    state.captureSelfTest.detail,
                    value = state.captureSelfTest.status.displayName(),
                    onClick = {
                        if (model.beginCaptureSelfTest() == CaptureSelfTestAction.REQUEST_NOTIFICATION_PERMISSION) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                SignalDivider()
                PreferenceRow(
                    Icons.Rounded.Share,
                    "Share diagnostics",
                    "Share app version, listener state, counters, and last capture age. No notification details.",
                    onClick = {
                        if (!shareCaptureDiagnostics(context, model.captureDiagnosticsReport())) {
                            model.showMessage("No app is available to share diagnostics.")
                        }
                    },
                )
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
/**
 * States the limit of the scheduled backup up front.
 *
 * A job running on a timer has nobody to ask for a passphrase, so it encrypts with a key held by
 * this device. That is what makes the file unreadable anywhere else, and the user needs to know it
 * before they rely on the backup for a phone they no longer have.
 */
private const val BACKUP_FOLDER_EXPLANATION =
    "Backups are encrypted with a key held by this device and restore only here. " +
        "Use the encrypted export to move rules to another phone."

/**
 * What the lock row says underneath its title.
 *
 * A device with no screen lock gets told why the setting will not take, rather than a toggle that
 * silently refuses. The tile and the widget are named because they keep working while locked, and
 * a user who relies on either needs to know that before turning this on.
 */
internal fun describeAppLock(state: UiState): String = when {
    !state.deviceCredentialAvailable -> NO_DEVICE_CREDENTIAL
    state.settings[APP_LOCK_SETTING] == "On" ->
        "Rules and history need your screen lock after a minute away. The Quick Settings tile and " +
            "the widget keep working; neither shows any content."
    else -> "Ask for your screen lock before showing any rule or record."
}

/** What Settings says the schedule last did. */
internal fun describeBackupStatus(status: BackupStatus): String = when (status.outcome) {
    BackupOutcome.NEVER_RUN -> "No backup has run yet."
    BackupOutcome.SUCCEEDED -> "Last backup ${formatBackupTime(status.atEpochMillis)}, " +
        "${counted(status.ruleCount, "rule")}."
    BackupOutcome.FAILED -> "Last attempt failed ${formatBackupTime(status.atEpochMillis)}. ${status.detail}"
}

/**
 * The moment a backup ran, in the reader's own conventions.
 *
 * A hardcoded "HH:mm" prints 24-hour time to somebody whose phone is set to 12-hour, which is the
 * one line in Settings that has to be read at a glance and believed.
 */
private fun formatBackupTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMillis))
private const val NO_ACTION_ENGINE = "No notification action engine is present."

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun CaptureSelfTestStatus.displayName(): String = when (this) {
    CaptureSelfTestStatus.NOT_RUN -> "Not run"
    CaptureSelfTestStatus.WAITING_FOR_PERMISSION -> "Permission"
    CaptureSelfTestStatus.RUNNING -> "Running"
    CaptureSelfTestStatus.PASSED -> "Passed"
    CaptureSelfTestStatus.FAILED -> "Failed"
}

private fun shareCaptureDiagnostics(context: android.content.Context, report: String): Boolean =
    runCatching {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "NoNo capture diagnostics")
            .putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(intent, "Share NoNo diagnostics"))
    }.isSuccess

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
        // Any row that cannot be operated reads as one. It used to depend on *why* it could not
        // be, so a row carrying both an explanation and an onClick looked live.
        !enabled -> SignalColors.Muted
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
    settingKey: String = title,
) {
    // The stored key is given separately from the displayed title. Keying by the title meant the
    // "Dismiss fixed notifications" row read and wrote a preference nothing seeded, while the
    // seeded "Allow dismissing fixed notifications" sat there unread.
    SwitchRow(
        icon = icon,
        title = title,
        summary = summary,
        checked = state.settings[settingKey] == "On",
        unavailable = unavailable,
        onCheckedChange = { model.setSetting(settingKey, if (it) "On" else "Off") },
    )
}

/**
 * A row whose whole width is one switch.
 *
 * Separate from [PreferenceRow] because the two answer different questions: a preference row opens
 * something, a switch row is the control. Rendering a toggle as a preference row is how the
 * capture switch came to look like navigation and sound to TalkBack like a button with a static
 * value.
 */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    unavailable: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val enabled = unavailable == null
    val on = enabled && checked
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(
                value = on,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                stateDescription = if (on) "On" else "Off"
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
            checked = on,
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
    // Whichever rule the user picked, falling back to the first so the screen is never blank
    // when rules exist.
    val rule = state.rules.firstOrNull { it.id == state.selectedRuleId } ?: state.rules.firstOrNull()
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
                if (state.rules.isEmpty()) {
                    SignalListRow(Icons.Rounded.Tune, "No saved rules", "Create a rule before making a shortcut.")
                } else {
                    state.rules.forEachIndexed { index, candidate ->
                        SignalListRow(
                            Icons.Rounded.Tune,
                            candidate.name,
                            "${candidate.app} · ${candidate.matchType} ${candidate.phrase}",
                            selected = candidate.id == rule?.id,
                            onClick = { model.selectShortcutRule(candidate.id) },
                        )
                        if (index != state.rules.lastIndex) SignalDivider()
                    }
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
                    focusedBorderColor = SignalColors.ControlOutline,
                    unfocusedBorderColor = SignalColors.ControlOutline,
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
        item {
            SignalPrimaryButton(
                "Create shortcut",
                onClick = { rule?.let { model.requestRuleShortcut(it.id) } },
                enabled = rule != null,
            )
        }
        item {
            Text(
                if (rule == null) {
                    "Save a rule first. A shortcut has to point at one."
                } else {
                    "Your launcher decides whether to add it, and may ask you to confirm."
                },
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
