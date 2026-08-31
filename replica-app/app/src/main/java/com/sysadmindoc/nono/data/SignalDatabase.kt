package com.sysadmindoc.nono.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.RemovalReason
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.runtime.SanitizedNotification
import com.sysadmindoc.nono.runtime.IngestionMetrics

@Entity(
    tableName = "notification_history",
    indices = [Index(value = ["notificationKey"], unique = true), Index(value = ["postedAtEpochMillis"])],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val notificationKey: String,
    val packageName: String,
    val postedAtEpochMillis: Long,
    val contentState: String,
    val channelId: String? = null,
    val groupKey: String? = null,
    /** The group the platform imposed, pseudonymized. Null below API 26 or when it imposed none. */
    val overrideGroupKey: String? = null,
    val isGroupSummary: Boolean = false,
    /** [com.sysadmindoc.nono.model.GroupSummaryOrigin], never inferred from sibling rows. */
    val groupSummaryOrigin: String = GroupSummaryOrigin.UNKNOWN.name,
    /** Ids of the saved rules that matched this notification, comma separated. Never any content. */
    val matchedRuleIds: String? = null,
    /** How far evaluation got: see [com.sysadmindoc.nono.model.RuleMatchState]. */
    val matchState: String? = null,
    /** Channel importance the platform assigned, 0 to 5. Null below API 26 or when unranked. */
    val importance: Int? = null,
    /** Whether the platform treats this as a conversation. Null below API 31. */
    val isConversation: Boolean? = null,
    /** Platform category constant. A fixed vocabulary, never anything the notification said. */
    val category: String? = null,
    val isOngoing: Boolean = false,
    /** Starred records are kept until the user unstars them, whatever the retention period says. */
    val starred: Boolean = false,
    /** When the platform said this notification left the shade. Null while it is still there. */
    val removedAtEpochMillis: Long? = null,
    /**
     * [com.sysadmindoc.nono.model.RemovalReason], never inferred.
     *
     * A row can carry a removal time with an UNKNOWN reason: below API 26 the callback supplies
     * no code at all, and lockdown and any code a later Android adds are deliberately not
     * translated into one.
     */
    @ColumnInfo(defaultValue = "UNKNOWN")
    val removalReason: String = RemovalReason.UNKNOWN.name,
    /**
     * Which identifier scheme [notificationKey], [channelId] and [groupKey] are written in.
     *
     * [IDENTIFIER_SCHEME_RAW] rows were stored by a build that kept the app's own strings.
     * [IDENTIFIER_SCHEME_PSEUDONYM] rows hold per-install HMAC pseudonyms.
     */
    val identifierScheme: Int = IDENTIFIER_SCHEME_PSEUDONYM,
)

const val IDENTIFIER_SCHEME_RAW = 0
const val IDENTIFIER_SCHEME_PSEUDONYM = 1

@Entity(tableName = "ingestion_diagnostics")
data class IngestionDiagnosticsEntity(
    @PrimaryKey val singletonId: Int = 1,
    val persisted: Long = 0L,
    val dropped: Long = 0L,
    val failed: Long = 0L,
    val lastFailureAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = 0L,
    /**
     * Counts the user has acknowledged.
     *
     * The totals never go down, so without this one bad minute kept the warning banner on screen
     * for good and the user learned to ignore it. Acknowledging records what they have seen
     * rather than erasing it.
     */
    @ColumnInfo(defaultValue = "0")
    val acknowledgedDropped: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val acknowledgedFailed: Long = 0L,
)

data class WidgetLatestRow(
    val postedAtEpochMillis: Long,
    val contentState: String,
)

fun IngestionDiagnosticsEntity.toMetrics(): IngestionMetrics = IngestionMetrics(
    persisted = persisted,
    dropped = dropped,
    failed = failed,
    lastFailureAtEpochMillis = lastFailureAtEpochMillis,
    acknowledgedDropped = acknowledgedDropped,
    acknowledgedFailed = acknowledgedFailed,
)

/**
 * A [SanitizedNotification] already carries pseudonymized identifiers: the listener applies the
 * install's key while the notification is still in hand, so a raw tag never reaches this layer.
 */
