package com.sysadmindoc.nono.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.data.CatalogedApp
import com.sysadmindoc.nono.data.matches
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import com.sysadmindoc.nono.model.UNSUPPORTED_ACTION_MESSAGE
import com.sysadmindoc.nono.model.isExecutableAction
import com.sysadmindoc.nono.model.renderActionSummary
import com.sysadmindoc.nono.model.NO_FILTER_ENGINE
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.UNSAVED_RULE_ID
import com.sysadmindoc.nono.model.actionCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RulesHomeScreen(state: UiState, model: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ListenerHealthBanner(state) }
        item {
            SignalPageHeader(
                title = "Rules",
                actionIcon = Icons.Rounded.Search,
                actionDescription = "Search rules",
                onAction = { model.showMessage("Rule search is not available yet.") },
            )
        }
        item {
            SignalStatusPanel(
                title = "On-device",
                description = "Metadata only · Preview mode",
                icon = Icons.Rounded.Shield,
            )
        }
        item {
            SignalSectionHeading(
                title = "Notification rules",
                subtitle = "Preview what each rule would do. Nothing is executed.",
            )
        }
        if (state.rules.isEmpty()) {
            item { EmptyRules(onCreate = model::newRule, onExplore = { model.selectRoot(RootTab.EXPLORE) }) }
        } else {
            items(state.rules, key = { it.id }) { rule ->
                key(rule.id) { RuleCard(rule, model, state.ruleMatchCounts[rule.id] ?: 0) }
            }
            item {
                Text(
                    if (state.rules.count { it.enabled } == 1) "1 active rule" else "${state.rules.count { it.enabled }} active rules",
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { CreateRuleButton(model::newRule) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun EmptyRules(onCreate: () -> Unit, onExplore: () -> Unit) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Box(
                Modifier.size(44.dp).background(SignalColors.Background, RoundedCornerShape(10.dp)).border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = SignalColors.Yellow)
            }
            Text("Add your first rule", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Choose an app, a match, and an action to preview. No notification is changed.",
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalOutlineButton("Browse ideas", onExplore, Modifier.weight(1f))
                SignalOutlineButton("Start a rule", onCreate, Modifier.weight(1f), Icons.Rounded.Add)
            }
        }
    }
}

@Composable
private fun CreateRuleButton(onClick: () -> Unit) {
    SignalPrimaryButton("Create rule", onClick, icon = Icons.Rounded.Add)
}

