package com.anm.signalrules.reconstruction.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anm.signalrules.reconstruction.MainViewModel
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.actionCatalog
import com.anm.signalrules.reconstruction.model.appOptions
import com.anm.signalrules.reconstruction.model.renderRuleCardSentence
import kotlinx.coroutines.delay

@Composable
fun RulesHomeScreen(state: UiState, model: MainViewModel) {
    if (state.rules.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ListenerHealthBanner(state)
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(26.dp)
            ) {
                Box(Modifier.size(48.dp).background(SignalColors.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = SignalColors.Background)
                }
                Text("Add your first rule", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 24.dp))
                Text(
                    "Tap the + button to create a rule that will be triggered when you get a new notification, or check out Suggestions on the Explore page.",
                    color = Color(0xFFD5D5E0), fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 12.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                    CreateRuleButton(model::newRule)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ListenerHealthBanner(state)
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                SignalIconButton(Icons.Rounded.Search, "Search rules", onClick = { model.showMessage("Rule search is not reconstructed.") })
            }
            Box(Modifier.size(48.dp).align(Alignment.CenterHorizontally).background(SignalColors.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = SignalColors.Background)
            }
            Text("Notification rules", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
            Text(
                "When you get a notification, if it matches any of the following rules it will perform the chosen action.",
                color = SignalColors.Secondary, fontSize = 17.sp, lineHeight = 23.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 12.dp)
            )
            state.rules.forEach { rule -> key(rule.id) { RuleCard(rule, model) } }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.End) { CreateRuleButton(model::newRule) }
        }
    }
}

