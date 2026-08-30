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
    val isGroupSummary: Boolean = false,
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
)

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
    isGroupSummary = isGroupSummary,
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
            NotificationContentState.HIDDEN_BY_SYSTEM -> "Content hidden by system"
            else -> "Notification received"
        },
        body = when (state) {
            NotificationContentState.HIDDEN_BY_SYSTEM -> "Android redacted sensitive content before delivery."
            NotificationContentState.NOT_AVAILABLE -> "No notification content was supplied."
            else -> "Metadata stored locally; notification content is not persisted."
        },
        time = postedAtEpochMillis.toString(),
        contentState = state,
        postedAtEpochMillis = postedAtEpochMillis,
        notificationKey = notificationKey,
        channelId = channelId,
        groupKey = groupKey,
        isGroupSummary = isGroupSummary,
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
     * Group summaries are excluded unless [groupSummary] asks for them. Android 16 groups an app's
     * notifications itself and posts a summary alongside the children, so counting both reports
     * more notifications than arrived and shows the user a row with nothing of its own in it.
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
          AND isGroupSummary = COALESCE(:groupSummary, 0)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    /** Retention never removes a record the user starred. */
    @Query("DELETE FROM notification_history WHERE postedAtEpochMillis < :cutoffEpochMillis AND starred = 0")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int

    @Query("UPDATE notification_history SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    /** Counts what actually arrived: a system-generated group summary is not its own notification. */
    @Query("SELECT COUNT(*) FROM notification_history WHERE isGroupSummary = 0")
    suspend fun readWidgetCount(): Int

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
        insert(notification)
        deleteBefore(cutoffEpochMillis)
    }
}

@Database(entities = [NotificationEntity::class, IngestionDiagnosticsEntity::class], version = 7, exportSchema = true)
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
}
