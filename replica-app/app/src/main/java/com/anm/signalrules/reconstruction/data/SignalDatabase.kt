package com.anm.signalrules.reconstruction.data

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
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.NotificationContentState
import com.anm.signalrules.reconstruction.runtime.SanitizedNotification
import com.anm.signalrules.reconstruction.runtime.IngestionMetrics

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

fun SanitizedNotification.toEntity(): NotificationEntity = NotificationEntity(
    notificationKey = notificationKey,
    packageName = packageName,
    postedAtEpochMillis = postedAtEpochMillis,
    contentState = contentState.name,
    channelId = channelId,
    groupKey = groupKey,
    isGroupSummary = isGroupSummary,
)

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
    )
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEntity>>

    /**
     * Queries only persisted metadata. Rule-triggered and dismissed are intentionally empty
     * until an action engine writes those states; returning no rows is safer than inventing them.
     */
    @Query(
        """
        SELECT * FROM notification_history
        WHERE (:query = '' OR packageName LIKE '%' || :query || '%' OR
            notificationKey LIKE '%' || :query || '%' OR
            contentState LIKE '%' || :query || '%' OR
            COALESCE(channelId, '') LIKE '%' || :query || '%' OR
            COALESCE(groupKey, '') LIKE '%' || :query || '%')
          AND (:filter = 'All')
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:channelId IS NULL OR channelId = :channelId)
          AND (:contentState IS NULL OR contentState = :contentState)
          AND (:groupKey IS NULL OR groupKey = :groupKey)
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
        fromEpochMillis: Long? = null,
        limit: Int = 100,
    ): Flow<List<NotificationEntity>>

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

    @Query("DELETE FROM notification_history WHERE postedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun readWidgetCount(): Int

    @Query("SELECT postedAtEpochMillis, contentState FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun readWidgetLatest(): WidgetLatestRow?

    @Transaction
    suspend fun insertAndPrune(notification: NotificationEntity, cutoffEpochMillis: Long) {
        insert(notification)
        deleteBefore(cutoffEpochMillis)
    }
}

@Database(entities = [NotificationEntity::class, IngestionDiagnosticsEntity::class], version = 4, exportSchema = true)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val DATABASE_NAME = "signal_rules_history.db"
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