fun SanitizedNotification.toEntity(
    matchedRuleIds: List<Long> = emptyList(),
    matchState: RuleMatchState = RuleMatchState.NOT_EVALUATED,
): NotificationEntity = NotificationEntity(
    notificationKey = notificationKey,
    packageName = packageName,
    postedAtEpochMillis = postedAtEpochMillis,
    contentState = contentState.name,
    channelId = channelId,
    groupKey = groupKey,
    overrideGroupKey = overrideGroupKey,
    isGroupSummary = isGroupSummary,
    groupSummaryOrigin = groupSummaryOrigin.name,
    matchedRuleIds = encodeMatchedRuleIds(matchedRuleIds),
    matchState = matchState.name,
    importance = importance,
    isConversation = isConversation,
    category = category,
    isOngoing = isOngoing,
)

/** Stored as a delimited list so a row can name every rule that matched, not only the winner. */
fun encodeMatchedRuleIds(ids: List<Long>): String? =
    ids.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")

fun decodeMatchedRuleIds(encoded: String?): List<Long> =
    encoded?.split(',').orEmpty().mapNotNull { it.trim().toLongOrNull() }

/**
 * The stored timestamp, in the device's own locale and zone.
 *
 * The row used to show the raw epoch value, which is what the store holds but not something
 * anyone can read. A record whose age matters is one the user is deciding whether to keep.
 *
 * java.time needs API 26 and this app supports 24, so the older formatter is the portable one.
 * Not a shared instance: SimpleDateFormat is not thread safe.
 */
internal fun formatStoredTime(epochMillis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMillis))

