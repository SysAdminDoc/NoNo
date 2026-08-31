package com.sysadmindoc.nono.model

import com.sysadmindoc.nono.data.CatalogedApp
import com.sysadmindoc.nono.runtime.BackupStatus
import kotlinx.serialization.Serializable

enum class RootTab(val label: String) {
    RULES("Rules"), HISTORY("History"), EXPLORE("Explore"), SETTINGS("Settings")
}

enum class Route {
    ONBOARDING,
    ROOT,
    RULE_BUILDER,
    APP_SELECTOR,
    PHRASE_EDITOR,
    FILTER_GROUP,
    ACTION_SELECTOR,
    HISTORY_ACTIVITY,
    SHORTCUT_EDITOR,
    INSIGHTS
}

enum class Overlay {
    NONE,
    CONDITION_TYPE,
    ADD_FILTER,
    METADATA_CONDITION,
    RULE_MORE,
    PRIORITY,
    FOLDER,
    RENAME,
    HISTORY_ITEM,
    HISTORY_FILTERS,
    CONTENT_HIDDEN,
    LISTENER_CHECKLIST,
    MUTE_MODE,
    MUTE_IMPORTANCE,
    HISTORY_STORAGE,
    HISTORY_RETENTION,
    BACKUP_CADENCE,
    WIDGET_SCOPE,
    SCHEDULE,
    TRANSFER_EXPORT_PASSPHRASE,
    TRANSFER_IMPORT_PASSPHRASE,
    TRANSFER_PREVIEW,
    THEME,
    LANGUAGE
}

/** Sentinel id for a rule that has never been saved. */
const val UNSAVED_RULE_ID = 0L

/**
 * Something the user can take back from the snackbar that reported it.
 *
 * Deleting a history record or saved rule is destructive and there is no confirmation dialog, by
 * design. The undo is what makes that safe.
 */
enum class UndoableAction(val label: String) {
    RESTORE_DELETED_HISTORY("Undo"),
    RESTORE_DELETED_RULES("Undo"),
}

@Serializable
data class SignalRule(
    /**
     * [UNSAVED_RULE_ID] until the repository allocates one. A draft that arrives carrying a real
     * id can replace whichever saved rule happens to share it, which is how an Explore suggestion
     * used to overwrite rule 1.
     */
    val id: Long = UNSAVED_RULE_ID,
    val name: String = "Test rule",
    val app: String = "any app",
    /** Stable package identity used for matching; [app] remains the display label. */
    val appPackageName: String? = null,
    val phrase: String = "anything",
    val action: String = "nothing",
    val enabled: Boolean = true,
    val priority: String = "Normal",
    val folder: String = "No folder",
    val matchType: String = DEFAULT_MATCH_TYPE,
    /** Metadata conditions authored by this build. Every entry is evaluated. */
    val metadataConditions: List<MetadataCondition> = emptyList(),
    /**
     * Free-string conditions written by rule-store versions 1 through 4.
     *
     * Kept visible and unsupported so upgrading cannot silently change whether an old rule
     * matches. New UI never adds to this list.
     */
    val extras: List<String> = emptyList(),
    val filterOperator: String = DEFAULT_FILTER_OPERATOR,
    val enabledFor: String? = null,
    /**
     * When this rule is allowed to match. Null means any time, which is what every rule written
     * before schedules existed meant and what a new rule means until one is set.
     */
    val schedule: RuleSchedule? = null,
    /**
     * The phrase side of the rule, once the user has touched it.
     *
     * Null for every rule written before this existed, and read through [phraseConditionFor],
     * which turns the older single phrase and its operator into the same value. Keeping the old
     * fields rather than rewriting them on load means a store this build writes can still be read
     * by the build before it.
     */
    val phraseCondition: PhraseCondition? = null,
)

/**
 * The phrase condition a rule is evaluated against.
 *
 * A rule that predates field selection was testing the title and the text joined together with
 * one substring, case-insensitively, and "doesn't contain" meant the phrase was absent. That maps
 * onto every field selected, [MatchMode.CONTAINS], and [PhraseQuantifier.NONE], which is what this
 * returns, so nothing changes meaning when it is read.
 */
