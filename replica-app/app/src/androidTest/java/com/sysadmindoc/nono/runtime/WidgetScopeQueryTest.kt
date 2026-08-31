package com.sysadmindoc.nono.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.data.NotificationEntity
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.model.NotificationContentState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each widget scope against the query it stands for.
 *
 * The mapping is one `when` over three Room queries, and the part that can be wrong is the SQL:
 * a scope pointed at the wrong condition would put a plausible number under a label that means
 * something else. One fixture answers all three at once, so a query drifting to include group
 * summaries fails here.
 */
@RunWith(AndroidJUnit4::class)
class WidgetScopeQueryTest {
    private lateinit var database: SignalDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SignalDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun insert(
        key: String,
        matchedRuleIds: String? = null,
        starred: Boolean = false,
        summary: Boolean = false,
    ) {
        database.notificationDao().upsert(
            NotificationEntity(
                notificationKey = key,
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                matchedRuleIds = matchedRuleIds,
                starred = starred,
                isGroupSummary = summary,
            ),
        )
    }

    @Test
    fun eachScopeCountsWhatItsLabelSays() = runBlocking {
        val dao = database.notificationDao()
        insert("plain")
        insert("matched", matchedRuleIds = "7")
        insert("matched-and-starred", matchedRuleIds = "7,9", starred = true)
        insert("starred", starred = true)
        insert("empty-match-string", matchedRuleIds = "")

        assertEquals(5, dao.readWidgetCount())
        assertEquals(2, dao.readRuleMatchedWidgetCount())
        assertEquals(2, dao.readStarredWidgetCount())
    }

    @Test
    fun noScopeCountsAGroupSummary() = runBlocking {
        // A summary stands for its group rather than being an arrival of its own. Every count in
        // the app excludes them, and a scope that did not would disagree with History.
        val dao = database.notificationDao()
        insert("summary", summary = true)
        insert("matched-summary", matchedRuleIds = "7", summary = true)
        insert("starred-summary", starred = true, summary = true)

        assertEquals(0, dao.readWidgetCount())
        assertEquals(0, dao.readRuleMatchedWidgetCount())
        assertEquals(0, dao.readStarredWidgetCount())
        assertEquals(3, dao.readGroupSummaryCount())
    }

    @Test
    fun anEmptyStoreAnswersZeroForEveryScope() = runBlocking {
        val dao = database.notificationDao()

        assertEquals(0, dao.readWidgetCount())
        assertEquals(0, dao.readRuleMatchedWidgetCount())
        assertEquals(0, dao.readStarredWidgetCount())
    }
}
