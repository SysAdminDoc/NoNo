package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RuleMatchState
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
    fun aFieldThatWouldBecomeAFormulaIsEmittedAsText() {
        // Category is written by the posting app, so it is the field that can carry this.
        val triggers = listOf("=1+1", "+1", "-1", "@SUM(A1)", "＝1+1", "＋1", "－1", "＠SUM(A1)")

        triggers.forEach { trigger ->
            val csv = HistoryExport.toCsv(listOf(record.copy(category = trigger)))

            assertTrue("$trigger was not neutralized", csv.contains("\"'$trigger\""))
            assertTrue("$trigger survived unguarded", !csv.contains("\"$trigger\""))
        }
    }

    @Test
    fun aTriggerHiddenBehindWhitespaceIsAlsoNeutralized() {
        // A spreadsheet skips the leading tab or newline and reads the trigger behind it.
        listOf("\t=1+1", "\r=1+1", "\n=1+1", "\t\t@cmd").forEach { value ->
            val csv = HistoryExport.toCsv(listOf(record.copy(category = value)))

            assertTrue("$value was not neutralized", csv.contains("\"'$value\""))
        }
    }

    @Test
    fun ordinaryFieldsAreNotDecoratedWithAnApostrophe() {
        val csv = HistoryExport.toCsv(listOf(record.copy(category = "msg")))

        assertTrue(csv.contains("\"msg\""))
        assertTrue(!csv.contains("\"'msg\""))
    }

    @Test
    fun everyRowIsWrittenWhateverThePageSizeWas() {
        // The old export wrote whatever the history screen had loaded, which was capped at 100.
        val records = (1..250).map { index ->
            record.copy(id = index.toLong(), notificationKey = "key-$index", postedAtEpochMillis = index.toLong())
        }

        val lines = HistoryExport.toCsv(records).trimEnd('\r', '\n').split("\r\n")

        assertEquals(251, lines.size)
        (1..250).forEach { index ->
            assertTrue("row $index is missing", lines.any { it.contains("\"key-$index\"") })
        }
    }

    @Test
    fun anAdversarialRowStillOccupiesExactlyOneLine() {
        val nasty = record.copy(
            category = "=cmd|'/c calc'!A1",
            groupKey = "line one\r\nline two",
            channelId = "quote\"and,comma",
        )

        val csv = HistoryExport.toCsv(listOf(nasty))

        // The embedded CRLF lives inside a quoted field, so a reader still sees one record.
        assertTrue(csv.contains("\"'=cmd|'/c calc'!A1\""))
        assertTrue(csv.contains("\"quote\"\"and,comma\""))
        assertEquals(1, csv.split("\r\n\"").size - 1)
    }

    @Test
    fun timestampsAreWrittenInUtcAlongsideTheRawValue() {
        val csv = HistoryExport.toCsv(listOf(record))
        assertTrue(csv.contains("\"2025-08-24T01:46:40Z\""))
        assertTrue(csv.contains("\"1756000000000\""))
    }
}
