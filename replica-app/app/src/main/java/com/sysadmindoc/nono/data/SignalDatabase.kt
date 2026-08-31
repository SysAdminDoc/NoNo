package com.sysadmindoc.nono.data

import android.content.Context
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
import com.sysadmindoc.nono.model.GroupSummaryOrigin
import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
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
        time = postedAtEpochMillis.toString(),
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
    )
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEntity>>

    /**
     * Queries only persisted metadata. Rule-triggered selects records whose saved rules matched
     * when they arrived. Dismissed stays empty until an action engine writes that state; returning
     * no rows is safer than inventing them.
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

    @Query("UPDATE notification_history SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

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

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteById(id: Long)

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

@Database(entities = [NotificationEntity::class, IngestionDiagnosticsEntity::class], version = 9, exportSchema = true)
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
        ).build()
    }
}