fun phraseConditionFor(rule: SignalRule): PhraseCondition {
    rule.phraseCondition?.let { return it }
    val phrase = rule.phrase.trim()
    val phrases = if (phrase.isEmpty() || phrase.equals("anything", ignoreCase = true)) {
        emptyList()
    } else {
        listOf(phrase)
    }
    return PhraseCondition(
        phrases = phrases,
        quantifier = if (isNegatedMatchType(rule.matchType)) PhraseQuantifier.NONE else PhraseQuantifier.ANY,
        mode = MatchMode.CONTAINS,
        fields = MatchField.ALL,
        caseSensitive = false,
    )
}

/**
 * Stores [condition] on the rule and keeps the older fields saying the same thing.
 *
 * [SignalRule.phrase] and [SignalRule.matchType] are what the rule card, the shortcut label and a
 * build older than this one read. Leaving them behind would make a rule display one condition and
 * evaluate another.
 */
fun SignalRule.withPhraseCondition(condition: PhraseCondition): SignalRule {
    val phrases = condition.phrases.filter { it.isNotBlank() }
    return copy(
        phraseCondition = condition,
        phrase = if (phrases.isEmpty()) "anything" else phrases.joinToString(", "),
        matchType = if (condition.quantifier == PhraseQuantifier.NONE) "doesn't contain" else "contains",
    )
}

/** What the rule card and the builder say a phrase condition does. */
fun describePhraseCondition(condition: PhraseCondition): String {
    if (condition.isEmpty) return "Anything"
    val phrases = condition.phrases.filter { it.isNotBlank() }
    val subject = if (condition.mode == MatchMode.REGEX) "pattern" else "phrase"
    val head = when {
        phrases.size == 1 && condition.quantifier == PhraseQuantifier.NONE -> "Does not contain the $subject"
        phrases.size == 1 -> "Contains the $subject"
        else -> condition.quantifier.label
    }
    val body = phrases.joinToString(", ")
    val fields = if (condition.fields == MatchField.ALL) {
        "any field"
    } else {
        condition.fields.sortedBy { it.ordinal }.joinToString(" or ") { it.label.lowercase() }
    }
    val case = if (condition.caseSensitive) ", case must match" else ""
    return "$head $body, in $fields$case"
}

/**
 * Operators for the content filter and for nested filter groups.
 *
 * The rule's phrase is one string, so "any of" and "all of" would mean the same thing as plain
 * containment. Only the two operators the evaluator implements are offered.
 */
const val DEFAULT_MATCH_TYPE = "contains"
const val NEGATED_MATCH_TYPE = "doesn't contain"
const val DEFAULT_FILTER_OPERATOR = "Contains any"
const val ANY_APP_LABEL = "any app"

data class AppOption(
    val label: String,
    val packageName: String,
)

/** The deterministic app choices used by the reconstruction's app selector. */
val appOptions = listOf(
    AppOption("NoNo", "com.sysadmindoc.nono"),
    AppOption("Messages", "com.google.android.apps.messaging"),
    AppOption("Phone", "com.google.android.dialer"),
    AppOption("Calendar", "com.google.android.calendar"),
    AppOption("Email", "com.google.android.gm"),
    AppOption("Android Auto", "com.google.android.projection.gearhead"),
    AppOption("Bluetooth", "com.android.bluetooth"),
    AppOption("System UI", "com.android.systemui"),
    AppOption("Clock", "com.google.android.deskclock"),
    AppOption("Files", "com.google.android.documentsui"),
)

fun appOptionForLabel(label: String): AppOption? =
    appOptions.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }

val matchTypeCatalog = listOf(DEFAULT_MATCH_TYPE, NEGATED_MATCH_TYPE)

