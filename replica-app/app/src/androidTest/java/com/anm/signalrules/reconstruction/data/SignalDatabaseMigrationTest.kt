package com.anm.signalrules.reconstruction.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalDatabaseMigrationTest {
    private val databaseName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SignalDatabase::class.java,
        emptyList(),
    )

    @Test
    @Throws(IOException::class)
    fun v1MetadataSurvivesEverySchemaMigration() {
        helper.createDatabase("migration-v1-test.db", 1).apply {
            execSQL(
                "INSERT INTO notification_history " +
                    "(notificationKey, packageName, postedAtEpochMillis, contentState) " +
                    "VALUES ('legacy-v1', 'com.example.messages', 24, 'NOT_AVAILABLE')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            "migration-v1-test.db",
            6,
            true,
            SignalDatabase.MIGRATION_1_2,
            SignalDatabase.MIGRATION_2_3,
            SignalDatabase.MIGRATION_3_4,
            SignalDatabase.MIGRATION_4_5,
            SignalDatabase.MIGRATION_5_6,
        )
        migrated.query("SELECT packageName, contentState, channelId, groupKey, isGroupSummary FROM notification_history").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("com.example.messages", cursor.getString(0))
            assertEquals("NOT_AVAILABLE", cursor.getString(1))
            check(cursor.isNull(2))
            check(cursor.isNull(3))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("migration-v1-test.db")
    }

    @Test
    @Throws(IOException::class)
    fun v3MetadataSurvivesTheChannelMigration() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                "INSERT INTO notification_history " +
                    "(notificationKey, packageName, postedAtEpochMillis, contentState, groupKey, isGroupSummary) " +
                    "VALUES ('legacy', 'com.example.messages', 42, 'HIDDEN_BY_SYSTEM', 'chat', 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            SignalDatabase.MIGRATION_3_4,
            SignalDatabase.MIGRATION_4_5,
            SignalDatabase.MIGRATION_5_6,
        )
        migrated.query("SELECT packageName, contentState, channelId, groupKey FROM notification_history").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("com.example.messages", cursor.getString(0))
            assertEquals("HIDDEN_BY_SYSTEM", cursor.getString(1))
            check(cursor.isNull(2))
            assertEquals("chat", cursor.getString(3))
        }
        migrated.close()

        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    @Test
    @Throws(IOException::class)
    fun v4RowsGainMatchColumnsWithoutClaimingTheyWereEvaluated() {
        val name = "migration-v4-test.db"
        helper.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO notification_history " +
                    "(notificationKey, packageName, postedAtEpochMillis, contentState, channelId, isGroupSummary) " +
                    "VALUES ('legacy-v4', 'com.example.messages', 64, 'AVAILABLE', 'messages', 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name,
            6,
            true,
            SignalDatabase.MIGRATION_4_5,
            SignalDatabase.MIGRATION_5_6,
        )
        migrated.query("SELECT packageName, channelId, matchedRuleIds, matchState FROM notification_history").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("com.example.messages", cursor.getString(0))
            assertEquals("messages", cursor.getString(1))
            // A row captured before evaluation existed must not read as "nothing matched".
            check(cursor.isNull(2))
            check(cursor.isNull(3))
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    @Test
    @Throws(IOException::class)
    fun theSchemaStoresNoNotificationContent() {
        val name = "migration-content-test.db"
        helper.createDatabase(name, 6).use { db ->
            db.query("SELECT name FROM pragma_table_info('notification_history')").use { cursor ->
                val columns = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                // Nothing that could hold what a notification said.
                listOf("title", "text", "body", "content", "message", "ticker", "bigText").forEach { banned ->
                    check(columns.none { it.equals(banned, ignoreCase = true) }) {
                        "notification_history must not carry a $banned column, found: $columns"
                    }
                }
            }
        }
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    @Test
    @Throws(IOException::class)
    fun v5RowsGainRankingColumnsWithoutInventingAnAssessment() {
        val name = "migration-v5-test.db"
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO notification_history " +
                    "(notificationKey, packageName, postedAtEpochMillis, contentState, isGroupSummary) " +
                    "VALUES ('legacy-v5', 'com.example.messages', 96, 'AVAILABLE', 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 6, true, SignalDatabase.MIGRATION_5_6)
        migrated.query("SELECT packageName, importance, isConversation, category, isOngoing FROM notification_history").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("com.example.messages", cursor.getString(0))
            // Nothing is known about a row captured before the ranking was read.
            check(cursor.isNull(1))
            check(cursor.isNull(2))
            check(cursor.isNull(3))
            assertEquals(0, cursor.getInt(4))
        }
        migrated.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }
}
