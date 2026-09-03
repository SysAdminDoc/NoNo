package com.sysadmindoc.nono.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.notificationCategoryCatalog
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
        dao.upsert(
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
        dao.upsert(
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
    fun unknownHistoricalCategoriesAreDiscarded() = runBlocking {
        val dao = database.notificationDao()
        dao.upsert(
            NotificationEntity(
                notificationKey = "known",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.NOT_STORED.name,
                category = "msg",
            ),
        )
        dao.upsert(
            NotificationEntity(
                notificationKey = "unknown",
                packageName = "com.example.hostile",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.NOT_STORED.name,
                category = "account=matt@example.com",
            ),
        )

        assertEquals(1, dao.discardUnknownCategories(notificationCategoryCatalog.map { it.first }))
        val rows = dao.observeRecent().first().associateBy { it.notificationKey }
        assertEquals("msg", rows.getValue("known").category)
        assertEquals(null, rows.getValue("unknown").category)
    }

    @Test
    fun theRuleTriggeredFilterSelectsRecordsWhoseRulesMatched() = runBlocking {
        val dao = database.notificationDao()
        dao.upsert(
            NotificationEntity(
                notificationKey = "matched",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                matchedRuleIds = "7",
                matchState = "EVALUATED",
            ),
        )
        dao.upsert(
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
        ).forEach { dao.upsert(it) }

        assertEquals(2, dao.readWidgetCount())
        assertEquals(1, dao.readGroupSummaryCount())
        // The summary is the newest row, so the widget would otherwise report its timestamp.
        assertEquals(2_000L, dao.readWidgetLatest()?.postedAtEpochMillis)

        // Visible, and labelled by the UI, rather than hidden. Hiding it meant a summary the app
        // itself posted, carrying its own metadata, could not be seen at all.
        val defaultHistory = dao.observeHistory(query = "", filter = "All").first()
        assertEquals(listOf("summary", "child-2", "child-1"), defaultHistory.map { it.notificationKey })

        val summariesOnly = dao.observeHistory(query = "", filter = "All", groupSummary = true).first()
        assertEquals(listOf("summary"), summariesOnly.map { it.notificationKey })

        val withoutSummaries = dao.observeHistory(query = "", filter = "All", groupSummary = false).first()
        assertEquals(listOf("child-2", "child-1"), withoutSummaries.map { it.notificationKey })
    }

    @Test
    fun everyRetainedRowIsReachableByGrowingTheWindow() = runBlocking {
        val dao = database.notificationDao()
        (1..250).forEach { index ->
            dao.upsert(
                NotificationEntity(
                    notificationKey = "n-$index",
                    packageName = "com.example.chat",
                    postedAtEpochMillis = index.toLong(),
                    contentState = NotificationContentState.AVAILABLE.name,
                ),
            )
        }

        // The count is separate from the page, so it reports everything retained even while the
        // first page holds a hundred.
        assertEquals(250, dao.observeTotalCount().first())
        assertEquals(250, dao.observeFilteredCount(query = "", filter = "All").first())
        assertEquals(100, dao.observeHistory(query = "", filter = "All", limit = 100).first().size)

        // Growing the window reaches the rest, in one query, so no two pages can disagree.
        val everything = dao.observeHistory(query = "", filter = "All", limit = 300).first()
        assertEquals(250, everything.size)
        assertEquals((1..250).map { "n-$it" }.reversed(), everything.map { it.notificationKey })
    }

    @Test
    fun aStarredRowStaysReachableHoweverOldItIs() = runBlocking {
        val dao = database.notificationDao()
        (1..150).forEach { index ->
            dao.upsert(
                NotificationEntity(
                    notificationKey = "n-$index",
                    packageName = "com.example.chat",
                    postedAtEpochMillis = index.toLong(),
                    contentState = NotificationContentState.AVAILABLE.name,
                ),
            )
        }
        // The oldest row, well past the first page.
        val oldest = dao.observeHistory(query = "", filter = "All", limit = 300).first().last()
        dao.setStarred(oldest.id, true)

        val starred = dao.observeHistory(query = "", filter = "Starred", limit = 100).first()

        assertEquals(listOf("n-1"), starred.map { it.notificationKey })
        assertEquals(1, dao.observeFilteredCount(query = "", filter = "Starred").first())
        // The unfiltered total is unchanged by the filter, which is what makes it a total.
        assertEquals(150, dao.observeTotalCount().first())
    }

    @Test
    fun theFilteredCountTracksTheFilterAndNotThePage() = runBlocking {
        val dao = database.notificationDao()
        (1..120).forEach { index ->
            dao.upsert(
                NotificationEntity(
                    notificationKey = "n-$index",
                    packageName = if (index % 3 == 0) "com.example.mail" else "com.example.chat",
                    postedAtEpochMillis = index.toLong(),
                    contentState = NotificationContentState.AVAILABLE.name,
                    matchedRuleIds = if (index % 4 == 0) "7" else null,
                ),
            )
        }

        assertEquals(120, dao.observeFilteredCount(query = "", filter = "All").first())
        assertEquals(30, dao.observeFilteredCount(query = "", filter = "Rule-triggered").first())
        assertEquals(
            40,
            dao.observeFilteredCount(query = "", filter = "All", packageName = "com.example.mail").first(),
        )
        // The page limit does not enter into it.
        assertEquals(120, dao.observeFilteredCount(query = "", filter = "All").first())
    }

    @Test
    fun arepostKeepsTheStarAndTheRowRatherThanReplacingBoth() = runBlocking {
        val dao = database.notificationDao()
        val first = NotificationEntity(
            notificationKey = "chat-1",
            packageName = "com.example.chat",
            postedAtEpochMillis = 1_000L,
            contentState = NotificationContentState.AVAILABLE.name,
            importance = 2,
        )

        assertEquals(true, dao.upsert(first))
        val stored = dao.observeHistory(query = "", filter = "All").first().single()
        dao.setStarred(stored.id, true)

        // The app reposts with new metadata. REPLACE used to delete and re-insert here, which
        // reset the star and handed the row a new id.
        assertEquals(false, dao.upsert(first.copy(postedAtEpochMillis = 2_000L, importance = 4)))

        val after = dao.observeHistory(query = "", filter = "All").first().single()
        assertEquals(stored.id, after.id)
        assertEquals(true, after.starred)
        assertEquals(2_000L, after.postedAtEpochMillis)
        assertEquals(4, after.importance)
        assertEquals(1, dao.count())
    }

    @Test
    fun arepostUpdatesTheMatchStateWithoutClearingTheStar() = runBlocking {
        val dao = database.notificationDao()
        val entity = NotificationEntity(
            notificationKey = "chat-2",
            packageName = "com.example.chat",
            postedAtEpochMillis = 1_000L,
            contentState = NotificationContentState.AVAILABLE.name,
            matchedRuleIds = null,
            matchState = "EVALUATED",
        )
        dao.upsert(entity)
        val stored = dao.observeHistory(query = "", filter = "All").first().single()
        dao.setStarred(stored.id, true)

        dao.upsert(entity.copy(matchedRuleIds = "7,9", postedAtEpochMillis = 3_000L))

        val after = dao.observeHistory(query = "", filter = "All").first().single()
        assertEquals("7,9", after.matchedRuleIds)
        assertEquals(true, after.starred)
        assertEquals(listOf("chat-2"), dao.observeHistory(query = "", filter = "Rule-triggered").first().map { it.notificationKey })
    }

    @Test
    fun aStoredSummaryKeepsBothGroupsAndAnUnknownOriginByDefault() = runBlocking {
        val dao = database.notificationDao()
        dao.upsert(
            NotificationEntity(
                notificationKey = "summary",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
                groupKey = "app-group",
                overrideGroupKey = "platform-group",
                isGroupSummary = true,
            ),
        )

        val row = dao.observeHistory(query = "", filter = "All").first().single()

        assertEquals("app-group", row.groupKey)
        assertEquals("platform-group", row.overrideGroupKey)
        // Nothing classifies a stored row after the fact; the two signals only exist live.
        assertEquals(GroupSummaryOrigin.UNKNOWN.name, row.groupSummaryOrigin)
    }

    @Test
    fun historyQueryAppliesMetadataSelectorsAndAHardResultLimit() = runBlocking {
        val dao = database.notificationDao()
        dao.upsert(
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
        dao.upsert(
            NotificationEntity(
                notificationKey = "chat-visible",
                packageName = "com.example.chat",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                groupKey = "conversation",
            ),
        )
        dao.upsert(
            NotificationEntity(
                notificationKey = "mail",
                packageName = "com.example.mail",
                postedAtEpochMillis = 3_000L,
                contentState = NotificationContentState.NOT_AVAILABLE.name,
            ),
        )

        // The target row is a group summary, and the query returns both kinds, so this selector
        // narrows to it the same way every other selector under test does.
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
        dao.upsert(
            NotificationEntity(
                notificationKey = "kept",
                packageName = "com.example.chat",
                postedAtEpochMillis = 100L,
                contentState = NotificationContentState.AVAILABLE.name,
                starred = true,
            ),
        )
        dao.upsert(
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

    @Test
    fun observedFilterValuesComeFromTheWholeStore() = runBlocking {
        val dao = database.notificationDao()
        dao.upsert(
            NotificationEntity(
                notificationKey = "chat",
                packageName = "com.example.chat",
                postedAtEpochMillis = 1_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                channelId = "messages",
                groupKey = "conversation",
            ),
        )
        dao.upsert(
            NotificationEntity(
                notificationKey = "shop",
                packageName = "com.example.shop",
                postedAtEpochMillis = 2_000L,
                contentState = NotificationContentState.AVAILABLE.name,
                channelId = "offers",
                groupKey = "",
            ),
        )
        dao.upsert(
            NotificationEntity(
                notificationKey = "bare",
                packageName = "com.example.shop",
                postedAtEpochMillis = 3_000L,
                contentState = NotificationContentState.NOT_STORED.name,
            ),
        )

        // Distinct, sorted, and no empty or absent value offered as something to filter on.
        assertEquals(
            listOf("com.example.chat", "com.example.shop"),
            dao.observeObservedPackages().first(),
        )
        assertEquals(listOf("messages", "offers"), dao.observeObservedChannels().first())
        assertEquals(listOf("conversation"), dao.observeObservedGroups().first())
    }
}