/**
 * Collapses any stored or imported operator onto one the evaluator implements.
 *
 * Deterministic in both directions: anything phrased as a negation becomes
 * [NEGATED_MATCH_TYPE], everything else becomes [DEFAULT_MATCH_TYPE]. The older four-value
 * vocabulary ("contains any of", "doesn't contain all of") maps through here on decode.
 */
fun normalizeMatchType(matchType: String): String {
    // Curly apostrophes reach this from any editor with smart quotes, and the failure direction
    // matters: an unrecognized negation would silently become an affirmative match, so a rule
    // written to exclude something would start selecting it instead.
    val normalized = matchType.trim().lowercase()
        .replace('’', '\'')
        .replace('ʼ', '\'')
    val negated = NEGATION_MARKERS.any { normalized.startsWith(it) }
    return if (negated) NEGATED_MATCH_TYPE else DEFAULT_MATCH_TYPE
}

private val NEGATION_MARKERS = listOf(
    "doesn't",
    "does not",
    "do not",
    "don't",
    "not ",
    "no ",
    "isn't",
    "is not",
    "won't",
    "will not",
    "excludes",
    "exclude",
    "without",
    "lacks",
    "missing",
)

fun isNegatedMatchType(matchType: String): Boolean =
    normalizeMatchType(matchType) == NEGATED_MATCH_TYPE


/** Why a legacy free-string condition cannot be evaluated. */
const val LEGACY_FILTER_MESSAGE = "Imported from an older build and not safe to interpret."

/**
 * Versioned persisted form of the rule list. The version field exists so a store written
 * by an older build can be recognised rather than parsed into nonsense.
 */
@Serializable
data class RuleStore(
    val version: Int = CURRENT_RULE_STORE_VERSION,
    val rules: List<SignalRule> = emptyList(),
    /**
     * The next id to hand out, saved so it survives deleting rules.
     *
     * History records store the rule ids that matched them permanently, so an id must never be
     * reused. Deriving the next one from the live rules alone would recycle it the moment the
     * highest rule was deleted, and old records would start naming a rule they never matched.
     * A file written before this existed has no counter; decode raises it past every stored id.
     */
    val nextRuleId: Long = 1L,
)

const val CURRENT_RULE_STORE_VERSION = 5

data class HistoryRecord(
    val id: Long = 1L,
    val app: String = "Shell",
    val appPackageName: String? = null,
    val title: String = "Audit test notification",
    val body: String = "Sanitized local test record",
    val time: String = "Now",
    val dismissed: Boolean = false,
    val triggeredRule: Boolean = false,
    val contentState: NotificationContentState = NotificationContentState.NOT_STORED,
    val postedAtEpochMillis: Long = 0L,
    val notificationKey: String = "",
    val channelId: String? = null,
    val groupKey: String? = null,
    /** The group the platform imposed, when it imposed one. Null when the app's own group stands. */
    val overrideGroupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val groupSummaryOrigin: GroupSummaryOrigin = GroupSummaryOrigin.UNKNOWN,
    val matchedRuleIds: List<Long> = emptyList(),
    val matchState: RuleMatchState = RuleMatchState.NOT_EVALUATED,
    val importance: Int? = null,
    val isConversation: Boolean? = null,
    val category: String? = null,
    val isOngoing: Boolean = false,
    val starred: Boolean = false,
    /** When the platform said this notification left the shade. Null while it is still there. */
    val removedAtEpochMillis: Long? = null,
    /** Why it left, when the platform said. Never inferred from anything else. */
    val removalReason: RemovalReason = RemovalReason.UNKNOWN,
)

enum class HistoryLoadState {
    LOADING,
    READY,
    ERROR,
}

/** Query contract shared by the history screen and the Room-backed metadata repository. */
data class HistoryQuery(
    val search: String = "",
    val filter: String = "All",
    val packageName: String? = null,
    val channelId: String? = null,
    val contentState: NotificationContentState? = null,
    val groupKey: String? = null,
    val groupSummary: Boolean? = null,
    val importance: Int? = null,
    val conversation: Boolean? = null,
    val fromEpochMillis: Long? = null,
    val limit: Int = HISTORY_PAGE_SIZE,
)

