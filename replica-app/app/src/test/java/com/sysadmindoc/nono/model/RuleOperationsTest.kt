package com.sysadmindoc.nono.model

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
    fun `an undoable deletion restores the exact rule at its old position`() {
        val result = deleteRuleWithUndo(rules, 2L)!!

        assertEquals(listOf(1L, 3L), result.remaining.map { it.id })
        assertEquals(rules, restoreDeletedRules(result.remaining, result.deletion))
        assertNull(
            "a live rule with the old id must not be overwritten",
            restoreDeletedRules(
                result.remaining + SignalRule(id = 2L, name = "Different rule"),
                result.deletion,
            ),
        )
    }

    @Test
    fun `delete all is restored as one ordered batch without losing later rules`() {
        val result = deleteAllRulesWithUndo(rules)!!
        val createdLater = SignalRule(id = 4L, name = "Created later")

        assertTrue(result.remaining.isEmpty())
        assertEquals(3, result.deletion.count)
        assertEquals(rules + createdLater, restoreDeletedRules(listOf(createdLater), result.deletion))
    }

    @Test
    fun `duplicating a rule appends a copy with a fresh id`() {
        val expanded = duplicateRule(rules, 2L, counter = 4L)

        assertEquals(4, expanded.size)
        assertEquals(4L, expanded.last().id)
        assertEquals("Second copy", expanded.last().name)
        assertEquals(rules, duplicateRule(rules, 99L, counter = 4L))
    }

    @Test
    fun `duplicate then toggle the copy preserves every original`() {
        // The exact sequence that used to destroy the first rule.
        val expanded = duplicateRule(rules, 1L, counter = 4L)
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
    fun `the next id comes from the counter and clears every live rule`() {
        assertEquals(4L, nextRuleId(counter = 4L, rules = rules))
        assertEquals(1L, nextRuleId(counter = 1L, rules = emptyList()))
        // A counter behind the live rules is raised past them rather than colliding.
        assertEquals(11L, nextRuleId(counter = 1L, rules = listOf(SignalRule(id = 10L))))
    }

    @Test
    fun `a deleted rule's id is never handed to a new one`() {
        // History records store the ids that matched them permanently. Recycling an id makes an
        // old record name a rule that had nothing to do with it, which is worse than a gap.
        val afterDeletingTheHighest = listOf(SignalRule(id = 1L), SignalRule(id = 2L))
        val counterAfterThreeRules = 4L

        val allocated = nextRuleId(counterAfterThreeRules, afterDeletingTheHighest)

        assertEquals("the id of the deleted rule 3 must not come back", 4L, allocated)
    }

    @Test
    fun `the counter keeps moving through repeated create and delete`() {
        var counter = 1L
        var live = emptyList<SignalRule>()
        val handedOut = mutableListOf<Long>()

        repeat(20) {
            val id = nextRuleId(counter, live)
            handedOut += id
            counter = advanceRuleCounter(id)
            // Create it, then immediately delete it: the naive schemes reuse the id here.
            live = live + SignalRule(id = id)
            live = removeRule(live, id)
        }

        assertEquals("every id handed out must be distinct", 20, handedOut.distinct().size)
    }

    @Test
    fun `a rule holding the largest possible id does not wrap the next one`() {
        // max + 1 wraps to Long.MIN_VALUE, and every later allocation returned that same value.
        val extremes = listOf(
            SignalRule(id = Long.MAX_VALUE, name = "MAX"),
            SignalRule(id = Long.MIN_VALUE, name = "MIN"),
        )

        val allocated = nextRuleId(counter = 1L, rules = extremes)

        assertTrue("must not collide with a live rule", extremes.none { it.id == allocated })
        // The counter cannot advance past the top of the range, so this is the one case where the
        // allocator falls back to the lowest free id. Pinned deliberately: it is the branch that
        // can reattribute history, and it is reachable only if a live rule already holds
        // Long.MAX_VALUE, which import can no longer introduce.
        assertEquals(1L, allocated)
    }

    @Test
    fun `two new rules saved in a row do not collide`() {
        val existing = listOf(SignalRule(id = Long.MAX_VALUE, name = "MAX", action = RECORD_ONLY_ACTION))

        val firstSaved = resolveSavedRule(existing, SignalRule(name = "First", action = RECORD_ONLY_ACTION), counter = 1L)
        val first = upsertRule(existing, firstSaved)
        val secondSaved = resolveSavedRule(first, SignalRule(name = "Second", action = RECORD_ONLY_ACTION), advanceRuleCounter(firstSaved.id))
        val second = upsertRule(first, secondSaved)

        assertEquals(3, second.size)
        assertEquals(listOf("MAX", "First", "Second"), second.map { it.name })
        assertEquals(3, second.map { it.id }.distinct().size)
    }

    @Test
    fun `the card sentence names the rule's own action rather than a fixed glyph`() {
        // The action still has to be visible. What changed is that an imported one is labelled
        // as never executed rather than reading like something the app does.
        val flashlight = SignalRule(app = "Messages", phrase = "urgent", action = "Flashlight")
        val rendered = renderRuleCardSentence(flashlight)

        assertTrue(rendered.contains("then do flashlight"))
        assertTrue(rendered.contains("never executes"))
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
        assertNull(validateRule(SignalRule(action = RECORD_ONLY_ACTION)))
    }

    @Test
    fun `a rule naming a device action cannot be saved`() {
        // The catalog exists so an imported rule stays readable. Saving one again would be the
        // app agreeing to carry it out, and there is nothing here that can.
        actionCatalog.forEach { action ->
            assertEquals(
                "action $action",
                UNSUPPORTED_ACTION_MESSAGE,
                validateRule(SignalRule(id = 1L, action = action)),
            )
        }
    }

    @Test
    fun `the only savable action is the one this build performs`() {
        assertNull(validateRule(SignalRule(id = 1L, action = RECORD_ONLY_ACTION)))
        assertEquals(MISSING_FIELD_MESSAGE, validateRule(SignalRule(id = 1L, action = "nothing")))
    }

    @Test
    fun `duplicating an imported rule does not create a new executable one`() {
        // Duplicate writes a saved rule without passing through validateRule, so it has to strip
        // the capabilities this build refuses to save rather than copying them forward.
        val imported = listOf(SignalRule(id = 1L, name = "From a file", action = "Mute", enabledFor = "1 hour"))

        val copy = duplicateRule(imported, 1L, counter = 2L).last()

        assertEquals(RECORD_ONLY_ACTION, copy.action)
        assertNull(copy.enabledFor)
        assertNull(validateRule(copy))
        // The original is untouched: it stays readable as what it was.
        assertEquals("Mute", imported.single().action)
    }

    @Test
    fun `duplicating an ordinary rule copies it unchanged apart from its identity`() {
        val ordinary = listOf(SignalRule(id = 1L, name = "Mine", phrase = "invoice", action = RECORD_ONLY_ACTION))

        val copy = duplicateRule(ordinary, 1L, counter = 2L).last()

        assertEquals("Mine copy", copy.name)
        assertEquals("invoice", copy.phrase)
        assertEquals(RECORD_ONLY_ACTION, copy.action)
        assertEquals(2L, copy.id)
    }

    @Test
    fun `an imported action stays readable and is labelled as never executed`() {
        assertEquals("Mute (not executed)", renderActionSummary("Mute"))
        assertEquals("Record the match · no device action", renderActionSummary(RECORD_ONLY_ACTION))
        assertEquals("No action chosen", renderActionSummary("nothing"))
        assertTrue(renderRuleSentence(SignalRule(action = "Mute")).contains("never executes"))
        assertTrue(renderRuleSentence(SignalRule(action = RECORD_ONLY_ACTION)).contains("no device action"))
    }

    @Test
    fun `a rule built without an id has not been saved`() {
        assertEquals(UNSAVED_RULE_ID, SignalRule().id)
        assertEquals(UNSAVED_RULE_ID, SignalRule(name = "Quiet group chats", app = "Messages").id)
    }

    @Test
    fun `saving a suggestion never replaces an existing rule`() {
        // Explore starters carry no id. Before this, the model default was 1 and saving one
        // overwrote whichever rule already held that id.
        val suggestion = SignalRule(name = "Quiet group chats", app = "Messages", phrase = "group", action = "Mute")

        val saved = resolveSavedRule(rules, suggestion, counter = 4L)
        val updated = upsertRule(rules, saved)

        assertEquals(4L, saved.id)
        assertEquals(4, updated.size)
        assertEquals("First", updated.first { it.id == 1L }.name)
        assertEquals("Quiet group chats", updated.first { it.id == 4L }.name)
    }

    @Test
    fun `an allocated id cannot collide with a saved rule`() {
        val gapped = listOf(SignalRule(id = 2L, name = "Two"), SignalRule(id = 9L, name = "Nine"))

        val saved = resolveSavedRule(gapped, SignalRule(name = "New"), counter = 10L)

        // The counter, raised past the highest live id. Gaps are left alone: an id below the
        // counter may already appear in a history record.
        assertEquals(10L, saved.id)
        assertTrue(gapped.none { it.id == saved.id })
    }

    @Test
    fun `editing a disabled rule leaves it disabled`() {
        val disabled = listOf(SignalRule(id = 1L, name = "Off rule", enabled = false))

        val saved = resolveSavedRule(disabled, disabled.first().copy(phrase = "invoice", action = "Mute"), counter = 2L)
        val updated = upsertRule(disabled, saved)

        assertEquals(1, updated.size)
        assertEquals(1L, saved.id)
        assertEquals(false, updated.single().enabled)
        assertEquals("invoice", updated.single().phrase)
    }

    @Test
    fun `editing a saved rule replaces it rather than adding a copy`() {
        val saved = resolveSavedRule(rules, rules[1].copy(name = "Renamed"), counter = 4L)
        val updated = upsertRule(rules, saved)

        assertEquals(3, updated.size)
        assertEquals(2L, saved.id)
        assertEquals("Renamed", updated.first { it.id == 2L }.name)
    }

    @Test
    fun `a blank name is filled in without touching the id`() {
        val saved = resolveSavedRule(rules, SignalRule(name = "", action = "Mute"), counter = 4L)

        assertEquals("Rule 4", saved.name)
        assertEquals(4L, saved.id)
    }
}
