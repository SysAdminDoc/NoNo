package com.anm.signalrules.reconstruction.audit

import android.content.Intent
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.Overlay
import com.anm.signalrules.reconstruction.model.RootTab
import com.anm.signalrules.reconstruction.model.Route
import com.anm.signalrules.reconstruction.model.SignalRule
import com.anm.signalrules.reconstruction.model.UiState

/**
 * QA-only reproduction of the captured audit states.
 *
 * This file exists solely in the debug variant. The release variant links a no-op twin, so
 * neither the intent extra nor the state table can be reached in a shipping build - product
 * behaviour must never depend on anything here.
 */

private const val AUDIT_STATE_EXTRA = "replica_state"

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
            // 033 is the Phrase/Extras/Group chooser; the rest are the text-entry dialog.
            phraseInputVisible = !id.startsWith("033_"),
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