/**
 * Rows loaded per page.
 *
 * History grows the limit rather than stitching pages together, so there is only ever one query
 * behind the list. That is what makes a filter change unable to mix rows selected by two
 * different sets of conditions.
 */
const val HISTORY_PAGE_SIZE = 100

/** Filters the History segmented control offers. Each one is implemented in SQL. */
val historyFilterCatalog = listOf("All", "Rule-triggered", "Starred", "Dismissed")

/**
 * Android's channel importance levels, as the platform numbers them.
 *
 * NotificationManager names these IMPORTANCE_NONE through IMPORTANCE_MAX. The labels are for the
 * history filter, where a raw 0 to 5 would mean nothing to anyone.
 */
val importanceCatalog: List<Pair<Int, String>> = listOf(
    0 to "None",
    1 to "Min",
    2 to "Low",
    3 to "Default",
    4 to "High",
    5 to "Max",
)

fun importanceLabel(importance: Int?): String? =
    importanceCatalog.firstOrNull { it.first == importance }?.second

/**
 * How far rule evaluation got for a captured notification.
 *
 * Recorded per record so history can say which rules matched without storing anything the
 * notification contained. No action is executed either way: this is a record of what a rule would
 * have done, which is the same boundary the dry-run evaluator holds.
 */
enum class RuleMatchState {
    /** No rules were saved when this arrived. */
    NOT_EVALUATED,

    /**
     * The notification arrived before the saved rules had been read from disk, which can happen
     * to the first few captures after the platform starts the listener. Recorded distinctly so it
     * cannot be misread as "your rules were checked and none matched".
     */
    RULES_NOT_LOADED,

    /** Evaluated against the saved rules with the text the platform supplied. */
    EVALUATED,

    /** Evaluated, but the system redacted the text, so phrase conditions could not be tested. */
    CONTENT_HIDDEN,

    /** A group summary was evaluated against rules that explicitly test summary state. */
    GROUP_SUMMARY_EVALUATED,

    /**
     * A group summary. It stands for the group rather than being an arrival of its own, so no
     * rule was tested against it. Recorded distinctly so the row cannot read as "nothing matched".
     */
    GROUP_SUMMARY,
}

/** Where the user-triggered listener check has reached. */
enum class CaptureSelfTestStatus {
    NOT_RUN,
    WAITING_FOR_PERMISSION,
    RUNNING,
    PASSED,
    FAILED,
}

/** Persistent Settings-row state for the current process. No test token or notification data. */
data class CaptureSelfTestState(
    val status: CaptureSelfTestStatus = CaptureSelfTestStatus.NOT_RUN,
    val detail: String = "Post one temporary notification and check that the listener receives it.",
)

/**
 * Who created a group summary, as far as a supported public API can tell.
 *
 * Android has no API that names the author of a summary. What it does publish is whether the app
 * declared a group of its own (`Notification.getGroup`) and whether the platform supplied one
 * (`StatusBarNotification.getOverrideGroupKey`). Together those settle exactly one case, [APP].
 * Everything else is [UNKNOWN], which is the common answer.
 *
 * Sibling rows are deliberately not consulted. A summary usually has children, and an
 * auto-generated one does too, so their presence proves nothing about who wrote it.
 */
enum class GroupSummaryOrigin {
    /** The app declared the group this summary sits in, and the platform did not override it. */
    APP,

    /**
     * The platform authored the summary.
     *
     * Never inferred: AOSP posts its auto-group summary with a group of its own *and* the same
     * value as the override key, so it is indistinguishable from an app summary the platform
     * regrouped without reading a constant that is not public API. The value stays in the enum
     * because stored rows may carry it and because a future platform release may publish a real
     * signal, but nothing assigns it today.
     */
    SYSTEM,

    /** Nothing supported says either way. The default, and the common case. */
    UNKNOWN,
}

/** Provenance of notification content exposed to the app. */
enum class NotificationContentState {
    /** The platform supplied content, but this build intentionally does not persist it. */
    AVAILABLE,

