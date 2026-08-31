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
        "override_group_key",
        "is_group_summary",
        "group_summary_origin",
        "importance",
        "is_conversation",
        "category",
        "is_ongoing",
        "starred",
        "removed_at_epoch_millis",
        "removal_reason",
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
                    record.overrideGroupKey.orEmpty(),
                    record.isGroupSummary.toString(),
                    record.groupSummaryOrigin.name,
                    record.importance?.toString().orEmpty(),
                    record.isConversation?.toString().orEmpty(),
                    record.category.orEmpty(),
                    record.isOngoing.toString(),
                    record.starred.toString(),
                    record.removedAtEpochMillis?.toString().orEmpty(),
                    record.removalReason.name,
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
     * RFC 4180 quoting plus formula neutralization.
     *
     * Quoting alone is not enough. Excel, LibreOffice and Google Sheets all evaluate a cell whose
     * text begins with `=`, `+`, `-` or `@` even when the CSV had it quoted. Historical rows and
     * defensive callers can still contain app-authored values, so every exported cell remains
     * neutralized. A leading tab, carriage return or newline is stripped first because the
     * spreadsheet ignores it and then reads the trigger behind it, and the full-width forms are
     * covered because the same applications normalize them.
     *
     * Neutralized cells are prefixed with a single quote, which is the documented convention: the
     * apostrophe is visible in the cell and the rest is text.
     */
    internal fun quote(value: String): String {
        val neutralized = if (needsFormulaGuard(value)) "'$value" else value
        return "\"" + neutralized.replace("\"", "\"\"") + "\""
    }

    private fun needsFormulaGuard(value: String): Boolean {
        val first = value.firstOrNull() ?: return false
        if (first in LEADING_WHITESPACE_TRIGGERS) return true
        return value.dropWhile { it in LEADING_WHITESPACE_TRIGGERS }.firstOrNull() in FORMULA_TRIGGERS
    }

    /**
     * ASCII triggers and their full-width equivalents: U+FF1D, U+FF0B, U+FF0D, U+FF20.
     */
    private val FORMULA_TRIGGERS = setOf('=', '+', '-', '@', '＝', '＋', '－', '＠')

    /** Leading characters a spreadsheet skips before reading the trigger behind them. */
    private val LEADING_WHITESPACE_TRIGGERS = setOf('\t', '\r', '\n')
}
