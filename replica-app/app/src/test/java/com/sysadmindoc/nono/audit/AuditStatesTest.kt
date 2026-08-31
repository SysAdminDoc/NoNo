package com.sysadmindoc.nono.audit

import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the debug-only capture harness. Unit tests compile against the debug variant, so a
 * release build that no-ops these is still correct; what this proves is that moving the
 * harness out of production code did not break state reproduction.
 */
class AuditStatesTest {

    private val base = UiState()

    private fun resolve(id: String): UiState = requireNotNull(auditStateFor(base.copy(auditState = id), id))

    @Test
    fun `history search states now set real search state`() {
        val empty = resolve("014_history_search_empty")
        assertEquals(RootTab.HISTORY, empty.rootTab)
        assertTrue("search must be driven by UiState, not by the audit id", empty.historySearchActive)

        val noResults = resolve("015_history_search_no_results")
        assertTrue(noResults.historySearchActive)
        assertEquals("nothing here", noResults.historySearch)

        assertFalse(resolve("013_history_empty").historySearchActive)
    }

    @Test
    fun `phrase editor states distinguish the chooser from the text input`() {
        // 033 is the Phrase/Extras/Group chooser; 034 and 041 are the text entry dialog.
        assertFalse(resolve("033_phrase_filter_editor").phraseInputVisible)
        assertTrue(resolve("034_phrase_filter_input").phraseInputVisible)

        val filled = resolve("041_phrase_input_filled")
        assertTrue(filled.phraseInputVisible)
        assertEquals("audit phrase", filled.phraseDraft)
        assertEquals(Route.PHRASE_EDITOR, filled.route)
    }

    @Test
    fun `representative states still resolve to their routes and overlays`() {
        assertEquals(Route.ONBOARDING, resolve("002_welcome_default").route)
        assertEquals(RootTab.EXPLORE, resolve("011_explore_default").rootTab)
        assertEquals(Route.APP_SELECTOR, resolve("030_app_selector").route)
        assertEquals(Route.ACTION_SELECTOR, resolve("049_action_selector_top").route)
        assertEquals(Overlay.RULE_MORE, resolve("065_rule_overflow_menu").overlay)
        assertEquals(Overlay.THEME, resolve("027_theme_dialog").overlay)
        assertEquals(Route.HISTORY_ACTIVITY, resolve("073_history_item_activity").route)
        assertEquals("Light", resolve("903_light_rules").settings["Theme"])
    }

    @Test
    fun `every captured audit state resolves`() {
        val ids = listOf(
            "002_welcome_default", "004_welcome_notifications_granted", "006_welcome_background_allowed",
            "010_home_empty", "011_explore_default", "013_history_empty", "016_settings_default",
            "021_mute_mode_dialog", "025_create_shortcut_empty", "029_rule_builder_default",
            "032_condition_match_type_dialog", "036_extras_filter_selector", "039_filter_group_default",
            "044_add_filter_menu", "059_rule_builder_validation_missing", "062_rule_builder_complete",
            "063_rules_populated_test_record", "064_rules_test_record_disabled", "066_rule_enable_for_dialog",
            "067_rule_priority_dialog", "068_rule_folder_dialog", "069_rule_rename_dialog",
            "070_rule_edit_existing", "072_history_notification_detail", "082_explore_suggestion_rule_preview",
            "900_shortcut_selected", "901_phrase_urgent", "902_filter_group_populated", "903_light_rules",
        )
        ids.forEach { assertNotNull("state $it must resolve", auditStateFor(base, it)) }
    }

    @Test
    fun `no captured state names an overlay the app no longer renders`() {
        // Resolving non-null is not enough: an id pointing at a removed overlay resolves to a
        // state that draws nothing, so the capture silently becomes a screenshot of the page
        // behind it. Overlay.NONE is the only value that legitimately draws no dialog.
        val overlayStates = listOf(
            "021_mute_mode_dialog" to Overlay.MUTE_MODE,
            "023_history_storage_dialog" to Overlay.HISTORY_STORAGE,
            "024_history_retention_dialog" to Overlay.HISTORY_RETENTION,
            "027_theme_dialog" to Overlay.THEME,
            "032_condition_match_type_dialog" to Overlay.CONDITION_TYPE,
            "044_add_filter_menu" to Overlay.ADD_FILTER,
            "065_rule_overflow_menu" to Overlay.RULE_MORE,
        )

        overlayStates.forEach { (id, expected) ->
            assertEquals("state $id", expected, resolve(id).overlay)
        }
    }

    @Test
    fun `the filter group captures differ from one another`() {
        // Five ids used to resolve to one identical state once the extras and operator dialogs
        // were removed, so five captures were the same screenshot.
        val states = listOf(
            "036_extras_filter_selector",
            "037_extras_filter_selector_scrolled",
            "038_extras_filter_selector_bottom",
            "039_filter_group_default",
            "040_filter_operator_dialog",
        ).map { resolve(it) }

        states.forEach { assertEquals(Route.FILTER_GROUP, it.route) }
        assertEquals(
            "every id must produce its own capture",
            states.size,
            states.map { it.draft.extras to it.draft.filterOperator }.distinct().size,
        )
    }
}