@Composable
private fun RuleCard(rule: SignalRule, model: MainViewModel, matchCount: Int) {
    SignalGroupedSurface(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (rule.enabled) "Enabled" else "Disabled", color = if (rule.enabled) SignalColors.Yellow else SignalColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { model.toggleRule(rule.id) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SignalColors.Yellow,
                    checkedThumbColor = SignalColors.Background,
                    uncheckedTrackColor = SignalColors.Border,
                    uncheckedThumbColor = SignalColors.Secondary,
                ),
            )
            IconButton(onClick = { model.showRuleOverlay(Overlay.RULE_MORE, rule.id) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Rule options", tint = SignalColors.Secondary)
            }
        }
        HorizontalDivider(color = SignalColors.Border)
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { model.editRule(rule) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            RuleFlowRow(1, Icons.Rounded.Apps, "APP", rule.app.replaceFirstChar { it.uppercase() })
            RuleConnector()
            RuleFlowRow(2, Icons.Rounded.Search, "MATCH", "${rule.matchType.replaceFirstChar { it.uppercase() }} ${rule.phrase}")
            RuleConnector()
            RuleFlowRow(3, actionIcon(rule.action), "ACTION", renderActionSummary(rule.action))
            if (rule.extras.isNotEmpty()) {
                Text("Filters: ${rule.extras.joinToString()}", color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            }
            if (matchCount > 0) {
                Text(
                    if (matchCount == 1) "Would match 1 stored notification" else "Would match $matchCount stored notifications",
                    color = SignalColors.Yellow,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleFlowRow(step: Int, icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(SignalColors.Background, RoundedCornerShape(10.dp)).border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (step == 3) SignalColors.Yellow else SignalColors.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, color = SignalColors.Muted, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RuleConnector() {
    Box(Modifier.padding(start = 19.dp).width(1.dp).height(12.dp).background(SignalColors.Border))
}

@Composable
fun RuleBuilderScreen(state: UiState, model: MainViewModel) {
    val editing = state.draft.id != UNSAVED_RULE_ID && state.rules.any { it.id == state.draft.id }
    val missing = state.validationError != null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SignalTopBar(
                title = if (editing) "Edit rule" else "New rule",
                onBack = { model.selectRoot(RootTab.RULES) },
                actionIcon = if (editing) Icons.Rounded.MoreVert else null,
                actionDescription = "Rule options",
                onAction = if (editing) ({ model.showRuleOverlay(Overlay.RULE_MORE, state.draft.id) }) else null,
            )
        }
        item { Text("Build a preview from three clear steps.", color = SignalColors.Secondary, style = MaterialTheme.typography.bodyLarge) }
        item {
            BuilderStep(
                step = 1,
                label = "FROM",
                value = state.draft.app.replaceFirstChar { it.uppercase() },
                icon = Icons.Rounded.Apps,
                action = "Change",
                onAction = { model.navigate(Route.APP_SELECTOR) },
            )
        }
        item {
            BuilderStep(
                step = 2,
                label = "WHEN",
                value = "${state.draft.matchType.replaceFirstChar { it.uppercase() }} ${state.draft.phrase}",
                icon = Icons.Rounded.Search,
                action = "Edit match",
                onAction = {
                    model.setPhraseDraft(if (state.draft.phrase == "anything") "" else state.draft.phrase)
                    model.navigate(Route.PHRASE_EDITOR)
                },
                secondaryAction = "Add filter",
                onSecondaryAction = { model.showOverlay(Overlay.ADD_FILTER) },
            )
        }
        if (state.draft.extras.isNotEmpty()) {
            item {
                SignalStatusPanel("Extra filters", state.draft.extras.joinToString(), icon = Icons.Rounded.FilterAlt)
            }
        }
        item {
            BuilderStep(
                step = 3,
                label = "THEN",
                value = renderActionSummary(state.draft.action),
                icon = actionIcon(state.draft.action),
                action = "Choose action",
                onAction = { model.navigate(Route.ACTION_SELECTOR) },
                error = missing,
            )
        }
        item {
            SignalStatusPanel(
                title = "Preview only",
                description = "No action is executed by this build.",
                icon = Icons.Rounded.Shield,
            )
        }
        if (missing) {
            item { SignalStatusPanel("Rule needs attention", state.validationError.orEmpty(), icon = Icons.Rounded.Info) }
        }
        item { SignalPrimaryButton("Save rule", model::saveRule, icon = Icons.Rounded.Save) }
        item {
            Text(
                "Discard changes",
                color = SignalColors.Yellow,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { model.selectRoot(RootTab.RULES) }.padding(vertical = 14.dp),
            )
        }
        item { HorizontalDivider(color = SignalColors.Border) }
        item { SignalSectionHeading("Recent matching metadata", "No stored metadata matches this draft.") }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun BuilderStep(
    step: Int,
    label: String,
    value: String,
    icon: ImageVector,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    error: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SignalStepNumber(step)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, color = if (error) SignalColors.Error else SignalColors.Secondary, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(SignalColors.Surface, RoundedCornerShape(10.dp)).border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, contentDescription = null, tint = if (error) SignalColors.Error else SignalColors.White) }
                Text(value, style = MaterialTheme.typography.headlineSmall, color = if (error) SignalColors.Error else SignalColors.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = if (secondaryAction != null) Arrangement.spacedBy(10.dp) else Arrangement.End,
            ) {
                if (secondaryAction != null && onSecondaryAction != null) {
                    SignalOutlineButton(action, onAction, Modifier.weight(1f))
                    SignalOutlineButton(secondaryAction, onSecondaryAction, Modifier.weight(1f), Icons.Rounded.Add)
                } else {
                    SignalOutlineButton(action, onAction)
                }
            }
        }
    }
}

@Composable
fun AppSelectorScreen(state: UiState, model: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (state.auditState.startsWith("031_")) {
        LaunchedEffect(state.auditState) { requestKeyboardFocus(focusRequester, keyboard) }
    }
    val filteredApps = state.appCatalog.filter { it.matches(state.appSearch) }
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Choose apps", onBack = { model.navigate(Route.RULE_BUILDER) })
        OutlinedTextField(
            value = state.appSearch,
            onValueChange = model::setAppSearch,
            placeholder = { Text("Search installed apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (state.appSearch.isNotEmpty()) ({
                IconButton(onClick = { model.setAppSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
            }) else null,
            singleLine = true,
            shape = RoundedCornerShape(SignalMetrics.controlRadius),
            colors = signalTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = SignalMetrics.pageHorizontal).focusRequester(focusRequester),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SignalMetrics.pageHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.draft.appPackageName == null) "Any app" else state.draft.app,
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Use any app",
                color = SignalColors.Yellow,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(role = Role.Button) { model.updateDraft { it.copy(app = "any app", appPackageName = null) } }.padding(12.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = SignalMetrics.pageHorizontal),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    SignalListRow(Icons.Rounded.Apps, "Any app", "Match every installed app", selected = state.draft.app == "any app") {
                        model.updateDraft { it.copy(app = "any app", appPackageName = null) }
                    }
                    if (filteredApps.isNotEmpty()) SignalDivider()
                }
            }
            if (filteredApps.isEmpty()) {
                item {
                    SignalStatusPanel(
                        if (state.appSearch.isBlank()) "No apps to choose from yet" else "No app matches that",
                        if (state.appSearch.isBlank()) {
                            "Apps appear here once they can be launched or have posted a notification."
                        } else {
                            "Search covers app names and package names."
                        },
                        icon = Icons.Rounded.Apps,
                    )
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    SignalGroupedSurface(Modifier.fillMaxWidth()) {
                        AppCatalogRow(
                            app = app,
                            // Matching on the package, not the label: two apps can share a label,
                            // and an app can rename itself.
                            selected = state.draft.appPackageName == app.packageName,
                            onClick = {
                                model.updateDraft { it.copy(app = app.label, appPackageName = app.packageName) }
                            },
                        )
                    }
                }
            }
        }
        SignalPrimaryButton(
            "Use selected app",
            { model.navigate(Route.RULE_BUILDER) },
            modifier = Modifier.padding(horizontal = SignalMetrics.pageHorizontal, vertical = 12.dp),
        )
    }
}

