package com.sysadmindoc.nono

import android.app.Application
import android.net.Uri
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
import com.sysadmindoc.nono.audit.auditStateFor
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.data.ConflictResolution
import com.sysadmindoc.nono.data.ImportRejection
import com.sysadmindoc.nono.data.RuleImportResult
import com.sysadmindoc.nono.data.RuleTransfer
import com.sysadmindoc.nono.data.RuleTransferLimits
import com.sysadmindoc.nono.data.readBoundedUtf8
import com.sysadmindoc.nono.data.PseudonymKeyStore
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.data.toMetrics
import com.sysadmindoc.nono.data.toHistoryRecord
import com.sysadmindoc.nono.data.HistoryExport
import com.sysadmindoc.nono.data.decodeMatchedRuleIds
import com.sysadmindoc.nono.data.decodeRules
import com.sysadmindoc.nono.data.encodeRules
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.HistoryLoadState
import com.sysadmindoc.nono.model.HistoryQuery
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.deriveRuleDraft
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.applyToRule
import com.sysadmindoc.nono.model.duplicateRule as duplicateRuleIn
import com.sysadmindoc.nono.model.nextRuleId as nextRuleIdFor
import com.sysadmindoc.nono.model.normalizeMatchType
import com.sysadmindoc.nono.model.removeRule
import com.sysadmindoc.nono.model.resolveSavedRule
import com.sysadmindoc.nono.model.upsertRule
import com.sysadmindoc.nono.model.UNSAVED_RULE_ID
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.defaultSettings
import com.sysadmindoc.nono.runtime.ListenerHealth
import com.sysadmindoc.nono.runtime.CaptureGate
import com.sysadmindoc.nono.runtime.HistoryRetentionSettings
import com.sysadmindoc.nono.runtime.HistoryStorageSettings
import com.sysadmindoc.nono.runtime.applyListenerSettings
import com.sysadmindoc.nono.runtime.historyStorage
import com.sysadmindoc.nono.runtime.listenerSettings
import com.sysadmindoc.nono.runtime.retentionCutoffEpochMillis
import com.sysadmindoc.nono.runtime.SignalNotificationListener
import com.sysadmindoc.nono.model.validateRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Shown once when an unreadable preferences file was replaced with defaults. */
const val SETTINGS_RESET_MESSAGE = "Saved settings could not be read and were reset."

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var auditOverride: String? = null
    private val historyRetry = MutableStateFlow(0)
    private var pendingExportPayload: String? = null
    private var pendingExportIsHistory = false

    /** Rows in the pending history CSV, so the confirmation can say what was written. */
    private var pendingExportRowCount = 0
    private var pendingImportEncoded: String? = null
    private var pendingImportRules: List<SignalRule>? = null

    // Shared with the notification listener, which evaluates rules as notifications arrive.
    private val dataStore: DataStore<Preferences> = SignalPreferences.get(application)
    private val historyDatabase = SignalDatabase.get(application)

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

    private fun settingKey(label: String) = SignalPreferences.settingKey(label)

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
            val stored = defaultSettings.mapValues { (label, default) -> values[settingKey(label)] ?: default }
            // An older build could persist a storage label this one does not offer. Resolving it
            // here means the dialog shows the choice actually in force rather than nothing.
            val settings = stored + (
                SignalPreferences.HISTORY_STORAGE_SETTING to
                    historyStorage(stored[SignalPreferences.HISTORY_STORAGE_SETTING]).label
                )
            applyListenerSettings(listenerSettings(values))
            _state.value = _state.value.copy(
                route = if (values[Keys.Onboarding] == true) Route.ROOT else Route.ONBOARDING,
                auditState = if (values[Keys.Onboarding] == true) "010_home_empty" else "002_welcome_default",
                rules = rule,
                settings = settings,
                transientMessage = if (SignalPreferences.consumeCorruptionRecovery()) SETTINGS_RESET_MESSAGE else null,
            )
            auditOverride?.let(::applyAuditState)
        }
        viewModelScope.launch {
            // The listener does this too, but it may never have run on this install, and rows an
            // older build wrote still hold the identifiers the posting apps chose.
            runCatching {
                historyDatabase.notificationDao().pseudonymizeStoredIdentifiers(
                    PseudonymKeyStore.get(application.noBackupFilesDir),
                )
            }
        }
        viewModelScope.launch {
            CaptureGate.load(application)
            CaptureGate.paused.collect { paused ->
                _state.value = _state.value.copy(capturePaused = paused)
            }
        }
        viewModelScope.launch {
            historyDatabase.notificationDao().observeMatchedRuleIds().collect { encoded ->
                val counts = encoded.flatMap(::decodeMatchedRuleIds)
                    .groupingBy { it }
                    .eachCount()
                _state.value = _state.value.copy(ruleMatchCounts = counts)
            }
        }
        viewModelScope.launch {
            historyDatabase.notificationDao().observeIngestionDiagnostics().collect { diagnostics ->
                ListenerHealth.restoreDurableIngestionMetrics(diagnostics?.toMetrics() ?: com.sysadmindoc.nono.runtime.IngestionMetrics())
            }
        }
        viewModelScope.launch {
            combine(
                _state
                    .map {
                        HistoryQuery(
                            search = it.historySearch,
                            filter = it.historyFilter,
                            packageName = it.historyPackageFilter,
                            channelId = it.historyChannelFilter,
                            contentState = it.historyContentStateFilter,
                            groupKey = it.historyGroupFilter,
                            groupSummary = it.historyGroupSummaryOnly.takeIf { only -> only },
                            importance = it.historyImportanceFilter,
                            conversation = it.historyConversationFilter,
                        )
                    }
                    .distinctUntilChanged(),
                historyRetry,
            ) { query, _ -> query }
                .flatMapLatest { query ->
                    historyDatabase.notificationDao().observeHistory(
                        query = query.search,
                        filter = query.filter,
                        packageName = query.packageName,
                        channelId = query.channelId,
                        contentState = query.contentState?.name,
                        groupKey = query.groupKey,
                        groupSummary = query.groupSummary,
                        importance = query.importance,
                        conversation = query.conversation,
                        fromEpochMillis = query.fromEpochMillis,
                        limit = query.limit,
                    ).map { records ->
                        HistoryLoadResult(
                            state = HistoryLoadState.READY,
                            records = records.map { it.toHistoryRecord() },
                        )
                    }.onStart {
                        emit(HistoryLoadResult(state = HistoryLoadState.LOADING))
                    }.catch {
                        emit(
                            HistoryLoadResult(
                                state = HistoryLoadState.ERROR,
                                error = "Notification history could not be read.",
                            ),
                        )
                    }
                }
                .collect { result ->
                    if (auditOverride != null) return@collect
                    _state.value = when (result.state) {
                        HistoryLoadState.LOADING -> _state.value.copy(
                            historyLoadState = HistoryLoadState.LOADING,
                            historyError = null,
                        )
                        HistoryLoadState.READY -> _state.value.copy(
                            history = result.records,
                            historyLoadState = HistoryLoadState.READY,
                            historyError = null,
                        )
                        HistoryLoadState.ERROR -> _state.value.copy(
                            historyLoadState = HistoryLoadState.ERROR,
                            historyError = result.error,
                        )
                    }
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
        if (auditOverride != null) return
        val app = getApplication<Application>()
        val listenerGranted = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        }.getOrDefault(false)

        when (ListenerHealth.capabilityAction(listenerGranted, ListenerHealth.connection.value)) {
            // The platform documents requestRebind for the disconnected window only.
            ListenerHealth.CapabilityAction.REQUEST_REBIND -> SignalNotificationListener.requestRebindIfPossible(app)
            ListenerHealth.CapabilityAction.MARK_REVOKED -> ListenerHealth.onAccessRevoked()
            ListenerHealth.CapabilityAction.NONE -> Unit
        }
        _state.value = _state.value.copy(listenerAccessGranted = listenerGranted)

        if (auditOverride != null || _state.value.route != Route.ONBOARDING) return
        // Notification-listener access is the only capability this build actually consumes.
        // It does not post notifications, execute actions, or require an exemption from Doze.
        val step = if (listenerGranted) 1 else 0
        _state.value = _state.value.copy(onboardingStep = step)
        if (step == 1) completeOnboarding()
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
    fun clearHistoryMetadataFilters() {
        _state.value = _state.value.copy(
            historyPackageFilter = null,
            historyChannelFilter = null,
            historyGroupFilter = null,
            historyContentStateFilter = null,
            historyGroupSummaryOnly = false,
            historyImportanceFilter = null,
            historyConversationFilter = null,
            overlay = Overlay.NONE,
        )
    }
    fun setHistoryPackageFilter(value: String?) {
        _state.value = _state.value.copy(historyPackageFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryChannelFilter(value: String?) {
        _state.value = _state.value.copy(historyChannelFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryGroupFilter(value: String?) {
        _state.value = _state.value.copy(historyGroupFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryContentStateFilter(value: NotificationContentState?) {
        _state.value = _state.value.copy(historyContentStateFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryGroupSummaryOnly(enabled: Boolean) {
        _state.value = _state.value.copy(historyGroupSummaryOnly = enabled, overlay = Overlay.NONE)
    }
    fun setHistoryImportanceFilter(value: Int?) {
        _state.value = _state.value.copy(historyImportanceFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryConversationFilter(value: Boolean?) {
        _state.value = _state.value.copy(historyConversationFilter = value, overlay = Overlay.NONE)
    }
    fun setCapturePaused(paused: Boolean) {
        CaptureGate.setPaused(getApplication(), paused)
    }
    fun beginExport() {
        _state.value = _state.value.copy(overlay = Overlay.TRANSFER_EXPORT_PASSPHRASE, transientMessage = null)
    }
    fun requestExport(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.value = _state.value.copy(transientMessage = "Enter a passphrase to encrypt the rule file.")
            return
        }
        val rules = _state.value.rules
        _state.value = _state.value.copy(overlay = Overlay.NONE, transientMessage = "Preparing encrypted rule export…")
        viewModelScope.launch(Dispatchers.Default) {
            val chars = passphrase.toCharArray()
            val result = runCatching { RuleTransfer.exportRules(rules, chars) }
                .onFailure { chars.fill('\u0000') }
            withContext(Dispatchers.Main.immediate) {
                result.fold(
                    onSuccess = {
                        pendingExportPayload = it
                        _state.value = _state.value.copy(
                            transferExportRequest = _state.value.transferExportRequest + 1,
                            transferExportIsHistory = false,
                            transientMessage = null,
                        )
                    },
                    onFailure = { _state.value = _state.value.copy(transientMessage = "Could not prepare the encrypted rule file.") },
                )
            }
        }
    }
    fun writeExport(uri: Uri) {
        val payload = pendingExportPayload
        val history = pendingExportIsHistory
        val rows = pendingExportRowCount
        pendingExportPayload = null
        pendingExportIsHistory = false
        pendingExportRowCount = 0
        if (payload == null) {
            _state.value = _state.value.copy(transientMessage = "The export expired; try again.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("The selected location could not be opened.")
            }
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(
                    transientMessage = result.fold(
                        {
                            if (history) {
                                // Says what the file holds, so nobody has to guess whether a
                                // filter or a page limit was in force.
                                "Exported all $rows retained records. No notification content was written."
                            } else {
                                "Encrypted rules exported. Notification history was not included."
                            }
                        },
                        { "Export failed; nothing on this device was changed." },
                    ),
                )
            }
        }
    }
    /**
     * Prepares a CSV of every retained record and asks the UI for a destination.
     *
     * Reads its own query rather than the history screen's list, which carries the user's filters
     * and a page limit: exporting that wrote at most one page and called it the history. Shares
     * the export plumbing with the rule file; the payload is decided here so the two cannot be
     * confused. Only stored metadata is written, never notification content.
     */
    fun beginHistoryExport() {
        _state.value = _state.value.copy(transientMessage = "Preparing the history export…")
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                historyDatabase.notificationDao().readAllForExport().map { it.toHistoryRecord() }
            }
            withContext(Dispatchers.Main.immediate) {
                result.fold(
                    onSuccess = { records ->
                        if (records.isEmpty()) {
                            _state.value = _state.value.copy(transientMessage = "There is no history to export yet.")
                            return@fold
                        }
                        pendingExportPayload = HistoryExport.toCsv(records)
                        pendingExportIsHistory = true
                        pendingExportRowCount = records.size
                        _state.value = _state.value.copy(
                            transferExportRequest = _state.value.transferExportRequest + 1,
                            transferExportIsHistory = true,
                            transientMessage = null,
                        )
                    },
                    onFailure = {
                        _state.value = _state.value.copy(transientMessage = "History could not be read for export.")
                    },
                )
            }
        }
    }

    fun exportCancelled() {
        pendingExportPayload = null
        pendingExportIsHistory = false
        pendingExportRowCount = 0
        _state.value = _state.value.copy(transientMessage = "Export cancelled.")
    }
    /**
     * Reads and parses a chosen rule file entirely off the main thread.
     *
     * Both the size the picker declares and the bytes actually delivered are checked: a document
     * provider can report one length and stream another, and only the second one is what has to
     * fit in memory.
     */
    fun beginImport(uri: Uri) {
        _state.value = _state.value.copy(transientMessage = "Reading rule file…")
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val declared = declaredSize(uri)
            if (declared != null && declared > RuleTransferLimits.MAX_ENCODED_BYTES) {
                withContext(Dispatchers.Main.immediate) {
                    _state.value = _state.value.copy(transientMessage = ImportRejection.TOO_LARGE.message)
                }
                return@launch
            }
            val encoded = runCatching {
                app.contentResolver.openInputStream(uri)?.use { readBoundedUtf8(it) }
            }.getOrNull()
            if (encoded == null) {
                withContext(Dispatchers.Main.immediate) {
                    _state.value = _state.value.copy(
                        transientMessage = if (declared == null) {
                            "Import failed; that file could not be read."
                        } else {
                            ImportRejection.TOO_LARGE.message
                        },
                    )
                }
                return@launch
            }
            // Parsing, base64 and any key derivation stay on this dispatcher.
            val result = RuleTransfer.importRules(encoded)
            withContext(Dispatchers.Main.immediate) { applyImportResult(encoded, result) }
        }
    }

    /** The size the document provider declares, or null when it will not say. */
    private fun declaredSize(uri: Uri): Long? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
            }
    }.getOrNull()

    private fun applyImportResult(encoded: String, result: RuleImportResult) {
        when (result) {
            is RuleImportResult.NeedsPassphrase -> {
                pendingImportEncoded = encoded
                _state.value = _state.value.copy(overlay = Overlay.TRANSFER_IMPORT_PASSPHRASE, transientMessage = null)
            }
            is RuleImportResult.Success -> showImportPreview(result.rules)
            RuleImportResult.Cancelled -> cancelTransfer()
            is RuleImportResult.InvalidFile ->
                _state.value = _state.value.copy(transientMessage = result.rejection.message)
        }
    }
    fun submitImportPassphrase(passphrase: String) {
        val encoded = pendingImportEncoded ?: run {
            cancelTransfer()
            return
        }
        pendingImportEncoded = null
        viewModelScope.launch(Dispatchers.Default) {
            val chars = passphrase.toCharArray()
            val result = try {
                RuleTransfer.importRules(encoded, chars)
            } finally {
                chars.fill('\u0000')
            }
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is RuleImportResult.Success -> showImportPreview(result.rules)
                    RuleImportResult.Cancelled -> cancelTransfer()
                    RuleImportResult.NeedsPassphrase -> _state.value = _state.value.copy(
                        overlay = Overlay.NONE,
                        transientMessage = ImportRejection.WRONG_PASSPHRASE.message,
                    )
                    is RuleImportResult.InvalidFile -> _state.value = _state.value.copy(
                        overlay = Overlay.NONE,
                        transientMessage = result.rejection.message,
                    )
                }
            }
        }
    }
    private fun showImportPreview(incoming: List<SignalRule>) {
        val preview = RuleTransfer.preview(_state.value.rules, incoming)
        pendingImportRules = incoming
        _state.value = _state.value.copy(
            overlay = Overlay.TRANSFER_PREVIEW,
            transferAdditions = preview.additions.size,
            transferConflicts = preview.conflicts.size,
            transientMessage = null,
        )
    }
    fun commitImportedRules(resolution: ConflictResolution) {
        val incoming = pendingImportRules ?: run {
            cancelTransfer()
            return
        }
        val current = _state.value.rules
        val preview = RuleTransfer.preview(current, incoming)
        val conflictIds = preview.conflicts.map { it.existing.id }
        val resolutions = conflictIds.associateWith { resolution }
        val updated = RuleTransfer.commit(current, incoming, resolutions) ?: run {
            cancelTransfer()
            return
        }
        pendingImportRules = null
        _state.value = _state.value.copy(
            rules = updated,
            overlay = Overlay.NONE,
            transferAdditions = 0,
            transferConflicts = 0,
            transientMessage = "Imported ${preview.additions.size} new rule(s); notification history was not imported.",
        )
        persistRules()
    }
    fun cancelTransfer() {
        pendingExportPayload = null
        pendingImportEncoded = null
        pendingImportRules = null
        _state.value = _state.value.copy(overlay = Overlay.NONE, transferAdditions = 0, transferConflicts = 0, transientMessage = "Transfer cancelled.")
    }
    fun retryHistory() { historyRetry.value += 1 }
    fun setHistoryActivityTab(tab: String) { _state.value = _state.value.copy(historyActivityTab = tab) }
    fun showHistoryOverlay(historyId: Long) {
        _state.value = _state.value.copy(selectedHistoryId = historyId, overlay = Overlay.HISTORY_ITEM)
    }
    /**
     * Opens the app a history record came from.
     *
     * Uses the launch intent rather than the notification's own PendingIntent, which this build
     * never stores and could not fire without acting on the notification.
     */
    fun openRecordedApp(packageName: String?) {
        val target = packageName?.takeIf { it.isNotBlank() }
        val app = getApplication<Application>()
        val intent = target?.let { app.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            _state.value = _state.value.copy(
                overlay = Overlay.NONE,
                transientMessage = "That app is not installed, or it has no screen to open.",
            )
            return
        }
        val launched = runCatching {
            app.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        _state.value = _state.value.copy(
            overlay = Overlay.NONE,
            transientMessage = if (launched) null else "That app could not be opened.",
        )
    }

    /** Stars or unstars a record. A starred record outlives the retention period. */
    fun setHistoryStarred(historyId: Long, starred: Boolean) {
        viewModelScope.launch {
            runCatching { historyDatabase.notificationDao().setStarred(historyId, starred) }
        }
        _state.value = _state.value.copy(
            overlay = Overlay.NONE,
            transientMessage = if (starred) "Kept until you unstar it." else "No longer kept.",
        )
    }

    /** Feedback for the copy action in the content-hidden explainer. */
    fun reportCommandCopied() {
        _state.value = _state.value.copy(overlay = Overlay.NONE, transientMessage = "Command copied.")
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

    /**
     * Opens a starter suggestion as a new rule.
     *
     * The suggestion is a template, not a saved rule, so it is stripped of any id before it
     * reaches the builder. Editing state is cleared for the same reason.
     */
    fun startRuleFromSuggestion(suggestion: SignalRule) {
        _state.value = _state.value.copy(
            route = Route.RULE_BUILDER,
            overlay = Overlay.NONE,
            draft = suggestion.copy(id = UNSAVED_RULE_ID),
            selectedRuleId = null,
            validationError = null,
            transientMessage = null,
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
        val draft = resolveSavedRule(existing, current)
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
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(matchType = normalizeMatchType(matchType)),
            overlay = Overlay.NONE,
        )
    }

    /**
     * Removes every extra filter from the draft.
     *
     * Extras cannot be added any more, because nothing evaluates them, but an imported rule can
     * still carry them and they stop it matching. Clearing is the repair.
     */
    fun clearExtraFilters() {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(extras = emptyList()))
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
        if (label == SignalPreferences.HISTORY_RETENTION_SETTING) {
            HistoryRetentionSettings.set(value)
            pruneHistory()
        }
        if (label == SignalPreferences.HISTORY_STORAGE_SETTING) {
            // Takes effect on the next capture. Records already stored are the user's to delete.
            HistoryStorageSettings.set(value)
        }
        _state.value = _state.value.copy(settings = _state.value.settings + (label to value), overlay = Overlay.NONE)
        editPreferences { it[settingKey(label)] = value }
    }

    private fun pruneHistory() {
        viewModelScope.launch {
            runCatching {
                historyDatabase.notificationDao().deleteBefore(
                    retentionCutoffEpochMillis(
                        HistoryRetentionSettings.get(),
                        System.currentTimeMillis(),
                    ),
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    historyLoadState = HistoryLoadState.ERROR,
                    historyError = "History retention could not be updated.",
                )
            }
        }
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
        ListenerHealth.reset()
        ListenerHealth.onConnected()
        _state.value = resolved.copy(
            listenerAccessGranted = true,
            historyLoadState = HistoryLoadState.READY,
        )
    }

    override fun onCleared() {
        pendingExportPayload = null
        pendingImportEncoded = null
        pendingImportRules = null
        // The database is shared with the listener and the widget, so it outlives this owner.
        super.onCleared()
    }
}

private data class HistoryLoadResult(
    val state: HistoryLoadState,
    val records: List<HistoryRecord> = emptyList(),
    val error: String? = null,
)
