package com.sysadmindoc.nono

import android.Manifest
import android.app.Application
import android.app.KeyguardManager
import android.os.SystemClock
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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
import com.sysadmindoc.nono.data.BackupFolder
import com.sysadmindoc.nono.data.BoundedReadResult
import com.sysadmindoc.nono.data.DeviceBackupKey
import com.sysadmindoc.nono.data.describeObservedApp
import com.sysadmindoc.nono.data.loadLaunchableApps
import com.sysadmindoc.nono.data.mergeAppCatalog
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
import com.sysadmindoc.nono.data.decodeRuleStore
import com.sysadmindoc.nono.data.decodeRules
import com.sysadmindoc.nono.data.encodeRules
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.HistoryLoadState
import com.sysadmindoc.nono.model.CaptureSelfTestState
import com.sysadmindoc.nono.model.CaptureSelfTestStatus
import com.sysadmindoc.nono.model.ChannelCondition
import com.sysadmindoc.nono.model.HISTORY_PAGE_SIZE
import com.sysadmindoc.nono.model.HistoryQuery
import com.sysadmindoc.nono.model.INSIGHT_DAY_COUNT
import com.sysadmindoc.nono.model.INSIGHT_TOP_APP_LIMIT
import com.sysadmindoc.nono.model.InsightTotals
import com.sysadmindoc.nono.model.LocalInsights
import com.sysadmindoc.nono.model.buildLocalInsights
import com.sysadmindoc.nono.model.insightsStartEpochMillis
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.notificationCategoryCatalog
import com.sysadmindoc.nono.model.deriveRuleDraft
import com.sysadmindoc.nono.model.deleteAllRulesWithUndo
import com.sysadmindoc.nono.model.deleteRuleWithUndo
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.MINUTES_PER_DAY
import com.sysadmindoc.nono.model.MatchField
import com.sysadmindoc.nono.model.MatchMode
import com.sysadmindoc.nono.model.MetadataCondition
import com.sysadmindoc.nono.model.MetadataField
import com.sysadmindoc.nono.model.PhraseCondition
import com.sysadmindoc.nono.model.PhraseQuantifier
import com.sysadmindoc.nono.model.phraseConditionFor
import com.sysadmindoc.nono.model.withPhraseCondition
import com.sysadmindoc.nono.model.withMetadataCondition
import com.sysadmindoc.nono.model.RuleSchedule
import com.sysadmindoc.nono.model.RuleDeletion
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.StatusMessages
import com.sysadmindoc.nono.model.applyToRule
import com.sysadmindoc.nono.model.duplicateRule as duplicateRuleIn
import com.sysadmindoc.nono.model.advanceRuleCounter
import com.sysadmindoc.nono.model.normalizeMatchType
import com.sysadmindoc.nono.model.restoreDeletedRules
import com.sysadmindoc.nono.model.resolveSavedRule
import com.sysadmindoc.nono.model.upsertRule
import com.sysadmindoc.nono.model.UNSAVED_RULE_ID
import com.sysadmindoc.nono.model.UndoableAction
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.model.defaultSettings
import com.sysadmindoc.nono.runtime.APP_LOCK_SETTING
import com.sysadmindoc.nono.runtime.NO_DEVICE_CREDENTIAL
import com.sysadmindoc.nono.runtime.shouldLock
import com.sysadmindoc.nono.runtime.BackupCadence
import com.sysadmindoc.nono.runtime.BackupScheduler
import com.sysadmindoc.nono.runtime.BackupStatus
import com.sysadmindoc.nono.runtime.backupCadence
import com.sysadmindoc.nono.runtime.decodeBackupStatus
import com.sysadmindoc.nono.runtime.ListenerHealth
import com.sysadmindoc.nono.runtime.ListenerActivityLog
import com.sysadmindoc.nono.runtime.CaptureGate
import com.sysadmindoc.nono.runtime.CAPTURE_SELF_TEST_TIMEOUT_MILLIS
import com.sysadmindoc.nono.runtime.CaptureDiagnosticsSnapshot
import com.sysadmindoc.nono.runtime.CaptureSelfTest
import com.sysadmindoc.nono.runtime.CaptureSelfTestOutcome
import com.sysadmindoc.nono.runtime.buildCaptureDiagnosticsReport
import com.sysadmindoc.nono.runtime.captureSelfTestFailureGuidance
import com.sysadmindoc.nono.runtime.combinedIngestionMetrics
import com.sysadmindoc.nono.runtime.HistoryRetentionSettings
import com.sysadmindoc.nono.runtime.HistoryStorageSettings
import com.sysadmindoc.nono.runtime.applyListenerSettings
import com.sysadmindoc.nono.runtime.historyStorage
import com.sysadmindoc.nono.runtime.listenerSettings
import com.sysadmindoc.nono.runtime.retentionCutoffEpochMillis
import com.sysadmindoc.nono.runtime.SignalNotificationListener
import com.sysadmindoc.nono.runtime.SignalWidgetProvider
import com.sysadmindoc.nono.model.validateRule
import com.sysadmindoc.nono.ui.historyMetadataClipboardText
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
import java.util.TimeZone
import kotlinx.coroutines.flow.flowOf

/** Shown once when an unreadable preferences file was replaced with defaults. */
const val SETTINGS_RESET_MESSAGE = "Saved settings could not be read and were reset."

/** Launchers truncate long shortcut labels; keeping it short avoids an ellipsis on the icon. */
private const val SHORTCUT_LABEL_LIMIT = 24

enum class CaptureSelfTestAction {
    NONE,
    REQUEST_NOTIFICATION_PERMISSION,
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var auditOverride: String? = null
    private val historyRetry = MutableStateFlow(0)

    /**
     * The clock and zone the fourteen-day trend is anchored to.
     *
     * Both have to be fixed while the query runs, or the SQL cutoff and the labels describe
     * different fortnights: the cutoff is computed once, and rebuilding the labels from a
     * `TimeZone.getDefault()` that has since changed shifts the window under them. Opening
     * Insights re-reads both, so a session left open past midnight, or across a flight, does not
     * keep charting yesterday in the wrong zone.
     */
    private data class InsightsAnchor(val nowEpochMillis: Long, val zone: TimeZone)

    private val insightsNow = MutableStateFlow(InsightsAnchor(System.currentTimeMillis(), TimeZone.getDefault()))

    /**
     * The app lock's memory, which is deliberately only memory.
     *
     * Nothing writes "unlocked" to disk, so a process that has been killed comes back locked. Both
     * are uptime rather than wall clock, because the wall clock can be moved and this decides
     * whether someone has to prove who they are.
     */
    private var lastUnlockedElapsed: Long? = null
    private var leftForegroundElapsed: Long? = null

    /**
     * Locked until the stored settings say otherwise.
     *
     * The settings are read from disk asynchronously while the Activity is already composing, so
     * anything that decides the lock from the in-memory defaults decides it from "Off" and lets
     * the whole app through before the real answer arrives. Starting locked and unlocking once the
     * read lands is the only ordering that cannot leak.
     */
    private var appLockSettingLoaded = false
    private var pendingExportPayload: String? = null
    private var pendingExportIsHistory = false

    /** Rows in the pending history CSV, so the confirmation can say what was written. */
    private var pendingExportRowCount = 0
    private var pendingImportEncoded: String? = null
    private var pendingImportRules: List<SignalRule>? = null

    /** Held between a delete and the snackbar action that can put it back. */
    private var deletedHistoryRecord: com.sysadmindoc.nono.data.NotificationEntity? = null

