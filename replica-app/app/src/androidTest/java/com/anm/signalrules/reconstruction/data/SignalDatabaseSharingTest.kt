package com.anm.signalrules.reconstruction.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The listener writes and the UI reads through what must be one Room instance.
 *
 * Room's invalidation tracker only notifies observers registered on the instance that performed
 * the write, so a second instance over the same file leaves the history flow silent. Two separate
 * things have to hold: every owner gets the same instance, and a write through it wakes a
 * collector that is already running.
 */
@RunWith(AndroidJUnit4::class)
class SignalDatabaseSharingTest {

    private lateinit var database: SignalDatabase

    @Before
    fun setUp() {
        // In memory: the real store belongs to the installed app, and a test has no business
        // inserting into a user's notification history or pruning rows out of it.
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SignalDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everyOwnerReceivesTheSameInstance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertSame(SignalDatabase.get(context), SignalDatabase.get(context))
    }

    @Test
    fun aWriteWakesACollectorThatIsAlreadyRunning() = runBlocking<Unit> {
        val dao = database.notificationDao()
        val emissions = Channel<List<NotificationEntity>>(Channel.UNLIMITED)

        // Held open across the write. Collecting with first() twice would instead re-run the query
        // on a fresh subscription and pass even if invalidation never fired.
        val collector = dao.observeHistory(query = "", filter = "All")
            .onEach { emissions.send(it) }
            .launchIn(this)

        try {
            assertEquals(emptyList<NotificationEntity>(), withTimeout(10_000) { emissions.receive() })

            dao.insertAndPrune(
                NotificationEntity(
                    notificationKey = "sharing-test",
                    packageName = "com.example.sharing",
                    postedAtEpochMillis = 5_000L,
                    contentState = "NOT_STORED",
                ),
                cutoffEpochMillis = 0L,
            )

            // Times out if the tracker never notifies the collector that is already running.
            val afterWrite = withTimeout(10_000) { emissions.receive() }
            assertEquals(listOf("sharing-test"), afterWrite.map { it.notificationKey })
        } finally {
            collector.cancel()
        }
    }
}
