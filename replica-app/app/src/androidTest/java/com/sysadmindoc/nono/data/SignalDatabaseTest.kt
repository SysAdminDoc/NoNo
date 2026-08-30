package com.sysadmindoc.nono.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.model.NotificationContentState
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
    fun insertAndPruneAreOneTransactionalOperation() = runBlocking {
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

    @Test
    fun historyFiltersOnThePlatformsOwnAssessment() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(
            NotificationEntity(
                notificationKey = "chat",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                importance = 4,
                isConversation = true,
                category = "msg",
            ),
        )
        dao.insert(
            NotificationEntity(
                notificationKey = "promo",
                packageName = "com.example.shop",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                importance = 2,
                isConversation = false,
                category = "promo",
                isOngoing = true,
            ),
        )

        assertEquals(
            listOf("chat"),
            dao.observeHistory(query = "", filter = "All", conversation = true).first().map { it.notificationKey },
        )
        assertEquals(
            listOf("promo"),
            dao.observeHistory(query = "", filter = "All", importance = 2).first().map { it.notificationKey },
        )
        val ongoing = dao.observeHistory(query = "", filter = "All").first().single { it.notificationKey == "promo" }
        assertEquals(true, ongoing.isOngoing)
        assertEquals("promo", ongoing.category)
    }

    @Test
    fun theRuleTriggeredFilterSelectsRecordsWhoseRulesMatched() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(
            NotificationEntity(
                notificationKey = "matched",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                matchedRuleIds = "7",
                matchState = "EVALUATED",
            ),
        )
        dao.insert(
            NotificationEntity(
                notificationKey = "unmatched",
                packageName = "com.example.chat",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                matchedRuleIds = null,
                matchState = "EVALUATED",
            ),
        )

        val triggered = dao.observeHistory(query = "", filter = "Rule-triggered").first()
        assertEquals(listOf("matched"), triggered.map { it.notificationKey })

        val all = dao.observeHistory(query = "", filter = "All").first()
        assertEquals(listOf("unmatched", "matched"), all.map { it.notificationKey })
    }

    @Test
    fun systemGroupSummariesAreNotCountedAsNotifications() = runBlocking {
        val dao = database.notificationDao()
        // What Android 16 delivers for one app's burst: the children, plus a summary it made itself.
        listOf(
            NotificationEntity(
                notificationKey = "child-1",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                groupKey = "chat-group",
            ),
            NotificationEntity(
                notificationKey = "child-2",
                packageName = "com.example.chat",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                groupKey = "chat-group",
            ),
            NotificationEntity(
                notificationKey = "summary",
                packageName = "com.example.chat",
                postedAtEpochMillis = 3_000L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
                groupKey = "chat-group",
                isGroupSummary = true,
            ),
        ).forEach { dao.insert(it) }

        assertEquals(2, dao.readWidgetCount())
        // The summary is the newest row, so the widget would otherwise report its timestamp.
        assertEquals(2_000L, dao.readWidgetLatest()?.postedAtEpochMillis)

        val defaultHistory = dao.observeHistory(query = "", filter = "All").first()
        assertEquals(listOf("child-2", "child-1"), defaultHistory.map { it.notificationKey })

        val summariesOnly = dao.observeHistory(query = "", filter = "All", groupSummary = true).first()
        assertEquals(listOf("summary"), summariesOnly.map { it.notificationKey })
    }

    @Test
    fun historyQueryAppliesMetadataSelectorsAndAHardResultLimit() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(
            NotificationEntity(
                notificationKey = "chat-hidden",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.HIDDEN_BY_SYSTEM.name,
                channelId = "messages",
                groupKey = "conversation",
                isGroupSummary = true,
            ),
        )
        dao.insert(
            NotificationEntity(
                notificationKey = "chat-visible",
                packageName = "com.example.chat",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                groupKey = "conversation",
            ),
        )
        dao.insert(
            NotificationEntity(
                notificationKey = "mail",
                packageName = "com.example.mail",
                postedAtEpochMillis = 3_000L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
            ),
        )

        // The target row is a group summary, and summaries are no longer returned by default, so
        // this selector has to be stated like any other. Every other selector under test is
        // unchanged.
        val filtered = dao.observeHistory(
            query = "chat",
            filter = "All",
            packageName = "com.example.chat",
            contentState = NotificationContentState.HIDDEN_BY_SYSTEM.name,
            groupKey = "conversation",
            groupSummary = true,
            fromEpochMillis = 500L,
            limit = 1,
        ).first()

        assertEquals(listOf("chat-hidden"), filtered.map { it.notificationKey })
        assertEquals(
            listOf("chat-hidden"),
            dao.observeHistory(
                query = "",
                filter = "All",
                channelId = "messages",
                groupSummary = true,
            ).first().map { it.notificationKey },
        )
        assertEquals(emptyList<NotificationEntity>(), dao.observeHistory("", "Rule-triggered").first())
    }

    @Test
    fun ingestionDiagnosticsAccumulateCountersWithoutPayloadData() = runBlocking {
        val dao = database.notificationDao()
        dao.mergeIngestionMetrics(
            persistedDelta = 4L,
            droppedDelta = 1L,
            failedDelta = 0L,
            failureAtEpochMillis = null,
            nowEpochMillis = 10_000L,
        )
        dao.mergeIngestionMetrics(
            persistedDelta = 2L,
            droppedDelta = 0L,
            failedDelta = 1L,
            failureAtEpochMillis = 11_000L,
            nowEpochMillis = 11_000L,
        )

        val diagnostics = dao.observeIngestionDiagnostics().first()
        requireNotNull(diagnostics)
        assertEquals(6L, diagnostics.persisted)
        assertEquals(1L, diagnostics.dropped)
        assertEquals(1L, diagnostics.failed)
        assertEquals(11_000L, diagnostics.lastFailureAtEpochMillis)
        assertEquals(11_000L, diagnostics.updatedAtEpochMillis)
    }

    @Test
    fun aStarredRecordSurvivesRetentionPruning() = runBlocking {
        val dao = database.notificationDao()
        dao.insert(
            NotificationEntity(
                notificationKey = "kept",
                packageName = "com.example.chat",
                postedAtEpochMillis = 100L,
                contentState = NotificationContentState.AVAILABLE.name,
                starred = true,
            ),
        )
        dao.insert(
            NotificationEntity(
                notificationKey = "aged-out",
                packageName = "com.example.chat",
                postedAtEpochMillis = 100L,
                contentState = NotificationContentState.AVAILABLE.name,
            ),
        )

        dao.deleteBefore(1_000L)

        assertEquals(
            listOf("kept"),
            dao.observeHistory(query = "", filter = "All").first().map { it.notificationKey },
        )

        // Unstarring makes it eligible again.
        val kept = dao.observeHistory(query = "", filter = "All").first().single()
        dao.setStarred(kept.id, false)
        dao.deleteBefore(1_000L)
        assertEquals(emptyList<String>(), dao.observeHistory(query = "", filter = "All").first().map { it.notificationKey })
    }
}
