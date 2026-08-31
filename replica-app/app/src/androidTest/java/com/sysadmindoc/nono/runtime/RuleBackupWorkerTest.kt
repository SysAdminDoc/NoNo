package com.sysadmindoc.nono.runtime

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.sysadmindoc.nono.data.DeviceBackupKey
import com.sysadmindoc.nono.data.RuleImportResult
import com.sysadmindoc.nono.data.RuleTransfer
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.model.SignalRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the scheduled backup does when it cannot write.
 *
 * The failures matter more than the happy path here: a job the user cannot see is a job that
 * quietly stops working, and a folder grant can be withdrawn at any time from system settings.
 * The write itself needs a real Storage Access Framework folder, which an instrumented test has no
 * way to pick, so this covers everything up to that point plus the file format on this device's
 * own keystore key.
 */
@RunWith(AndroidJUnit4::class)
class RuleBackupWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = clearBackupPreferences()

    @After
    fun tearDown() = clearBackupPreferences()

    private fun clearBackupPreferences() = runBlocking {
        SignalPreferences.get(context).edit {
            it.remove(SignalPreferences.BACKUP_FOLDER_URI)
            it.remove(SignalPreferences.BACKUP_FOLDER_LABEL)
            it.remove(SignalPreferences.BACKUP_STATUS)
            it.remove(SignalPreferences.settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING))
        }
        Unit
    }

    private suspend fun setCadence(cadence: BackupCadence) {
        SignalPreferences.get(context).edit {
            it[SignalPreferences.settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING)] = cadence.label
        }
    }

    private suspend fun readStatus(): BackupStatus =
        decodeBackupStatus(SignalPreferences.get(context).data.first()[SignalPreferences.BACKUP_STATUS])

    private suspend fun run(): ListenableWorker.Result =
        TestListenableWorkerBuilder<RuleBackupWorker>(context).build().doWork()

    @Test
    fun withNoFolderChosenTheRunReportsWhyRatherThanDoingNothingQuietly() = runBlocking {
        setCadence(BackupCadence.DAILY)

        val result = run()

        assertEquals(ListenableWorker.Result.success(), result)
        val status = readStatus()
        assertEquals(BackupOutcome.FAILED, status.outcome)
        assertEquals("No backup folder is selected.", status.detail)
        assertTrue(status.atEpochMillis > 0L)
    }

    @Test
    fun aFolderThisAppNoLongerHasAccessToIsReportedAsAWithdrawnGrant() = runBlocking {
        // Nothing ever granted this tree, which is exactly the shape of a grant the user revoked.
        setCadence(BackupCadence.DAILY)
        SignalPreferences.get(context).edit {
            it[SignalPreferences.BACKUP_FOLDER_URI] =
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANoNo").toString()
        }

        run()

        val status = readStatus()
        assertEquals(BackupOutcome.FAILED, status.outcome)
        assertTrue(status.detail, status.detail.contains("withdrawn"))
    }

    @Test
    fun aCadenceOfOffWritesNoStatusAtAll() = runBlocking {
        // The schedule was turned off after this run was queued. There is nothing to report.
        setCadence(BackupCadence.OFF)

        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(BackupOutcome.NEVER_RUN, readStatus().outcome)
    }

    @Test
    fun aRunStartedRightAfterTheSettingIsSavedSeesTheSetting() = runBlocking {
        // The worker reads the cadence and the folder back out of DataStore. Enqueuing it before
        // the write landed made it read the old values, decide the schedule was off, and write no
        // result at all, which is the case the "run it now" call exists to prevent.
        SignalPreferences.get(context).edit {
            it[SignalPreferences.settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING)] = BackupCadence.DAILY.label
            it[SignalPreferences.BACKUP_FOLDER_URI] = "content://com.example.provider/tree/backups"
        }

        run()

        // It got far enough to check the grant, which means it read both values.
        val status = readStatus()
        assertEquals(BackupOutcome.FAILED, status.outcome)
        assertTrue(status.detail, status.detail.contains("withdrawn"))
    }

    @Test
    fun aStatusWrittenBeforeThisRunIsReplacedRatherThanLeftStale() = runBlocking {
        SignalPreferences.get(context).edit {
            it[SignalPreferences.BACKUP_STATUS] = encodeBackupStatus(
                BackupStatus(BackupOutcome.SUCCEEDED, 1L, 7),
            )
            it[SignalPreferences.settingKey(SignalPreferences.AUTOMATIC_BACKUP_SETTING)] = BackupCadence.DAILY.label
        }

        run()

        val status = readStatus()
        assertEquals(BackupOutcome.FAILED, status.outcome)
        assertEquals("No backup folder is selected.", status.detail)
    }

    @Test
    fun thisDevicesKeystoreKeyRoundTripsARealBackupFile() = runBlocking {
        val rules = listOf(SignalRule(id = 1L, name = "Group chats", phrase = "standup"))
        val key = DeviceBackupKey.get()
        assertNotNull("the keystore must supply a key on a supported device", key)

        val encoded = RuleTransfer.exportRulesForDevice(rules, key!!)

        assertTrue("rule text must not survive into the file", !encoded.contains("standup"))
        assertTrue("the same key must come back", DeviceBackupKey.exists())
        val result = RuleTransfer.importRules(encoded, deviceKey = DeviceBackupKey.get())
        assertEquals(rules, (result as RuleImportResult.Success).rules)
    }
}
