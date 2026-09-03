package com.sysadmindoc.nono.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sysadmindoc.nono.data.SignalPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The backup job's own report about itself.
 *
 * A full disk is the realistic failure for a job whose purpose is writing files, and it is exactly
 * when the status line matters. An exception out of the status write used to leave `doWork`, so
 * WorkManager recorded a failure and Settings kept its previous sentence over a backup that had
 * been written.
 */
class BackupStatusWriteTest {

    private class ThrowingStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("no space left on device")
    }

    private class RecordingStore : DataStore<Preferences> {
        var written: Preferences = emptyPreferences()
            private set

        override val data: Flow<Preferences> = flowOf(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(mutablePreferencesOf()).also { written = it }
    }

    @Test
    fun aStoreThatRefusesTheWriteDoesNotTakeTheRunDownWithIt() = runTest {
        // No assertion on a return value: the point is that this returns at all.
        writeBackupStatus(
            ThrowingStore(),
            BackupStatus(BackupOutcome.SUCCEEDED, 1_756_000_000_000L, 4, ""),
        )
    }

    @Test
    fun aWorkingStoreStillGetsTheStatus() = runTest {
        // The positive control. Without it, a writeBackupStatus that swallowed everything,
        // including its own logic being wrong, would pass the test above just as well.
        val store = RecordingStore()

        writeBackupStatus(store, BackupStatus(BackupOutcome.FAILED, 42L, 0, "No backup folder is selected."))

        val encoded = store.written[SignalPreferences.BACKUP_STATUS]
        assertTrue("nothing was written", encoded != null)
        val status = decodeBackupStatus(encoded)
        assertEquals(BackupOutcome.FAILED, status.outcome)
        assertEquals(42L, status.atEpochMillis)
        assertEquals("No backup folder is selected.", status.detail)
    }
}
