package com.anm.signalrules.reconstruction.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The listener writes and the UI reads through what must be the same Room instance.
 *
 * Room's invalidation tracker only notifies observers registered on the instance that performed
 * the write, so a second instance over the same file produces a history flow that never updates.
 */
@RunWith(AndroidJUnit4::class)
class SignalDatabaseSharingTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun everyOwnerReceivesTheSameInstance() {
        assertSame(SignalDatabase.get(context), SignalDatabase.get(context))
    }

    @Test
    fun aWriteThroughTheSharedInstanceWakesAnObserverWithoutRequerying() = runBlocking<Unit> {
        val dao = SignalDatabase.get(context).notificationDao()
        val key = "sharing-test-${System.nanoTime()}"
        val postedAt = System.currentTimeMillis()

        // Collected once, before the write, exactly as the view model collects it.
        val observed = withTimeout(10_000) {
            val flow = dao.observeRecent(limit = 100)
            val before = flow.first()
            assertTrue(before.none { it.notificationKey == key })

            dao.insertAndPrune(
                NotificationEntity(
                    notificationKey = key,
                    packageName = "com.example.sharing",
                    postedAtEpochMillis = postedAt,
                    contentState = "NOT_STORED",
                ),
                cutoffEpochMillis = 0L,
            )

            flow.first { rows -> rows.any { it.notificationKey == key } }
        }

        val row = observed.first { it.notificationKey == key }
        assertEquals("com.example.sharing", row.packageName)
        assertEquals(postedAt, row.postedAtEpochMillis)

        dao.deleteBefore(postedAt + 1)
    }
}
