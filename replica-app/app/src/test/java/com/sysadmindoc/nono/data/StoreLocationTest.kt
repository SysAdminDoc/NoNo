package com.sysadmindoc.nono.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreLocationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun noBackupDir(): File = tempFolder.newFolder("no_backup")

    private fun legacyFile(): File {
        val dir = tempFolder.newFolder("files", "datastore")
        return File(dir, "nono.preferences_pb")
    }

    @Test
    fun `store resolves outside the backed up files directory`() {
        val noBackup = noBackupDir()
        val legacy = legacyFile()

        val resolved = SignalPreferences.resolveStoreFile(noBackup, legacy)

        assertTrue(
            "the store must live under noBackupFilesDir so it cannot ride along in a backup",
            resolved.absolutePath.startsWith(noBackup.absolutePath),
        )
        assertEquals("nono.preferences_pb", resolved.name)
    }

    @Test
    fun `an existing store is migrated out of the backed up location`() {
        val noBackup = noBackupDir()
        val legacy = legacyFile()
        legacy.parentFile.mkdirs()
        legacy.writeBytes(byteArrayOf(1, 2, 3, 4))

        val resolved = SignalPreferences.resolveStoreFile(noBackup, legacy)

        assertTrue("migrated store must exist at the new location", resolved.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), resolved.readBytes())
        assertFalse("the backed-up copy must not survive migration", legacy.exists())
    }

    @Test
    fun `migration does not clobber an existing store`() {
        val noBackup = noBackupDir()
        val legacy = legacyFile()
        legacy.parentFile.mkdirs()
        legacy.writeBytes(byteArrayOf(9, 9))
        val existing = File(noBackup, "datastore/nono.preferences_pb")
        existing.parentFile.mkdirs()
        existing.writeBytes(byteArrayOf(1, 1))

        val resolved = SignalPreferences.resolveStoreFile(noBackup, legacy)

        assertArrayEquals(byteArrayOf(1, 1), resolved.readBytes())
    }

    @Test
    fun `resolving with nothing to migrate is harmless`() {
        val resolved = SignalPreferences.resolveStoreFile(noBackupDir(), legacyFile())

        assertFalse("no file is created until the store is first written", resolved.exists())
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        org.junit.Assert.assertArrayEquals(expected, actual)
    }
}
