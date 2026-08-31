package com.sysadmindoc.nono.model

import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSearchTest {

    private val rules = listOf(
        SignalRule(
            id = 1L,
            name = "Silence delivery pings",
            app = "Parcel Tracker",
            appPackageName = "com.example.parcel",
            phrase = "out for delivery",
            action = RECORD_ONLY_ACTION,
            folder = "Shopping",
            priority = "Low",
        ),
        SignalRule(
            id = 2L,
            name = "Work chat",
            app = "Teamspace",
            appPackageName = "com.example.teamspace",
            phrase = "standup",
            matchType = "doesn't contain",
            action = RECORD_ONLY_ACTION,
            folder = "Work",
            priority = "High",
        ),
        SignalRule(
            id = 3L,
            name = "Imported rule",
            app = "Parcel Tracker",
            appPackageName = "com.other.parcel",
            phrase = "arriving",
            action = "Mute",
            folder = "No folder",
            priority = "Normal",
        ),
    )

    @Test
    fun `an empty query is the whole list, not an empty one`() {
        // An open search field with nothing typed has to show everything. Returning nothing would
        // make the field look like it had already filtered something out.
        assertEquals(rules, filterRules(rules, ""))
        assertEquals(rules, filterRules(rules, "   "))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(listOf(2L), filterRules(rules, "TEAMSPACE").map { it.id })
        assertEquals(listOf(2L), filterRules(rules, "teamspace").map { it.id })
        assertEquals(listOf(2L), filterRules(rules, "TeAmSpAcE").map { it.id })
    }

    @Test
    fun `the app label finds every rule written against that app`() {
        assertEquals(listOf(1L, 3L), filterRules(rules, "parcel tracker").map { it.id })
    }

    @Test
    fun `the package tells two apps with the same label apart`() {
        // Both rules say "Parcel Tracker". The package is the only thing that separates them.
        assertEquals(listOf(1L), filterRules(rules, "com.example.parcel").map { it.id })
        assertEquals(listOf(3L), filterRules(rules, "com.other.parcel").map { it.id })
    }

    @Test
    fun `the phrase, the operator, the folder and the priority are all searchable`() {
        assertEquals(listOf(1L), filterRules(rules, "out for delivery").map { it.id })
        assertEquals(listOf(2L), filterRules(rules, "doesn't contain").map { it.id })
        assertEquals(listOf(1L), filterRules(rules, "shopping").map { it.id })
        assertEquals(listOf(2L), filterRules(rules, "high").map { it.id })
    }

    @Test
    fun `the action is searchable by what the card says, not only by the stored word`() {
        // The card shows the rendered summary, so that is what a user would type.
        assertEquals(listOf(1L, 2L), filterRules(rules, "record the match").map { it.id })
        assertEquals(listOf(3L), filterRules(rules, "mute").map { it.id })
    }

    @Test
    fun `results keep the order the list already had`() {
        // Re-ranking by relevance would move rules under the query and leave them somewhere new
        // when it cleared.
        val matching = filterRules(rules, "parcel")
        assertEquals(listOf(1L, 3L), matching.map { it.id })
        assertEquals(rules.filter { it in matching }, matching)
    }

    @Test
    fun `a query matching nothing returns nothing rather than everything`() {
        assertTrue(filterRules(rules, "nothing here matches this").isEmpty())
    }

    @Test
    fun `a thousand rules filter well inside a frame`() {
        val many = (1L..1_000L).map { id ->
            SignalRule(
                id = id,
                name = "Rule $id",
                app = "App ${id % 40}",
                appPackageName = "com.example.app${id % 40}",
                phrase = "phrase $id",
                action = RECORD_ONLY_ACTION,
                folder = "Folder ${id % 12}",
            )
        }

        // Warm the JIT so the measurement is of the filter and not of class loading.
        repeat(5) { filterRules(many, "phrase 7") }
        // The best of several attempts, not one. A single timing is a measurement of whatever else
        // the machine was doing, and this failed at 17ms on 2026-08-31 with an emulator and a
        // Gradle daemon running beside it while the same code passed minutes earlier. The fastest
        // attempt is the one where the filter had the CPU to itself, which is what is being
        // measured. Noise can only make an attempt slower, so this cannot hide a regression: work
        // that has become quadratic is far slower than the budget in every attempt.
        val elapsed = (1..7).minOf { measureTimeMillis { repeat(10) { filterRules(many, "phrase 7") } } }

        // A frame is about 16ms. Ten passes inside one frame leaves an order of magnitude of head
        // room for a linear scan over a thousand rules.
        assertTrue("the fastest of seven attempts at ten passes over 1000 rules took ${elapsed}ms", elapsed < 16L)
        // "phrase 7", then 70-79, then 700-799.
        assertEquals(111, filterRules(many, "phrase 7").size)
    }

    @Test
    fun `matching one rule is the same question as filtering the list`() {
        for (rule in rules) {
            for (query in listOf("parcel", "work", "mute", "", "nothing")) {
                assertEquals(
                    "$query against ${rule.id}",
                    rule in filterRules(rules, query),
                    ruleMatchesSearch(rule, query),
                )
            }
        }
    }
}
