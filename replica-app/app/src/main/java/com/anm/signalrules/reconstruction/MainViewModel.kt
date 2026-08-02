package com.anm.signalrules.reconstruction

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anm.signalrules.reconstruction.audit.auditStateFor
import com.anm.signalrules.reconstruction.data.SignalPreferences
import com.anm.signalrules.reconstruction.data.SignalDatabase
import com.anm.signalrules.reconstruction.data.toHistoryRecord
import com.anm.signalrules.reconstruction.data.decodeRules
import com.anm.signalrules.reconstruction.data.encodeRules
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.deriveRuleDraft
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.RootTab
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.model.applyToRule
import com.anm.signalrules.reconstruction.model.duplicateRule as duplicateRuleIn
import com.anm.signalrules.reconstruction.model.nextRuleId as nextRuleIdFor
import com.anm.signalrules.reconstruction.model.removeRule
import com.anm.signalrules.reconstruction.model.upsertRule
import com.anm.signalrules.reconstruction.model.UNSAVED_RULE_ID
import com.anm.signalrules.reconstruction.model.UiState
import com.anm.signalrules.reconstruction.model.defaultSettings
import com.anm.signalrules.reconstruction.runtime.ListenerHealth
import com.anm.signalrules.reconstruction.runtime.HistoryRetentionSettings
import com.anm.signalrules.reconstruction.runtime.SignalNotificationListener
import com.anm.signalrules.reconstruction.model.validateRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/** Shown once when an unreadable preferences file was replaced with defaults. */
const val SETTINGS_RESET_MESSAGE = "Saved settings could not be read and were reset."

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var auditOverride: String? = null

    @Volatile
    private var recoveredFromCorruption = false

    private val dataStore: DataStore<Preferences> = SignalPreferences.create(
        scope = viewModelScope,
        produceFile = {
            SignalPreferences.resolveStoreFile(
                noBackupFilesDir = application.noBackupFilesDir,
                legacyFile = application.preferencesDataStoreFile(SignalPreferences.STORE_NAME),
            )
        },
        onCorruption = { recoveredFromCorruption = true },
    )
    private val historyDatabase = SignalDatabase.create(application)

    private object Keys {
        val Onboarding = booleanPreferencesKey("onboarding_complete")
        val Rules = stringPreferencesKey("rules_v1")

        // Superseded by [Rules]; read once so an upgrade keeps the user's rule.
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
            // An unreadable store must degrade to defaults, never propagate out of this
            // coroutine: an uncaught throw here kills the process on every launch.
            val values = dataStore.data
                .catch { emit(emptyPreferences()) }
                .first()
            val legacyRule = if (values[Keys.HasRule] == true) {
                SignalRule(
                    id = 1L,
                    name = values[Keys.RuleName] ?: "Test rule",
                    app = values[Keys.RuleApp] ?: "any app",
                    phrase = values[Keys.RulePhrase] ?: "anything",
                    action = values[Keys.RuleAction] ?: "Mute",
                    enabled = values[Keys.RuleEnabled] ?: true,
                )
            } else {
                null
            }
            val rule = decodeRules(values[Keys.Rules]) ?: listOfNotNull(legacyRule)
            val settings = defaultSettings.mapValues { (label, default) -> values[settingKey(label)] ?: default }
            HistoryRetentionSettings.set(settings["History retention"])
            _state.value = _state.value.copy(
                route = if (values[Keys.Onboarding] == true) Route.ROOT else Route.ONBOARDING,
                auditState = if (values[Keys.Onboarding] == true) "010_home_empty" else "002_welcome_default",
                rules = rule,
                settings = settings,
                transientMessage = if (recoveredFromCorruption) SETTINGS_RESET_MESSAGE else null,
            )
            auditOverride?.let(::applyAuditState)
        }
        viewModelScope.launch {
            historyDatabase.notificationDao().observeRecent().collect { records ->
                _state.value = _state.value.copy(history = records.map { it.toHistoryRecord() })
            }
        }
    }

    /**
     * Persistence must never take the process down. A failed write costs the user one
     * unsaved change; an uncaught IO error costs them the app.
     */
    private fun editPreferences(transform: (MutablePreferences) -> Unit) {
        viewModelScope.launch {
            try {
                dataStore.edit(transform)
            } catch (error: IOException) {
                _state.value = _state.value.copy(transientMessage = "Could not save to storage.")
            }
        }
    }

    fun completeOnboarding() {
        _state.value = _state.value.copy(route = Route.ROOT, rootTab = RootTab.RULES, overlay = Overlay.NONE)
        editPreferences { it[Keys.Onboarding] = true }
    }

    /**
     * Re-reads platform capability state.
     *
     * This runs on every resume regardless of route. The previous revision returned early
     * unless onboarding was on screen, so notification access revoked after setup was
     * invisible: the app kept presenting a working rule list while the listener was unbound.
     */
    fun refreshCapabilities() {
        val app = getApplication<Application>()
        val listenerGranted = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        }.getOrDefault(false)

        if (listenerGranted) {
            // Cheap and idempotent; recovers a listener the platform unbound while we were away.
            SignalNotificationListener.requestRebindIfPossible(app)
        } else {
            ListenerHealth.onAccessRevoked()
        }
        _state.value = _state.value.copy(listenerAccessGranted = listenerGranted)

        if (auditOverride != null || _state.value.route != Route.ONBOARDING) return
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val batteryGranted = runCatching {
            (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(app.packageName)
        }.getOrDefault(false)
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
    fun navigate(route: Route) { _state.value = _state.value.copy(route = route, overlay = Overlay.NONE, transientMessage = null, phraseInputVisible = false) }
    fun showOverlay(overlay: Overlay) { _state.value = _state.value.copy(overlay = overlay) }
    fun dismissOverlay() { _state.value = _state.value.copy(overlay = Overlay.NONE) }
    fun updateDraft(transform: (SignalRule) -> SignalRule) { _state.value = _state.value.copy(draft = transform(_state.value.draft), validationError = null) }
    fun setPhraseDraft(text: String) { _state.value = _state.value.copy(phraseDraft = text) }
    fun commitPhrase() {
        val phrase = _state.value.phraseDraft.ifBlank { "anything" }
        _state.value = _state.value.copy(route = Route.RULE_BUILDER, draft = _state.value.draft.copy(phrase = phrase), overlay = Overlay.NONE, phraseInputVisible = false)
    }
    fun setAppSearch(text: String) { _state.value = _state.value.copy(appSearch = text) }
    fun setHistorySearch(text: String) { _state.value = _state.value.copy(historySearch = text) }
    fun openHistorySearch() { _state.value = _state.value.copy(historySearchActive = true) }
    fun closeHistorySearch() { _state.value = _state.value.copy(historySearchActive = false, historySearch = "") }
    fun showPhraseInput() { _state.value = _state.value.copy(phraseInputVisible = true) }
    fun hidePhraseInput() { _state.value = _state.value.copy(phraseInputVisible = false) }
    fun setHistoryFilter(filter: String) { _state.value = _state.value.copy(historyFilter = filter) }
    fun setHistoryActivityTab(tab: String) { _state.value = _state.value.copy(historyActivityTab = tab) }
    fun showHistoryOverlay(historyId: Long) {
        _state.value = _state.value.copy(selectedHistoryId = historyId, overlay = Overlay.HISTORY_ITEM)
    }
    fun setRenameDraft(text: String) { _state.value = _state.value.copy(renameDraft = text) }
    fun setFolderDraft(text: String) { _state.value = _state.value.copy(folderDraft = text) }

    fun newRule() {
        _state.value = _state.value.copy(
            route = Route.RULE_BUILDER,
            overlay = Overlay.NONE,
            draft = SignalRule(id = UNSAVED_RULE_ID, name = "New rule"),
            validationError = null,
        )
    }

    fun createRuleFromSelectedHistory() {
        val record = _state.value.history.firstOrNull { it.id == _state.value.selectedHistoryId }
        if (record == null) {
            newRule()
            _state.value = _state.value.copy(transientMessage = "That history entry is no longer available.")
            return
        }
        val derived = deriveRuleDraft(record)
        _state.value = _state.value.copy(
            route = Route.RULE_BUILDER,
            overlay = Overlay.NONE,
            selectedHistoryId = null,
            draft = SignalRule(
                id = UNSAVED_RULE_ID,
                name = "Rule from ${record.app}",
                app = record.app,
                appPackageName = derived.appPackageName,
                phrase = derived.phrase,
            ),
            phraseDraft = if (derived.phrase == "anything") "" else derived.phrase,
            validationError = null,
            transientMessage = derived.provenanceMessage,
        )
    }

    /** Opens a rule-scoped overlay, recording which rule the following action applies to. */
    fun showRuleOverlay(overlay: Overlay, ruleId: Long) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId }
        _state.value = _state.value.copy(
            overlay = overlay,
            selectedRuleId = ruleId,
            renameDraft = rule?.name.orEmpty(),
            folderDraft = rule?.folder.orEmpty(),
        )
    }

    fun editRule(rule: SignalRule) {
        _state.value = _state.value.copy(route = Route.RULE_BUILDER, overlay = Overlay.NONE, draft = rule, selectedRuleId = rule.id)
    }

    /** Applies [transform] to exactly the addressed rule and leaves every other rule untouched. */
    private fun mutateRule(ruleId: Long?, transform: (SignalRule) -> SignalRule): SignalRule? {
        val current = _state.value.rules
        if (current.none { it.id == ruleId }) return null
        val updated = applyToRule(current, ruleId, transform)
        _state.value = _state.value.copy(rules = updated)
        persistRules()
        return updated.firstOrNull { it.id == ruleId }
    }

    fun saveRule() {
        val current = _state.value.draft
        val error = validateRule(current)
        if (error != null) {
            _state.value = _state.value.copy(validationError = error, transientMessage = error)
            return
        }
        val existing = _state.value.rules
        val isNew = current.id == UNSAVED_RULE_ID || existing.none { it.id == current.id }
        val draft = current.copy(
            id = if (isNew) nextRuleIdFor(existing) else current.id,
            name = current.name.ifBlank { "Rule ${existing.size + 1}" },
            enabled = true,
        )
        val rules = upsertRule(existing, draft)
        _state.value = _state.value.copy(
            route = Route.ROOT,
            rootTab = RootTab.RULES,
            rules = rules,
            selectedRuleId = draft.id,
            validationError = null,
            transientMessage = "Rule saved",
        )
        persistRules()
    }

    fun toggleRule(ruleId: Long) {
        mutateRule(ruleId) { it.copy(enabled = !it.enabled) }
    }

    fun renameRule() {
        val ruleId = _state.value.selectedRuleId
        mutateRule(ruleId) { it.copy(name = _state.value.renameDraft.ifBlank { it.name }) }
        _state.value = _state.value.copy(overlay = Overlay.NONE)
    }

    fun setRulePriority(priority: String) {
        mutateRule(_state.value.selectedRuleId) { it.copy(priority = priority) }
        _state.value = _state.value.copy(overlay = Overlay.NONE)
    }

    fun setMatchType(matchType: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(matchType = matchType), overlay = Overlay.NONE)
    }

    fun setFilterOperator(operator: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(filterOperator = operator), overlay = Overlay.NONE)
    }

    /** Extras are a set in practice; selecting an already-chosen one removes it. */
    fun toggleExtraFilter(extra: String) {
        val current = _state.value.draft.extras
        val updated = if (current.contains(extra)) current - extra else current + extra
        _state.value = _state.value.copy(draft = _state.value.draft.copy(extras = updated), overlay = Overlay.NONE)
    }

    fun setEnabledFor(duration: String) {
        mutateRule(_state.value.selectedRuleId) { it.copy(enabledFor = duration, enabled = true) }
        _state.value = _state.value.copy(
            overlay = Overlay.NONE,
            transientMessage = "Rule enabled for $duration",
        )
    }

    fun setRuleFolder(folder: String) {
        mutateRule(_state.value.selectedRuleId) { it.copy(folder = folder.ifBlank { "No folder" }) }
        _state.value = _state.value.copy(overlay = Overlay.NONE)
    }

    fun duplicateRule() {
        val existing = _state.value.rules
        val rules = duplicateRuleIn(existing, _state.value.selectedRuleId)
        if (rules.size == existing.size) return
        _state.value = _state.value.copy(
            rules = rules,
            overlay = Overlay.NONE,
            selectedRuleId = rules.last().id,
            transientMessage = "Rule duplicated",
        )
        persistRules()
    }

    fun deleteRule() {
        val ruleId = _state.value.selectedRuleId
        val remaining = removeRule(_state.value.rules, ruleId)
        if (remaining.size == _state.value.rules.size) {
            _state.value = _state.value.copy(overlay = Overlay.NONE)
            return
        }
        _state.value = _state.value.copy(
            rules = remaining,
            overlay = Overlay.NONE,
            selectedRuleId = null,
        )
        persistRules()
    }

    fun deleteAllRules() {
        if (_state.value.rules.isEmpty()) {
            _state.value = _state.value.copy(transientMessage = "There are no rules to delete.")
            return
        }
        val removed = _state.value.rules.size
        _state.value = _state.value.copy(
            rules = emptyList(),
            selectedRuleId = null,
            overlay = Overlay.NONE,
            transientMessage = if (removed == 1) "Deleted 1 rule" else "Deleted $removed rules",
        )
        persistRules()
    }

    fun setSetting(label: String, value: String) {
        if (label == "History retention") HistoryRetentionSettings.set(value)
        _state.value = _state.value.copy(settings = _state.value.settings + (label to value), overlay = Overlay.NONE)
        editPreferences { it[settingKey(label)] = value }
    }

    fun addTestHistory() {
        _state.value = _state.value.copy(history = listOf(HistoryRecord()), rootTab = RootTab.HISTORY, route = Route.ROOT)
    }

    fun clearTransient() { _state.value = _state.value.copy(transientMessage = null) }
    fun showMessage(message: String) { _state.value = _state.value.copy(transientMessage = message) }

    private fun persistRules() {
        val encoded = encodeRules(_state.value.rules)
        editPreferences {
            it[Keys.Rules] = encoded
            // The legacy single-rule keys are no longer read; clear them so an older
            // build's data cannot resurrect a rule the user deleted here.
            it.remove(Keys.HasRule)
            it.remove(Keys.RuleEnabled)
            it.remove(Keys.RuleName)
            it.remove(Keys.RuleApp)
            it.remove(Keys.RulePhrase)
            it.remove(Keys.RuleAction)
        }
    }

    fun applyAuditState(id: String) {
        if (id.isBlank()) return
        val base = _state.value.copy(auditState = id, overlay = Overlay.NONE, transientMessage = null)
        // Release builds link a no-op resolver, so the QA override cannot exist there.
        val resolved = auditStateFor(base, id) ?: return
        auditOverride = id
        _state.value = resolved
    }

    override fun onCleared() {
        historyDatabase.close()
        super.onCleared()
    }
}