    /** One rule or one delete-all batch held only while its snackbar can still undo it. */
    private var deletedRules: RuleDeletion? = null

    /**
     * The next rule id to hand out. Saved with the rules, because history stores the ids that
     * matched it and a reused id would rewrite what an old record says happened.
     */
    private var nextRuleIdCounter = 1L

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
            val ruleStore = decodeRuleStore(values[Keys.Rules])
            val rule = ruleStore?.rules ?: listOfNotNull(legacyRule)
            nextRuleIdCounter = ruleStore?.nextRuleId ?: ((rule.maxOfOrNull { it.id } ?: 0L) + 1L)
            val stored = defaultSettings.mapValues { (label, default) -> values[settingKey(label)] ?: default }
            // An older build could persist a storage label this one does not offer. Resolving it
            // here means the dialog shows the choice actually in force rather than nothing.
            val settings = stored + (
                SignalPreferences.HISTORY_STORAGE_SETTING to
                    historyStorage(stored[SignalPreferences.HISTORY_STORAGE_SETTING]).label
                )
            applyListenerSettings(listenerSettings(values))
            appLockSettingLoaded = true
            _state.value = _state.value.copy(
                route = if (values[Keys.Onboarding] == true) Route.ROOT else Route.ONBOARDING,
                auditState = if (values[Keys.Onboarding] == true) "010_home_empty" else "002_welcome_default",
                rules = rule,
                rulesLoaded = true,
                settings = settings,
            ).withMessage(
                if (SignalPreferences.consumeCorruptionRecovery()) SETTINGS_RESET_MESSAGE else null,
            )
            // The lock was decided before this read landed, from defaults that say "Off". Decide
            // it again now that the stored answer exists.
            refreshAppLock()
            auditOverride?.let(::applyAuditState)
        }
        viewModelScope.launch {
            // The listener does this too, but it may never have run on this install, and rows an
            // older build wrote still hold the identifiers the posting apps chose.
            runCatching {
                val dao = historyDatabase.notificationDao()
                dao.pseudonymizeStoredIdentifiers(
                    PseudonymKeyStore.get(application.noBackupFilesDir),
                )
                dao.discardUnknownCategories(notificationCategoryCatalog.map { it.first })
            }
        }
        viewModelScope.launch {
            // The launcher query is a one-off; the observed packages change as history does.
            val launchable = withContext(Dispatchers.IO) {
                loadLaunchableApps(application.packageManager)
            }
            historyDatabase.notificationDao().observeObservedPackages()
                .catch { emit(emptyList()) }
                .collect { observed ->
                    val catalog = withContext(Dispatchers.IO) {
                        mergeAppCatalog(launchable, observed, application.packageName) { packageName ->
                            describeObservedApp(application.packageManager, packageName)
                        }
                    }
                    _state.value = _state.value.copy(appCatalog = catalog)
                }
        }
        viewModelScope.launch {
            // The worker writes its result with no Activity running, so the screen has to watch
            // the store rather than read it once at startup.
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { it[SignalPreferences.BACKUP_FOLDER_LABEL] to it[SignalPreferences.BACKUP_STATUS] }
                .distinctUntilChanged()
                .collect { (label, status) ->
                    _state.value = _state.value.copy(
                        backupFolderLabel = label?.takeIf { it.isNotBlank() },
                        backupStatus = decodeBackupStatus(status),
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
            // The heaviest query in the app: a full scan returning one string per matched row,
            // decoded on this dispatcher, re-run on every capture. Only two screens read the
            // result, so it runs only while one of them is on.
            _state
                .map { it.route == Route.INSIGHTS || (it.route == Route.ROOT && it.rootTab == RootTab.RULES) }
                .distinctUntilChanged()
                .flatMapLatest { needed ->
                    if (!needed) {
                        flowOf(emptyList())
                    } else {
                        historyDatabase.notificationDao().observeMatchedRuleIds().catch { emit(emptyList()) }
                    }
                }
                .collect { encoded ->
                    val counts = withContext(Dispatchers.Default) {
                        encoded.flatMap(::decodeMatchedRuleIds).groupingBy { it }.eachCount()
                    }
                    _state.value = _state.value.copy(ruleMatchCounts = counts)
                }
        }
        viewModelScope.launch {
            historyDatabase.notificationDao().observeIngestionDiagnostics()
                .catch { emit(null) }
                .collect { diagnostics ->
                    ListenerHealth.restoreDurableIngestionMetrics(diagnostics?.toMetrics() ?: com.sysadmindoc.nono.runtime.IngestionMetrics())
                }
        }
        viewModelScope.launch {
            // The filter dialog offers what the store holds, not what the loaded page happens to
            // show: a filtered page only contains its own values, which made switching straight
            // from one filter to another impossible. Bounded scans, on only while the dialog is.
            val dao = historyDatabase.notificationDao()
            _state
                .map { it.overlay == Overlay.HISTORY_FILTERS }
                .distinctUntilChanged()
                .flatMapLatest { open ->
                    if (!open) {
                        flowOf(Triple(emptyList<String>(), emptyList<String>(), emptyList<String>()))
                    } else {
                        combine(
                            dao.observeObservedPackages().catch { emit(emptyList()) },
                            dao.observeObservedChannels().catch { emit(emptyList()) },
                            dao.observeObservedGroups().catch { emit(emptyList()) },
                        ) { packages, channels, groups -> Triple(packages, channels, groups) }
                    }
                }
                .collect { (packages, channels, groups) ->
                    _state.value = _state.value.copy(
                        historyFilterPackages = packages,
                        historyFilterChannels = channels,
                        historyFilterGroups = groups,
                    )
                }
        }
        viewModelScope.launch {
            // Real hours for the History overview chart. Same whole-table aggregate Insights
            // uses, so it is likewise on only while its screen is.
            _state
                .map { it.route == Route.ROOT && it.rootTab == RootTab.HISTORY }
                .distinctUntilChanged()
                .flatMapLatest { open ->
                    if (!open) {
                        flowOf(emptyList())
                    } else {
                        historyDatabase.notificationDao().observeInsightHours().catch { emit(emptyList()) }
                    }
                }
                .collect { hours -> _state.value = _state.value.copy(historyHourCounts = hours) }
        }
        viewModelScope.launch {
            historyDatabase.notificationDao().observeTotalCount()
                .catch { emit(0) }
                .collect { total -> _state.value = _state.value.copy(historyTotalCount = total) }
        }
        viewModelScope.launch {
            val dao = historyDatabase.notificationDao()
            // Only while the screen is on. These are whole-table aggregates and Room re-runs every
            // one of them on every capture; keeping them subscribed for the life of the view model
            // would scan the history several times a second for a screen the user may never open.
            _state
                .map { it.route == Route.INSIGHTS }
                .distinctUntilChanged()
                .flatMapLatest { open ->
                    if (!open) {
                        flowOf(LocalInsights())
                    } else {
                        combine(
                            dao.observeInsightTotals().catch { emit(InsightTotals()) },
                            dao.observeTopInsightApps(INSIGHT_TOP_APP_LIMIT).catch { emit(emptyList()) },
                            dao.observeInsightHours().catch { emit(emptyList()) },
                            insightsNow.flatMapLatest { anchor ->
                                dao.observeInsightDays(
                                    insightsStartEpochMillis(anchor.nowEpochMillis, anchor.zone),
                                    INSIGHT_DAY_COUNT,
                                )
                                    .catch { emit(emptyList()) }
                                    .map { days -> anchor to days }
                            },
                        ) { totals, apps, hours, (anchor, days) ->
                            // The same clock and the same zone the SQL cutoff was built from, so
                            // the labels and the query cannot describe different fortnights.
                            buildLocalInsights(totals, apps, hours, days, anchor.nowEpochMillis, anchor.zone)
                        }
                    }
                }
                .collect { insights -> _state.value = _state.value.copy(insights = insights) }
        }
        viewModelScope.launch {
            // Counted separately from the page, so a row past the limit is still counted. The
            // limit is deliberately not part of this query's identity.
            _state
                .map { state ->
                    HistoryQuery(
                        search = state.historySearch,
                        filter = state.historyFilter,
                        packageName = state.historyPackageFilter,
                        channelId = state.historyChannelFilter,
                        contentState = state.historyContentStateFilter,
                        groupKey = state.historyGroupFilter,
                        groupSummary = state.historyGroupSummaryOnly.takeIf { only -> only },
                        importance = state.historyImportanceFilter,
                        conversation = state.historyConversationFilter,
                    )
                }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    historyDatabase.notificationDao().observeFilteredCount(
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
                    ).catch { emit(0) }
                }
                .collect { count -> _state.value = _state.value.copy(historyFilteredCount = count) }
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
                            limit = it.historyLimit,
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
                _state.value = _state.value.withMessage("Could not save to storage.")
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
        val listenerGranted = hasNotificationListenerAccess(app)

        when (ListenerHealth.capabilityAction(listenerGranted, ListenerHealth.connection.value)) {
            // The platform documents requestRebind for the disconnected window only.
            ListenerHealth.CapabilityAction.REQUEST_REBIND -> SignalNotificationListener.requestRebindIfPossible(app)
            ListenerHealth.CapabilityAction.MARK_REVOKED -> ListenerHealth.onAccessRevoked()
            ListenerHealth.CapabilityAction.NONE -> Unit
        }
        _state.value = _state.value.copy(listenerAccessGranted = listenerGranted)

        if (auditOverride != null || _state.value.route != Route.ONBOARDING) return
        // Listener access is the only capability normal capture consumes. A temporary local
        // notification is posted only after the user starts the self-test.
        val step = if (listenerGranted) 1 else 0
        _state.value = _state.value.copy(onboardingStep = step)
        if (step == 1) completeOnboarding()
    }

    /** Starts the test or tells Settings to request the one permission needed to post it. */
    fun beginCaptureSelfTest(): CaptureSelfTestAction {
        if (_state.value.captureSelfTest.status in setOf(
                CaptureSelfTestStatus.WAITING_FOR_PERMISSION,
                CaptureSelfTestStatus.RUNNING,
            )
        ) {
            return CaptureSelfTestAction.NONE
        }
        val app = getApplication<Application>()
        val accessGranted = hasNotificationListenerAccess(app)
        _state.value = _state.value.copy(listenerAccessGranted = accessGranted)
        if (!accessGranted) {
            failCaptureSelfTest("Notification access is off, so the listener cannot receive the test.")
            return CaptureSelfTestAction.NONE
        }
        if (_state.value.capturePaused) {
            failCaptureSelfTest("Notification capture is paused. Turn it back on before running the test.")
            return CaptureSelfTestAction.NONE
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                captureSelfTest = CaptureSelfTestState(
                    CaptureSelfTestStatus.WAITING_FOR_PERMISSION,
                    "Allow one temporary NoNo notification to run the listener check.",
                ),
            )
            return CaptureSelfTestAction.REQUEST_NOTIFICATION_PERMISSION
        }
        startCaptureSelfTest()
        return CaptureSelfTestAction.NONE
    }

    fun onCaptureSelfTestPermissionResult(granted: Boolean) {
        if (_state.value.captureSelfTest.status != CaptureSelfTestStatus.WAITING_FOR_PERMISSION) return
        if (!granted) {
            failCaptureSelfTest(
                "Notification permission was not granted. It is used only to post the temporary test.",
            )
            return
        }
        startCaptureSelfTest()
    }

    private fun startCaptureSelfTest() {
        val app = getApplication<Application>()
        val accessGranted = hasNotificationListenerAccess(app)
        _state.value = _state.value.copy(listenerAccessGranted = accessGranted)
        if (!accessGranted) {
            failCaptureSelfTest("Notification access turned off before the test could start.")
            return
        }
        if (_state.value.capturePaused) {
            failCaptureSelfTest("Notification capture was paused before the test could start.")
            return
        }
        SignalNotificationListener.requestRebindIfPossible(app)
        _state.value = _state.value.copy(
            captureSelfTest = CaptureSelfTestState(
                CaptureSelfTestStatus.RUNNING,
                "Waiting for the listener to receive the temporary notification.",
            ),
        )
        viewModelScope.launch {
            when (val outcome = CaptureSelfTest.run(app)) {
                is CaptureSelfTestOutcome.Passed -> {
                    _state.value = _state.value.copy(
                        captureSelfTest = CaptureSelfTestState(
                            CaptureSelfTestStatus.PASSED,
                            "The listener received the test in ${outcome.elapsedMillis} ms. Nothing was added to History.",
                        ),
                    ).withMessage("Capture self-test passed.")
                }
                CaptureSelfTestOutcome.TimedOut -> failCaptureSelfTest(
                    "The listener did not receive the test within " +
                        "${CAPTURE_SELF_TEST_TIMEOUT_MILLIS / 1_000L} seconds.",
                )
                CaptureSelfTestOutcome.NotificationsBlocked -> failCaptureSelfTest(
                    "NoNo could not post the temporary notification. Check its notification permission.",
                )
                CaptureSelfTestOutcome.AlreadyRunning -> failCaptureSelfTest(
                    "Another capture self-test is already running.",
                )
                CaptureSelfTestOutcome.PostFailed -> failCaptureSelfTest(
                    "Android could not post the temporary notification.",
                )
            }
        }
    }

    private fun failCaptureSelfTest(reason: String) {
        val guidance = captureSelfTestFailureGuidance(Build.MANUFACTURER, Build.VERSION.SDK_INT)
        _state.value = _state.value.copy(
            captureSelfTest = CaptureSelfTestState(
                CaptureSelfTestStatus.FAILED,
                "$reason $guidance",
            ),
        ).withMessage("Capture self-test failed.")
    }

    /** Builds the shareable report at tap time so every counter and state is current. */
    fun captureDiagnosticsReport(): String {
        val app = getApplication<Application>()
        return buildCaptureDiagnosticsReport(
            CaptureDiagnosticsSnapshot(
                appVersion = BuildConfig.VERSION_NAME,
                accessGranted = hasNotificationListenerAccess(app),
                connection = ListenerHealth.connection.value,
                metrics = combinedIngestionMetrics(
                    ListenerHealth.ingestionMetrics.value,
                    ListenerHealth.durableIngestionMetrics.value,
                ),
                lastCaptureAtEpochMillis = ListenerActivityLog.lastEventAt(app),
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun setOnboardingStep(step: Int) { _state.value = _state.value.copy(onboardingStep = step.coerceIn(0, 3)) }
    fun selectRoot(tab: RootTab) { _state.value = _state.value.copy(route = Route.ROOT, rootTab = tab, overlay = Overlay.NONE, transientMessage = null) }
    fun navigate(route: Route) { _state.value = _state.value.copy(route = route, overlay = Overlay.NONE, transientMessage = null, phraseInputVisible = false, selectedMetadataField = null) }
    /**
     * Recomputes the lock, and reports whether Android has a credential to check against.
     *
     * Called on every resume and every pause. A lock the device cannot satisfy is not applied:
     * a user who removed their screen lock while this setting was on would otherwise be shut out
     * of their own rules with no way back in.
     */
    fun refreshAppLock(leftForeground: Boolean = false) {
        val app = getApplication<Application>()
        val secure = runCatching {
            ContextCompat.getSystemService(app, KeyguardManager::class.java)?.isDeviceSecure == true
        }.getOrDefault(false)
        if (leftForeground) {
            leftForegroundElapsed = SystemClock.elapsedRealtime()
            _state.value = _state.value.copy(deviceCredentialAvailable = secure)
            return
        }
        // Whether the app was away long enough is a question about the trip that just ended. Left
        // set, the stamp answers it again for every later resume that had no trip: a permission
        // dialog or a multi-window focus change would lock the app in the user's hands.
        val away = leftForegroundElapsed
        leftForegroundElapsed = null
        val enabled = _state.value.settings[APP_LOCK_SETTING] == "On"
        val locked = if (!appLockSettingLoaded) {
            // The stored answer has not arrived. Holding the lock closed costs a user with the
            // setting off nothing they will see; opening it would show everything to someone with
            // the setting on.
            secure
        } else {
            shouldLock(
                enabled = enabled,
                deviceSecure = secure,
                lastUnlockedElapsed = lastUnlockedElapsed,
                leftForegroundElapsed = away,
                nowElapsed = SystemClock.elapsedRealtime(),
            )
        }
        if (locked) lastUnlockedElapsed = null
        _state.value = _state.value.copy(
            appLocked = locked,
            deviceCredentialAvailable = secure,
            appUnlockRefused = if (locked) _state.value.appUnlockRefused else false,
        )
    }

    /** Records a successful unlock, and starts the grace period from now. */
    fun onAppUnlocked() {
        lastUnlockedElapsed = SystemClock.elapsedRealtime()
        leftForegroundElapsed = null
        _state.value = _state.value.copy(appLocked = false, appUnlockRefused = false)
    }

    /**
     * The app stays locked and the lock screen says so.
     *
     * Not a snackbar: nothing behind the lock is composed, the host it would need with it, so a
     * message set here would be invisible now and would surface out of nowhere after a later
     * successful unlock.
     */
    fun onAppUnlockFailed() {
        _state.value = _state.value.copy(appUnlockRefused = true)
    }

    /** Re-anchors the fourteen-day trend on the day, and the zone, the user is actually in. */
    fun openInsights() {
        insightsNow.value = InsightsAnchor(System.currentTimeMillis(), TimeZone.getDefault())
        navigate(Route.INSIGHTS)
    }

    fun showOverlay(overlay: Overlay) { _state.value = _state.value.copy(overlay = overlay) }
    fun dismissOverlay() { _state.value = _state.value.copy(overlay = Overlay.NONE, selectedMetadataField = null) }
    fun updateDraft(transform: (SignalRule) -> SignalRule) { _state.value = _state.value.copy(draft = transform(_state.value.draft), validationError = null) }
    fun setPhraseDraft(text: String) { _state.value = _state.value.copy(phraseDraft = text) }
    /**
     * Applies the editor's phrase to the draft.
     *
     * One phrase per line. The legacy [SignalRule.phrase] and [SignalRule.matchType] are kept in
     * step with the condition rather than abandoned, because the rule card, the shortcut label and
     * a store read by the previous build all still go through them.
     */
    fun commitPhrase() {
        val phrases = _state.value.phraseDraft.lines().map(String::trim).filter(String::isNotEmpty)
        val condition = currentPhraseCondition().copy(phrases = phrases)
        _state.value = _state.value.copy(
            route = Route.RULE_BUILDER,
            draft = _state.value.draft.withPhraseCondition(condition),
            overlay = Overlay.NONE,
            phraseInputVisible = false,
        )
    }

    /** The condition being edited, which is the draft's own or the one its old fields imply. */
    private fun currentPhraseCondition(): PhraseCondition = phraseConditionFor(_state.value.draft)

    private fun editPhraseCondition(transform: (PhraseCondition) -> PhraseCondition) {
        val updated = transform(currentPhraseCondition())
        _state.value = _state.value.copy(
            draft = _state.value.draft.withPhraseCondition(updated),
            validationError = null,
        )
    }

    fun setMatchMode(mode: MatchMode) = editPhraseCondition { it.copy(mode = mode) }

    fun setPhraseQuantifier(quantifier: PhraseQuantifier) = editPhraseCondition { it.copy(quantifier = quantifier) }

    fun setMatchCaseSensitive(caseSensitive: Boolean) = editPhraseCondition { it.copy(caseSensitive = caseSensitive) }

    /**
     * Turns one field on or off.
     *
     * The last field cannot be turned off from here: a condition with no field searches nothing,
     * and leaving the user in that state with no way back is worse than refusing the tap. Saving
     * still refuses it, for a condition that arrives from a file.
     */
    fun toggleMatchField(field: MatchField) = editPhraseCondition { condition ->
        val fields = if (field in condition.fields) condition.fields - field else condition.fields + field
        if (fields.isEmpty()) condition else condition.copy(fields = fields)
    }

    fun setTesterTitle(text: String) { _state.value = _state.value.copy(testerTitle = text) }

    fun setTesterText(text: String) { _state.value = _state.value.copy(testerText = text) }

    fun showMetadataCondition(field: MetadataField) {
        _state.value = _state.value.copy(
            selectedMetadataField = field,
            overlay = Overlay.METADATA_CONDITION,
        )
    }

    /** Applies the choice for the metadata row whose picker is open. Null means "Any". */
    fun setMetadataCondition(condition: MetadataCondition?) {
        val field = _state.value.selectedMetadataField ?: return
        _state.value = _state.value.copy(
            draft = _state.value.draft.withMetadataCondition(field, condition),
            overlay = Overlay.NONE,
            selectedMetadataField = null,
            validationError = null,
        )
    }

    fun clearMetadataConditions() {
        updateDraft { it.copy(metadataConditions = emptyList()) }
    }
    /**
     * Turns the draft's schedule on or off.
     *
     * A schedule that has just been switched on covers every day, all day: the same behaviour the
     * rule had a moment ago. Starting from an empty selection would silently stop the rule
     * matching the instant the user opened the editor.
     */
    fun setScheduleEnabled(enabled: Boolean) {
        updateDraft { draft ->
            draft.copy(schedule = if (enabled) draft.schedule ?: RuleSchedule() else null)
        }
    }

    fun toggleScheduleDay(isoDay: Int) {
        updateDraft { draft ->
            val schedule = draft.schedule ?: RuleSchedule()
            val days = if (isoDay in schedule.days) schedule.days - isoDay else schedule.days + isoDay
            draft.copy(schedule = schedule.copy(days = days))
        }
    }

    /** Both ends in minutes from local midnight. Equal ends mean the whole day. */
    fun setScheduleWindow(startMinute: Int, endMinute: Int) {
        updateDraft { draft ->
            val schedule = draft.schedule ?: RuleSchedule()
            draft.copy(
                schedule = schedule.copy(
                    startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY - 1),
                    endMinute = endMinute.coerceIn(0, MINUTES_PER_DAY - 1),
                ),
            )
        }
    }

    fun openRuleSearch() { _state.value = _state.value.copy(ruleSearchActive = true) }

    /** Closing clears the query, so the list the user comes back to is the whole list. */
    fun closeRuleSearch() { _state.value = _state.value.copy(ruleSearchActive = false, ruleSearch = "") }

    fun setRuleSearch(text: String) { _state.value = _state.value.copy(ruleSearch = text) }

    /**
     * Opens the rule a search result names, and leaves search behind.
     *
     * Takes the id rather than the rule, so a result for a rule deleted in another tab while the
     * results were on screen reports that instead of opening a stale copy.
     */
    fun openRuleFromSearch(ruleId: Long) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId }
        if (rule == null) {
            _state.value = _state.value.withMessage("That rule is no longer saved.")
            return
        }
        _state.value = _state.value.copy(
            route = Route.RULE_BUILDER,
            overlay = Overlay.NONE,
            draft = rule,
            selectedRuleId = rule.id,
            ruleSearchActive = false,
            ruleSearch = "",
        )
    }

    fun setAppSearch(text: String) { _state.value = _state.value.copy(appSearch = text) }
    fun setHistorySearch(text: String) { _state.value = _state.value.resetHistoryWindow().copy(historySearch = text) }
    fun openHistorySearch() { _state.value = _state.value.resetHistoryWindow().copy(historySearchActive = true) }
    fun closeHistorySearch() { _state.value = _state.value.resetHistoryWindow().copy(historySearchActive = false, historySearch = "") }
    fun showPhraseInput() { _state.value = _state.value.copy(phraseInputVisible = true) }
    fun hidePhraseInput() { _state.value = _state.value.copy(phraseInputVisible = false) }
    fun setHistoryFilter(filter: String) { _state.value = _state.value.resetHistoryWindow().copy(historyFilter = filter) }
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
        _state.value = _state.value.resetHistoryWindow().copy(historyPackageFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryChannelFilter(value: String?) {
        _state.value = _state.value.resetHistoryWindow().copy(historyChannelFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryGroupFilter(value: String?) {
        _state.value = _state.value.resetHistoryWindow().copy(historyGroupFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryContentStateFilter(value: NotificationContentState?) {
        _state.value = _state.value.resetHistoryWindow().copy(historyContentStateFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryGroupSummaryOnly(enabled: Boolean) {
        _state.value = _state.value.resetHistoryWindow().copy(historyGroupSummaryOnly = enabled, overlay = Overlay.NONE)
    }
    fun setHistoryImportanceFilter(value: Int?) {
        _state.value = _state.value.resetHistoryWindow().copy(historyImportanceFilter = value, overlay = Overlay.NONE)
    }
    fun setHistoryConversationFilter(value: Boolean?) {
        _state.value = _state.value.resetHistoryWindow().copy(historyConversationFilter = value, overlay = Overlay.NONE)
    }
    /** Loads another page of the current query. One query, so no two pages can disagree. */
    fun loadMoreHistory() {
        if (!_state.value.hasMoreHistory) return
        _state.value = _state.value.copy(historyLimit = _state.value.historyLimit + HISTORY_PAGE_SIZE)
    }

    fun setCapturePaused(paused: Boolean) {
        CaptureGate.setPaused(getApplication(), paused)
    }
    fun beginExport() {
        _state.value = _state.value.copy(overlay = Overlay.TRANSFER_EXPORT_PASSPHRASE, transientMessage = null, transientUndo = null)
    }
    fun requestExport(passphrase: String) {
        if (passphrase.isBlank()) {
            _state.value = _state.value.withMessage("Enter a passphrase to encrypt the rule file.")
            return
        }
        val rules = _state.value.rules
        _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage("Preparing encrypted rule export…")
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
                            transientUndo = null,
                        )
                    },
                    onFailure = { _state.value = _state.value.withMessage("Could not prepare the encrypted rule file.") },
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
            _state.value = _state.value.withMessage("The export expired; try again.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("The selected location could not be opened.")
            }
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.withMessage(
                    result.fold(
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
        _state.value = _state.value.withMessage("Preparing the history export…")
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                historyDatabase.notificationDao().readAllForExport().map { it.toHistoryRecord() }
            }
            withContext(Dispatchers.Main.immediate) {
                result.fold(
                    onSuccess = { records ->
                        if (records.isEmpty()) {
                            _state.value = _state.value.withMessage("There is no history to export yet.")
                            return@fold
                        }
                        pendingExportPayload = HistoryExport.toCsv(records)
                        pendingExportIsHistory = true
                        pendingExportRowCount = records.size
                        _state.value = _state.value.copy(
                            transferExportRequest = _state.value.transferExportRequest + 1,
                            transferExportIsHistory = true,
                            transientMessage = null,
                            transientUndo = null,
                        )
                    },
                    onFailure = {
                        _state.value = _state.value.withMessage("History could not be read for export.")
                    },
                )
            }
        }
    }

    fun exportCancelled() {
        pendingExportPayload = null
        pendingExportIsHistory = false
        pendingExportRowCount = 0
        _state.value = _state.value.withMessage("Export cancelled.")
    }
    /**
     * Reads and parses a chosen rule file entirely off the main thread.
     *
     * Both the size the picker declares and the bytes actually delivered are checked: a document
     * provider can report one length and stream another, and only the second one is what has to
     * fit in memory.
     */
    fun beginImport(uri: Uri) {
        _state.value = _state.value.withMessage("Reading rule file…")
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val declared = declaredSize(uri)
            if (declared != null && declared > RuleTransferLimits.MAX_ENCODED_BYTES) {
                withContext(Dispatchers.Main.immediate) {
                    _state.value = _state.value.withMessage(ImportRejection.TOO_LARGE.message)
                }
                return@launch
            }
            val encoded = when (val read = readBoundedUtf8 { app.contentResolver.openInputStream(uri) }) {
                is BoundedReadResult.Text -> read.value
                // The reason comes from what actually happened, not from whether the provider
                // volunteered a size.
                BoundedReadResult.TooLarge -> {
                    withContext(Dispatchers.Main.immediate) {
                        _state.value = _state.value.withMessage(ImportRejection.TOO_LARGE.message)
                    }
                    return@launch
                }
                BoundedReadResult.Unreadable -> {
                    withContext(Dispatchers.Main.immediate) {
                        _state.value = _state.value.withMessage("Import failed; that file could not be read.")
                    }
                    return@launch
                }
            }
            // Parsing, base64 and any key derivation stay on this dispatcher. The device key is
            // offered so a scheduled backup restores through the same picker as any other file;
            // it opens nothing else, and a file from another device is refused by name.
            val result = RuleTransfer.importRules(encoded, deviceKey = DeviceBackupKey.get())
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
                _state.value = _state.value.copy(overlay = Overlay.TRANSFER_IMPORT_PASSPHRASE, transientMessage = null, transientUndo = null)
            }
            is RuleImportResult.Success -> showImportPreview(
                result.rules,
                result.channelConditionsNeedingReselection,
            )
            RuleImportResult.Cancelled -> cancelTransfer()
            is RuleImportResult.InvalidFile ->
                _state.value = _state.value.withMessage(result.rejection.message)
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
                    is RuleImportResult.Success -> showImportPreview(
                        result.rules,
                        result.channelConditionsNeedingReselection,
                    )
                    RuleImportResult.Cancelled -> cancelTransfer()
                    RuleImportResult.NeedsPassphrase -> _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage(ImportRejection.WRONG_PASSPHRASE.message)
                    is RuleImportResult.InvalidFile -> _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage(result.rejection.message)
                }
            }
        }
    }
    /**
     * Merging is quadratic in the two rule lists, and the import cap allows ten thousand of them,
     * so both the preview and the commit run off the main thread. Only the resulting state is
     * applied on it.
     */
    private fun showImportPreview(incoming: List<SignalRule>, channelReselections: Int) {
        val current = _state.value.rules
        pendingImportRules = incoming
        viewModelScope.launch(Dispatchers.Default) {
            val preview = RuleTransfer.preview(current, incoming)
            withContext(Dispatchers.Main.immediate) {
                _state.value = _state.value.copy(
                    overlay = Overlay.TRANSFER_PREVIEW,
                    transferAdditions = preview.additions.size,
                    transferConflicts = preview.conflicts.size,
                    transientMessage = if (channelReselections > 0) {
                        "Channel filters are device-bound. Select them again after import."
                    } else {
                        null
                    },
                    transientUndo = null,
                )
            }
        }
    }
    fun commitImportedRules(resolution: ConflictResolution) {
        val incoming = pendingImportRules ?: run {
            cancelTransfer()
            return
        }
        val current = _state.value.rules
        pendingImportRules = null
        viewModelScope.launch(Dispatchers.Default) {
            val preview = RuleTransfer.preview(current, incoming)
            val resolutions = preview.conflicts.associate { it.existing.id to resolution }
            val appliedImports = preview.additions + if (resolution == ConflictResolution.REPLACE_EXISTING) {
                preview.conflicts.map { it.incoming }
            } else {
                emptyList()
            }
            val channelReselections = appliedImports.sumOf { rule ->
                rule.metadataConditions.count { condition ->
                    condition is ChannelCondition && condition.needsReselection
                }
            }
            // Additions are renumbered from this device's counter, so a file cannot claim an id
            // that history already attributes to a rule the user deleted.
            val committed = RuleTransfer.commit(current, incoming, resolutions, nextRuleIdCounter)
            val encoded = committed?.let { encodeRules(it.rules, it.nextRuleId) }
            withContext(Dispatchers.Main.immediate) {
                if (committed == null || encoded == null) {
                    cancelTransfer()
                    return@withContext
                }
                _state.value = _state.value.copy(
                    rules = committed.rules,
                    overlay = Overlay.NONE,
                    transferAdditions = 0,
                    transferConflicts = 0,
                ).withMessage(
                    buildString {
                        append("Imported ${preview.additions.size} new rule(s). Notification history was not imported.")
                        if (channelReselections > 0) {
                            append(" Select $channelReselections channel filter(s) again before those rules can match.")
                        }
                    },
                )
                // Keep the in-memory counter in step with what was just written.
                nextRuleIdCounter = decodeRuleStore(encoded)?.nextRuleId ?: nextRuleIdCounter
                writeEncodedRules(encoded)
            }
        }
    }
    fun cancelTransfer() {
        pendingExportPayload = null
        pendingImportEncoded = null
        pendingImportRules = null
        _state.value = _state.value.copy(overlay = Overlay.NONE, transferAdditions = 0, transferConflicts = 0).withMessage("Transfer cancelled.")
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
            _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage("That app is not installed, or it has no screen to open.")
            return
        }
        val launched = runCatching {
            app.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage(if (launched) null else "That app could not be opened.")
    }

    /**
     * Records the ingestion counts the user has seen.
     *
     * Durable, so it survives a restart: an acknowledgement held only in memory would let the
     * same warning come back the next time the app opened.
     */
    fun acknowledgeIngestionProblems() {
        viewModelScope.launch {
            // The live counters can lead the durable row between merges. Acknowledging only what
            // the row holds would leave the banner showing the difference with nothing said.
            val live = ListenerHealth.ingestionMetrics.value
            val acknowledged = runCatching {
                historyDatabase.notificationDao()
                    .acknowledgeIngestionProblems(live.dropped, live.failed)
            }.getOrDefault(false)
            _state.value = _state.value.withMessage(StatusMessages.acknowledgementOutcome(acknowledged))
        }
    }

    /** Stars or unstars a record. A starred record outlives the retention period. */
    fun setHistoryStarred(historyId: Long, starred: Boolean) {
        _state.value = _state.value.copy(overlay = Overlay.NONE)
        viewModelScope.launch {
            // The message follows the write. It used to be shown before it, so a failed update
            // told the user their record was kept when it was about to be pruned.
            val updated = runCatching {
                historyDatabase.notificationDao().setStarred(historyId, starred) > 0
            }.getOrDefault(false)
            _state.value = _state.value.withMessage(StatusMessages.starOutcome(updated, starred))
        }
    }

    /**
     * Deletes one history record, keeping it in hand so the snackbar can put it back.
     *
     * There is no confirmation dialog, by design. The undo is what makes that safe, so the row is
     * read before the delete rather than reconstructed from the screen's copy of it.
     */
    fun deleteHistoryRecord(historyId: Long) {
        _state.value = _state.value.copy(overlay = Overlay.NONE)
        viewModelScope.launch {
            val dao = historyDatabase.notificationDao()
            val removed = runCatching {
                // Only treated as removed when the delete reported a row. A record that fell off
                // under retention between the read and the delete must not claim a deletion the
                // undo would then have nothing to reverse.
                dao.readById(historyId)?.takeIf { dao.deleteById(historyId) > 0 }
            }.getOrNull()
            _state.value = if (removed == null) {
                _state.value.withMessage(StatusMessages.deleteOutcome(removed = false))
            } else {
                // A second delete before the first snackbar resolves would otherwise overwrite
                // this slot and lose the earlier record with no way back and nothing said. It is
                // restore, not upsert, for the same reason the undo path is: if the app reposted
                // that notification meanwhile, upsert would update the live row and drop its star.
                val pending = deletedHistoryRecord.takeIf {
                    _state.value.transientUndo == UndoableAction.RESTORE_DELETED_HISTORY
                }
                deletedHistoryRecord = null
                val pendingRestored = pending == null || runCatching {
                    historyDatabase.notificationDao().restore(pending)
                }.getOrDefault(false)
                deletedHistoryRecord = removed
                if (pendingRestored) {
                    _state.value.withMessage(
                        StatusMessages.deleteOutcome(removed = true),
                        UndoableAction.RESTORE_DELETED_HISTORY,
                    )
                } else {
                    // The earlier record could not be put back, so the undo slot no longer
                    // describes what is on the device. Saying so beats a snackbar that lies.
                    _state.value.withMessage(StatusMessages.deleteOutcomeWithLostUndo())
                }
            }
        }
    }

    /** Puts back whatever the last undoable action removed. */
    fun performUndo(action: UndoableAction) {
        when (action) {
            UndoableAction.RESTORE_DELETED_HISTORY -> {
                val record = deletedHistoryRecord
                deletedHistoryRecord = null
                if (record == null) {
                    _state.value = _state.value.withMessage(null)
                    return
                }
                viewModelScope.launch {
                    // A plain insert, not an upsert: a restore has to bring the row back as it
                    // was. If the app reposted that notification while the snackbar was up, the
                    // update path would keep the live row's identity and drop the star instead.
                    val restored = runCatching {
                        historyDatabase.notificationDao().restore(record)
                    }.getOrDefault(false)
                    _state.value = _state.value.withMessage(StatusMessages.restoreOutcome(restored))
                }
            }
            UndoableAction.RESTORE_DELETED_RULES -> {
                val deletion = deletedRules
                deletedRules = null
                if (
                    deletion == null ||
                    _state.value.transientUndo != UndoableAction.RESTORE_DELETED_RULES
                ) {
                    _state.value = _state.value.withMessage("That deletion can no longer be undone.")
                    return
                }
                val restored = restoreDeletedRules(_state.value.rules, deletion)
                if (restored == null) {
                    _state.value = _state.value.withMessage(
                        if (deletion.count == 1) {
                            "That rule could not be restored."
                        } else {
                            "Those rules could not be restored."
                        },
                    )
                    return
                }
                _state.value = _state.value.copy(rules = restored).withMessage(null)
                persistRules()
            }
        }
    }

    /** Copies the metadata a record actually holds. Never notification content, because none is stored. */
    fun copyHistoryMetadata(historyId: Long) {
        _state.value = _state.value.copy(overlay = Overlay.NONE)
        viewModelScope.launch {
            // Read by id like the delete path, not from the loaded page: on a busy device the
            // record can fall off the page between opening the menu and tapping Copy, and Copy
            // would then refuse a record Delete would still handle.
            val record = runCatching {
                historyDatabase.notificationDao().readById(historyId)?.toHistoryRecord()
            }.getOrNull()
            if (record == null) {
                _state.value = _state.value.withMessage("That record is no longer available.")
                return@launch
            }
            val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
            val copied = clipboard != null && runCatching {
                clipboard.setPrimaryClip(ClipData.newPlainText("NoNo record", historyMetadataClipboardText(record)))
            }.isSuccess
            _state.value = _state.value.withMessage(
                if (copied) "Metadata copied." else "The clipboard is not available.",
            )
        }
    }

    /**
     * Asks the launcher to pin a shortcut that opens this rule.
     *
     * The launcher decides, and some do not support pinning at all, so the outcome is reported
     * rather than assumed. Nothing about the shortcut runs a notification action: it opens the
     * rule for review, which is all this build does.
     */
    fun requestRuleShortcut(ruleId: Long) {
        val app = getApplication<Application>()
        val rule = _state.value.rules.firstOrNull { it.id == ruleId }
        if (rule == null) {
            _state.value = _state.value.withMessage("That rule is no longer saved.")
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(app)) {
            _state.value = _state.value.withMessage("This launcher does not support pinned shortcuts.")
            return
        }
        val shortcut = ShortcutInfoCompat.Builder(app, "rule-${rule.id}")
            .setShortLabel(rule.name.take(SHORTCUT_LABEL_LIMIT).ifBlank { "NoNo rule" })
            .setLongLabel(rule.name.take(SHORTCUT_LABEL_LIMIT).ifBlank { "NoNo rule" })
            .setIcon(IconCompat.createWithResource(app, R.mipmap.ic_launcher))
            .setIntent(
                android.content.Intent(app, MainActivity::class.java)
                    .setAction(android.content.Intent.ACTION_VIEW)
                    .putExtra(MainActivity.EXTRA_RULE_ID, rule.id),
            )
            .build()
        val requested = runCatching { ShortcutManagerCompat.requestPinShortcut(app, shortcut, null) }.getOrDefault(false)
        _state.value = _state.value.copy(route = Route.ROOT, rootTab = RootTab.SETTINGS).withMessage(
            if (requested) "Shortcut sent to your launcher." else "The launcher refused the shortcut.",
        )
    }

    /** Chooses which saved rule the shortcut being built will point at. */
    fun selectShortcutRule(ruleId: Long) {
        _state.value = _state.value.copy(selectedRuleId = ruleId)
    }

    /** Opens a rule the launcher shortcut named, if it still exists. */
    fun openRuleFromShortcut(ruleId: Long) {
        val rule = _state.value.rules.firstOrNull { it.id == ruleId }
        _state.value = if (rule == null) {
            _state.value.copy(route = Route.ROOT, rootTab = RootTab.RULES)
                .withMessage("That shortcut points at a rule that no longer exists.")
        } else {
            _state.value.copy(route = Route.RULE_BUILDER, overlay = Overlay.NONE, draft = rule, selectedRuleId = rule.id)
        }
    }

    /** Feedback for the copy action in the content-hidden explainer. */
    fun reportCommandCopied() {
        _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage("Command copied.")
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
            transientUndo = null,
        )
    }

    fun createRuleFromSelectedHistory() {
        val record = _state.value.history.firstOrNull { it.id == _state.value.selectedHistoryId }
        if (record == null) {
            newRule()
            _state.value = _state.value.withMessage("That history entry is no longer available.")
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
        ).withMessage(derived.provenanceMessage)
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
            _state.value = _state.value.copy(validationError = error).withMessage(error)
            return
        }
        val existing = _state.value.rules
        val draft = resolveSavedRule(existing, current, nextRuleIdCounter)
        if (draft.id != current.id) nextRuleIdCounter = advanceRuleCounter(draft.id)
        val rules = upsertRule(existing, draft)
        _state.value = _state.value.copy(
            route = Route.ROOT,
            rootTab = RootTab.RULES,
            rules = rules,
            selectedRuleId = draft.id,
            validationError = null,
        ).withMessage("Rule saved")
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

    /**
     * Clears an expiry an imported rule carries.
     *
     * There is no scheduler, so an expiry was never going to fire. Setting one is gone; removing
     * one an import brought along is the repair.
     */
    fun clearRuleExpiry() {
        mutateRule(_state.value.selectedRuleId) { it.copy(enabledFor = null) }
        _state.value = _state.value.copy(overlay = Overlay.NONE)
    }

    fun setRuleFolder(folder: String) {
        mutateRule(_state.value.selectedRuleId) { it.copy(folder = folder.ifBlank { "No folder" }) }
        _state.value = _state.value.copy(overlay = Overlay.NONE)
    }

    fun duplicateRule() {
        val existing = _state.value.rules
        val rules = duplicateRuleIn(existing, _state.value.selectedRuleId, nextRuleIdCounter)
        rules.lastOrNull()?.takeIf { rules.size != existing.size }?.let {
            nextRuleIdCounter = advanceRuleCounter(it.id)
        }
        if (rules.size == existing.size) return
        _state.value = _state.value.copy(
            rules = rules,
            overlay = Overlay.NONE,
            selectedRuleId = rules.last().id,
        ).withMessage("Rule duplicated")
        persistRules()
    }

    fun deleteRule() {
        val current = _state.value
        val ruleId = current.selectedRuleId
        if (current.rules.none { it.id == ruleId }) {
            deletedRules = null
            _state.value = current.copy(overlay = Overlay.NONE).withMessage("That rule could not be deleted.")
            return
        }
        val (base, previousRestored) = restorePendingRules(current.rules)
        val result = checkNotNull(deleteRuleWithUndo(base, ruleId))
        deletedRules = result.deletion
        _state.value = _state.value.copy(
            rules = result.remaining,
            overlay = Overlay.NONE,
            selectedRuleId = null,
        ).withMessage(
            if (previousRestored) {
                "Rule deleted."
            } else {
                "Rule deleted. The previous rule could not be restored."
            },
            UndoableAction.RESTORE_DELETED_RULES,
        )
        persistRules()
    }

    fun deleteAllRules() {
        val (base, previousRestored) = restorePendingRules(_state.value.rules)
        val result = deleteAllRulesWithUndo(base)
        if (result == null) {
            deletedRules = null
            _state.value = _state.value.withMessage("There are no rules to delete.")
            return
        }
        deletedRules = result.deletion
        val removed = result.deletion.count
        _state.value = _state.value.copy(
            rules = result.remaining,
            selectedRuleId = null,
            overlay = Overlay.NONE,
        ).withMessage(
            buildString {
                append(if (removed == 1) "Deleted 1 rule." else "Deleted $removed rules.")
                if (!previousRestored) append(" The previous rule could not be restored.")
            },
            UndoableAction.RESTORE_DELETED_RULES,
        )
        persistRules()
    }

    /** Restores the still-active prior undo before a second delete replaces its snackbar. */
    private fun restorePendingRules(current: List<SignalRule>): Pair<List<SignalRule>, Boolean> {
        val pending = deletedRules.takeIf {
            _state.value.transientUndo == UndoableAction.RESTORE_DELETED_RULES
        }
        deletedRules = null
        if (pending == null) return current to true
        val restored = restoreDeletedRules(current, pending)
        return if (restored == null) current to false else restored to true
    }

    /**
     * Records the folder the user picked and starts a first backup straight away.
     *
     * The grant is taken persistably here, because the job that uses it runs when this process is
     * gone. A provider that refuses to hand over a lasting grant is reported now rather than at
     * the first scheduled run, which could be a day later.
     */
    fun setBackupFolder(uri: Uri) {
        val app = getApplication<Application>()
        if (!BackupFolder.persist(app, uri)) {
            _state.value = _state.value.withMessage("That folder did not grant lasting access.")
            return
        }
        val label = BackupFolder.describe(uri)
        viewModelScope.launch {
            // Read before the write, so the folder being replaced is known even after it is gone
            // from the store. A read that fails leaves nothing to hand back, and the new grant is
            // still the one that matters.
            val previous = runCatching { dataStore.data.first()[SignalPreferences.BACKUP_FOLDER_URI] }.getOrNull()
            try {
                dataStore.edit {
                    it[SignalPreferences.BACKUP_FOLDER_URI] = uri.toString()
                    it[SignalPreferences.BACKUP_FOLDER_LABEL] = label
                }
            } catch (error: IOException) {
                // Nothing was stored, so nothing is claimed. Announcing the folder first would
                // leave the row naming a folder the scheduled job cannot find.
                BackupFolder.release(app, uri)
                _state.value = _state.value.withMessage("Could not save the backup folder.")
                return@launch
            }
            _state.value = _state.value.copy(backupFolderLabel = label)
                .withMessage("Backup folder set to $label.")
            // Handed back only once the replacement is stored. Holding on to it would keep this
            // app's access to a folder the user has stopped pointing it at, and the platform caps
            // how many of those an app may hold at once.
            previous?.takeIf { it.isNotBlank() && it != uri.toString() }
                ?.let { BackupFolder.release(app, Uri.parse(it)) }
            // Only after the write lands: the worker reads the folder back out of this store, and
            // starting it first is how a run reports "No backup folder is selected" about the
            // folder the user just picked.
            val cadence = backupCadence(_state.value.settings[SignalPreferences.AUTOMATIC_BACKUP_SETTING])
            if (cadence.enabled) {
                BackupScheduler.apply(app, cadence)
                BackupScheduler.runOnce(app)
            }
        }
    }

    /** Stops the schedule and hands the folder grant back. */
    fun clearBackupFolder() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val stored = dataStore.data.catch { emit(emptyPreferences()) }.first()[SignalPreferences.BACKUP_FOLDER_URI]
            stored?.takeIf { it.isNotBlank() }?.let { BackupFolder.release(app, Uri.parse(it)) }
            BackupScheduler.apply(app, BackupCadence.OFF)
            editPreferences {
                it.remove(SignalPreferences.BACKUP_FOLDER_URI)
                it.remove(SignalPreferences.BACKUP_FOLDER_LABEL)
                it.remove(SignalPreferences.BACKUP_STATUS)
                it[settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING)] = BackupCadence.OFF.label
            }
            _state.value = _state.value
                .copy(
                    backupFolderLabel = null,
                    backupStatus = BackupStatus(),
                    settings = _state.value.settings +
                        (SignalPreferences.AUTOMATIC_BACKUP_SETTING to BackupCadence.OFF.label),
                )
                .withMessage("Backup folder cleared.")
        }
    }

    fun setSetting(label: String, value: String) {
        if (label == SignalPreferences.AUTOMATIC_BACKUP_SETTING) {
            val app = getApplication<Application>()
            val cadence = backupCadence(value)
            val hasFolder = _state.value.backupFolderLabel != null
            _state.value = _state.value.copy(
                settings = _state.value.settings + (label to value),
                overlay = Overlay.NONE,
            ).let {
                // Turning it on with nowhere to write would schedule a job that can only fail.
                // Saying so now is the difference between a setting that works and one that looks
                // like it does.
                if (cadence.enabled && !hasFolder) it.withMessage("Pick a backup folder to finish setting this up.") else it
            }
            viewModelScope.launch {
                try {
                    dataStore.edit { it[settingKey(label)] = value }
                } catch (error: IOException) {
                    _state.value = _state.value.withMessage("Could not save to storage.")
                    return@launch
                }
                // Both the periodic schedule and the immediate run read this cadence back out of
                // the store. Either one started before the write lands reads the old value,
                // decides the schedule is off, and writes no result at all.
                BackupScheduler.apply(app, cadence)
                if (cadence.enabled && hasFolder) BackupScheduler.runOnce(app)
            }
            return
        }
        if (label == APP_LOCK_SETTING) {
            // Turning it on with no screen lock set would leave the app asking for a credential
            // that does not exist. The row says so instead of taking a setting it cannot honour.
            if (value == "On" && !_state.value.deviceCredentialAvailable) {
                _state.value = _state.value.copy(overlay = Overlay.NONE).withMessage(NO_DEVICE_CREDENTIAL)
                return
            }
            // Turning it on locks nothing right now: the user is standing here having just proved
            // they are the user. The lock takes effect when they leave.
            if (value == "On") lastUnlockedElapsed = SystemClock.elapsedRealtime()
        }
        if (label == SignalPreferences.WIDGET_COUNT_SETTING) {
            val app = getApplication<Application>()
            _state.value = _state.value.copy(
                settings = _state.value.settings + (label to value),
                overlay = Overlay.NONE,
            )
            viewModelScope.launch {
                try {
                    dataStore.edit { it[settingKey(label)] = value }
                } catch (error: IOException) {
                    _state.value = _state.value.withMessage("Could not save to storage.")
                    return@launch
                }
                // The provider reads this back out of the store, so the redraw has to come after
                // the write or the widget shows the previous scope's number under the new label.
                SignalWidgetProvider.requestUpdate(app)
            }
            return
        }
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

    fun clearTransient() {
        when (_state.value.transientUndo) {
            UndoableAction.RESTORE_DELETED_HISTORY -> deletedHistoryRecord = null
            UndoableAction.RESTORE_DELETED_RULES -> deletedRules = null
            null -> Unit
        }
        _state.value = _state.value.withMessage(null)
    }
    fun showMessage(message: String) { _state.value = _state.value.withMessage(message) }

    private fun persistRules() = writeEncodedRules(encodeRules(_state.value.rules, nextRuleIdCounter))

    /** Split out so a caller that already encoded off the main thread does not encode twice. */
    private fun writeEncodedRules(encoded: String) {
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
        val base = _state.value.copy(auditState = id, overlay = Overlay.NONE, transientMessage = null, transientUndo = null)
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
        deletedHistoryRecord = null
        deletedRules = null
        // The database is shared with the listener and the widget, so it outlives this owner.
        super.onCleared()
    }
}

private fun hasNotificationListenerAccess(application: Application): Boolean = runCatching {
    NotificationManagerCompat.getEnabledListenerPackages(application).contains(application.packageName)
}.getOrDefault(false)

private data class HistoryLoadResult(
    val state: HistoryLoadState,
    val records: List<HistoryRecord> = emptyList(),
    val error: String? = null,
)
