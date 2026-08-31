package com.sysadmindoc.nono.runtime

import androidx.datastore.preferences.core.edit
import com.sysadmindoc.nono.data.SignalPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The listener can be started by the platform with no Activity ever having run, so everything
 * here reads the store directly rather than through a view model.
 */
class ListenerSettingsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun storeFile(): File = File(tempFolder.newFolder("store"), "nono.preferences_pb")

    @Test
    fun `a fresh install falls back to the shipped defaults`() = runTest {
        val file = storeFile()
        val store = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})

        val settings = listenerSettings(store.data.first())

        assertEquals(HistoryRetention.THIRTY_DAYS, settings.retention)
        assertEquals(HistoryStorage.METADATA_ONLY, settings.storage)
    }

    @Test
    fun `a cold started listener reads the saved seven day retention`() = runTest {
        val file = storeFile()
        val writer = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})
        writer.edit { it[SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING)] = "7 days" }

        // Nothing carries the value across; the second reader is the cold-started service.
        val settings = listenerSettings(writer.data.first())

        assertEquals(HistoryRetention.SEVEN_DAYS, settings.retention)
        val cutoff = retentionCutoffEpochMillis(settings.retention, 0L)
        assertEquals(-7L * 24 * 60 * 60 * 1000, cutoff)
    }

    @Test
    fun `a cold started listener reads forever and prunes nothing`() = runTest {
        val file = storeFile()
        val writer = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})
        writer.edit { it[SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING)] = "Forever" }

        val settings = listenerSettings(writer.data.first())

        assertEquals(HistoryRetention.FOREVER, settings.retention)
        assertEquals(Long.MIN_VALUE, retentionCutoffEpochMillis(settings.retention, 1_700_000_000_000L))
    }

    @Test
    fun `a cold started listener reads the saved storage policy`() = runTest {
        val file = storeFile()
        val writer = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})
        writer.edit { it[SignalPreferences.settingKey(SignalPreferences.HISTORY_STORAGE_SETTING)] = "Off" }

        assertEquals(HistoryStorage.OFF, listenerSettings(writer.data.first()).storage)
    }

    @Test
    fun `applying settings publishes both bridges the listener reads`() = runTest {
        val file = storeFile()
        val writer = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})
        writer.edit {
            it[SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING)] = "3 months"
            it[SignalPreferences.settingKey(SignalPreferences.HISTORY_STORAGE_SETTING)] = "Off"
        }

        applyListenerSettings(listenerSettings(writer.data.first()))
        try {
            assertEquals(HistoryRetention.THREE_MONTHS, HistoryRetentionSettings.get())
            assertEquals(HistoryStorage.OFF, HistoryStorageSettings.get())
        } finally {
            // Process-wide singletons; leaving them set would leak into whatever runs next.
            applyListenerSettings(ListenerSettings())
        }
    }

    @Test
    fun `storage off writes nothing`() = runTest {
        var writes = 0

        val written = persistCapture(ListenerSettings(storage = HistoryStorage.OFF), 1_000L) { writes += 1 }

        assertEquals(false, written)
        assertEquals(0, writes)
    }

    @Test
    fun `metadata only writes once with the cutoff for the saved period`() = runTest {
        val cutoffs = mutableListOf<Long>()
        val now = 1_700_000_000_000L

        val written = persistCapture(
            ListenerSettings(retention = HistoryRetention.SEVEN_DAYS, storage = HistoryStorage.METADATA_ONLY),
            now,
        ) { cutoffs += it }

        assertEquals(true, written)
        assertEquals(listOf(now - 7L * 24 * 60 * 60 * 1000), cutoffs)
    }

    @Test
    fun `a cold started listener prunes with the saved period, not the process default`() = runTest {
        val file = storeFile()
        val writer = SignalPreferences.create(backgroundScope, { file }, onCorruption = {})
        writer.edit { it[SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING)] = "Forever" }
        val now = 1_700_000_000_000L
        val cutoffs = mutableListOf<Long>()

        // Exactly what the service does in onCreate: read the store, then write.
        persistCapture(listenerSettings(writer.data.first()), now) { cutoffs += it }

        assertEquals(listOf(Long.MIN_VALUE), cutoffs)
    }

    @Test
    fun `the settings key matches the one the settings screen writes`() {
        // The view model derives these from the same helper. A drift here means a saved setting
        // is written under one name and read under another, which is silent and total.
        assertEquals(
            "setting_history_retention",
            SignalPreferences.settingKey(SignalPreferences.HISTORY_RETENTION_SETTING).name,
        )
        assertEquals(
            "setting_notification_history",
            SignalPreferences.settingKey(SignalPreferences.HISTORY_STORAGE_SETTING).name,
        )
    }
}
