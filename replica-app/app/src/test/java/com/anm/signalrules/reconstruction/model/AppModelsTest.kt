package com.anm.signalrules.reconstruction.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModelsTest {
    @Test
    fun actionCatalog_preservesAuditedOrderAndSize() {
        assertEquals(29, actionCatalog.size)
        assertEquals("Cooldown", actionCatalog.first())
        assertEquals("Multi-tool", actionCatalog.last())
        assertEquals(1, actionCatalog.indexOf("Mute"))
    }

    @Test
    fun missingAction_usesExactAuditedValidationCopy() {
        val result = validateRule(SignalRule(action = "nothing"))
        assertEquals("You have a missing field. Please tap to fill it in to complete the rule.", result)
    }

    @Test
    fun completedRule_isValidAndRendersNaturalLanguage() {
        val rule = SignalRule(app = "Messages", phrase = "urgent", action = "Flashlight")
        assertNull(validateRule(rule))
        assertEquals("When I get a notification from Messages that contains urgent then do Flashlight", renderRuleSentence(rule))
    }

    @Test
    fun historyFiltering_isDeterministic() {
        val records = listOf(
            HistoryRecord(id = 1, app = "Messages", title = "Urgent", body = "Please call", triggeredRule = true),
            HistoryRecord(id = 2, app = "Calendar", title = "Reminder", body = "Meeting", dismissed = true),
        )
        assertEquals(listOf(1L), filterHistory(records, "call", "All").map { it.id })
        assertEquals(listOf(1L), filterHistory(records, "", "Rule-triggered").map { it.id })
        assertEquals(listOf(2L), filterHistory(records, "", "Dismissed").map { it.id })
    }
}
