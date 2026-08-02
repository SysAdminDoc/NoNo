package com.anm.signalrules.reconstruction.ui

import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.enableForCatalog
import com.anm.signalrules.reconstruction.model.extraFilterCatalog
import com.anm.signalrules.reconstruction.model.filterOperatorCatalog
import com.anm.signalrules.reconstruction.model.matchTypeCatalog
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
        Overlay.CONDITION_EXTRAS -> CatalogDialog(
            "Extra notification properties",
            extraFilterCatalog,
            when { state.auditState.startsWith("038_") -> 7; state.auditState.startsWith("037_") -> 4; else -> 0 },
            model::dismissOverlay,
        ) { model.toggleExtraFilter(it) }
        Overlay.FILTER_OPERATOR -> ChoiceDialog(
            "Filter operator",
            filterOperatorCatalog,
            state.draft.filterOperator,
            model::dismissOverlay,
        ) { model.setFilterOperator(it) }
        Overlay.ADD_FILTER -> MenuDialog(
            "Add a filter",
            listOf(
                MenuItem("Words or phrase", Icons.Rounded.Add) { model.setPhraseDraft(""); model.navigate(Route.PHRASE_EDITOR) },
                MenuItem("Extra property", Icons.Rounded.FilterAlt) { model.showOverlay(Overlay.CONDITION_EXTRAS) },
                MenuItem("Filter group", Icons.Rounded.Tune) { model.navigate(Route.FILTER_GROUP) },
            ),
            model::dismissOverlay,
        )
        Overlay.RULE_MORE -> MenuDialog(
            "Rule options",
            listOf(
                MenuItem("Enable for…", Icons.Rounded.MoreTime) { model.showOverlay(Overlay.ENABLE_FOR) },
                MenuItem("Set priority", Icons.Rounded.Tune) { model.showOverlay(Overlay.PRIORITY) },
                MenuItem("Set folder", Icons.Rounded.Folder) { model.showOverlay(Overlay.FOLDER) },
                MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) { model.setRenameDraft(state.rules.firstOrNull { it.id == state.selectedRuleId }?.name.orEmpty()); model.showOverlay(Overlay.RENAME) },
                MenuItem("Duplicate", Icons.Rounded.Add) { model.duplicateRule() },
                MenuItem("Delete", Icons.Rounded.DeleteForever, destructive = true) { model.deleteRule() },
            ), model::dismissOverlay,
        )
        Overlay.ENABLE_FOR -> ChoiceDialog(
            "Enable for…",
            enableForCatalog,
            state.rules.firstOrNull { it.id == state.selectedRuleId }?.enabledFor,
            model::dismissOverlay,
        ) { model.setEnabledFor(it) }
        Overlay.PRIORITY -> ChoiceDialog("Rule priority", listOf("Highest", "High", "Normal", "Low", "Lowest"), state.rules.firstOrNull { it.id == state.selectedRuleId }?.priority, model::dismissOverlay) { model.setRulePriority(it) }
        Overlay.FOLDER -> TextEntryDialog("Pick folder", state.folderDraft, model::setFolderDraft, model::dismissOverlay) { model.setRuleFolder(state.folderDraft) }
        Overlay.RENAME -> RenameDialog(state, model)
        Overlay.HISTORY_ITEM -> MenuDialog(
            "Notification actions",
            listOf(
                MenuItem("Restore", Icons.Rounded.MoreTime) { model.dismissOverlay() },
                MenuItem("Open notification", Icons.Rounded.ChevronRight) { model.dismissOverlay() },
                MenuItem("View activity", Icons.Rounded.Tune) { model.navigate(Route.HISTORY_ACTIVITY) },
                MenuItem("Copy", Icons.Rounded.Add) { model.dismissOverlay() },
                MenuItem("Create rule", Icons.Rounded.Add) { model.newRule() },
                MenuItem("Delete", Icons.Rounded.DeleteForever, destructive = true) { model.dismissOverlay() },
            ), model::dismissOverlay,
        )
        Overlay.MUTE_MODE -> ChoiceDialog("Mute mode", listOf("Default", "Mute all sounds", "Aggressive"), state.settings["Mute mode"], model::dismissOverlay) { model.setSetting("Mute mode", it) }
        Overlay.MUTE_IMPORTANCE -> ChoiceDialog("Mute importance level", listOf("All important notifications", "High and above", "Urgent only"), state.settings["Mute importance"], model::dismissOverlay) { model.setSetting("Mute importance", it) }
        Overlay.HISTORY_STORAGE -> ChoiceDialog("Notification history", listOf("All notifications", "Store notification content", "Metadata only", "Off"), state.settings["Notification history"], model::dismissOverlay) { model.setSetting("Notification history", it) }
        Overlay.HISTORY_RETENTION -> ChoiceDialog("Keep history for", listOf("7 days", "30 days", "3 months", "6 months", "Forever"), state.settings["History retention"], model::dismissOverlay) { model.setSetting("History retention", it) }
        Overlay.THEME -> ChoiceDialog("Theme", listOf("Dark", "Light", "System default"), state.settings["Theme"], model::dismissOverlay) { model.setSetting("Theme", it) }
        Overlay.LANGUAGE -> ChoiceDialog("Language", listOf("System default", "English", "Deutsch", "Español", "Français"), state.settings["Language"], model::dismissOverlay) { model.setSetting("Language", it) }
        Overlay.NONE -> Unit
    }
}

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(22.dp)).padding(20.dp)
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            content()
            Text("Cancel", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(12.dp))
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, choices: List<String>, selected: String?, onDismiss: () -> Unit, onChoice: (String) -> Unit) {
    DialogFrame(title, onDismiss) {
        LazyColumn(Modifier.heightIn(max = 570.dp)) {
            items(choices) { choice ->
                ChoiceRow(choice, choice == selected, { onChoice(choice) })
            }
        }
    }
}

@Composable
private fun CatalogDialog(title: String, choices: List<String>, initialIndex: Int, onDismiss: () -> Unit, onChoice: (String) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtMost(choices.lastIndex))
    Dialog(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.width(205.dp).heightIn(max = 560.dp).background(Color(0xFF3D3F44), RoundedCornerShape(4.dp)).padding(vertical = 4.dp),
            state = listState,
        ) {
            items(choices) { choice ->
                Text(choice, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoice(choice) }.padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
    }
}

private data class MenuItem(val label: String, val icon: ImageVector, val destructive: Boolean = false, val action: () -> Unit)

@Composable
private fun MenuDialog(title: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = item.action).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (item.destructive) SignalColors.Error else SignalColors.Yellow)
                Text(item.label, color = if (item.destructive) SignalColors.Error else SignalColors.White, fontSize = 17.sp, modifier = Modifier.padding(start = 16.dp))
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
        repeat(3) {
            delay(200)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Dialog(onDismissRequest = { keyboard?.hide(); onDismiss() }) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Column(Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(22.dp)).padding(22.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Start typing…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF4A4C56),
                    unfocusedContainerColor = Color(0xFF4A4C56),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(focusRequester),
            )
            SignalPrimaryButton("Done", { keyboard?.hide(); onDone() }, modifier = Modifier.padding(top = 14.dp))
            Text("CANCEL", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { keyboard?.hide(); onDismiss() }.padding(18.dp))
        }
    }
}
