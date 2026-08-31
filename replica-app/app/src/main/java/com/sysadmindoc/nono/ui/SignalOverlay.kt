package com.sysadmindoc.nono.ui

import android.content.ClipData
import android.os.Build
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.data.ConflictResolution
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.NO_FILTER_ENGINE
import com.sysadmindoc.nono.model.NO_RULE_EXPIRY_MESSAGE
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.importanceCatalog
import com.sysadmindoc.nono.model.matchTypeCatalog
import com.sysadmindoc.nono.runtime.historyRetentionCatalog
import com.sysadmindoc.nono.runtime.historyStorageCatalog
import com.sysadmindoc.nono.runtime.oemListenerChecklist
import kotlinx.coroutines.delay

@Composable
fun SignalOverlay(state: UiState, model: MainViewModel) {
    when (state.overlay) {
        Overlay.CONDITION_TYPE -> ChoiceDialog(
            "Notification match type",
            matchTypeCatalog,
            state.draft.matchType,
            onDismiss = model::dismissOverlay,
            onChoice = model::setMatchType,
        )
        Overlay.ADD_FILTER -> MenuDialog(
            "Add a filter",
            listOf(
                MenuItem("Words or phrase", Icons.Rounded.Add) { model.setPhraseDraft(""); model.navigate(Route.PHRASE_EDITOR) },
                MenuItem("Extra property", Icons.Rounded.FilterAlt, unavailable = NO_FILTER_ENGINE) {},
                MenuItem("Filter group", Icons.Rounded.Tune) { model.navigate(Route.FILTER_GROUP) },
            ),
            model::dismissOverlay,
        )
        Overlay.RULE_MORE -> MenuDialog(
            "Rule options",
            buildList {
                val selected = state.rules.firstOrNull { it.id == state.selectedRuleId }
                if (selected?.enabledFor != null) {
                    // Only an imported rule can carry one, and it never fired.
                    add(
                        MenuItem("Remove the \"${selected.enabledFor}\" expiry", Icons.Rounded.MoreTime) {
                            model.clearRuleExpiry()
                        },
                    )
                } else {
                    add(MenuItem("Enable for…", Icons.Rounded.MoreTime, unavailable = NO_RULE_EXPIRY_MESSAGE) {})
                }
                addAll(
                    listOf(
                        MenuItem("Set priority", Icons.Rounded.Tune) { model.showOverlay(Overlay.PRIORITY) },
                        MenuItem("Set folder", Icons.Rounded.Folder) { model.showOverlay(Overlay.FOLDER) },
                        MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) { model.setRenameDraft(selected?.name.orEmpty()); model.showOverlay(Overlay.RENAME) },
                        MenuItem("Duplicate", Icons.Rounded.Add) { model.duplicateRule() },
                        MenuItem("Delete", Icons.Rounded.DeleteForever, destructive = true) { model.deleteRule() },
                    ),
                )
            },
            model::dismissOverlay,
        )
        Overlay.PRIORITY -> ChoiceDialog("Rule priority", listOf("Highest", "High", "Normal", "Low", "Lowest"), state.rules.firstOrNull { it.id == state.selectedRuleId }?.priority, model::dismissOverlay) { model.setRulePriority(it) }
        Overlay.FOLDER -> TextEntryDialog("Pick folder", state.folderDraft, model::setFolderDraft, model::dismissOverlay) { model.setRuleFolder(state.folderDraft) }
        Overlay.RENAME -> RenameDialog(state, model)
        Overlay.HISTORY_ITEM -> MenuDialog(
            "Notification actions",
            buildList {
                add(
                    MenuItem(
                        "Restore notification",
                        Icons.Rounded.MoreTime,
                        unavailable = "A notification belongs to the app that sent it. Nothing can post it again.",
                    ) {},
                )
                add(
                    MenuItem("Open app", Icons.Rounded.ChevronRight) {
                        model.openRecordedApp(state.selectedHistoryPackageName)
                    },
                )
                add(MenuItem("View activity", Icons.Rounded.Tune) { model.navigate(Route.HISTORY_ACTIVITY) })
                add(
                    MenuItem("Copy metadata", Icons.Rounded.Add) {
                        state.selectedHistoryId?.let(model::copyHistoryMetadata)
                    },
                )
                add(MenuItem("Create rule", Icons.Rounded.Add) { model.createRuleFromSelectedHistory() })
                val starred = state.selectedHistoryStarred
                add(
                    MenuItem(if (starred) "Stop keeping this" else "Keep this record", Icons.Rounded.Tune) {
                        state.selectedHistoryId?.let { model.setHistoryStarred(it, !starred) }
                    },
                )
                // Only offered where it applies, so it explains this record rather than a general topic.
                if (state.selectedHistoryContentState in CONTENT_MISSING_STATES) {
                    add(MenuItem("Why was there no content?", Icons.Rounded.Tune) { model.showOverlay(Overlay.CONTENT_HIDDEN) })
                }
                add(
                    MenuItem("Delete", Icons.Rounded.DeleteForever, destructive = true) {
                        state.selectedHistoryId?.let(model::deleteHistoryRecord)
                    },
                )
            }, model::dismissOverlay,
        )
        Overlay.CONTENT_HIDDEN -> ContentHiddenDialog(model)
        Overlay.LISTENER_CHECKLIST -> ListenerChecklistDialog(model)
        Overlay.HISTORY_FILTERS -> {
            val packages = state.history.map { it.appPackageName ?: it.app }.distinct().sorted()
            val channels = state.history.mapNotNull { it.channelId }.distinct().sorted()
            val groups = state.history.mapNotNull { it.groupKey }.distinct().sorted()
            val items = buildList {
                add(MenuItem("Clear metadata filters", Icons.Rounded.FilterAlt) { model.clearHistoryMetadataFilters() })
                add(MenuItem("Group summaries only", Icons.Rounded.Tune) { model.setHistoryGroupSummaryOnly(true) })
                packages.forEach { value ->
                    add(MenuItem("Package: $value", Icons.Rounded.FilterAlt) { model.setHistoryPackageFilter(value) })
                }
                channels.forEach { value ->
                    add(MenuItem("Channel: $value", Icons.Rounded.FilterAlt) { model.setHistoryChannelFilter(value) })
                }
                groups.forEach { value ->
                    add(MenuItem("Group: $value", Icons.Rounded.FilterAlt) { model.setHistoryGroupFilter(value) })
                }
                add(MenuItem("Conversations only", Icons.Rounded.Tune) { model.setHistoryConversationFilter(true) })
                importanceCatalog.forEach { (level, label) ->
                    add(MenuItem("Importance: $label", Icons.Rounded.FilterAlt) { model.setHistoryImportanceFilter(level) })
                }
                NotificationContentState.values().forEach { value ->
                    add(MenuItem("Content: ${value.name}", Icons.Rounded.FilterAlt) { model.setHistoryContentStateFilter(value) })
                }
            }
            MenuDialog("History metadata filters", items, model::dismissOverlay)
        }
        Overlay.MUTE_MODE -> ChoiceDialog("Mute mode", listOf("Default", "Mute all sounds", "Aggressive"), state.settings["Mute mode"], model::dismissOverlay) { model.setSetting("Mute mode", it) }
        Overlay.MUTE_IMPORTANCE -> ChoiceDialog("Mute importance level", listOf("All important notifications", "High and above", "Urgent only"), state.settings["Mute importance"], model::dismissOverlay) { model.setSetting("Mute importance", it) }
        Overlay.HISTORY_STORAGE -> ChoiceDialog("Notification history", historyStorageCatalog, state.settings["Notification history"], model::dismissOverlay) { model.setSetting("Notification history", it) }
        Overlay.HISTORY_RETENTION -> ChoiceDialog("Keep history for", historyRetentionCatalog, state.settings["History retention"], model::dismissOverlay) { model.setSetting("History retention", it) }
        Overlay.TRANSFER_EXPORT_PASSPHRASE -> TransferPassphraseDialog(
            title = "Encrypt rule export",
            explanation = "The passphrase is used once and never saved or logged. Notification history is never included.",
            onDismiss = model::cancelTransfer,
            onDone = model::requestExport,
        )
        Overlay.TRANSFER_IMPORT_PASSPHRASE -> TransferPassphraseDialog(
            title = "Unlock rule import",
            explanation = "Enter the passphrase for this encrypted rule file. It is never saved or logged.",
            onDismiss = model::cancelTransfer,
            onDone = model::submitImportPassphrase,
        )
        Overlay.TRANSFER_PREVIEW -> TransferPreviewDialog(state, model)
        Overlay.THEME -> ChoiceDialog("Theme", listOf("Dark", "Light", "System default"), state.settings["Theme"], model::dismissOverlay) { model.setSetting("Theme", it) }
        Overlay.LANGUAGE -> ChoiceDialog("Language", listOf("System default", "English", "Deutsch", "Español", "Français"), state.settings["Language"], model::dismissOverlay) { model.setSetting("Language", it) }
        Overlay.NONE -> Unit
    }
}

