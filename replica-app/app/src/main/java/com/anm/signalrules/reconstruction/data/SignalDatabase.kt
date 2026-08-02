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
import kotlinx.coroutines.flow.Flow
import com.anm.signalrules.reconstruction.model.HistoryRecord
import com.anm.signalrules.reconstruction.model.NotificationContentState
import com.anm.signalrules.reconstruction.runtime.SanitizedNotification

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
)

fun SanitizedNotification.toEntity(): NotificationEntity = NotificationEntity(
    notificationKey = notificationKey,
    packageName = packageName,
    postedAtEpochMillis = postedAtEpochMillis,
    contentState = contentState.name,
)

fun NotificationEntity.toHistoryRecord(): HistoryRecord {
    val state = runCatching { NotificationContentState.valueOf(contentState) }
        .getOrDefault(NotificationContentState.NOT_AVAILABLE)
    return HistoryRecord(
        id = id,
        app = packageName,
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
    )
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_history ORDER BY postedAtEpochMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("DELETE FROM notification_history WHERE postedAtEpochMillis < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM notification_history")
    suspend fun count(): Int

    @Transaction
    suspend fun insertAndPrune(notification: NotificationEntity, cutoffEpochMillis: Long) {
        insert(notification)
        deleteBefore(cutoffEpochMillis)
    }
}

@Database(entities = [NotificationEntity::class], version = 1, exportSchema = false)
abstract class SignalDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val DATABASE_NAME = "signal_rules_history.db"

        /** Stores notification-derived metadata in the no-backup tree by default. */
        fun create(context: Context): SignalDatabase = Room.databaseBuilder(
            context.applicationContext,
            SignalDatabase::class.java,
            context.noBackupFilesDir.resolve(DATABASE_NAME).absolutePath,
        ).build()
    }
}