/**
 * One app in the picker, with its real icon where the system will give us one.
 *
 * The icon is loaded off the main thread and only for rows that are actually on screen: an
 * eager pass over every launchable app would decode a few hundred bitmaps to show a dozen.
 */
@Composable
private fun AppCatalogRow(app: CatalogedApp, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(SignalColors.Background, RoundedCornerShape(10.dp))
                .border(1.dp, SignalColors.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = icon
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.size(26.dp))
            } else {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = if (app.installed) SignalColors.White else SignalColors.Muted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.detail,
                color = SignalColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = SignalColors.Yellow, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun PhraseEditorScreen(state: UiState, model: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SignalTopBar("Match text", onBack = { model.navigate(Route.RULE_BUILDER) }) }
        item {
            SignalSectionHeading(
                "What should the notification contain?",
                "This text is checked in memory and is never stored.",
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SignalMetrics.controlRadius))
                    .border(1.dp, SignalColors.Border, RoundedCornerShape(SignalMetrics.controlRadius)),
            ) {
                OperatorChoice("Contains", !state.draft.matchType.contains("doesn't", true), Modifier.weight(1f)) {
                    model.updateDraft { it.copy(matchType = "contains") }
                }
                OperatorChoice("Does not contain", state.draft.matchType.contains("doesn't", true), Modifier.weight(1f)) {
                    model.updateDraft { it.copy(matchType = "doesn't contain") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.phraseDraft,
                onValueChange = model::setPhraseDraft,
                label = { Text("Phrase") },
                trailingIcon = if (state.phraseDraft.isNotEmpty()) ({
                    IconButton(onClick = { model.setPhraseDraft("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear phrase") }
                }) else null,
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); model.commitPhrase() }),
                shape = RoundedCornerShape(SignalMetrics.controlRadius),
                colors = signalTextFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        item { Text("${state.phraseDraft.length} characters", color = SignalColors.Secondary, style = MaterialTheme.typography.bodyMedium) }
        item { SignalStatusPanel("Private by design", "Notification title and body are not saved.", icon = Icons.Rounded.Shield) }
        item { SignalPrimaryButton("Use this phrase", { keyboard?.hide(); model.commitPhrase() }, icon = Icons.Rounded.Check) }
        item {
            SignalOutlineButton(
                "Match anything",
                { model.setPhraseDraft(""); keyboard?.hide(); model.commitPhrase() },
                Modifier.fillMaxWidth(),
                Icons.Rounded.Search,
            )
        }
        item { Text("MATCH PREVIEW", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                SignalListRow(Icons.Rounded.Info, "Current condition", "${state.draft.matchType.replaceFirstChar { it.uppercase() }} ${state.phraseDraft.ifBlank { "anything" }}")
            }
        }
    }
}

