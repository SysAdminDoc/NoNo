package com.sysadmindoc.nono.runtime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Written files kept in the folder. Older ones are removed after a successful write. */
const val BACKUP_RETAINED_FILES = 5

private const val BACKUP_FILE_PREFIX = "nono-rules-backup-"
private const val BACKUP_FILE_SUFFIX = ".json"

/** Matches only what this app writes, so rotation can never remove somebody else's file. */
private val backupFileName = Regex("^${Regex.escape(BACKUP_FILE_PREFIX)}\\d{8}-\\d{6}${Regex.escape(BACKUP_FILE_SUFFIX)}$")

/**
 * How often the backup job runs.
 *
 * WorkManager's shortest periodic interval is fifteen minutes; both live cadences are far above
 * that, and the job is opportunistic rather than punctual by design.
 */
enum class BackupCadence(val label: String, val repeatIntervalHours: Long) {
    OFF("Off", 0L),
    DAILY("Daily", 24L),
    WEEKLY("Weekly", 24L * 7L),
    ;

    val enabled: Boolean get() = this != OFF
}

/** Resolves a stored or displayed label, falling back to off for anything unrecognized. */
fun backupCadence(label: String?): BackupCadence =
    BackupCadence.entries.firstOrNull { it.label.equals(label?.trim(), ignoreCase = true) } ?: BackupCadence.OFF

/** What the last run did, so Settings can report it instead of staying silent. */
enum class BackupOutcome { NEVER_RUN, SUCCEEDED, FAILED }

/**
 * @param detail why a run failed, in the app's own words. A run that succeeded says how many
 * rules it wrote, because "backed up" with no number cannot be told from a backup of nothing.
 */
@Serializable
data class BackupStatus(
    val outcome: BackupOutcome = BackupOutcome.NEVER_RUN,
    val atEpochMillis: Long = 0L,
    val ruleCount: Int = 0,
    val detail: String = "",
)

private val statusJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

fun encodeBackupStatus(status: BackupStatus): String =
    statusJson.encodeToString(BackupStatus.serializer(), status)

/** A stored value this build cannot read is reported as no run rather than as a failure. */
fun decodeBackupStatus(encoded: String?): BackupStatus {
    if (encoded.isNullOrBlank()) return BackupStatus()
    return runCatching { statusJson.decodeFromString(BackupStatus.serializer(), encoded) }
        .getOrElse { BackupStatus() }
}

/** The name a run writes. Sorting these as text is the same as sorting them by time. */
fun backupFileName(atEpochMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
        .apply { timeZone = zone }
        .format(Date(atEpochMillis))
    return "$BACKUP_FILE_PREFIX$stamp$BACKUP_FILE_SUFFIX"
}

/**
 * Chooses what rotation removes.
 *
 * The folder is the user's, and it may hold anything. Only names this app writes are considered,
 * and only the ones past [keep] are named for deletion, newest first by the timestamp in the name.
 *
 * @return the names to delete, in the order they should go.
 */
fun expiredBackupFileNames(present: List<String>, keep: Int = BACKUP_RETAINED_FILES): List<String> {
    if (keep < 0) return emptyList()
    return present
        .filter { backupFileName.matches(it) }
        .distinct()
        .sortedDescending()
        .drop(keep)
}
