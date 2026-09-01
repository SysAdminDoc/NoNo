package com.sysadmindoc.nono.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.runtime.CaptureDeduplicator
import com.sysadmindoc.nono.model.RemovalReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Storing that a notification left the shade, and not storing anything more than that.
 */
@RunWith(AndroidJUnit4::class)
class RemovalRecordTest {
    private lateinit var database: SignalDatabase

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SignalDatabase::class.java,
        emptyList(),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SignalDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun store(key: String, postedAt: Long = 1_000L) {
        database.notificationDao().insertAndPrune(
            NotificationEntity(
                notificationKey = key,
                packageName = "com.example.app",
                postedAtEpochMillis = postedAt,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
            ),
            cutoffEpochMillis = 0L,
        )
    }

    @Test
    fun aStoredRecordTakesTheRemovalTimeAndTheReason() = runBlocking {
        val dao = database.notificationDao()
        store("key-a")

        assertEquals(1, dao.markRemoved("key-a", 5_000L, RemovalReason.DISMISSED.name))

        val record = dao.readAllForExport().single().toHistoryRecord()
        assertEquals(5_000L, record.removedAtEpochMillis)
        assertEquals(RemovalReason.DISMISSED, record.removalReason)
        assertTrue("a reason the platform gave for a user action is a dismissal", record.dismissed)
    }

    @Test
    fun aNotificationThisBuildNeverCapturedRecordsNothing() = runBlocking {
        // Capture can be paused, or the app can start after the notification arrived. There is no
        // row to mark, and inventing one would put a record in the history with no metadata.
        assertEquals(0, database.notificationDao().markRemoved("never-seen", 5_000L, RemovalReason.DISMISSED.name))
        assertEquals(0, database.notificationDao().count())
    }

    @Test
    fun aRemovalWithNoReasonIsStillARemovalButNotADismissal() = runBlocking {
        val dao = database.notificationDao()
        store("key-b")

        dao.markRemoved("key-b", 6_000L, RemovalReason.UNKNOWN.name)

        val record = dao.readAllForExport().single().toHistoryRecord()
        assertEquals("it went, and that much is known", 6_000L, record.removedAtEpochMillis)
        assertEquals(RemovalReason.UNKNOWN, record.removalReason)
        assertFalse("no reason is not a dismissal", record.dismissed)
    }

    @Test
    fun aSecondRemovalDoesNotOverwriteTheFirst() = runBlocking {
        val dao = database.notificationDao()
        store("key-c")
        dao.markRemoved("key-c", 7_000L, RemovalReason.DISMISSED.name)

        assertEquals(0, dao.markRemoved("key-c", 9_000L, RemovalReason.TIMED_OUT.name))

        val record = dao.readAllForExport().single().toHistoryRecord()
        assertEquals(7_000L, record.removedAtEpochMillis)
        assertEquals(RemovalReason.DISMISSED, record.removalReason)
    }

    @Test
    fun aRepostClearsTheRemovalBecauseTheNotificationIsBack() = runBlocking {
        val dao = database.notificationDao()
        store("key-d")
        dao.markRemoved("key-d", 8_000L, RemovalReason.WITHDRAWN_BY_APP.name)

        assertEquals(1, dao.clearRemoval("key-d", RemovalReason.UNKNOWN.name))

        val record = dao.readAllForExport().single().toHistoryRecord()
        assertNull("a notification on screen is not gone", record.removedAtEpochMillis)
        assertEquals(RemovalReason.UNKNOWN, record.removalReason)

        // And it can be marked again the next time it goes.
        assertEquals(1, dao.markRemoved("key-d", 10_000L, RemovalReason.DISMISSED.name))
    }

    @Test
    fun anIdenticalRepostInsideTheDedupWindowStillClearsTheRemoval() = runBlocking {
        // The listener's order is dedup gate first, clearRemoval second. Cancel-then-repost
        // inside the window used to be suppressed at the gate, so the row kept saying a
        // notification on screen had left the shade. Removal now forgets the key.
        val deduplicator = CaptureDeduplicator(windowMillis = 2_000L)
        val dao = database.notificationDao()
        store("key-e")
        assertTrue(deduplicator.shouldCapture("key-e", "same", 0L))

        // The removal path: mark the row, forget the key.
        dao.markRemoved("key-e", 1_000L, RemovalReason.WITHDRAWN_BY_APP.name)
        deduplicator.forget("key-e")

        // An identical repost half a second later passes the gate and reaches clearRemoval.
        assertTrue(deduplicator.shouldCapture("key-e", "same", 1_500L))
        dao.clearRemoval("key-e", RemovalReason.UNKNOWN.name)

        assertNull(dao.readAllForExport().single().toHistoryRecord().removedAtEpochMillis)
    }

    @Test
    fun theDismissedFilterSelectsOnlyWhatThePlatformCalledAUserRemoval() = runBlocking {
        val dao = database.notificationDao()
        store("dismissed", postedAt = 1L)
        store("withdrawn", postedAt = 2L)
        store("unknown", postedAt = 3L)
        store("still-here", postedAt = 4L)
        dao.markRemoved("dismissed", 100L, RemovalReason.DISMISSED.name)
        dao.markRemoved("withdrawn", 100L, RemovalReason.WITHDRAWN_BY_APP.name)
        dao.markRemoved("unknown", 100L, RemovalReason.UNKNOWN.name)

        val selected = dao.observeHistory(query = "", filter = "Dismissed").first()

        assertEquals(listOf("dismissed"), selected.map { it.notificationKey })
        assertEquals(1, dao.observeFilteredCount(query = "", filter = "Dismissed").first())
    }

    @Test
    fun v10RowsGainTheRemovalColumnsWithoutBeingCalledRemoved() {
        val name = "migration-v10-removal.db"
        helper.createDatabase(name, 10).apply {
            execSQL(
                "INSERT INTO notification_history " +
                    "(notificationKey, packageName, postedAtEpochMillis, contentState, isGroupSummary, " +
                    "groupSummaryOrigin, isOngoing, starred, identifierScheme) " +
                    "VALUES ('legacy-v10', 'com.example.app', 42, 'NOT_AVAILABLE', 0, 'UNKNOWN', 0, 0, 1)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 11, true, SignalDatabase.MIGRATION_10_11)
        migrated.query("SELECT removedAtEpochMillis, removalReason FROM notification_history").use { cursor ->
            check(cursor.moveToFirst())
            // A row captured before this existed says nothing about leaving the shade, which is
            // different from saying it is still there and different again from saying it went.
            check(cursor.isNull(0))
            assertEquals("UNKNOWN", cursor.getString(1))
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }
}