/**
 * Explains a record the platform redacted before this app ever saw it.
 *
 * Android 15 and newer hide the content of notifications that look like they carry a one-time
 * code from any listener that does not hold RECEIVE_SENSITIVE_NOTIFICATIONS, which is a
 * signature-or-role permission an installed app cannot obtain. Users read the resulting rows as a
 * bug in whichever notification app they are using, so the app says plainly what happened, what
 * still works, and what they can do about it.
 */
@Composable
private fun ContentHiddenDialog(model: MainViewModel) {
    val context = LocalContext.current
    val command = remember { sensitiveNotificationsAppOpsCommand(context.packageName) }
    DialogFrame("No content arrived", model::dismissOverlay) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(
                "This notification reached NoNo with no title and no text. Some apps post one " +
                    "that way. Android also hides the text of notifications it treats as " +
                    "sensitive, such as ones carrying a sign-in code, from every app that reads " +
                    "notifications. It gives an app no way to tell the two apart, so NoNo does " +
                    "not guess which happened here.",
                color = SignalColors.Secondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.padding(vertical = 6.dp))
            Text(
                "A rule that matches on the app still works. Only a phrase condition needs text, " +
                    "and it cannot be tested against a notification that carried none.",
                color = SignalColors.Secondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.padding(vertical = 6.dp))
            Text("If Android was the reason", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Some devices offer Enhanced notifications under notification settings; turning it " +
                    "off stops the redaction. Where that switch is missing, the permission can be " +
                    "granted over ADB:",
                color = SignalColors.Secondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.padding(vertical = 6.dp))
            Text(
                command,
                color = SignalColors.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SignalColors.Background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.padding(vertical = 6.dp))
            SignalPrimaryButton("Copy command", onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("NoNo ADB command", command))
                model.reportCommandCopied()
            })
        }
    }
}

