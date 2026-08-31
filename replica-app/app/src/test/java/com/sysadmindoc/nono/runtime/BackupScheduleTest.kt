package com.sysadmindoc.nono.runtime

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scheduled backup's cadence, file naming, rotation, and reported status. */
class BackupScheduleTest {

    private val zone = TimeZone.getTimeZone("UTC")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun `an unknown or missing cadence is off rather than a guess`() {
        assertEquals(BackupCadence.OFF, backupCadence(null))
        assertEquals(BackupCadence.OFF, backupCadence(""))
        assertEquals(BackupCadence.OFF, backupCadence("Hourly"))
        assertEquals(BackupCadence.OFF, backupCadence("Off"))
    }

    @Test
    fun `a stored label resolves whatever its spacing or case`() {
        assertEquals(BackupCadence.DAILY, backupCadence("Daily"))
        assertEquals(BackupCadence.DAILY, backupCadence(" daily "))
        assertEquals(BackupCadence.WEEKLY, backupCadence("WEEKLY"))
    }

    @Test
    fun `only a live cadence carries an interval`() {
        assertTrue(BackupCadence.DAILY.enabled)
        assertTrue(BackupCadence.WEEKLY.enabled)
        assertTrue(!BackupCadence.OFF.enabled)
        assertEquals(24L, BackupCadence.DAILY.repeatIntervalHours)
        assertEquals(168L, BackupCadence.WEEKLY.repeatIntervalHours)
        // WorkManager refuses a periodic interval under fifteen minutes.
        assertTrue(BackupCadence.entries.filter { it.enabled }.all { it.repeatIntervalHours >= 1L })
    }

    @Test
    fun `file names sort as text in the same order as they sort by time`() {
        val earlier = backupFileName(millis(2026, Calendar.AUGUST, 31, 9, 5, 3))
        val later = backupFileName(millis(2026, Calendar.SEPTEMBER, 1, 0, 0, 0))

        assertEquals("nono-rules-backup-20260831-090503.json", earlier)
        assertEquals("nono-rules-backup-20260901-000000.json", later)
        assertTrue(earlier < later)
    }

    @Test
    fun `two runs a second apart do not collide`() {
        assertNotEquals(
            backupFileName(millis(2026, Calendar.AUGUST, 31, 9, 5, 3)),
            backupFileName(millis(2026, Calendar.AUGUST, 31, 9, 5, 4)),
        )
    }

    @Test
    fun `a later backup still sorts above an earlier one after the device changes zone`() {
        // Rotation sorts these as text. A local-time stamp does not advance with real time across
        // an offset change: fly from UTC+13 to UTC-8 and the newer file sorts below the older one,
        // so rotation names the file it just wrote as the oldest and deletes it. Stamping in UTC
        // is what makes text order and time order the same order.
        val earlier = millis(2026, Calendar.SEPTEMBER, 1, 11, 0, 0)
        val later = earlier + 60 * 60 * 1000L

        val earlierName = withDefaultZone("Pacific/Auckland") { backupFileName(earlier) }
        val laterName = withDefaultZone("America/Los_Angeles") { backupFileName(later) }

        assertTrue("$earlierName must sort below $laterName", earlierName < laterName)
        assertEquals(
            emptyList<String>(),
            expiredBackupFileNames(listOf(earlierName, laterName), keep = 1).filter { it == laterName },
        )
    }

