package com.sysadmindoc.nono.audit

import android.content.Intent
import com.sysadmindoc.nono.data.CatalogedApp
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.ui.DYNAMIC_THEME

/**
 * QA-only reproduction of the captured audit states.
 *
 * This file exists solely in the debug variant. The release variant links a no-op twin, so
 * neither the intent extra nor the state table can be reached in a shipping build - product
 * behaviour must never depend on anything here.
 */

private const val AUDIT_STATE_EXTRA = "replica_state"

private val designHistoryRecords = listOf(
    HistoryRecord(
        id = 1L,
        app = "Messages",
        appPackageName = "com.google.android.apps.messaging",
        title = "Content available",
        body = "Notification metadata captured",
        time = "8:42 PM",
        triggeredRule = true,
        contentState = NotificationContentState.AVAILABLE,
        channelId = "messages",
        matchedRuleIds = listOf(1L),
        matchState = RuleMatchState.EVALUATED,
        importance = 3,
        isConversation = true,
        category = "msg",
    ),
    HistoryRecord(
        id = 2L,
        app = "Calendar",
        appPackageName = "com.google.android.calendar",
        title = "Content hidden by Android",
        body = "Sensitive content was redacted",
        time = "6:15 PM",
        contentState = NotificationContentState.HIDDEN_BY_SYSTEM,
        matchState = RuleMatchState.CONTENT_HIDDEN,
    ),
    HistoryRecord(
        id = 3L,
        app = "System UI",
        appPackageName = "com.android.systemui",
        title = "Metadata captured",
        body = "No notification content was retained",
        time = "2:09 PM",
        contentState = NotificationContentState.NOT_STORED,
        matchState = RuleMatchState.EVALUATED,
    ),
)

/**
 * A fixed picker catalog for the app-selector captures.
 *
 * Two entries share the label "Messages" on purpose: that is the case the picker has to
 * disambiguate, and it is the one a device might not happen to provide.
 */
private val designAppCatalog = listOf(
    CatalogedApp("Calendar", "com.google.android.calendar"),
    CatalogedApp("Clock", "com.google.android.deskclock"),
    CatalogedApp("Messages", "com.google.android.apps.messaging", duplicateLabel = true),
    CatalogedApp("Messages", "com.example.othermessenger", duplicateLabel = true),
    CatalogedApp("Phone", "com.google.android.dialer"),
    CatalogedApp("Retired app", "com.example.retired", installed = false),
)

fun readAuditState(intent: Intent): String = intent.getStringExtra(AUDIT_STATE_EXTRA).orEmpty()

