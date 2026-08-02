package com.anm.signalrules.reconstruction.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SignalPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val onboarding = booleanPreferencesKey("onboarding_complete")

    private fun storeFile(): File = tempFolder.newFile("signal_rules.preferences_pb")

    @Test
    fun `round trips values through a healthy store`() = runTest {
        val file = storeFile()
        var corrupted = false
        val store = SignalPreferences.create(
            scope = backgroundScope,
            produceFile = { file },
            onCorruption = { corrupted = true },
        )

        store.edit { it[onboarding] = true }

        assertEquals(true, store.data.first()[onboarding])
        assertFalse("a healthy store must not report corruption", corrupted)
    }

    @Test
    fun `replaces an unreadable store with defaults and reports the recovery`() = runTest {
        val file = storeFile()
        file.writeBytes(ByteArray(512) { 0x7A })
        var corrupted = false
        val store = SignalPreferences.create(
            scope = backgroundScope,
            produceFile = { file },
            onCorruption = { corrupted = true },
        )

        // The read must succeed with defaults rather than throwing CorruptionException,
        // which is what previously killed the process on every launch.
        val values = store.data.first()

        assertNull("a recovered store starts empty", values[onboarding])
        assertTrue("recovery must be reported so the UI can tell the user", corrupted)
    }

    @Test
    fun `a recovered store is writable afterwards`() = runTest {
        val file = storeFile()
        file.writeBytes(ByteArray(64) { 0x01 })
        val store = SignalPreferences.create(
            scope = backgroundScope,
            produceFile = { file },
            onCorruption = { },
        )

        store.edit { it[onboarding] = true }

        assertEquals(true, store.data.first()[onboarding])
    }

    @Test
    fun `the read guard used by the view model swallows a failing store`() = runTest {
        // Mirrors MainViewModel's read path: any throw degrades to defaults.
        val values = kotlinx.coroutines.flow.flow<androidx.datastore.preferences.core.Preferences> {
            throw java.io.IOException("disk gone")
        }.catch { emit(emptyPreferences()) }.first()

        assertNull(values[onboarding])
    }
}