    private fun <T> withDefaultZone(id: String, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `rotation removes the oldest backups past the retained count`() {
        val present = (1..8).map { backupFileName(millis(2026, Calendar.AUGUST, it + 20, 3, 0, 0)) }

        val expired = expiredBackupFileNames(present, keep = 5)

        assertEquals(3, expired.size)
        assertEquals(
            listOf("20260823", "20260822", "20260821").map { "nono-rules-backup-$it-030000.json" },
            expired,
        )
    }

    @Test
    fun `rotation never names a file this app did not write`() {
        // The folder belongs to the user and can hold anything, including files whose names look
        // close to ours. Deleting one of those would be data loss caused by a housekeeping step.
        val present = listOf(
            "tax-return.pdf",
            "nono-rules-backup.json",
            "nono-rules-backup-2026-08-31-030000.json",
            "nono-rules-backup-20260831-030000.json.bak",
            "NONO-RULES-BACKUP-20260831-030000.json",
        ) + (1..8).map { backupFileName(millis(2026, Calendar.AUGUST, it + 20, 3, 0, 0)) }

        val expired = expiredBackupFileNames(present, keep = 0)

        assertEquals(8, expired.size)
        assertTrue(expired.all { it.startsWith("nono-rules-backup-") && it.endsWith(".json") })
        assertTrue(expired.none { it in listOf("tax-return.pdf", "nono-rules-backup.json") })
    }

    @Test
    fun `the file this run wrote is never expired`() {
        // Names written before the stamp moved to UTC carry a local offset. On a device east of
        // UTC the first UTC-stamped file sorts below every older one, so the sort alone would name
        // the backup that was just written as the oldest and delete it seconds later.
        val legacy = (1..5).map { "nono-rules-backup-2026090${it}-230000.json" }
        val justWritten = "nono-rules-backup-20260901-100000.json"
        val present = legacy + justWritten

        // It sorts below every legacy name, so without the guard it is the first thing dropped.
        assertEquals(justWritten, present.sortedDescending().last())

        assertTrue(
            "the new backup must survive its own rotation",
            justWritten !in expiredBackupFileNames(present, keep = 5, justWritten = justWritten),
        )
        // Older files are still rotated normally around it.
        assertEquals(
            listOf("nono-rules-backup-20260902-230000.json", "nono-rules-backup-20260901-230000.json"),
            expiredBackupFileNames(present, keep = 3, justWritten = justWritten),
        )
    }

    @Test
    fun `a copy the provider renamed is rotated rather than kept for ever`() {
        // Two runs in the same second: the provider will not overwrite, so the second document
        // comes back with a numbered suffix. A pattern that did not accept that shape would leave
        // those files in the folder permanently.
        val present = listOf(
            "nono-rules-backup-20260901-030000.json",
            "nono-rules-backup-20260901-030000 (1).json",
            "nono-rules-backup-20260901-030000 (2).json",
        )

        assertEquals(3, expiredBackupFileNames(present, keep = 0).size)
        assertEquals(emptyList<String>(), expiredBackupFileNames(present, keep = 3))
    }

    @Test
    fun `nothing is removed while the folder holds no more than the retained count`() {
        val present = (1..5).map { backupFileName(millis(2026, Calendar.AUGUST, it + 20, 3, 0, 0)) }

        assertEquals(emptyList<String>(), expiredBackupFileNames(present, keep = 5))
        assertEquals(emptyList<String>(), expiredBackupFileNames(emptyList(), keep = 5))
    }

    @Test
    fun `a duplicate name is counted once`() {
        val one = backupFileName(millis(2026, Calendar.AUGUST, 31, 3, 0, 0))

        assertEquals(emptyList<String>(), expiredBackupFileNames(listOf(one, one, one), keep = 1))
    }

    @Test
    fun `status round trips through storage`() {
        val status = BackupStatus(BackupOutcome.SUCCEEDED, 1_700_000_000_000L, 4)

        assertEquals(status, decodeBackupStatus(encodeBackupStatus(status)))
    }

    @Test
    fun `an unreadable stored status reads as no run rather than as a failure`() {
        // A failure the app invented would send the user looking for a problem that is not there.
        assertEquals(BackupStatus(), decodeBackupStatus(null))
        assertEquals(BackupStatus(), decodeBackupStatus(""))
        assertEquals(BackupStatus(), decodeBackupStatus("{not json"))
        assertEquals(BackupOutcome.NEVER_RUN, decodeBackupStatus("{\"outcome\":\"SOMETHING_ELSE\"}").outcome)
    }

    @Test
    fun `a failure keeps the reason it was given`() {
        val failed = BackupStatus(BackupOutcome.FAILED, 1L, 0, "Access to the backup folder was withdrawn.")

        assertEquals(failed, decodeBackupStatus(encodeBackupStatus(failed)))
    }
}
