package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.runtime.BackupOutcome
import com.sysadmindoc.nono.runtime.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Settings row says the schedule last did. */
class BackupStatusCopyTest {

    @Test
    fun beforeAnythingHasRunTheRowSaysSo() {
        assertEquals("No backup has run yet.", describeBackupStatus(BackupStatus()))
    }

    @Test
    fun aSuccessNamesHowManyRulesItWrote() {
        // "Backed up" with no number cannot be told from a backup of nothing, which is exactly the
        // case where the user needs to notice.
        val line = describeBackupStatus(BackupStatus(BackupOutcome.SUCCEEDED, 1_756_000_000_000L, 4))

        assertTrue(line, line.startsWith("Last backup "))
        assertTrue(line, line.endsWith("4 rules."))
    }

    @Test
    fun oneRuleIsNotCalledRules() {
        assertTrue(describeBackupStatus(BackupStatus(BackupOutcome.SUCCEEDED, 1L, 1)).endsWith("1 rule."))
    }

    @Test
    fun aFailureCarriesItsReasonRatherThanJustSayingItFailed() {
        val line = describeBackupStatus(
            BackupStatus(BackupOutcome.FAILED, 1L, 0, "Access to the backup folder was withdrawn. Pick it again."),
        )

        assertTrue(line, line.startsWith("Last attempt failed "))
        assertTrue(line, line.contains("Access to the backup folder was withdrawn. Pick it again."))
    }

    @Test
    fun noneOfTheCopyUsesADash() {
        val lines = listOf(
            describeBackupStatus(BackupStatus()),
            describeBackupStatus(BackupStatus(BackupOutcome.SUCCEEDED, 1L, 2)),
            describeBackupStatus(BackupStatus(BackupOutcome.FAILED, 1L, 0, "No backup folder is selected.")),
        )

        assertTrue(lines.none { line -> line.any { it == '—' || it == '–' } })
    }
}
