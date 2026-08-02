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
    fun `validation reports the audited copy for a missing action`() {
        assertEquals(MISSING_FIELD_MESSAGE, validateRule(SignalRule(action = "nothing")))
        assertNull(validateRule(SignalRule(action = "Mute")))
    }
}
