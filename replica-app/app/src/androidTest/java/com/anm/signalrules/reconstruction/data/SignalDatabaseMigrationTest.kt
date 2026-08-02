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
    fun `v1 metadata survives every schema migration`() {
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
            4,
            true,
            SignalDatabase.MIGRATION_1_2,
            SignalDatabase.MIGRATION_2_3,
            SignalDatabase.MIGRATION_3_4,
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
    fun `v3 metadata survives the channel migration`() {
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
            4,
            true,
            SignalDatabase.MIGRATION_3_4,
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
}
