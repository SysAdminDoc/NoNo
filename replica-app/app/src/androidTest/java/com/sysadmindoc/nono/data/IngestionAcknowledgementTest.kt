package com.sysadmindoc.nono.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.StatusMessages
import com.sysadmindoc.nono.runtime.IngestionMetrics
import com.sysadmindoc.nono.runtime.outstandingIngestionProblems
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The acknowledgement has to be durable, and every message that reports a write has to be
 * reachable only from a write the database confirmed.
 *
 * The failure cases here are produced the way they happen in the field: the row the user is
 * acting on is gone. A retention pass, an export-then-clear, or a second tap on a stale screen all
 * land the same way, and before this the app said "Record deleted." either way.
 */
@RunWith(AndroidJUnit4::class)
class IngestionAcknowledgementTest {
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

    @Test
    fun acknowledgingWithNothingRecordedReportsFailureRatherThanSilence() = runBlocking {
        // No diagnostics row exists yet, so there is nothing to acknowledge. The user tapped a
        // control, and saying nothing at all would read as success.
        val acknowledged = database.notificationDao().acknowledgeIngestionProblems()
        assertFalse(acknowledged)
        assertEquals(
            "Those counts could not be dismissed.",
            StatusMessages.acknowledgementOutcome(acknowledged),
        )
    }

    @Test
    fun acknowledgementClearsTheBannerAndSurvivesAReopen() = runBlocking {
        val dao = database.notificationDao()
        dao.mergeIngestionMetrics(
            persistedDelta = 12L,
            droppedDelta = 5L,
            failedDelta = 2L,
            failureAtEpochMillis = 4_000L,
            nowEpochMillis = 4_000L,
        )
        val before = outstandingIngestionProblems(IngestionMetrics(), dao.readIngestionDiagnostics()!!.toMetrics())
        assertTrue(before.hasCurrentProblem)

        assertTrue(dao.acknowledgeIngestionProblems())

        val after = outstandingIngestionProblems(IngestionMetrics(), dao.readIngestionDiagnostics()!!.toMetrics())
        assertFalse(after.hasCurrentProblem)
        // The record itself is kept, so a support question about what happened is still answerable.
        assertTrue(after.hasAcknowledgedHistory)
        assertEquals(5L, dao.readIngestionDiagnostics()!!.dropped)
        assertEquals(4_000L, after.lastFailureAtEpochMillis)

        // A new failure after the acknowledgement has to raise the banner again.
        dao.mergeIngestionMetrics(
            persistedDelta = 0L,
            droppedDelta = 0L,
            failedDelta = 1L,
            failureAtEpochMillis = 9_000L,
            nowEpochMillis = 9_000L,
        )
        val reraised = outstandingIngestionProblems(IngestionMetrics(), dao.readIngestionDiagnostics()!!.toMetrics())
        assertTrue(reraised.hasCurrentProblem)
        assertEquals(1L, reraised.failed)
        assertEquals(0L, reraised.dropped)
    }

    @Test
    fun starringARecordThatIsGoneReportsFailure() = runBlocking {
        val dao = database.notificationDao()
        val missingId = 4_242L
        val updated = dao.setStarred(missingId, true) > 0
        assertFalse(updated)
        assertEquals(
            "That record could not be updated.",
            StatusMessages.starOutcome(updated, starred = true),
        )
    }

    @Test
    fun starringARecordThatExistsReportsSuccess() = runBlocking {
        val dao = database.notificationDao()
        dao.insertAndPrune(
            NotificationEntity(
                notificationKey = "present",
                packageName = "com.example.present",
                postedAtEpochMillis = 10L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
            ),
            cutoffEpochMillis = 0L,
        )
        val id = dao.readAllForExport().single().id
        assertTrue(dao.setStarred(id, true) > 0)
        assertTrue(dao.readById(id)!!.starred)
    }

    @Test
    fun deletingARecordThatIsGoneReportsFailure() = runBlocking {
        val removed = database.notificationDao().deleteById(9_999L) > 0
        assertFalse(removed)
        assertEquals("That record could not be deleted.", StatusMessages.deleteOutcome(removed))
    }

    @Test
    fun v9DiagnosticsGainAcknowledgementCountersWithoutDismissingAnything() {
        // An upgrade must not silence a warning the user has never seen.
        val name = "migration-v9-diagnostics.db"
        helper.createDatabase(name, 9).apply {
            execSQL(
                "INSERT INTO ingestion_diagnostics " +
                    "(singletonId, persisted, dropped, failed, lastFailureAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES (1, 40, 6, 3, 500, 500)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 10, true, SignalDatabase.MIGRATION_9_10)
        migrated.query(
            "SELECT dropped, failed, acknowledgedDropped, acknowledgedFailed FROM ingestion_diagnostics",
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(6, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }
}
