package com.anm.signalrules.reconstruction.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anm.signalrules.reconstruction.model.NotificationContentState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalDatabaseTest {
    private lateinit var database: SignalDatabase

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
    fun `insert and prune are one transactional operation`() = runBlocking {
        val dao = database.notificationDao()
        dao.insertAndPrune(
            NotificationEntity(
                notificationKey = "old",
                packageName = "com.example.old",
                postedAtEpochMillis = 999L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
            ),
            cutoffEpochMillis = 1_000L,
        )
        dao.insertAndPrune(
            NotificationEntity(
                notificationKey = "keep",
                packageName = "com.example.keep",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
            ),
            cutoffEpochMillis = 1_000L,
        )

        assertEquals(1, dao.count())
        assertEquals("keep", dao.observeRecent().first().single().notificationKey)
    }
}
