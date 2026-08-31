package com.sysadmindoc.nono.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grouping the rules list by folder.
 *
 * The property that matters most is the one about doing nothing: a user who has never used folders
 * must get exactly the list they already know, in the same order, with no heading added.
 */
class RuleFoldersTest {

    private fun rule(id: Long, folder: String = NO_FOLDER) =
        SignalRule(id = id, name = "Rule $id", folder = folder)

    @Test
    fun anEmptyListHasNothingToGroup() {
        assertEquals(emptyList<RuleGroup>(), groupRulesByFolder(emptyList()))
    }

    @Test
    fun withNothingFiledTheListIsUnchangedAndUnheaded() {
        val rules = listOf(rule(1), rule(2), rule(3))

        val groups = groupRulesByFolder(rules)

        assertEquals(1, groups.size)
        assertNull("no folder is in use, so no heading belongs on the screen", groups.single().heading)
        assertEquals(rules, groups.single().rules)
    }

    @Test
    fun blankAndWhitespaceFoldersCountAsUnfiled() {
        // The folder is free text, so a user can type spaces into it. A heading of "   " would be
        // an empty row the list gained for no reason the user could see.
        val rules = listOf(rule(1, folder = ""), rule(2, folder = "   "), rule(3))

        val groups = groupRulesByFolder(rules)

        assertEquals(1, groups.size)
        assertNull(groups.single().heading)
        assertEquals(rules, groups.single().rules)
    }

    @Test
    fun oneFiledRuleIsEnoughToGroupTheWholeList() {
        val rules = listOf(rule(1, folder = "Work"), rule(2), rule(3))

        val groups = groupRulesByFolder(rules)

        assertEquals(listOf("Work", NO_FOLDER), groups.map { it.heading })
        assertEquals(listOf(1L), groups.first().rules.map { it.id })
        assertEquals(listOf(2L, 3L), groups.last().rules.map { it.id })
    }

    @Test
    fun unfiledRulesComeLastRatherThanFirst() {
        // They arrive first in the list itself. Rendering them above the first folder heading with
        // nothing said about them reads as a rendering mistake rather than as a group.
        val rules = listOf(rule(1), rule(2, folder = "Work"), rule(3))

        assertEquals(listOf("Work", NO_FOLDER), groupRulesByFolder(rules).map { it.heading })
    }

    @Test
    fun foldersAppearInTheOrderTheRulesIntroduceThem() {
        // Not alphabetical: filing one rule would then rearrange the whole screen.
        val rules = listOf(
            rule(1, folder = "Work"),
            rule(2, folder = "Alerts"),
            rule(3, folder = "Work"),
            rule(4, folder = "Bills"),
        )

        assertEquals(listOf("Work", "Alerts", "Bills"), groupRulesByFolder(rules).map { it.heading })
    }

    @Test
    fun orderInsideAGroupIsTheOrderTheUserArranged() {
        val rules = listOf(
            rule(9, folder = "Work"),
            rule(3, folder = "Work"),
            rule(7, folder = "Work"),
        )

        assertEquals(listOf(9L, 3L, 7L), groupRulesByFolder(rules).single().rules.map { it.id })
    }

    @Test
    fun everyRuleAppearsExactlyOnce() {
        val rules = listOf(
            rule(1, folder = "Work"),
            rule(2),
            rule(3, folder = "Bills"),
            rule(4, folder = "Work"),
            rule(5, folder = "   "),
        )

        val grouped = groupRulesByFolder(rules).flatMap { it.rules }

        assertEquals(rules.size, grouped.size)
        assertEquals(rules.map { it.id }.toSet(), grouped.map { it.id }.toSet())
        assertTrue("no rule may be listed twice", grouped.map { it.id }.distinct().size == grouped.size)
    }

    @Test
    fun aFolderTypedWithStrayWhitespaceIsTheSameFolder() {
        val rules = listOf(rule(1, folder = "Work"), rule(2, folder = " Work "))

        val groups = groupRulesByFolder(rules)

        assertEquals(listOf("Work"), groups.map { it.heading })
        assertEquals(listOf(1L, 2L), groups.single().rules.map { it.id })
    }

    @Test
    fun everyRuleFiledMeansNoUnfiledGroup() {
        val rules = listOf(rule(1, folder = "Work"), rule(2, folder = "Bills"))

        assertEquals(listOf("Work", "Bills"), groupRulesByFolder(rules).map { it.heading })
    }
}