    /**
     * Android or the device OEM replaced sensitive content before it reached the listener.
     *
     * Only present on rows an earlier build stored. Nothing infers this any more: the platform
     * exposes no supported signal for it, and guessing was how the app claimed provenance it
     * did not have.
     */
    HIDDEN_BY_SYSTEM,

    /** No content was supplied, without enough evidence to attribute that to Android redaction. */
    NOT_AVAILABLE,

    /** A metadata-only record created by this build after safe ingestion. */
    NOT_STORED,
}

data class UiState(
    val route: Route = Route.ONBOARDING,
    val rootTab: RootTab = RootTab.RULES,
    val overlay: Overlay = Overlay.NONE,
    val auditState: String = "002_welcome_default",
    val onboardingStep: Int = 0,
    val listenerAccessGranted: Boolean = true,
    val rules: List<SignalRule> = emptyList(),
    val history: List<HistoryRecord> = emptyList(),
    val historyLoadState: HistoryLoadState = HistoryLoadState.LOADING,
    val historyError: String? = null,
    val historySearch: String = "",
    val historySearchActive: Boolean = false,
    val historyFilter: String = "All",
    val historyPackageFilter: String? = null,
    val historyChannelFilter: String? = null,
    val historyGroupFilter: String? = null,
    val historyContentStateFilter: NotificationContentState? = null,
    val historyGroupSummaryOnly: Boolean = false,
    /** Matches per rule id across all stored history, not just the page History is showing. */
    val ruleMatchCounts: Map<Long, Int> = emptyMap(),
    val historyImportanceFilter: Int? = null,
    val historyConversationFilter: Boolean? = null,
    /** How many rows the current query is loading. Grows a page at a time, resets on a change. */
    val historyLimit: Int = HISTORY_PAGE_SIZE,
    /** Rows the current filters select, whatever [historyLimit] has loaded so far. */
    val historyFilteredCount: Int = 0,
    /** Everything retained, ignoring the filters. */
    val historyTotalCount: Int = 0,
    /** Aggregates over everything retained, for the Insights screen. */
    val insights: LocalInsights = LocalInsights(),
    /** True while the lock is hiding every rule and every record behind the unlock screen. */
    val appLocked: Boolean = false,
    /** Whether Android has a screen lock for the app lock to check against. */
    val deviceCredentialAvailable: Boolean = false,
    /** The chosen backup folder's short name, or null when none has been picked. */
    val backupFolderLabel: String? = null,
    /** What the scheduled backup last did. Written by the worker, read here. */
    val backupStatus: BackupStatus = BackupStatus(),
    val transferExportRequest: Int = 0,
    /** True when the pending export is history CSV rather than the encrypted rule file. */
    val transferExportIsHistory: Boolean = false,
    val transferAdditions: Int = 0,
    val transferConflicts: Int = 0,
    val capturePaused: Boolean = false,
    val captureSelfTest: CaptureSelfTestState = CaptureSelfTestState(),
    val historyActivityTab: String = "Rules",
    val selectedHistoryId: Long? = null,
    val draft: SignalRule = SignalRule(name = "New rule"),
    val selectedRuleId: Long? = null,
    val phraseDraft: String = "",
    val phraseInputVisible: Boolean = false,
    val appSearch: String = "",
    /** What the user typed into rule search. Empty when they are not searching. */
    val ruleSearch: String = "",
    /** Whether the rule-search field is open. Separate from the text: an open, empty field is a
     * different screen from a closed one, and the two empty states have to read differently. */
    val ruleSearchActive: Boolean = false,
    /** Sample text typed into the match tester. Never stored and never leaves the screen. */
    val testerTitle: String = "",
    val testerText: String = "",
    /** The metadata row whose picker is open. */
    val selectedMetadataField: MetadataField? = null,
    /** Apps the picker offers: launchable ones merged with everything history has seen. */
    val appCatalog: List<CatalogedApp> = emptyList(),
    val renameDraft: String = "",
    val folderDraft: String = "",
    val settings: Map<String, String> = defaultSettings,
    /** True once the saved rules have been read from disk. An empty list is a real answer. */
    val rulesLoaded: Boolean = false,
    val validationError: String? = null,
    val transientMessage: String? = null,
    /** Offered alongside [transientMessage] when the action it reports can be taken back. */
    val transientUndo: UndoableAction? = null,
    /**
     * Bumped every time a message is set, so the snackbar re-fires for an identical string.
     *
     * Two deletes in a row produce the same text. Without this the effect that shows the snackbar
     * did not restart, so the second one was never offered and its undo was unreachable while the
     * first record had already been replaced.
     */
    val transientMessageId: Long = 0L,
) {
    /** Whether the record whose menu is open is kept past the retention period. */
    val selectedHistoryStarred: Boolean
        get() = history.firstOrNull { it.id == selectedHistoryId }?.starred == true

    /** Package of the record whose menu is open, if one is. */
    val selectedHistoryPackageName: String?
        get() = history.firstOrNull { it.id == selectedHistoryId }?.appPackageName

    /** Content provenance of the record whose menu is open, if one is. */
    val selectedHistoryContentState: NotificationContentState?
        get() = history.firstOrNull { it.id == selectedHistoryId }?.contentState

    /**
     * Sets the snackbar message, and the undo it may carry.
     *
     * Every message goes through here so an undo cannot outlive the message it belongs to. It
     * used to survive a navigation or a later unrelated message, and the Undo button then
     * reappeared next to "Metadata copied" and resurrected a record deleted minutes earlier.
     */
    fun withMessage(message: String?, undo: UndoableAction? = null): UiState = copy(
        transientMessage = message,
        transientUndo = if (message == null) null else undo,
        transientMessageId = transientMessageId + 1,
    )

    /** True when the filters select more rows than have been loaded. */
    val hasMoreHistory: Boolean
        get() = historyFilteredCount > history.size

    /**
     * Starts the window over.
     *
     * Any change to what the list selects has to reset it, or a filter applied while three pages
     * deep would load three pages of the new query and read as though that were all of it.
     */
    fun resetHistoryWindow(): UiState = copy(historyLimit = HISTORY_PAGE_SIZE)
}

