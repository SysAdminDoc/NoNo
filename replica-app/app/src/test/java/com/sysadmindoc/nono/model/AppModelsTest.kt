package com.sysadmindoc.nono.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Flashlight was the example here, back when any of the twenty-nine catalog actions could
        // be saved even though none of them ran. The only outcome this build produces is the one
        // a rule may now name.
        val rule = SignalRule(app = "Messages", phrase = "urgent", action = RECORD_ONLY_ACTION)
        assertNull(validateRule(rule))
        assertEquals(
            "When I get a notification from Messages that contains urgent then record the match and take no device action",
            renderRuleSentence(rule),
        )
    }

    @Test
    fun anImportedDeviceActionIsRejectedRatherThanSilentlyAccepted() {
        val flashlight = SignalRule(app = "Messages", phrase = "urgent", action = "Flashlight")

        assertEquals(UNSUPPORTED_ACTION_MESSAGE, validateRule(flashlight))
        assertEquals(
            "When I get a notification from Messages that contains urgent then do flashlight, which this build never executes",
            renderRuleSentence(flashlight),
        )
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

    @Test
    fun aCountAndItsNounAgree() {
        assertEquals("0 rules", counted(0, "rule"))
        assertEquals("1 rule", counted(1, "rule"))
        assertEquals("2 rules", counted(2, "rule"))
        assertEquals("1 group summary", counted(1, "group summary", "group summaries"))
        assertEquals("3 group summaries", counted(3, "group summary", "group summaries"))
        // The banner counts elapsed time, which is a Long.
        assertEquals("1 day", counted(1L, "day"))
    }

    @Test
    fun contentStateLabels_readAsProseRatherThanEnumNames() {
        val labels = NotificationContentState.values().map { contentStateLabel(it) }

        assertEquals(NotificationContentState.values().size, labels.distinct().size)
        labels.forEach { label ->
            assertFalse(label, label.contains('_'))
            assertFalse(label, label == label.uppercase())
        }
    }
}