@Composable
private fun OperatorChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .background(if (selected) SignalColors.SurfaceSelected else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) SignalColors.Yellow else Color.Transparent, RoundedCornerShape(0.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = SignalColors.Yellow, modifier = Modifier.size(20.dp))
        Text(label, color = if (selected) SignalColors.Yellow else SignalColors.Secondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (selected) 6.dp else 0.dp))
    }
}

@Composable
fun FilterGroupScreen(state: UiState, model: MainViewModel) {
    val metadataFilters = listOf(
        Triple("Channel", "Any channel", Icons.Rounded.Notifications),
        Triple("Importance", "Any importance", Icons.Rounded.Star),
        Triple("Category", "Any category", Icons.Rounded.Category),
        Triple("Conversation", "Either", Icons.Rounded.Group),
    )
    val systemFilters = listOf(
        Triple("Screen state", "Any state", Icons.Rounded.PhoneAndroid),
        Triple("Charging", "Either", Icons.Rounded.BatteryChargingFull),
        Triple("Day and time", "Always", Icons.Rounded.Schedule),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = SignalMetrics.pageHorizontal, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SignalTopBar("Extra filters", onBack = { model.navigate(Route.RULE_BUILDER) }, actionIcon = Icons.Rounded.Info, onAction = { model.showMessage(NO_FILTER_ENGINE) }) }
        item { SignalSectionHeading("Narrow this match", NO_FILTER_ENGINE) }
        item { Text("NOTIFICATION METADATA", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                metadataFilters.forEachIndexed { index, (title, _, icon) ->
                    SignalListRow(
                        icon = icon,
                        title = title,
                        subtitle = NO_FILTER_ENGINE,
                        selected = state.draft.extras.contains(title),
                        enabled = false,
                    )
                    if (index != metadataFilters.lastIndex) SignalDivider()
                }
            }
        }
        item { Text("SYSTEM STATE", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                systemFilters.forEachIndexed { index, (title, _, icon) ->
                    SignalListRow(
                        icon = icon,
                        title = title,
                        subtitle = NO_FILTER_ENGINE,
                        selected = state.draft.extras.contains(title),
                        enabled = false,
                    )
                    if (index != systemFilters.lastIndex) SignalDivider()
                }
            }
        }
        item {
            SignalStatusPanel(
                "Not evaluated",
                "A rule carrying any of these never matches. Clear them to make the rule work again.",
                icon = Icons.Rounded.Shield,
            )
        }
        item { SignalPrimaryButton("Back to the rule", { model.navigate(Route.RULE_BUILDER) }) }
        if (state.draft.extras.isNotEmpty()) {
            item { SignalOutlineButton("Clear these filters", model::clearExtraFilters, Modifier.fillMaxWidth()) }
        }
        item { Text("MATCH LOGIC", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium) }
        item {
            SignalGroupedSurface(Modifier.fillMaxWidth()) {
                SignalListRow(
                    Icons.Rounded.FilterAlt,
                    "Operator",
                    subtitle = NO_FILTER_ENGINE,
                    value = state.draft.filterOperator,
                    enabled = false,
                )
            }
        }
    }
}

