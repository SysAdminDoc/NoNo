package com.anm.signalrules.reconstruction.model

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
    SHORTCUT_EDITOR
}

enum class Overlay {
    NONE,
    CONDITION_TYPE,
    CONDITION_EXTRAS,
    FILTER_OPERATOR,
    ADD_FILTER,
    RULE_MORE,
    ENABLE_FOR,
    PRIORITY,
    FOLDER,
    RENAME,
    HISTORY_ITEM,
    HISTORY_FILTERS,
    CONTENT_HIDDEN,
    MUTE_MODE,
    MUTE_IMPORTANCE,
    HISTORY_STORAGE,
    HISTORY_RETENTION,
    TRANSFER_EXPORT_PASSPHRASE,
    TRANSFER_IMPORT_PASSPHRASE,
    TRANSFER_PREVIEW,
    THEME,
    LANGUAGE
}

/** Sentinel id for a rule that has never been saved. */
const val UNSAVED_RULE_ID = 0L

@Serializable
data class SignalRule(
    val id: Long = 1L,
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
    val extras: List<String> = emptyList(),
    val filterOperator: String = DEFAULT_FILTER_OPERATOR,
    val enabledFor: String? = null,
)

/**
 * Operators recorded in the audit for the content filter and for nested filter groups.
 *
 * The default sentence token is the bare verb "contains", exactly as captured; the four
 * explicit operators are what the match-type dialog offers once the user opens it.
 */
const val DEFAULT_MATCH_TYPE = "contains"
const val DEFAULT_FILTER_OPERATOR = "Contains any"
const val ANY_APP_LABEL = "any app"

data class AppOption(
    val label: String,
    val packageName: String,
)

/** The deterministic app choices used by the reconstruction's app selector. */
val appOptions = listOf(
    AppOption("Signal Rules", "com.anm.signalrules.reconstruction"),
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

val matchTypeCatalog = listOf(
    "contains any of",
    "contains all of",
    "doesn't contain any of",
    "doesn't contain all of",
)

val filterOperatorCatalog = listOf(
    "Contains any",
    "Contains all",
    "Doesn't contain any",
    "Doesn't contain all",
)

val enableForCatalog = listOf(
    "10 mins", "30 mins", "1 hour", "6 hours", "8 hours", "12 hours", "1 day", "7 days",
)

/**
 * Versioned persisted form of the rule list. The version field exists so a store written
 * by an older build can be recognised rather than parsed into nonsense.
 */
@Serializable
data class RuleStore(
    val version: Int = CURRENT_RULE_STORE_VERSION,
    val rules: List<SignalRule> = emptyList(),
)

const val CURRENT_RULE_STORE_VERSION = 3

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
    val isGroupSummary: Boolean = false,
    val matchedRuleIds: List<Long> = emptyList(),
    val matchState: RuleMatchState = RuleMatchState.NOT_EVALUATED,
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
    val fromEpochMillis: Long? = null,
    val limit: Int = 100,
)

/**
 * How far rule evaluation got for a captured notification.
 *
 * Recorded per record so history can say which rules matched without storing anything the
 * notification contained. No action is executed either way: this is a record of what a rule would
 * have done, which is the same boundary the dry-run evaluator holds.
 */
enum class RuleMatchState {
    /** No rules were saved when this arrived, or evaluation could not run. */
    NOT_EVALUATED,

    /** Evaluated against the saved rules with the text the platform supplied. */
    EVALUATED,

    /** Evaluated, but the system redacted the text, so phrase conditions could not be tested. */
    CONTENT_HIDDEN,
}

/** Provenance of notification content exposed to the app. */
enum class NotificationContentState {
    /** The platform supplied content, but this build intentionally does not persist it. */
    AVAILABLE,

    /** Android or the device OEM replaced sensitive content before it reached the listener. */
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
    val transferExportRequest: Int = 0,
    val transferAdditions: Int = 0,
    val transferConflicts: Int = 0,
    val capturePaused: Boolean = false,
    val historyActivityTab: String = "Rules",
    val selectedHistoryId: Long? = null,
    val draft: SignalRule = SignalRule(name = "New rule"),
    val selectedRuleId: Long? = null,
    val phraseDraft: String = "",
    val phraseInputVisible: Boolean = false,
    val appSearch: String = "",
    val renameDraft: String = "",
    val folderDraft: String = "",
    val settings: Map<String, String> = defaultSettings,
    val validationError: String? = null,
    val transientMessage: String? = null,
) {
    /** Content provenance of the record whose menu is open, if one is. */
    val selectedHistoryContentState: NotificationContentState?
        get() = history.firstOrNull { it.id == selectedHistoryId }?.contentState
}

val defaultSettings = mapOf(
    "Mute mode" to "Mute all sounds",
    "Mute importance" to "All notifications",
    "Notification history" to "Store notification metadata",
    "History retention" to "30 days",
    "Theme" to "Dark",
    "Language" to "System default",
    "Automatic backups" to "Off",
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

val extraFilterCatalog = listOf(
    "Image", "Phone number", "Emoji", "Group conversation", "Language", "Custom layout",
    "Fixed notification", "Media notification", "Category", "Image of", "Text length"
)

fun renderRuleSentence(rule: SignalRule): String =
    "When I get a notification from ${rule.app} that ${rule.matchType} ${rule.phrase} then do ${rule.action}"

/** Wrapped form used on the rule card, which lays the sentence out over four lines. */
fun renderRuleCardSentence(rule: SignalRule): String = buildString {
    appendLine("When I get a notification")
    appendLine("from ${rule.app} that ${rule.matchType}")
    appendLine("\"${rule.phrase}\"")
    append("then ${rule.action.lowercase()}")
}

/** Verbatim validation copy recorded in the audit (V001). */
const val MISSING_FIELD_MESSAGE = "You have a missing field. Please tap to fill it in to complete the rule."

fun validateRule(rule: SignalRule): String? = when {
    rule.app.isBlank() -> "Choose an app."
    rule.phrase.isBlank() -> "Choose notification content to match."
    rule.action.isBlank() || rule.action == "nothing" -> MISSING_FIELD_MESSAGE
    else -> null
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
