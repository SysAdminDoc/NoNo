package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.NotificationContentState
import com.anm.signalrules.reconstruction.model.RuleMatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportTest {

    private val record = HistoryRecord(
        id = 1L,
        app = "com.example.chat",
        appPackageName = "com.example.chat",
        postedAtEpochMillis = 1_756_000_000_000L,
        notificationKey = "0|com.example.chat|42|null|10123",
        contentState = NotificationContentState.AVAILABLE,
        matchState = RuleMatchState.EVALUATED,
        matchedRuleIds = listOf(7L, 9L),
        channelId = "messages",
        groupKey = "chat, urgent",
        importance = 4,
        isConversation = true,
        category = "msg",
        starred = true,
    )

    @Test
    fun theHeaderNamesEveryColumnAndTheRowMatchesIt() {
        val csv = HistoryExport.toCsv(listOf(record))
        val lines = csv.trimEnd('\r', '\n').split("\r\n")

        assertEquals(2, lines.size)
        assertEquals(lines[0].split("\",\"").size, lines[1].split("\",\"").size)
        assertTrue(lines[0].startsWith("\"posted_at_utc\",\"posted_at_epoch_millis\",\"package\""))
    }

    @Test
    fun fieldsCarryingCommasAndQuotesSurviveTheRoundTrip() {
        val csv = HistoryExport.toCsv(
            listOf(record.copy(groupKey = "he said \"hello\", then left")),
        )

        // A quote is doubled and the whole field stays inside one pair of quotes.
        assertTrue(csv.contains("\"he said \"\"hello\"\", then left\""))
        // The embedded comma must not create an extra field.
        assertEquals(2, csv.trimEnd('\r', '\n').split("\r\n").size)
    }

    @Test
    fun theKeyIsWrittenWholeDespiteItsSeparators() {
        val csv = HistoryExport.toCsv(listOf(record))
        assertTrue(csv.contains("\"0|com.example.chat|42|null|10123\""))
    }

    @Test
    fun noColumnCanCarryNotificationContent() {
        val header = HistoryExport.toCsv(emptyList()).trimEnd('\r', '\n')
        listOf("title", "text", "body", "message", "content\"").forEach { banned ->
            assertTrue("header exposes $banned", !header.contains(banned))
        }
        // content_state is provenance, not content, and must survive that check.
        assertTrue(header.contains("content_state"))
    }

    @Test
    fun anEmptyHistoryStillProducesAUsableFile() {
        val csv = HistoryExport.toCsv(emptyList())
        assertEquals(1, csv.trimEnd('\r', '\n').split("\r\n").size)
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun absentValuesAreEmptyRatherThanTheWordNull() {
        val csv = HistoryExport.toCsv(
            listOf(record.copy(channelId = null, importance = null, isConversation = null, category = null)),
        )
        assertTrue(csv.contains("\"\""))
        assertTrue(!csv.contains("\"null\""))
    }

    @Test
    fun timestampsAreWrittenInUtcAlongsideTheRawValue() {
        val csv = HistoryExport.toCsv(listOf(record))
        assertTrue(csv.contains("\"2025-08-24T01:46:40Z\""))
        assertTrue(csv.contains("\"1756000000000\""))
    }
}
