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
    MUTE_MODE,
    MUTE_IMPORTANCE,
    HISTORY_STORAGE,
    HISTORY_RETENTION,
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

const val CURRENT_RULE_STORE_VERSION = 2

data class HistoryRecord(
    val id: Long = 1L,
    val app: String = "Shell",
    val title: String = "Audit test notification",
    val body: String = "Sanitized local test record",
    val time: String = "Now",
    val dismissed: Boolean = false,
    val triggeredRule: Boolean = false,
    val contentState: NotificationContentState = NotificationContentState.NOT_STORED,
    val postedAtEpochMillis: Long = 0L,
    val notificationKey: String = "",
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
)

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
    val historySearch: String = "",
    val historySearchActive: Boolean = false,
    val historyFilter: String = "All",
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
)

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
        "Rule-triggered" -> record.triggeredRule
        "Dismissed" -> record.dismissed
        else -> true
    }
    matchesQuery && matchesFilter
}
