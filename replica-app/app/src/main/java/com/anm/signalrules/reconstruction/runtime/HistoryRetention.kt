package com.anm.signalrules.reconstruction.runtime

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

enum class HistoryRetention(val label: String) {
    THIRTY_DAYS("30 days"),
    THREE_MONTHS("3 months"),
    SIX_MONTHS("6 months"),
}

fun historyRetention(label: String?): HistoryRetention =
    HistoryRetention.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        ?: HistoryRetention.THIRTY_DAYS

fun retentionCutoffEpochMillis(
    retention: HistoryRetention,
    nowEpochMillis: Long,
): Long {
    val cutoff = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = nowEpochMillis
        when (retention) {
            HistoryRetention.THIRTY_DAYS -> add(Calendar.DAY_OF_YEAR, -30)
            HistoryRetention.THREE_MONTHS -> add(Calendar.MONTH, -3)
            HistoryRetention.SIX_MONTHS -> add(Calendar.MONTH, -6)
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