val defaultSettings = mapOf(
    "Mute mode" to "Mute all sounds",
    "Mute importance" to "All notifications",
    "Notification history" to "Metadata only",
    "History retention" to "30 days",
    "Theme" to "Dark",
    "Language" to "System default",
    "Automatic backups" to "Off",
    "Widget count" to "All captured",
    "Lock the app" to "Off",
    "Allow dismissing fixed notifications" to "On",
    "Adjust silent ringer mode for calls" to "Off",
    "Privacy mode" to "Off",
    "Hide popups when muting" to "On",
    "Restore batches after reboot" to "Off",
    "Android 15+ icon workaround" to "On",
    "Notification grouping workaround" to "On",
)

val actionCatalog = listOf(
    "Cooldown", "Mute", "Alarm", "Pocket check", "Remind me", "Speak", "Unsilence",
    "Add snooze button", "Batch", "Batch every", "Custom alert", "Flashlight", "Secret",
    "Sticky", "Summarize", "Add share button", "Dismiss", "Keep if", "Undo dismiss",
    "Open notification", "Press button", "Reply", "Copy verification code", "Remove from history",
    "Restore after reboot", "Set ringer", "Trigger MacroDroid", "Trigger Tasker", "Multi-tool"
)

/**
 * The only outcome this build produces: the match is recorded, and the device is left alone.
 *
 * There is no action engine, and adding one was rejected. A rule that could be saved naming
 * Mute or Flashlight would be claiming a capability that does not exist.
 */
const val RECORD_ONLY_ACTION = "record the match"

/** Shown wherever a rule's outcome is summarised, so the absence of an action is explicit. */
const val NO_DEVICE_ACTION_LABEL = "no device action"

/** Why an action from the catalog cannot be saved. */
const val UNSUPPORTED_ACTION_MESSAGE =
    "This build performs no device actions, so that action cannot be saved."