fun NotificationEntity.toHistoryRecord(): HistoryRecord {
    val state = runCatching { NotificationContentState.valueOf(contentState) }
        .getOrDefault(NotificationContentState.NOT_AVAILABLE)
    return HistoryRecord(
        id = id,
        app = packageName,
        appPackageName = packageName,
        title = when (state) {
            NotificationContentState.HIDDEN_BY_SYSTEM -> "Content recorded as hidden by an earlier build"
            else -> "Notification received"
        },
        body = when (state) {
            NotificationContentState.HIDDEN_BY_SYSTEM -> "Stored by an earlier build, which inferred redaction the platform never confirmed."
            NotificationContentState.NOT_AVAILABLE -> "This notification arrived with no title and no text."
            else -> "Metadata stored locally; notification content is not persisted."
        },
        time = formatStoredTime(postedAtEpochMillis),
        contentState = state,
        postedAtEpochMillis = postedAtEpochMillis,
        notificationKey = notificationKey,
        channelId = channelId,
        groupKey = groupKey,
        overrideGroupKey = overrideGroupKey,
        isGroupSummary = isGroupSummary,
        groupSummaryOrigin = runCatching { GroupSummaryOrigin.valueOf(groupSummaryOrigin) }
            .getOrDefault(GroupSummaryOrigin.UNKNOWN),
        matchedRuleIds = decodeMatchedRuleIds(matchedRuleIds),
        matchState = matchState?.let { name -> runCatching { RuleMatchState.valueOf(name) }.getOrNull() }
            ?: RuleMatchState.NOT_EVALUATED,
        importance = importance,
        isConversation = isConversation,
        category = category,
        isOngoing = isOngoing,
        starred = starred,
        removedAtEpochMillis = removedAtEpochMillis,
        removalReason = RemovalReason.fromStored(removalReason),
        // Only a reason the platform actually gave for a user action counts. A row with no reason
        // is not a dismissal, and this filter must not imply the app watched someone swipe.
        dismissed = RemovalReason.fromStored(removalReason).userDismissed,
    )
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEntity>>

    /**
     * Queries only persisted metadata. Rule-triggered selects records whose saved rules matched
     * when they arrived. Dismissed selects records the platform said the user removed, which is a
     * reason it supplied rather than anything this app watched or inferred.
     *
     * Group summaries are included. They used to be hidden unless asked for, which meant a
     * summary the app itself posted, carrying its own metadata, was invisible. They are labelled
     * instead, and the counts that stand for "how many notifications arrived" exclude them
     * separately. [groupSummary] narrows to summaries only, or to non-summaries only, when set.
     */
    @Query(
        """
        SELECT * FROM notification_history
        WHERE (:query = '' OR packageName LIKE '%' || :query || '%' OR
            notificationKey LIKE '%' || :query || '%' OR
            contentState LIKE '%' || :query || '%' OR
            COALESCE(channelId, '') LIKE '%' || :query || '%' OR
            COALESCE(groupKey, '') LIKE '%' || :query || '%')
          AND (
            :filter = 'All'
            OR (:filter = 'Rule-triggered' AND matchedRuleIds IS NOT NULL AND matchedRuleIds != '')
            OR (:filter = 'Starred' AND starred = 1)
            OR (:filter = 'Dismissed' AND removalReason IN ('CLICKED', 'DISMISSED', 'DISMISSED_ALL'))
          )
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:channelId IS NULL OR channelId = :channelId)
          AND (:contentState IS NULL OR contentState = :contentState)
          AND (:groupKey IS NULL OR groupKey = :groupKey)
          AND (:importance IS NULL OR importance = :importance)
          AND (:conversation IS NULL OR isConversation = :conversation)
          AND (:groupSummary IS NULL OR isGroupSummary = :groupSummary)
          AND (:fromEpochMillis IS NULL OR postedAtEpochMillis >= :fromEpochMillis)
        ORDER BY postedAtEpochMillis DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeHistory(
        query: String,
        filter: String,
        packageName: String? = null,
        channelId: String? = null,
        contentState: String? = null,
        groupKey: String? = null,
        groupSummary: Boolean? = null,
        importance: Int? = null,
        conversation: Boolean? = null,
        fromEpochMillis: Long? = null,
        limit: Int = 100,
    ): Flow<List<NotificationEntity>>

    /**
     * How many rows the same filters select, whatever the page limit is.
     *
     * Separate from the page query on purpose. The screen used to show the size of the loaded
     * page as though it were a count of notifications, so anything past the limit was invisible
     * and uncounted.
     */
    @Query(
        """
        SELECT COUNT(*) FROM notification_history
        WHERE (:query = '' OR packageName LIKE '%' || :query || '%' OR
            notificationKey LIKE '%' || :query || '%' OR
            contentState LIKE '%' || :query || '%' OR
            COALESCE(channelId, '') LIKE '%' || :query || '%' OR
            COALESCE(groupKey, '') LIKE '%' || :query || '%')
          AND (
            :filter = 'All'
            OR (:filter = 'Rule-triggered' AND matchedRuleIds IS NOT NULL AND matchedRuleIds != '')
            OR (:filter = 'Starred' AND starred = 1)
            OR (:filter = 'Dismissed' AND removalReason IN ('CLICKED', 'DISMISSED', 'DISMISSED_ALL'))
          )
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:channelId IS NULL OR channelId = :channelId)
          AND (:contentState IS NULL OR contentState = :contentState)
          AND (:groupKey IS NULL OR groupKey = :groupKey)
          AND (:importance IS NULL OR importance = :importance)
          AND (:conversation IS NULL OR isConversation = :conversation)
          AND (:groupSummary IS NULL OR isGroupSummary = :groupSummary)
          AND (:fromEpochMillis IS NULL OR postedAtEpochMillis >= :fromEpochMillis)
        """,
    )
    fun observeFilteredCount(
        query: String,
        filter: String,
        packageName: String? = null,
        channelId: String? = null,
        contentState: String? = null,
        groupKey: String? = null,
        groupSummary: Boolean? = null,
        importance: Int? = null,
        conversation: Boolean? = null,
        fromEpochMillis: Long? = null,
    ): Flow<Int>

    /** Everything retained, whatever the user has filtered to. */
    @Query("SELECT COUNT(*) FROM notification_history")
    fun observeTotalCount(): Flow<Int>

    /**
     * Every stored match, for counting how often each rule would have fired.
     *
     * Deliberately not the history screen's query: that one carries the user's filters and a page
     * limit, so counting from it would report a rule as idle simply because History was filtered
     * to another app.
     */
    @Query("SELECT matchedRuleIds FROM notification_history WHERE matchedRuleIds IS NOT NULL AND matchedRuleIds != ''")
    fun observeMatchedRuleIds(): Flow<List<String>>

    /**
     * Every package history has seen, for the app picker.
     *
     * The launcher query misses apps with no launcher activity, and those post notifications too.
     * It also misses an app the user has since uninstalled, whose rules should stay editable.
     */
    @Query("SELECT DISTINCT packageName FROM notification_history ORDER BY packageName")
    fun observeObservedPackages(): Flow<List<String>>

    @Query("SELECT * FROM ingestion_diagnostics WHERE singletonId = 1 LIMIT 1")
    fun observeIngestionDiagnostics(): Flow<IngestionDiagnosticsEntity?>

    @Query("SELECT * FROM ingestion_diagnostics WHERE singletonId = 1 LIMIT 1")
    suspend fun readIngestionDiagnostics(): IngestionDiagnosticsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveIngestionDiagnostics(diagnostics: IngestionDiagnosticsEntity)

    /**
     * Records the counts the user has seen, so the banner reports what is happening now.
     *
     * Nothing is erased: the totals stay, and what was acknowledged stays alongside them, so a
     * later failure still raises the banner.
     *
     * @return true when the acknowledgement was written.
     */
    @Transaction
    suspend fun acknowledgeIngestionProblems(liveDropped: Long = 0L, liveFailed: Long = 0L): Boolean {
        val current = readIngestionDiagnostics() ?: return false
        saveIngestionDiagnostics(
            current.copy(
                // The banner reports the larger of the live and durable counts, so the
                // acknowledgement has to cover the same number or the dismissal does nothing
                // visible while still reporting that it worked.
                acknowledgedDropped = maxOf(current.dropped, liveDropped),
                acknowledgedFailed = maxOf(current.failed, liveFailed),
            ),
        )
        return true
    }

    @Transaction
    suspend fun mergeIngestionMetrics(
        persistedDelta: Long,
        droppedDelta: Long,
        failedDelta: Long,
        failureAtEpochMillis: Long?,
        nowEpochMillis: Long,
    ) {
        val current = readIngestionDiagnostics() ?: IngestionDiagnosticsEntity()
        saveIngestionDiagnostics(
            current.copy(
                persisted = current.persisted + persistedDelta.coerceAtLeast(0L),
                dropped = current.dropped + droppedDelta.coerceAtLeast(0L),
                failed = current.failed + failedDelta.coerceAtLeast(0L),
                lastFailureAtEpochMillis = failureAtEpochMillis ?: current.lastFailureAtEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            ),
        )
    }

    /**
     * REPLACE is deliberately not used here.
     *
     * SQLite implements it as a delete followed by an insert, so a repost of a notification the
     * user had starred silently unstarred it and gave the row a new id. IGNORE leaves the
     * existing row alone and returns -1, which is the signal to update it in place instead.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(notification: NotificationEntity): Long

    /**
     * Updates everything a repost can change, and nothing the user owns.
     *
     * The star and the row id are the user's; they survive. So does the first-seen timestamp
     * of the row's primary key, because the key is what makes it one logical capture.
     */
    @Query(
        """
        UPDATE notification_history
        SET postedAtEpochMillis = :postedAtEpochMillis,
            contentState = :contentState,
            channelId = :channelId,
            groupKey = :groupKey,
            overrideGroupKey = :overrideGroupKey,
            isGroupSummary = :isGroupSummary,
            groupSummaryOrigin = :groupSummaryOrigin,
            matchedRuleIds = :matchedRuleIds,
            matchState = :matchState,
            importance = :importance,
            isConversation = :isConversation,
            category = :category,
            isOngoing = :isOngoing,
            identifierScheme = :identifierScheme
        WHERE notificationKey = :notificationKey
        """,
    )
    suspend fun updateByKey(
        notificationKey: String,
        postedAtEpochMillis: Long,
        contentState: String,
        channelId: String?,
        groupKey: String?,
        overrideGroupKey: String?,
        isGroupSummary: Boolean,
        groupSummaryOrigin: String,
        matchedRuleIds: String?,
        matchState: String?,
        importance: Int?,
        isConversation: Boolean?,
        category: String?,
        isOngoing: Boolean,
        identifierScheme: Int,
    )

    /**
     * Writes [notification], updating an existing row with the same key rather than replacing it.
     *
     * @return true when the row was new.
     */
    @Transaction
    suspend fun upsert(notification: NotificationEntity): Boolean {
        if (insertIfAbsent(notification) != -1L) return true
        updateByKey(
            notificationKey = notification.notificationKey,
            postedAtEpochMillis = notification.postedAtEpochMillis,
            contentState = notification.contentState,
            channelId = notification.channelId,
            groupKey = notification.groupKey,
            overrideGroupKey = notification.overrideGroupKey,
            isGroupSummary = notification.isGroupSummary,
            groupSummaryOrigin = notification.groupSummaryOrigin,
            matchedRuleIds = notification.matchedRuleIds,
            matchState = notification.matchState,
            importance = notification.importance,
            isConversation = notification.isConversation,
            category = notification.category,
            isOngoing = notification.isOngoing,
            identifierScheme = notification.identifierScheme,
        )
        return false
    }

    /** Retention never removes a record the user starred. */
    @Query("DELETE FROM notification_history WHERE postedAtEpochMillis < :cutoffEpochMillis AND starred = 0")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int

    /** @return rows updated, so a caller can tell a real change from a record that has gone. */
    @Query("UPDATE notification_history SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean): Int

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    /**
     * Every retained row, for export.
     *
     * Deliberately not the history screen's query: that one carries the user's filters and a page
     * limit, so exporting from it wrote whatever happened to be on screen and called it the
     * history. Suspending, so the caller reads it off the main thread.
     */
    @Query("SELECT * FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC")
    suspend fun readAllForExport(): List<NotificationEntity>

    /**
     * Counts what arrived. A group summary stands for its group rather than being an arrival of
     * its own, so counting it would report more notifications than the device received. The
     * widget says so rather than leaving the difference unexplained.
     */
    @Query("SELECT COUNT(*) FROM notification_history WHERE isGroupSummary = 0")
    suspend fun readWidgetCount(): Int

    /** Summaries stored alongside them, so the widget can say how many it left out. */
    @Query("SELECT COUNT(*) FROM notification_history WHERE isGroupSummary = 1")
    suspend fun readGroupSummaryCount(): Int

    @Query(
        """
        SELECT postedAtEpochMillis, contentState FROM notification_history
        WHERE isGroupSummary = 0
        ORDER BY postedAtEpochMillis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun readWidgetLatest(): WidgetLatestRow?

    @Transaction
    suspend fun insertAndPrune(notification: NotificationEntity, cutoffEpochMillis: Long) {
        upsert(notification)
        deleteBefore(cutoffEpochMillis)
    }

    @Query("SELECT * FROM notification_history WHERE identifierScheme = $IDENTIFIER_SCHEME_RAW")
    suspend fun readRawIdentifierRows(): List<NotificationEntity>

    @Query(
        """
        UPDATE notification_history
        SET notificationKey = :notificationKey,
            channelId = :channelId,
            groupKey = :groupKey,
            overrideGroupKey = :overrideGroupKey,
            identifierScheme = $IDENTIFIER_SCHEME_PSEUDONYM
        WHERE id = :id
        """,
    )
    suspend fun rewriteIdentifiers(
        id: Long,
        notificationKey: String,
        channelId: String?,
        groupKey: String?,
        overrideGroupKey: String?,
    )

    /** @return rows removed, so "deleted" is only said when a row actually went. */
    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Records that a notification left the shade.
     *
     * Matches on the stored key, which is a per-install pseudonym, so the caller has to
     * pseudonymize the platform key the same way the capture did. Only rows that are not already
     * marked are touched: a repost followed by a second removal is the same notification, and the
     * first departure is the one with a reason attached to the metadata that was stored.
     *
     * @return rows marked, so a caller can tell a real removal from one for a notification this
     * build never captured.
     */
    @Query(
        "UPDATE notification_history SET removedAtEpochMillis = :removedAtEpochMillis, " +
            "removalReason = :removalReason WHERE notificationKey = :notificationKey " +
            "AND removedAtEpochMillis IS NULL",
    )
    suspend fun markRemoved(
        notificationKey: String,
        removedAtEpochMillis: Long,
        removalReason: String,
    ): Int

    /**
     * Clears a removal because the same notification is back.
     *
     * An app that reposts a key it withdrew has a live notification again, and leaving the old
     * departure on the row would have the history say a notification that is on screen is gone.
     */
    @Query(
        "UPDATE notification_history SET removedAtEpochMillis = NULL, removalReason = :unknownReason " +
            "WHERE notificationKey = :notificationKey",
    )
    suspend fun clearRemoval(notificationKey: String, unknownReason: String): Int

    /** Read before a delete, so the row can be put back if the user takes it back. */
    @Query("SELECT * FROM notification_history WHERE id = :id LIMIT 1")
    suspend fun readById(id: Long): NotificationEntity?

    /**
     * Puts a deleted row back exactly as it was, or reports that it could not.
     *
     * Deliberately not [upsert]. If the app reposted that notification while the snackbar was up,
     * a new row already holds the key, and upsert would update that row instead: the original id
     * would never come back, the star would be dropped, and the live row's timestamp would be
     * rewound, all reported as success.
     *
     * @return true when the row was restored.
     */
    @Transaction
    suspend fun restore(notification: NotificationEntity): Boolean =
        insertIfAbsent(notification) != -1L

    /**
     * Replaces the raw identifiers left by an older build.
     *
     * A row whose rewritten key would collide with one already present is deleted rather than
     * left holding the app's own strings: two rows claiming one notification identity are the
     * same capture, and keeping the readable copy is exactly what this is here to stop.
     *
     * @return how many rows were rewritten.
     */
    @Transaction
    suspend fun pseudonymizeStoredIdentifiers(pseudonyms: IdentifierPseudonyms): Int {
        var rewritten = 0
        readRawIdentifierRows().forEach { row ->
            val key = pseudonyms.pseudonym(row.notificationKey).orEmpty()
            val result = runCatching {
                rewriteIdentifiers(
                    id = row.id,
                    notificationKey = key,
                    channelId = pseudonyms.pseudonym(row.channelId),
                    groupKey = pseudonyms.pseudonym(row.groupKey),
                    overrideGroupKey = pseudonyms.pseudonym(row.overrideGroupKey),
                )
            }
            if (result.isSuccess) rewritten += 1 else deleteById(row.id)
        }
        return rewritten
    }
}

