package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.HistoryRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders stored history metadata as CSV.
 *
 * Exactly the columns the database holds, which is metadata about notifications rather than
 * anything they said. The synthesized title and body the history screen shows are deliberately
 * absent: they are UI copy derived from the content-state enum, and putting them in a file would
 * invite the reader to treat them as captured content.
 */
object HistoryExport {

    private val columns = listOf(
        "posted_at_utc",
        "posted_at_epoch_millis",
        "package",
        "notification_key",
        "content_state",
        "match_state",
        "matched_rule_ids",
        "channel_id",
        "group_key",
        "is_group_summary",
        "importance",
        "is_conversation",
        "category",
        "is_ongoing",
        "starred",
    )

    fun toCsv(records: List<HistoryRecord>): String = buildString {
        append(columns.joinToString(",") { quote(it) })
        append("\r\n")
        records.forEach { record ->
            append(
                listOf(
                    formatUtc(record.postedAtEpochMillis),
                    record.postedAtEpochMillis.toString(),
                    record.appPackageName ?: record.app,
                    record.notificationKey,
                    record.contentState.name,
                    record.matchState.name,
                    record.matchedRuleIds.joinToString(" "),
                    record.channelId.orEmpty(),
                    record.groupKey.orEmpty(),
                    record.isGroupSummary.toString(),
                    record.importance?.toString().orEmpty(),
                    record.isConversation?.toString().orEmpty(),
                    record.category.orEmpty(),
                    record.isOngoing.toString(),
                    record.starred.toString(),
                ).joinToString(",") { quote(it) },
            )
            append("\r\n")
        }
    }

    // java.time needs API 26 and this app supports 24, so the older formatter is the portable one.
    // Not a shared instance: SimpleDateFormat is not thread safe.
    private fun formatUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMillis))

    /**
     * RFC 4180 quoting for every field.
     *
     * A notification key routinely contains commas and vertical bars, and a leading equals or plus
     * would be read as a formula by a spreadsheet, so nothing is left unquoted.
     */
    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
