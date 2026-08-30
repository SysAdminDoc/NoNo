package com.sysadmindoc.nono.runtime

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

enum class HistoryRetention(val label: String) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    THREE_MONTHS("3 months"),
    SIX_MONTHS("6 months"),

    /** Nothing is pruned by age. Retention still ends when the user clears history. */
    FOREVER("Forever"),
}

/** Exactly what the retention dialog may offer. Offering more would delete data silently. */
val historyRetentionCatalog: List<String> = HistoryRetention.entries.map { it.label }

fun historyRetention(label: String?): HistoryRetention =
    HistoryRetention.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        ?: HistoryRetention.THIRTY_DAYS

/**
 * @return the timestamp below which records are pruned. [HistoryRetention.FOREVER] returns
 * [Long.MIN_VALUE], which no stored record can fall below, so nothing ages out.
 */
fun retentionCutoffEpochMillis(
    retention: HistoryRetention,
    nowEpochMillis: Long,
): Long {
    if (retention == HistoryRetention.FOREVER) return Long.MIN_VALUE
    val cutoff = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = nowEpochMillis
        when (retention) {
            HistoryRetention.SEVEN_DAYS -> add(Calendar.DAY_OF_YEAR, -7)
            HistoryRetention.THIRTY_DAYS -> add(Calendar.DAY_OF_YEAR, -30)
            HistoryRetention.THREE_MONTHS -> add(Calendar.MONTH, -3)
            HistoryRetention.SIX_MONTHS -> add(Calendar.MONTH, -6)
            HistoryRetention.FOREVER -> Unit
        }
    }
    return cutoff.timeInMillis
}

/** Process-wide setting bridge shared by the ViewModel and listener service. */
object HistoryRetentionSettings {
    private val current = AtomicReference(HistoryRetention.THIRTY_DAYS)

    fun set(label: String?) {
        current.set(historyRetention(label))
    }

    fun get(): HistoryRetention = current.get()
}