@Composable
fun ActionSelectorScreen(state: UiState, model: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val filtered = actionCatalog.filter { it.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        SignalTopBar("Choose action", onBack = { model.navigate(Route.RULE_BUILDER) })
        Column(Modifier.padding(horizontal = SignalMetrics.pageHorizontal)) {
            SignalStatusPanel("Preview only", UNSUPPORTED_ACTION_MESSAGE, icon = Icons.Rounded.Shield)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search actions") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(SignalMetrics.controlRadius),
                colors = signalTextFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Text("WHAT THIS BUILD DOES", color = SignalColors.Secondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = SignalMetrics.pageHorizontal),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    SignalListRow(
                        Icons.Rounded.VisibilityOff,
                        RECORD_ONLY_ACTION.replaceFirstChar { it.uppercase() },
                        "The match is written to History. Nothing on the device changes.",
                        selected = !isExecutableAction(state.draft.action),
                        onClick = { model.updateDraft { it.copy(action = RECORD_ONLY_ACTION) } },
                    )
                }
            }
            item {
                Text(
                    "NOT AVAILABLE IN THIS BUILD",
                    color = SignalColors.Secondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
                )
            }
            item {
                SignalGroupedSurface(Modifier.fillMaxWidth()) {
                    filtered.forEachIndexed { index, action ->
                        SignalListRow(
                            actionIcon(action),
                            action,
                            // An imported rule can already name this one, so say so rather than
                            // showing it as an ordinary greyed row.
                            if (state.draft.action.equals(action, ignoreCase = true)) {
                                "In this rule from an import. Never executed, and it cannot be saved again."
                            } else {
                                UNSUPPORTED_ACTION_MESSAGE
                            },
                            selected = state.draft.action.equals(action, ignoreCase = true),
                            enabled = false,
                        )
                        if (index != filtered.lastIndex) SignalDivider()
                    }
                }
            }
        }
        SignalPrimaryButton(
            "Use selected action",
            { model.navigate(Route.RULE_BUILDER) },
            modifier = Modifier.padding(horizontal = SignalMetrics.pageHorizontal, vertical = 12.dp),
        )
    }
}

@Composable
private fun signalTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SignalColors.Surface,
    unfocusedContainerColor = SignalColors.Surface,
    focusedBorderColor = SignalColors.Yellow,
    unfocusedBorderColor = SignalColors.Border,
    focusedLabelColor = SignalColors.Yellow,
    unfocusedLabelColor = SignalColors.Secondary,
    cursorColor = SignalColors.Yellow,
)

private fun actionIcon(action: String): ImageVector = when {
    action.equals("Mute", true) -> Icons.AutoMirrored.Rounded.VolumeOff
    action.equals("nothing", true) || action.equals("Do nothing", true) -> Icons.Rounded.VisibilityOff
    action.equals("Snooze", true) -> Icons.Rounded.Schedule
    action.equals("Remind me", true) -> Icons.Rounded.Notifications
    action.equals("Open notification", true) -> Icons.AutoMirrored.Rounded.OpenInNew
    else -> Icons.Rounded.Tune
}