@Composable
private fun CreateRuleButton(onClick: () -> Unit) {
    Row(
        Modifier.background(SignalColors.Yellow, RoundedCornerShape(40.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(28.dp))
        Text("Create rule", color = SignalColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RuleCard(rule: SignalRule, model: MainViewModel) {
    val accent = if (rule.enabled) SignalColors.RuleBlue else SignalColors.RuleDisabled
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp).background(accent, RoundedCornerShape(18.dp)).padding(5.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { model.showRuleOverlay(Overlay.RULE_MORE, rule.id) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Rule options", tint = SignalColors.Background)
            }
            Spacer(Modifier.weight(1f))
            Text(if (rule.enabled) "Enabled" else "Disabled", color = SignalColors.Background, fontWeight = FontWeight.Bold)
            Switch(
                checked = rule.enabled,
                onCheckedChange = { model.toggleRule(rule.id) },
                colors = SwitchDefaults.colors(checkedTrackColor = SignalColors.Background, checkedThumbColor = SignalColors.White, uncheckedTrackColor = SignalColors.Background, uncheckedThumbColor = SignalColors.Secondary),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(SignalColors.Background, RoundedCornerShape(16.dp)).clickable { model.editRule(rule) }.padding(20.dp)
        ) {
            Text(rule.name, color = SignalColors.Secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            rule.enabledFor?.let { duration ->
                Text("Enabled for $duration", color = SignalColors.Yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                renderRuleCardSentence(rule),
                color = SignalColors.White,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 27.sp,
                textDecoration = if (rule.enabled) TextDecoration.None else TextDecoration.LineThrough,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun RuleBuilderScreen(state: UiState, model: MainViewModel) {
    if (state.auditState.startsWith("082_")) {
        SuggestionRulePreview(model)
        return
    }
    val missing = state.validationError != null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { model.selectRoot(com.anm.signalrules.reconstruction.model.RootTab.RULES) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                val editingId = state.draft.id
                if (state.rules.any { it.id == editingId }) {
                    IconButton(onClick = { model.showRuleOverlay(Overlay.RULE_MORE, editingId) }) { Icon(Icons.Rounded.MoreVert, "More") }
                }
            }
            Text("When I get a notification", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("from ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                TokenButton(state.draft.app) { model.navigate(Route.APP_SELECTOR) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("that ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                TokenButton(state.draft.matchType) { model.showOverlay(Overlay.CONDITION_TYPE) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenButton(state.draft.phrase) { model.setPhraseDraft(if (state.draft.phrase == "anything") "" else state.draft.phrase); model.navigate(Route.PHRASE_EDITOR) }
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.background(SignalColors.Yellow, RoundedCornerShape(13.dp)).clickable { model.showOverlay(Overlay.ADD_FILTER) }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Background)
                    Text("Filter", color = SignalColors.Background, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            if (state.draft.extras.isNotEmpty()) {
                Text(
                    "with " + state.draft.extras.joinToString(", "),
                    color = SignalColors.Secondary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("then ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                TokenButton(if (missing) "missing action" else "do ${state.draft.action}", onClick = { model.navigate(Route.ACTION_SELECTOR) }, error = missing)
            }
            if (missing) {
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(SignalColors.Error, CircleShape), contentAlignment = Alignment.Center) {
                        Text("!", color = SignalColors.Background, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "You have a missing field. Please tap to fill it in to complete the rule.",
                        color = SignalColors.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp,
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                    )
                }
            }
            SignalPrimaryButton("Save rule", model::saveRule, modifier = Modifier.padding(top = if (missing) 0.dp else 18.dp))
            HorizontalDivider(color = SignalColors.Surface, thickness = 3.dp, modifier = Modifier.padding(vertical = 42.dp))
            Text("Recent matching notifications", style = MaterialTheme.typography.headlineMedium)
            Text(
                "No recent notifications match this rule. This may be because the rule is very specific or the notifications arrived before Signal Rules was installed.",
                color = SignalColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 10.dp, bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRulePreview(model: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("When I get a notification", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("from ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("any app", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            Text(" that ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Text("contains", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
        Text("anything and device is in", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, modifier = Modifier.padding(top = 12.dp))
        Text("pocket/face down and device", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("is on table", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            Row(Modifier.padding(start = 10.dp).background(SignalColors.Yellow, RoundedCornerShape(13.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = SignalColors.Background)
                Text("Filter", color = SignalColors.Background, fontWeight = FontWeight.Bold)
            }
            Text(" then", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Box(Modifier.size(28.dp).background(SignalColors.SuggestionGreen, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FlashlightOn, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(18.dp))
            }
            Text(" flashlight", color = SignalColors.Yellow, fontSize = 25.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
            Text(" with ", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.size(36.dp).background(SignalColors.Yellow))
        }
        SignalPrimaryButton("Save rule", { model.updateDraft { it.copy(action = "Flashlight") }; model.saveRule() }, modifier = Modifier.padding(top = 24.dp))
        HorizontalDivider(color = SignalColors.Surface, thickness = 3.dp, modifier = Modifier.padding(vertical = 40.dp))
        Text("Recent matching notifications", style = MaterialTheme.typography.headlineMedium)
        Text("These recent notifications from your local history may have triggered this rule.", color = SignalColors.Secondary, fontWeight = FontWeight.Bold, lineHeight = 22.sp, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 24.dp).background(SignalColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.White)
            Column(Modifier.padding(start = 14.dp)) {
                Text("Shell    Now", fontWeight = FontWeight.Bold)
                Text("Sanitized audit notice", color = SignalColors.Secondary)
            }
        }
    }
}

@Composable
fun AppSelectorScreen(state: UiState, model: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val searching = state.auditState.startsWith("031_")
    if (searching) LaunchedEffect(state.auditState) {
        requestKeyboardFocus(focusRequester, keyboard)
    }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(46.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom) {
            Text("When notification ", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("is from", color = SignalColors.Yellow, fontSize = 27.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
        }
        OutlinedTextField(
            value = state.appSearch,
            onValueChange = model::setAppSearch,
            placeholder = { Text("Search…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SignalColors.Yellow) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SignalColors.Surface,
                unfocusedContainerColor = SignalColors.Surface,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp).focusRequester(focusRequester),
        )
        val apps = appOptions.filter { it.label.contains(state.appSearch, ignoreCase = true) }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)) {
            items((apps.size + 1) / 2) { rowIndex ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (column in 0..1) {
                        val index = rowIndex * 2 + column
                        if (index >= apps.size) { Spacer(Modifier.weight(1f)); continue }
                        val app = apps[index]
                        Column(
                            Modifier.weight(1f).height(104.dp).padding(bottom = 10.dp).background(SignalColors.Surface, RoundedCornerShape(16.dp)).clickable {
                                model.updateDraft { it.copy(app = app.label, appPackageName = app.packageName) }
                                model.navigate(Route.RULE_BUILDER)
                            }.padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(Modifier.size(30.dp).background(actionColor(index), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = SignalColors.Background, modifier = Modifier.size(18.dp))
                            }
                            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
        SignalPrimaryButton(
            "Pick all apps",
            {
                model.updateDraft { it.copy(app = "any app", appPackageName = null) }
                model.navigate(Route.RULE_BUILDER)
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun PhraseEditorScreen(state: UiState, model: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val showPhraseInput = state.phraseInputVisible
    val requestKeyboard = showPhraseInput
    if (requestKeyboard) LaunchedEffect(requestKeyboard) {
        requestKeyboardFocus(focusRequester, keyboard)
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(56.dp))
        Text("When notification", color = Color(0xFFB4B4B6), fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        Text("contains any of", color = SignalColors.Yellow, fontSize = 27.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 46.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConditionChoice("Phrase", "Filter with a name, word or phrase", true, Modifier.weight(1f)) { model.showPhraseInput() }
            ConditionChoice("Extras", "Filter by images and more", true, Modifier.weight(1f)) { model.showOverlay(Overlay.CONDITION_EXTRAS) }
            ConditionChoice("Group", "For more complex filters", false, Modifier.weight(1f)) { model.navigate(Route.FILTER_GROUP) }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().background(SignalColors.Surface).padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text("When you're done tap apply to set the filter for your notification rule.", color = SignalColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            SignalPrimaryButton("Apply filter", { model.commitPhrase() }, modifier = Modifier.padding(top = 14.dp))
        }
    }

    if (showPhraseInput) {
        Dialog(onDismissRequest = { keyboard?.hide(); model.hidePhraseInput() }) {
            Column(Modifier.fillMaxWidth().background(SignalColors.Surface, RoundedCornerShape(22.dp)).padding(22.dp)) {
                Text("Notification contains", color = SignalColors.Yellow, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.phraseDraft,
                    onValueChange = model::setPhraseDraft,
                    placeholder = { Text("Start typing…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); model.commitPhrase() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF4A4C56),
                        unfocusedContainerColor = Color(0xFF4A4C56),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).focusRequester(focusRequester),
                )
                SignalPrimaryButton("Done", { keyboard?.hide(); model.commitPhrase() }, modifier = Modifier.padding(top = 16.dp))
                Text("CANCEL", color = SignalColors.Yellow, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { keyboard?.hide(); model.navigate(Route.RULE_BUILDER) }.padding(18.dp))
            }
        }
    }
}

@Composable
private fun ConditionChoice(title: String, description: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(description, color = SignalColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, minLines = 3)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().height(82.dp)
                .background(if (filled) SignalColors.Yellow else SignalColors.Background, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(title, color = if (filled) SignalColors.Background else SignalColors.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun FilterGroupScreen(state: UiState, model: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Filter group", onBack = { model.navigate(Route.RULE_BUILDER) }, actionIcon = Icons.Rounded.Check, actionDescription = "Use group", onAction = { model.navigate(Route.RULE_BUILDER) })
        Text("Match", color = SignalColors.Secondary, modifier = Modifier.padding(start = 24.dp, top = 20.dp))
        Row(Modifier.fillMaxWidth().clickable { model.showOverlay(Overlay.FILTER_OPERATOR) }.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FilterAlt, contentDescription = null, tint = SignalColors.Yellow)
            Text("Contains any of these filters", fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
        }
        SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column {
                Text("Notification contains anything", fontWeight = FontWeight.Bold)
                Text("Tap + to add another condition", color = SignalColors.Secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) { CreateRuleButton { model.showOverlay(Overlay.ADD_FILTER) } }
    }
}

@Composable
fun ActionSelectorScreen(state: UiState, model: MainViewModel) {
    if (state.auditState.startsWith("061_")) {
        SelectedActionScreen(model)
        return
    }
    val listState = rememberLazyListState()
    val target = when (state.auditState.substringBefore('_').toIntOrNull()) {
        50 -> 2; 51 -> 5; 52 -> 8; 53 -> 11; 54 -> 14; 55 -> 17; 56 -> 20; 57 -> 24; 58 -> 26; else -> 0
    }
    LaunchedEffect(state.auditState) { listState.scrollToItem(target.coerceAtMost(actionCatalog.lastIndex)) }
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Choose an action", onBack = { model.navigate(Route.RULE_BUILDER) })
        LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)) {
            itemsIndexed(actionCatalog) { index, action ->
                if (index in setOf(2, 7, 10, 16, 19, 22, 27)) {
                    val label = when (index) { 2 -> "Get your attention"; 7 -> "Group notifications"; 10 -> "Change notifications"; 16 -> "Dismiss"; 19 -> "Interact"; 22 -> "Utilities"; else -> "Integrations" }
                    SectionLabel(label)
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        model.updateDraft { it.copy(action = action) }
                        model.navigate(Route.RULE_BUILDER)
                    }.padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(46.dp).background(actionColor(index), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (action == "Mute") Icons.AutoMirrored.Rounded.VolumeOff else Icons.Rounded.Tune, contentDescription = null, tint = SignalColors.Background)
                    }
                    Text(action, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    if (state.draft.action == action) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SignalColors.Yellow)
                }
            }
        }
    }
}

@Composable
private fun SelectedActionScreen(model: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(72.dp))
        Text("Silence actions ︿", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(42.dp))
        ActionFeatureCard("Cooldown", "Prevent the same app or conversation from interrupting you multiple times in quick succession.", SignalColors.Surface, SignalColors.White, 158.dp) { }
        Spacer(Modifier.height(18.dp))
        ActionFeatureCard("Mute", "Prevent a matching notification from buzzing or playing a sound.", SignalColors.White, SignalColors.Background, 148.dp) {
            model.updateDraft { it.copy(action = "Mute") }
        }
        Text("Attention actions ︿", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 30.dp))
        ActionFeatureCard("Alarm", "Show a full-screen alert with sound and vibration.", SignalColors.Surface, SignalColors.White, 130.dp) { }
        Spacer(Modifier.weight(1f))
        SignalPrimaryButton("Pick action", { model.navigate(Route.RULE_BUILDER) }, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ActionFeatureCard(title: String, description: String, background: Color, foreground: Color, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(height).background(background, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(24.dp)
    ) {
        Box(Modifier.size(42.dp).background(SignalColors.RuleBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(if (title == "Mute") Icons.AutoMirrored.Rounded.VolumeOff else Icons.Rounded.Tune, contentDescription = null, tint = SignalColors.Background)
        }
        Text(title, color = foreground, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(description, color = if (background == SignalColors.White) SignalColors.Background else SignalColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun actionColor(index: Int): Color = when (index % 5) {
    0 -> SignalColors.RuleBlue
    1 -> SignalColors.Yellow
    2 -> SignalColors.SuggestionGreen
    3 -> SignalColors.SuggestionPurple
    else -> SignalColors.Error
}
