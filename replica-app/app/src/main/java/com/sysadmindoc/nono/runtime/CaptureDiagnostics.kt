package com.sysadmindoc.nono.runtime

data class CaptureDiagnosticsSnapshot(
    val appVersion: String,
    val accessGranted: Boolean,
    val connection: ListenerHealth.Connection,
    val metrics: IngestionMetrics,
    val lastCaptureAtEpochMillis: Long?,
    val nowEpochMillis: Long,
)

/** Uses the durable totals while retaining the live queue depth that Room does not store. */
fun combinedIngestionMetrics(
    live: IngestionMetrics,
    durable: IngestionMetrics,
): IngestionMetrics = IngestionMetrics(
    queued = live.queued,
    persisted = maxOf(live.persisted, durable.persisted),
    dropped = maxOf(live.dropped, durable.dropped),
    failed = maxOf(live.failed, durable.failed),
    lastFailureAtEpochMillis = listOfNotNull(
        live.lastFailureAtEpochMillis,
        durable.lastFailureAtEpochMillis,
    ).maxOrNull(),
    acknowledgedDropped = durable.acknowledgedDropped,
    acknowledgedFailed = durable.acknowledgedFailed,
)

/** A plain-text support report with operational state only. */
fun buildCaptureDiagnosticsReport(snapshot: CaptureDiagnosticsSnapshot): String = buildString {
    appendLine("NoNo capture diagnostics")
    appendLine("App version: ${snapshot.appVersion}")
    appendLine("Notification access: ${if (snapshot.accessGranted) "Granted" else "Not granted"}")
    appendLine("Listener connection: ${snapshot.connection.displayName()}")
    appendLine("Ingestion queued: ${snapshot.metrics.queued}")
    appendLine("Ingestion persisted: ${snapshot.metrics.persisted}")
    appendLine("Ingestion dropped: ${snapshot.metrics.dropped}")
    appendLine("Ingestion failed: ${snapshot.metrics.failed}")
    appendLine("Last capture age: ${captureAge(snapshot.lastCaptureAtEpochMillis, snapshot.nowEpochMillis)}")
    append("Privacy: no notification content or posting-app, channel, group, or rule identifiers are included.")
}

fun captureSelfTestFailureGuidance(manufacturer: String, sdkInt: Int): String {
    val steps = oemListenerChecklist(manufacturer, sdkInt)
    return listOfNotNull(steps.firstOrNull(), steps.getOrNull(1)).joinToString(" ")
}

private fun ListenerHealth.Connection.displayName(): String = when (this) {
    ListenerHealth.Connection.UNKNOWN -> "Not confirmed in this process"
    ListenerHealth.Connection.CONNECTED -> "Connected"
    ListenerHealth.Connection.DISCONNECTED -> "Disconnected"
}

internal fun captureAge(lastCaptureAtEpochMillis: Long?, nowEpochMillis: Long): String {
    val last = lastCaptureAtEpochMillis ?: return "Never recorded"
    val age = nowEpochMillis - last
    if (age < 0L) return "Unavailable because the device clock moved backwards"
    return when {
        age < 60_000L -> "Less than a minute"
        age < 3_600_000L -> countWithUnit(age / 60_000L, "minute")
        age < 86_400_000L -> countWithUnit(age / 3_600_000L, "hour")
        else -> countWithUnit(age / 86_400_000L, "day")
    }
}

private fun countWithUnit(count: Long, unit: String): String =
    "$count $unit${if (count == 1L) "" else "s"}"
