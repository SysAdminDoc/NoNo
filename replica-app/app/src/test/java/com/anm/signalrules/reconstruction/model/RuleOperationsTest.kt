package com.anm.signalrules.reconstruction.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleOperationsTest {

    private val rules = listOf(
        SignalRule(id = 1L, name = "First", enabled = true),
        SignalRule(id = 2L, name = "Second", enabled = true),
        SignalRule(id = 3L, name = "Third", enabled = true),
    )

    @Test
    fun `toggling one rule leaves the others untouched`() {
        val updated = applyToRule(rules, 2L) { it.copy(enabled = !it.enabled) }

        assertEquals(3, updated.size)
        assertEquals(listOf(true, false, true), updated.map { it.enabled })
        assertEquals(listOf("First", "Second", "Third"), updated.map { it.name })
    }

    @Test
    fun `addressing an unknown rule changes nothing`() {
        assertEquals(rules, applyToRule(rules, 99L) { it.copy(name = "clobbered") })
        assertEquals(rules, applyToRule(rules, null) { it.copy(name = "clobbered") })
    }

    @Test
    fun `deleting one rule keeps the rest in order`() {
        val remaining = removeRule(rules, 1L)

        assertEquals(listOf(2L, 3L), remaining.map { it.id })
        assertEquals(rules, removeRule(rules, null))
        assertEquals(rules, removeRule(rules, 42L))
    }

    @Test
    fun `duplicating a rule appends a copy with a fresh id`() {
        val expanded = duplicateRule(rules, 2L)

        assertEquals(4, expanded.size)
        assertEquals(4L, expanded.last().id)
        assertEquals("Second copy", expanded.last().name)
        assertEquals(rules, duplicateRule(rules, 99L))
    }

    @Test
    fun `duplicate then toggle the copy preserves every original`() {
        // The exact sequence that used to destroy the first rule.
        val expanded = duplicateRule(rules, 1L)
        val copyId = expanded.last().id
        val toggled = applyToRule(expanded, copyId) { it.copy(enabled = false) }

        assertEquals(4, toggled.size)
        assertTrue("originals must stay enabled", toggled.take(3).all { it.enabled })
        assertEquals(false, toggled.last().enabled)
        assertEquals(listOf("First", "Second", "Third", "First copy"), toggled.map { it.name })
    }

    @Test
    fun `upsert appends new rules and replaces existing ones`() {
        val added = upsertRule(rules, SignalRule(id = 4L, name = "Fourth"))
        assertEquals(4, added.size)

        val replaced = upsertRule(rules, SignalRule(id = 2L, name = "Renamed"))
        assertEquals(3, replaced.size)
        assertEquals("Renamed", replaced.first { it.id == 2L }.name)
        assertEquals(listOf(1L, 2L, 3L), replaced.map { it.id })
    }

    @Test
    fun `next id is unique against the highest existing id`() {
        assertEquals(4L, nextRuleId(rules))
        assertEquals(1L, nextRuleId(emptyList()))
        assertEquals(11L, nextRuleId(listOf(SignalRule(id = 10L))))
    }

    @Test
    fun `the card sentence names the rule's own action rather than a fixed glyph`() {
        val flashlight = SignalRule(app = "Messages", phrase = "urgent", action = "Flashlight")
        val rendered = renderRuleCardSentence(flashlight)

        assertTrue(rendered.contains("then flashlight"))
        assertTrue(rendered.contains("from Messages that contains"))
        assertTrue(rendered.contains("\"urgent\""))
        assertEquals(4, rendered.lines().size)
    }

    @Test
    fun `history filtering honours both the query and the segmented filter`() {
        val records = listOf(
            HistoryRecord(id = 1L, app = "Messages", title = "Code 123", body = "verification", triggeredRule = true),
            HistoryRecord(id = 2L, app = "Calendar", title = "Standup", body = "meeting", dismissed = true),
        )

        assertEquals(2, filterHistory(records, "", "All").size)
        assertEquals(listOf(1L), filterHistory(records, "verification", "All").map { it.id })
        assertEquals(listOf(1L), filterHistory(records, "", "Rule-triggered").map { it.id })
        assertEquals(listOf(2L), filterHistory(records, "", "Dismissed").map { it.id })
        assertEquals(emptyList<Long>(), filterHistory(records, "nothing here", "All").map { it.id })
    }

    @Test
    fun `dialog selections survive on the rule they were applied to`() {
        val configured = applyToRule(rules, 2L) {
            it.copy(
                matchType = "doesn't contain any of",
                extras = listOf("Image", "Emoji"),
                filterOperator = "Contains all",
                enabledFor = "30 mins",
            )
        }

        val target = configured.first { it.id == 2L }
        assertEquals("doesn't contain any of", target.matchType)
        assertEquals(listOf("Image", "Emoji"), target.extras)
        assertEquals("Contains all", target.filterOperator)
        assertEquals("30 mins", target.enabledFor)

        // Untouched rules keep the audited defaults.
        val other = configured.first { it.id == 1L }
        assertEquals(DEFAULT_MATCH_TYPE, other.matchType)
        assertEquals(DEFAULT_FILTER_OPERATOR, other.filterOperator)
        assertEquals(emptyList<String>(), other.extras)
        assertNull(other.enabledFor)
    }

    @Test
    fun `the default sentence token is the audited bare verb`() {
        assertEquals("contains", DEFAULT_MATCH_TYPE)
        assertTrue(renderRuleSentence(SignalRule()).contains("that contains anything"))
    }

    @Test
    fun `validation reports the audited copy for a missing action`() {
        assertEquals(MISSING_FIELD_MESSAGE, validateRule(SignalRule(action = "nothing")))
        assertNull(validateRule(SignalRule(action = "Mute")))
    }
}
