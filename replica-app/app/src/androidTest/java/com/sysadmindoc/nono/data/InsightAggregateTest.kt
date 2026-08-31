package com.sysadmindoc.nono.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.model.INSIGHT_DAY_COUNT
import com.sysadmindoc.nono.model.INSIGHT_TOP_APP_LIMIT
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.insightsStartEpochMillis
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The aggregate queries the Insights screen reads.
 *
 * These run on a device because SQLite does the grouping, including the local-time conversion that
 * strftime performs against the device's own zone. The arithmetic on top of the rows is covered by
 * LocalInsightsTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class InsightAggregateTest {
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

    /** Local wall-clock time on the device running the test, so strftime's answer is predictable. */
    private fun localMillis(daysAgo: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private suspend fun insert(
        key: String,
        packageName: String,
        postedAt: Long,
        summary: Boolean = false,
    ) {
        database.notificationDao().upsert(
            NotificationEntity(
                notificationKey = key,
                packageName = packageName,
                postedAtEpochMillis = postedAt,
                contentState = NotificationContentState.AVAILABLE.name,
                isGroupSummary = summary,
            ),
        )
    }

    @Test
    fun totalsSeparateCapturedNotificationsFromGroupSummaries() = runBlocking {
        insert("a", "com.example.chat", localMillis(0, 9))
        insert("b", "com.example.chat", localMillis(0, 9))
        insert("summary", "com.example.chat", localMillis(0, 9), summary = true)

        val totals = database.notificationDao().observeInsightTotals().first()

        assertEquals(3, totals.storedRecordCount)
        assertEquals(2, totals.totalCaptured)
        assertEquals(1, totals.excludedGroupSummaries)
        assertEquals(totals.storedRecordCount, database.notificationDao().observeTotalCount().first())
    }

    @Test
    fun totalsOnAnEmptyDatabaseAreZeroRatherThanNull() = runBlocking {
        val totals = database.notificationDao().observeInsightTotals().first()

        assertEquals(0, totals.storedRecordCount)
        assertEquals(0, totals.totalCaptured)
        assertEquals(0, totals.excludedGroupSummaries)
    }

    @Test
    fun topAppsRankByCountAndStopAtTheLimit() = runBlocking {
        // Seven packages, so the limit has something to cut.
        (1..7).forEach { index ->
            repeat(index) { copy -> insert("p$index-$copy", "com.example.app$index", localMillis(0, 10)) }
        }
        insert("summary", "com.example.app7", localMillis(0, 10), summary = true)

        val apps = database.notificationDao().observeTopInsightApps(INSIGHT_TOP_APP_LIMIT).first()

        assertEquals(INSIGHT_TOP_APP_LIMIT, apps.size)
        assertEquals("com.example.app7", apps.first().packageName)
        // The summary from app7 is not counted, so its total stays at seven.
        assertEquals(listOf(7, 6, 5, 4, 3), apps.map { it.count })
    }

    @Test
    fun hourGroupsUseTheDevicesLocalClock() = runBlocking {
        insert("morning", "com.example.chat", localMillis(0, 8))
        insert("morning-again", "com.example.chat", localMillis(1, 8))
        insert("evening", "com.example.chat", localMillis(0, 21))
        insert("summary", "com.example.chat", localMillis(0, 21), summary = true)

        val hours = database.notificationDao().observeInsightHours().first()

        assertEquals(2, hours.single { it.hour == 8 }.count)
        assertEquals(1, hours.single { it.hour == 21 }.count)
        assertTrue("hours must stay inside the clock", hours.all { it.hour in 0..23 })
    }

    @Test
    fun theDayWindowExcludesAnythingBeforeItsCutoff() = runBlocking {
        insert("today", "com.example.chat", localMillis(0, 12))
        insert("in-window", "com.example.chat", localMillis(INSIGHT_DAY_COUNT - 1, 12))
        insert("too-old", "com.example.chat", localMillis(INSIGHT_DAY_COUNT + 3, 12))
        insert("summary", "com.example.chat", localMillis(0, 12), summary = true)

        val cutoff = insightsStartEpochMillis(System.currentTimeMillis(), TimeZone.getDefault())
        val days = database.notificationDao().observeInsightDays(cutoff, INSIGHT_DAY_COUNT).first()

        assertEquals(2, days.sumOf { it.count })
        assertEquals(2, days.size)
        assertTrue("days must come back oldest first", days.map { it.dayKey } == days.map { it.dayKey }.sorted())
    }
}
