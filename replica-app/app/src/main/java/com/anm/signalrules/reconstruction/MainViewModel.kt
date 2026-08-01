package com.anm.signalrules.reconstruction

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.RootTab
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.defaultSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Application.signalDataStore by preferencesDataStore(name = "signal_rules")

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var auditOverride: String? = null

    private object Keys {
        val Onboarding = booleanPreferencesKey("onboarding_complete")
        val HasRule = booleanPreferencesKey("has_rule")
        val RuleEnabled = booleanPreferencesKey("rule_enabled")
        val RuleName = stringPreferencesKey("rule_name")
        val RuleApp = stringPreferencesKey("rule_app")
        val RulePhrase = stringPreferencesKey("rule_phrase")
        val RuleAction = stringPreferencesKey("rule_action")
    }

    private fun settingKey(label: String) = stringPreferencesKey(
        "setting_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    )

    init {
        viewModelScope.launch {
            val values = getApplication<Application>().signalDataStore.data.first()
            val rule = if (values[Keys.HasRule] == true) listOf(
                SignalRule(
                    name = values[Keys.RuleName] ?: "Test rule",
                    app = values[Keys.RuleApp] ?: "any app",
                    phrase = values[Keys.RulePhrase] ?: "anything",
                    action = values[Keys.RuleAction] ?: "Mute",
                    enabled = values[Keys.RuleEnabled] ?: true,
                )
            ) else emptyList()
            val settings = defaultSettings.mapValues { (label, default) -> values[settingKey(label)] ?: default }
            _state.value = _state.value.copy(
                route = if (values[Keys.Onboarding] == true) Route.ROOT else Route.ONBOARDING,
                auditState = if (values[Keys.Onboarding] == true) "010_home_empty" else "002_welcome_default",
                rules = rule,
                settings = settings,
            )
            auditOverride?.let(::applyAuditState)
        }
    }

    fun completeOnboarding() {
        _state.value = _state.value.copy(route = Route.ROOT, rootTab = RootTab.RULES, overlay = Overlay.NONE, auditState = "010_home_empty")
        viewModelScope.launch { getApplication<Application>().signalDataStore.edit { it[Keys.Onboarding] = true } }
    }

    fun refreshOnboardingCapabilities() {
        if (auditOverride != null || _state.value.route != Route.ONBOARDING) return
        val app = getApplication<Application>()
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val batteryGranted = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(app.packageName)
        val listenerGranted = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        val step = when {
            !notificationsGranted -> 0
            !batteryGranted -> 1
            !listenerGranted -> 2
            else -> 3
        }
        _state.value = _state.value.copy(onboardingStep = step)
        if (step == 3) completeOnboarding()
    }

    fun setOnboardingStep(step: Int) { _state.value = _state.value.copy(onboardingStep = step.coerceIn(0, 3)) }
    fun selectRoot(tab: RootTab) { _state.value = _state.value.copy(route = Route.ROOT, rootTab = tab, overlay = Overlay.NONE, transientMessage = null) }
    fun navigate(route: Route) { _state.value = _state.value.copy(route = route, overlay = Overlay.NONE, transientMessage = null) }
    fun showOverlay(overlay: Overlay) { _state.value = _state.value.copy(overlay = overlay) }
    fun dismissOverlay() { _state.value = _state.value.copy(overlay = Overlay.NONE) }
    fun updateDraft(transform: (SignalRule) -> SignalRule) { _state.value = _state.value.copy(draft = transform(_state.value.draft)) }
    fun setPhraseDraft(text: String) { _state.value = _state.value.copy(phraseDraft = text) }
    fun commitPhrase() {
        val phrase = _state.value.phraseDraft.ifBlank { "anything" }
        _state.value = _state.value.copy(route = Route.RULE_BUILDER, draft = _state.value.draft.copy(phrase = phrase), overlay = Overlay.NONE)
    }
    fun setAppSearch(text: String) { _state.value = _state.value.copy(appSearch = text) }
    fun setHistorySearch(text: String) { _state.value = _state.value.copy(historySearch = text) }
    fun setHistoryFilter(filter: String) { _state.value = _state.value.copy(historyFilter = filter) }
    fun setHistoryActivityTab(tab: String) { _state.value = _state.value.copy(historyActivityTab = tab) }
    fun setRenameDraft(text: String) { _state.value = _state.value.copy(renameDraft = text) }

    fun newRule() {
        _state.value = _state.value.copy(route = Route.RULE_BUILDER, overlay = Overlay.NONE, draft = SignalRule(name = "New rule"), auditState = "029_rule_builder_default")
    }

    fun saveRule() {
        val draft = _state.value.draft.copy(
            name = _state.value.draft.name.ifBlank { "Rule ${_state.value.rules.size + 1}" },
            enabled = true,
        )
        if (draft.action == "nothing") {
            _state.value = _state.value.copy(auditState = "059_rule_builder_validation_missing", transientMessage = "You have a missing field. Please tap to fill it in to complete the rule.")
            return
        }
        _state.value = _state.value.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(draft), auditState = "063_rules_populated_test_record", transientMessage = "Rule saved")
        persistRule(draft)
    }

    fun toggleRule() {
        val rule = _state.value.rules.firstOrNull() ?: return
        val updated = rule.copy(enabled = !rule.enabled)
        _state.value = _state.value.copy(rules = listOf(updated), auditState = if (updated.enabled) "063_rules_populated_test_record" else "064_rules_test_record_disabled")
        persistRule(updated)
    }

    fun renameRule() {
        val rule = _state.value.rules.firstOrNull() ?: return
        val updated = rule.copy(name = _state.value.renameDraft.ifBlank { rule.name })
        _state.value = _state.value.copy(rules = listOf(updated), overlay = Overlay.NONE)
        persistRule(updated)
    }

    fun duplicateRule() {
        val rule = _state.value.rules.firstOrNull() ?: return
        _state.value = _state.value.copy(rules = listOf(rule, rule.copy(id = 2L, name = "${rule.name} copy")), overlay = Overlay.NONE, transientMessage = "Rule duplicated")
    }

    fun deleteRule() {
        _state.value = _state.value.copy(rules = emptyList(), overlay = Overlay.NONE, auditState = "010_home_empty")
        viewModelScope.launch { getApplication<Application>().signalDataStore.edit { it[Keys.HasRule] = false } }
    }

    fun setSetting(label: String, value: String) {
        _state.value = _state.value.copy(settings = _state.value.settings + (label to value), overlay = Overlay.NONE)
        viewModelScope.launch {
            getApplication<Application>().signalDataStore.edit { it[settingKey(label)] = value }
        }
    }

    fun addTestHistory() {
        _state.value = _state.value.copy(history = listOf(HistoryRecord()), rootTab = RootTab.HISTORY, route = Route.ROOT, auditState = "071_history_populated_test_notification")
    }

    fun clearTransient() { _state.value = _state.value.copy(transientMessage = null) }
    fun showMessage(message: String) { _state.value = _state.value.copy(transientMessage = message) }

    private fun persistRule(rule: SignalRule) {
        viewModelScope.launch {
            getApplication<Application>().signalDataStore.edit {
                it[Keys.HasRule] = true
                it[Keys.RuleEnabled] = rule.enabled
                it[Keys.RuleName] = rule.name
                it[Keys.RuleApp] = rule.app
                it[Keys.RulePhrase] = rule.phrase
                it[Keys.RuleAction] = rule.action
            }
        }
    }

    fun applyAuditState(id: String) {
        if (!BuildConfig.DEBUG || id.isBlank()) return
        auditOverride = id
        val base = _state.value.copy(auditState = id, overlay = Overlay.NONE, transientMessage = null)
        _state.value = when {
            id.startsWith("002_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 0)
            id.startsWith("004_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 1)
            id.startsWith("006_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 2)
            id.startsWith("010_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = emptyList())
            id.startsWith("011_") || id.startsWith("077_") || id.startsWith("078_") || id.startsWith("079_") || id.startsWith("080_") || id.startsWith("081_") -> base.copy(route = Route.ROOT, rootTab = RootTab.EXPLORE)
            id.startsWith("013_") || id.startsWith("014_") || id.startsWith("015_") || id.startsWith("071_") || id.startsWith("075_") || id.startsWith("076_") -> base.copy(
                route = Route.ROOT,
                rootTab = RootTab.HISTORY,
                history = if (id.startsWith("071_")) listOf(HistoryRecord()) else emptyList(),
                historySearch = if (id.startsWith("015_")) "nothing here" else "",
                historyFilter = when { id.startsWith("075_") -> "Rule-triggered"; id.startsWith("076_") -> "Dismissed"; else -> "All" },
            )
            id.startsWith("016_") || id.startsWith("017_") || id.startsWith("018_") || id.startsWith("019_") || id.startsWith("020_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS)
            id.startsWith("021_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.MUTE_MODE)
            id.startsWith("022_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.MUTE_IMPORTANCE)
            id.startsWith("023_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.HISTORY_STORAGE)
            id.startsWith("024_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.HISTORY_RETENTION)
            id.startsWith("025_") -> base.copy(route = Route.SHORTCUT_EDITOR)
            id.startsWith("027_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.THEME)
            id.startsWith("028_") -> base.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS, overlay = Overlay.LANGUAGE)
            id.startsWith("029_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "New rule"))
            id.startsWith("030_") || id.startsWith("031_") -> base.copy(route = Route.APP_SELECTOR, appSearch = if (id.startsWith("031_")) "Signal" else "")
            id.startsWith("032_") -> base.copy(route = Route.RULE_BUILDER, overlay = Overlay.CONDITION_TYPE)
            id.startsWith("033_") || id.startsWith("034_") || id.startsWith("041_") || id.startsWith("045_") || id.startsWith("046_") || id.startsWith("047_") || id.startsWith("048_") -> base.copy(
                route = Route.PHRASE_EDITOR,
                phraseDraft = if (id.startsWith("041_") || id.startsWith("045_") || id.startsWith("046_") || id.startsWith("047_") || id.startsWith("048_")) "audit phrase" else "",
            )
            id.startsWith("036_") || id.startsWith("037_") || id.startsWith("038_") -> base.copy(route = Route.PHRASE_EDITOR, overlay = Overlay.CONDITION_EXTRAS)
            id.startsWith("039_") -> base.copy(route = Route.FILTER_GROUP)
            id.startsWith("040_") -> base.copy(route = Route.FILTER_GROUP, overlay = Overlay.FILTER_OPERATOR)
            id.startsWith("044_") -> base.copy(route = Route.RULE_BUILDER, overlay = Overlay.ADD_FILTER)
            id.startsWith("049_") || id.startsWith("050_") || id.startsWith("051_") || id.startsWith("052_") || id.startsWith("053_") || id.startsWith("054_") || id.startsWith("055_") || id.startsWith("056_") || id.startsWith("057_") || id.startsWith("058_") || id.startsWith("061_") -> base.copy(route = Route.ACTION_SELECTOR, draft = if (id.startsWith("061_")) base.draft.copy(action = "Mute") else base.draft)
            id.startsWith("059_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "New rule", phrase = "anything", action = "nothing"), transientMessage = null)
            id.startsWith("035_") || id.startsWith("042_") || id.startsWith("060_") || id.startsWith("062_") || id.startsWith("043_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "New rule", app = "Signal Rules", phrase = "audit phrase", action = if (id.startsWith("062_")) "Mute" else "nothing"))
            id.startsWith("063_") || id.startsWith("085_") || id.startsWith("087_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute", enabled = true)))
            id.startsWith("064_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute", enabled = false)))
            id.startsWith("065_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute")), overlay = Overlay.RULE_MORE)
            id.startsWith("066_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute")), overlay = Overlay.ENABLE_FOR)
            id.startsWith("067_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute")), overlay = Overlay.PRIORITY)
            id.startsWith("068_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute")), overlay = Overlay.FOLDER)
            id.startsWith("069_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(action = "Mute")), overlay = Overlay.RENAME, renameDraft = "Test rule")
            id.startsWith("070_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(action = "Mute"), rules = listOf(SignalRule(action = "Mute")))
            id.startsWith("072_") -> base.copy(route = Route.ROOT, rootTab = RootTab.HISTORY, history = listOf(HistoryRecord()), overlay = Overlay.HISTORY_ITEM)
            id.startsWith("073_") || id.startsWith("074_") -> base.copy(route = Route.HISTORY_ACTIVITY, historyActivityTab = if (id.startsWith("074_")) "Changes" else "Rules")
            id.startsWith("082_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "Flashlight suggestion", app = "Messages", phrase = "urgent", action = "Flashlight"))
            else -> base
        }
    }
}