fun auditStateFor(base: UiState, id: String): UiState? = when {
        id.startsWith("002_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 0)
        id.startsWith("004_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 1)
        id.startsWith("006_") -> base.copy(route = Route.ONBOARDING, onboardingStep = 2)
        id.startsWith("010_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = emptyList())
        id.startsWith("011_") || id.startsWith("077_") || id.startsWith("078_") || id.startsWith("079_") || id.startsWith("080_") || id.startsWith("081_") -> base.copy(route = Route.ROOT, rootTab = RootTab.EXPLORE)
        id.startsWith("013_") || id.startsWith("014_") || id.startsWith("015_") || id.startsWith("071_") || id.startsWith("075_") || id.startsWith("076_") -> base.copy(
        historySearchActive = id.startsWith("014_") || id.startsWith("015_"),
            route = Route.ROOT,
            rootTab = RootTab.HISTORY,
            history = if (id.startsWith("071_")) designHistoryRecords else emptyList(),
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
        // The picker reads the device now, so the catalog is seeded here to keep the capture
        // reproducible on any machine.
        id.startsWith("030_") || id.startsWith("031_") -> base.copy(
            route = Route.APP_SELECTOR,
            appSearch = if (id.startsWith("031_")) "mess" else "",
            appCatalog = designAppCatalog,
        )
        id.startsWith("032_") -> base.copy(route = Route.RULE_BUILDER, overlay = Overlay.CONDITION_TYPE)
        id.startsWith("033_") || id.startsWith("034_") || id.startsWith("041_") || id.startsWith("045_") || id.startsWith("046_") || id.startsWith("047_") || id.startsWith("048_") -> base.copy(
            route = Route.PHRASE_EDITOR,
            // 033 is the Phrase/Extras/Group chooser; the rest are the text-entry dialog.
            phraseInputVisible = !id.startsWith("033_"),
            phraseDraft = if (id.startsWith("041_") || id.startsWith("045_") || id.startsWith("046_") || id.startsWith("047_") || id.startsWith("048_")) "audit phrase" else "",
        )
        // The extras selector and the filter-operator dialog are gone: nothing evaluates either,
        // so the rule builder no longer offers them. All five ids land on the one screen that
        // still shows those properties, distinguished by whether the draft carries any. A rule
        // that arrived by import can, which is the only way they exist now.
        id.startsWith("039_") -> base.copy(route = Route.FILTER_GROUP)
        id.startsWith("036_") || id.startsWith("037_") || id.startsWith("038_") || id.startsWith("040_") -> base.copy(
            route = Route.FILTER_GROUP,
            draft = base.draft.copy(
                extras = when {
                    id.startsWith("038_") -> listOf("Image", "Category", "Text length")
                    id.startsWith("037_") -> listOf("Image", "Phone number")
                    else -> listOf("Image")
                },
                filterOperator = if (id.startsWith("040_")) "Doesn't contain any" else base.draft.filterOperator,
            ),
        )
        id.startsWith("044_") -> base.copy(route = Route.RULE_BUILDER, overlay = Overlay.ADD_FILTER)
        id.startsWith("049_") || id.startsWith("050_") || id.startsWith("051_") || id.startsWith("052_") || id.startsWith("053_") || id.startsWith("054_") || id.startsWith("055_") || id.startsWith("056_") || id.startsWith("057_") || id.startsWith("058_") || id.startsWith("061_") -> base.copy(route = Route.ACTION_SELECTOR, draft = if (id.startsWith("061_")) base.draft.copy(action = "Mute") else base.draft)
        id.startsWith("059_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "New rule", phrase = "anything", action = "nothing"), transientMessage = null)
        id.startsWith("035_") || id.startsWith("042_") || id.startsWith("060_") || id.startsWith("062_") || id.startsWith("043_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "New rule", app = "NoNo", phrase = "audit phrase", action = if (id.startsWith("062_")) RECORD_ONLY_ACTION else "nothing"))
        id.startsWith("063_") || id.startsWith("085_") || id.startsWith("087_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute", enabled = true)))
        id.startsWith("064_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute", enabled = false)))
        id.startsWith("065_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute")), overlay = Overlay.RULE_MORE)
        id.startsWith("066_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute", enabledFor = "1 hour")), selectedRuleId = 1L, overlay = Overlay.RULE_MORE)
        id.startsWith("067_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute")), overlay = Overlay.PRIORITY)
        id.startsWith("068_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute")), overlay = Overlay.FOLDER)
        id.startsWith("069_") -> base.copy(route = Route.ROOT, rootTab = RootTab.RULES, rules = listOf(SignalRule(id = 1L, action = "Mute")), overlay = Overlay.RENAME, renameDraft = "Test rule")
        id.startsWith("070_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(id = 1L, action = "Mute"), rules = listOf(SignalRule(id = 1L, action = "Mute")))
        id.startsWith("072_") -> base.copy(route = Route.ROOT, rootTab = RootTab.HISTORY, history = listOf(HistoryRecord()), overlay = Overlay.HISTORY_ITEM)
        id.startsWith("073_") || id.startsWith("074_") -> base.copy(
            route = Route.HISTORY_ACTIVITY,
            historyActivityTab = if (id.startsWith("074_")) "Changes" else "Rules",
            history = listOf(designHistoryRecords.first()),
            selectedHistoryId = 1L,
            rules = listOf(SignalRule(id = 1L, action = "Mute")),
        )
        id.startsWith("900_shortcut_selected") -> base.copy(
            route = Route.SHORTCUT_EDITOR,
            rules = listOf(SignalRule(id = 1L, action = "Mute")),
        )
        id.startsWith("901_phrase_urgent") -> base.copy(
            route = Route.PHRASE_EDITOR,
            phraseInputVisible = true,
            phraseDraft = "urgent",
            draft = SignalRule(phrase = "urgent", action = "Mute"),
        )
        id.startsWith("902_filter_group_populated") -> base.copy(
            route = Route.FILTER_GROUP,
            draft = SignalRule(extras = listOf("Conversation")),
        )
        id.startsWith("903_light_rules") -> base.copy(
            route = Route.ROOT,
            rootTab = RootTab.RULES,
            rules = listOf(SignalRule(id = 1L, action = "Mute", enabled = true)),
            settings = base.settings + ("Theme" to "Light"),
        )
        id.startsWith("904_dynamic_rules") -> base.copy(
            route = Route.ROOT,
            rootTab = RootTab.RULES,
            rules = listOf(SignalRule(id = 1L, action = "Mute", enabled = true)),
            settings = base.settings + ("Theme" to DYNAMIC_THEME),
        )
        id.startsWith("082_") -> base.copy(route = Route.RULE_BUILDER, draft = SignalRule(name = "Flashlight suggestion", app = "Messages", phrase = "urgent", action = "Flashlight"))
        else -> base
    }