/**
 * Steps for keeping the listener bound on this particular phone.
 *
 * An OEM battery manager unbinding the service is the most common reason an app in this category
 * appears to stop working, and the setting that causes it is in a different place on every brand.
 */
@Composable
private fun ListenerChecklistDialog(model: MainViewModel) {
    val steps = remember { oemListenerChecklist(Build.MANUFACTURER, Build.VERSION.SDK_INT) }
    DialogFrame("Keeping the listener alive", model::dismissOverlay) {
        LazyColumn(Modifier.heightIn(max = 520.dp).padding(horizontal = 8.dp)) {
            item {
                Text(
                    "Android lets a manufacturer stop background services to save power, which is " +
                        "what usually silences a notification listener. On " + Build.MANUFACTURER + ":",
                    color = SignalColors.Secondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            items(steps) { step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("•", color = SignalColors.Yellow, fontSize = 15.sp)
                    Text(
                        step,
                        color = SignalColors.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Record states the explainer applies to.
 *
 * [NotificationContentState.HIDDEN_BY_SYSTEM] only appears on rows an earlier build stored, back
 * when redaction was inferred; new captures with no content are NOT_AVAILABLE.
 */
internal val CONTENT_MISSING_STATES = setOf(
    NotificationContentState.NOT_AVAILABLE,
    NotificationContentState.HIDDEN_BY_SYSTEM,
)

/** Built here so the dialog and its test agree on the exact command. */
internal fun sensitiveNotificationsAppOpsCommand(packageName: String): String =
    "adb shell cmd appops set --user 0 $packageName RECEIVE_SENSITIVE_NOTIFICATIONS allow"

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
                .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius))
                .padding(20.dp)
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            content()
            Text("Cancel", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(12.dp))
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, choices: List<String>, selected: String?, onDismiss: () -> Unit, onChoice: (String) -> Unit) {
    DialogFrame(title, onDismiss) {
        LazyColumn(Modifier.heightIn(max = 570.dp).semantics { selectableGroup() }) {
            items(choices) { choice ->
                ChoiceRow(choice, choice == selected, { onChoice(choice) })
            }
        }
    }
}

/** @param unavailable when set, the entry is shown with the reason and cannot be chosen. */
private data class MenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val unavailable: String? = null,
    val action: () -> Unit,
)

@Composable
private fun MenuDialog(title: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) {
        items.forEach { item ->
            val enabled = item.unavailable == null
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = enabled, role = Role.Button, onClick = item.action)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics { if (!enabled) contentDescription = "${item.label}. Unavailable: ${item.unavailable}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = when {
                        !enabled -> SignalColors.Muted
                        item.destructive -> SignalColors.Error
                        else -> SignalColors.Yellow
                    },
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        item.label,
                        color = when {
                            !enabled -> SignalColors.Muted
                            item.destructive -> SignalColors.Error
                            else -> SignalColors.White
                        },
                        fontSize = 17.sp,
                    )
                    item.unavailable?.let { Text(it, color = SignalColors.Secondary, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(state: UiState, model: MainViewModel) {
    TextEntryDialog("Pick nickname", state.renameDraft, model::setRenameDraft, model::dismissOverlay, model::renameRule)
}

@Composable
private fun TextEntryDialog(title: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onDone: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        requestKeyboardFocus(focusRequester, keyboard)
    }
    Dialog(onDismissRequest = { keyboard?.hide(); onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
                .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius)).padding(22.dp),
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Start typing…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SignalColors.Background,
                    unfocusedContainerColor = SignalColors.Background,
                    focusedBorderColor = SignalColors.Yellow,
                    unfocusedBorderColor = SignalColors.ControlOutline,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(focusRequester),
            )
            SignalPrimaryButton("Done", { keyboard?.hide(); onDone() }, modifier = Modifier.padding(top = 14.dp))
            Text("CANCEL", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { keyboard?.hide(); onDismiss() }.padding(18.dp))
        }
    }
}

@Composable
private fun TransferPassphraseDialog(
    title: String,
    explanation: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(SignalMetrics.cardRadius))
                .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.cardRadius)).padding(22.dp),
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(explanation, color = SignalColors.Secondary, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SignalColors.Background,
                    unfocusedContainerColor = SignalColors.Background,
                    focusedBorderColor = SignalColors.Yellow,
                    unfocusedBorderColor = SignalColors.ControlOutline,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            SignalPrimaryButton("Continue", { onDone(passphrase) }, modifier = Modifier.padding(top = 14.dp))
            Text("CANCEL", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDismiss).padding(18.dp))
        }
    }
}

@Composable
private fun TransferPreviewDialog(state: UiState, model: MainViewModel) {
    DialogFrame("Review rule import", model::cancelTransfer) {
        Text(
            "${state.transferAdditions} new rule(s), ${state.transferConflicts} conflict(s). Notification history is never imported.",
            color = SignalColors.Secondary,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            "Keep existing rules when IDs conflict, or replace those rules with the imported copies.",
            color = SignalColors.Secondary,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        TransferChoice("Keep existing", { model.commitImportedRules(ConflictResolution.KEEP_EXISTING) })
        TransferChoice("Replace conflicts", { model.commitImportedRules(ConflictResolution.REPLACE_EXISTING) })
    }
}

@Composable
private fun TransferChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = SignalColors.Yellow,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
    )
}