/** Why a rule cannot be given an expiry. */
const val NO_RULE_EXPIRY_MESSAGE = "This build has no scheduler, so a rule cannot expire on its own."

/**
 * True when [action] names a change to the device.
 *
 * An imported file, or a rule saved by an older build, can carry any of the catalog's names.
 * They stay readable, and they are labelled as never executed, but they cannot be saved again.
 */
fun isExecutableAction(action: String): Boolean {
    val normalized = action.trim()
    return normalized.isNotBlank() &&
        !normalized.equals(RECORD_ONLY_ACTION, ignoreCase = true) &&
        !normalized.equals("nothing", ignoreCase = true) &&
        !normalized.equals("Do nothing", ignoreCase = true)
}

/** How a rule's outcome reads anywhere it is summarised. */
fun renderActionSummary(action: String): String = when {
    action.isBlank() || action.equals("nothing", ignoreCase = true) -> "No action chosen"
    isExecutableAction(action) -> "$action (not executed)"
    else -> "Record the match · $NO_DEVICE_ACTION_LABEL"
}

fun renderRuleSentence(rule: SignalRule): String =
    "When I get a notification from ${rule.app} that ${rule.matchType} ${rule.phrase} then ${actionClause(rule.action)}"

/** Wrapped form used on the rule card, which lays the sentence out over four lines. */
fun renderRuleCardSentence(rule: SignalRule): String = buildString {
    appendLine("When I get a notification")
    appendLine("from ${rule.app} that ${rule.matchType}")
    appendLine("\"${rule.phrase}\"")
    append("then ${actionClause(rule.action)}")
}

private fun actionClause(action: String): String = when {
    isExecutableAction(action) -> "do ${action.lowercase()}, which this build never executes"
    else -> "$RECORD_ONLY_ACTION and take $NO_DEVICE_ACTION_LABEL"
}

/** Verbatim validation copy recorded in the audit (V001). */
const val MISSING_FIELD_MESSAGE = "You have a missing field. Please tap to fill it in to complete the rule."

const val INVALID_PATTERN_MESSAGE = "That pattern is not valid. Fix it, or switch back to Contains."
const val NO_FIELD_SELECTED_MESSAGE = "Choose at least one field to search."

fun validateRule(rule: SignalRule): String? = when {
    rule.app.isBlank() -> "Choose an app."
    rule.phrase.isBlank() -> "Choose notification content to match."
    // A pattern that will not compile tests nothing, so a rule carrying one would sit in the list
    // looking active and never match. Refused at the point it can still be corrected.
    invalidPatternIn(rule) -> INVALID_PATTERN_MESSAGE
    rule.phraseCondition?.let { it.fields.isEmpty() && !it.isEmpty } == true -> NO_FIELD_SELECTED_MESSAGE
    rule.action.isBlank() || rule.action == "nothing" -> MISSING_FIELD_MESSAGE
    // An imported rule keeps its action and stays readable, but saving it again would be the
    // app agreeing to carry it out.
    isExecutableAction(rule.action) -> UNSUPPORTED_ACTION_MESSAGE
    else -> null
}

/** True when the rule matches on a pattern and at least one of them will not compile. */
private fun invalidPatternIn(rule: SignalRule): Boolean {
    val condition = rule.phraseCondition ?: return false
    if (condition.mode != MatchMode.REGEX) return false
    return condition.phrases.filter { it.isNotBlank() }.any { !isValidPattern(it) }
}

fun filterHistory(records: List<HistoryRecord>, query: String, filter: String): List<HistoryRecord> = records.filter { record ->
    val matchesQuery = query.isBlank() || listOf(record.app, record.title, record.body).any { it.contains(query, ignoreCase = true) }
    val matchesFilter = when (filter) {
        "Rule-triggered" -> record.triggeredRule || record.matchedRuleIds.isNotEmpty()
        "Dismissed" -> record.dismissed
        else -> true
    }
    matchesQuery && matchesFilter
}