@Database(entities = [NotificationEntity::class, IngestionDiagnosticsEntity::class], version = 11, exportSchema = true)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val DATABASE_NAME = "nono_history.db"
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN groupKey TEXT")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN isGroupSummary INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ingestion_diagnostics (
                        singletonId INTEGER NOT NULL,
                        persisted INTEGER NOT NULL,
                        dropped INTEGER NOT NULL,
                        failed INTEGER NOT NULL,
                        lastFailureAtEpochMillis INTEGER,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(singletonId)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN channelId TEXT")
            }
        }

        /**
         * Records which saved rules matched a notification when it arrived. Existing rows keep a
         * null match state, which reads as "not evaluated" rather than "nothing matched".
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN matchedRuleIds TEXT")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN matchState TEXT")
            }
        }

        /**
         * Records the platform's own assessment of a notification. All four are supplied by
         * Android rather than by the notification's content.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN importance INTEGER")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN isConversation INTEGER")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE notification_history ADD COLUMN isOngoing INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds the star. Existing records default to unstarred, which is what they were. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Marks every existing row as holding the app's own identifier strings. The values are
         * rewritten in Kotlin by [NotificationDao.pseudonymizeStoredIdentifiers], because an
         * HMAC is not something SQLite can compute.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notification_history ADD COLUMN identifierScheme INTEGER NOT NULL " +
                        "DEFAULT $IDENTIFIER_SCHEME_RAW",
                )
            }
        }

        /**
         * Records the platform's own group alongside the app's, and who the summary came from.
         *
         * Existing rows default to UNKNOWN rather than being classified retroactively: the two
         * signals that decide it are only available while the notification is in hand.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN overrideGroupKey TEXT")
                db.execSQL(
                    "ALTER TABLE notification_history ADD COLUMN groupSummaryOrigin TEXT NOT NULL " +
                        "DEFAULT '${GroupSummaryOrigin.UNKNOWN.name}'",
                )
            }
        }

        /** Lets the user acknowledge ingestion counts that would otherwise warn for ever. */
        /** Adds the removal record. Existing rows are not retroactively called removed. */
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_history ADD COLUMN removedAtEpochMillis INTEGER")
                db.execSQL(
                    "ALTER TABLE notification_history ADD COLUMN removalReason TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ingestion_diagnostics ADD COLUMN acknowledgedDropped INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ingestion_diagnostics ADD COLUMN acknowledgedFailed INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: SignalDatabase? = null

        /**
         * The process-wide handle on the metadata store, kept in the no-backup tree.
         *
         * Room's invalidation tracker only notifies observers registered on the instance that
         * performed the write. The listener, the view model, and the widget all read and write
         * the same file, so handing each of them its own instance meant a captured notification
         * never woke the history flow: the screen looked frozen until it was rebuilt. One
         * instance per process is also what lets the widget answer a broadcast without opening
         * and closing the database each time.
         *
         * Nothing closes this. A shared handle closed by whichever owner happens to stop first
         * would break the others, and the store is meant to live as long as the process.
         */
        fun get(context: Context): SignalDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        /**
         * Discards the process-wide handle and the file behind it.
         *
         * The counterpart to [SignalPreferences.resetForTest]: a view-model test needs an empty
         * history, and the instance is deliberately shared and never closed in normal operation.
         *
         * Never called from shipping code.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun resetForTest(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
                val file = context.applicationContext.noBackupFilesDir.resolve(DATABASE_NAME)
                listOf(file, File("${file.path}-wal"), File("${file.path}-shm")).forEach { it.delete() }
            }
        }

        private fun build(context: Context): SignalDatabase = Room.databaseBuilder(
            context.applicationContext,
            SignalDatabase::class.java,
            context.applicationContext.noBackupFilesDir.resolve(DATABASE_NAME).absolutePath,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        ).build()
    }
}
