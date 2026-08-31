package com.sysadmindoc.nono.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sysadmindoc.nono.MainViewModel
import com.sysadmindoc.nono.data.ImportRejection
import com.sysadmindoc.nono.data.NotificationEntity
import com.sysadmindoc.nono.data.RuleTransferLimits
import com.sysadmindoc.nono.data.SignalDatabase
import com.sysadmindoc.nono.data.SignalPreferences
import com.sysadmindoc.nono.data.decodeRuleStore
import com.sysadmindoc.nono.model.HISTORY_PAGE_SIZE
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.Overlay
import com.sysadmindoc.nono.model.RECORD_ONLY_ACTION
import com.sysadmindoc.nono.model.RootTab
import com.sysadmindoc.nono.model.RuleStore
import com.sysadmindoc.nono.model.Route
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UNSAVED_RULE_ID
import com.sysadmindoc.nono.model.UiState
import com.sysadmindoc.nono.runtime.HistoryStorage
import com.sysadmindoc.nono.runtime.HistoryStorageSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the view model as a stateful thing, rather than for the pure functions it
 * calls.
 *
 * Every defect this suite pins was green under the pure-function tests: the arithmetic was right
 * and the wiring was wrong. So these drive the real view model against the real preference store
 * and the real database, and assert on the state a screen would actually render.
 *
 * Both stores are process-wide singletons on purpose, which is why each test resets them: without
 * that, a rule saved by one test decides what the next one sees.
 */
@RunWith(AndroidJUnit4::class)
class MainViewModelStateTest {

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var model: MainViewModel

    /**
     * A store per model this test built, including the ones a restart replaced.
     *
     * A view model keeps collecting from the database for as long as its scope lives, and nothing
     * in a test clears it the way a destroyed Activity would. Left running, a model from an
     * earlier test queries a database file the next test has already deleted, and the failure
     * lands on whichever test happens to be running.
     */
    private val stores = mutableListOf<ViewModelStore>()

    /** Builds a view model the way a screen does, in a store the test can clear afterwards. */
    private fun createModel(): MainViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )[MainViewModel::class.java]
    }

    @Before
    fun setUp() {
        stores.clear()
        SignalPreferences.resetForTest(application)
        SignalDatabase.resetForTest(application)
        HistoryStorageSettings.set(HistoryStorage.METADATA_ONLY.label)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stores.forEach { it.clear() }
            stores.clear()
        }
        SignalPreferences.resetForTest(application)
        SignalDatabase.resetForTest(application)
    }

    /** Builds a view model on the main thread and waits for its startup read to land. */
    private fun startModel(): MainViewModel {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { model = createModel() }
        awaitState("startup read never completed") { it.rulesLoaded }
        return model
    }

    /** Rebuilds the view model against the same stores, the way a cold start does. */
    private fun restartModel(): UiState {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { model = createModel() }
        return awaitState("the restarted model never read its stores") { it.rulesLoaded }
    }

    private fun onMain(block: MainViewModel.() -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { model.block() }
    }

    /**
     * Polls rather than collecting the flow: the view model writes its state from the main thread
     * and several of these paths hop to the IO dispatcher and back, so the assertion has to be
     * allowed to arrive late without the test deciding in advance how late.
     */
    private fun awaitState(reason: String, timeoutMillis: Long = 10_000L, predicate: (UiState) -> Boolean): UiState {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val current = model.state.value
            if (predicate(current)) return current
            Thread.sleep(25L)
        }
        throw AssertionError(reason + " (last state: " + model.state.value + ")")
    }

    /**
     * Waits for a setting to reach the preference file.
     *
     * [MainViewModel.setSetting] updates the state first and writes afterwards, so a cold start
     * launched immediately after it can genuinely read the previous value. That is a race in the
     * test, not in the app, and waiting for the write is what makes the restart assertion mean
     * what it says.
     */
    /**
     * Waits for the rule store to reach the preference file.
     *
     * Rules are written after the state is updated, so a cold start launched the instant a save
     * returns can read the previous file. Waiting for the write is what makes a restart assertion
     * about persistence rather than about timing.
     */
    private fun awaitPersistedRules(reason: String, timeoutMillis: Long = 10_000L, predicate: (RuleStore) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: RuleStore? = null
        while (System.currentTimeMillis() < deadline) {
            val stored = runBlocking {
                decodeRuleStore(SignalPreferences.get(application).data.first()[SignalPreferences.RULES_KEY])
            }
            last = stored
            if (stored != null && predicate(stored)) return
            Thread.sleep(25L)
        }
        throw AssertionError(reason + " (stored: " + last + ")")
    }

    private fun awaitPersistedSetting(label: String, value: String, timeoutMillis: Long = 10_000L) {
        val key = SignalPreferences.settingKey(label)
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val stored = runBlocking { SignalPreferences.get(application).data.first()[key] }
            if (stored == value) return
            Thread.sleep(25L)
        }
        throw AssertionError(label + " never reached storage")
    }

    private fun insertHistory(count: Int, packageName: String = "com.example.app") {
        val dao = SignalDatabase.get(application).notificationDao()
        runBlocking {
            repeat(count) { index ->
                dao.insertAndPrune(
                    NotificationEntity(
                        notificationKey = "key-" + packageName + "-" + index,
                        packageName = packageName,
                        postedAtEpochMillis = 1_000L + index,
                        contentState = NotificationContentState.NOT_AVAILABLE.name,
                    ),
                    cutoffEpochMillis = 0L,
                )
            }
        }
    }

    @Test
    fun aSuggestionOpensAsANewRuleRatherThanEditingTheOneItResembles() {
        startModel()
        val suggestion = SignalRule(
            id = 77L,
            name = "Silence delivery updates",
            app = "com.example.delivery",
            action = RECORD_ONLY_ACTION,
        )

        onMain { startRuleFromSuggestion(suggestion) }
        val opened = model.state.value
        assertEquals(Route.RULE_BUILDER, opened.route)
        assertEquals("a suggestion carries a template id that must not be saved", UNSAVED_RULE_ID, opened.draft.id)
        assertNull("editing state from a previous rule must not survive", opened.selectedRuleId)

        onMain { saveRule() }
        val saved = awaitState("the suggestion was never saved") { it.rules.isNotEmpty() }
        assertEquals(1, saved.rules.size)
        assertNotEquals("a saved rule must get a real id", UNSAVED_RULE_ID, saved.rules.single().id)
        assertNotEquals("the template id must not be adopted", 77L, saved.rules.single().id)
    }

    @Test
    fun editingASavedRuleReplacesItInsteadOfAddingASecondCopy() {
        startModel()
        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "First", app = "com.example.one", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        val first = awaitState("nothing saved") { it.rules.size == 1 }.rules.single()

        onMain { editRule(first) }
        assertEquals(first.id, model.state.value.selectedRuleId)
        onMain { updateDraft { it.copy(name = "Renamed") } }
        onMain { saveRule() }

        val after = awaitState("the edit never landed") { it.rules.single().name == "Renamed" }
        assertEquals("editing must not create a second rule", 1, after.rules.size)
        assertEquals("the id must survive an edit, because history refers to it", first.id, after.rules.single().id)
    }

    @Test
    fun aDisabledRuleStaysDisabledThroughAnEditAndARestart() {
        startModel()
        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "Quiet hours", app = "com.example.two", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        val saved = awaitState("nothing saved") { it.rules.size == 1 }.rules.single()
        assertTrue("a new rule starts enabled", saved.enabled)

        onMain { toggleRule(saved.id) }
        awaitState("the toggle never landed") { !it.rules.single().enabled }

        // Editing an unrelated field must not hand back the builder copy the rule was opened with.
        onMain { editRule(model.state.value.rules.single()) }
        onMain { updateDraft { it.copy(name = "Quiet hours evening") } }
        onMain { saveRule() }
        val edited = awaitState("the edit never landed") { it.rules.single().name == "Quiet hours evening" }
        assertFalse("an edit must not silently re-enable a rule", edited.rules.single().enabled)

        // And it has to survive the process, because that is where the user actually notices.
        awaitPersistedRules("the edit never reached storage") { store ->
            store.rules.singleOrNull()?.name == "Quiet hours evening"
        }
        val restarted = restartModel()
        assertFalse("a disabled rule must come back disabled", restarted.rules.single().enabled)
        assertEquals("Quiet hours evening", restarted.rules.single().name)
    }

    @Test
    fun ruleIdsAreNotHandedOutTwiceAcrossDeletesAndRestarts() {
        startModel()
        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "A", app = "com.example.a", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        val first = awaitState("nothing saved") { it.rules.size == 1 }.rules.single().id

        onMain { showRuleOverlay(Overlay.RULE_MORE, first) }
        onMain { deleteRule() }
        awaitState("the delete never landed") { it.rules.isEmpty() }
        awaitPersistedRules("the delete never reached storage") { it.rules.isEmpty() }

        restartModel()
        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "B", app = "com.example.b", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        val second = awaitState("nothing saved after the restart") { it.rules.size == 1 }.rules.single().id

        // History rows record the ids that matched them. Reusing a deleted rule id makes an old
        // record claim it was caught by a rule that did not exist when it arrived.
        assertNotEquals("a deleted rule id must not be reissued", first, second)
    }

    @Test
    fun aDuplicatedRuleGetsItsOwnIdentityAndRecordsOnly() {
        startModel()
        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "Source", app = "com.example.source", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        val source = awaitState("nothing saved") { it.rules.size == 1 }.rules.single()

        onMain { showRuleOverlay(Overlay.RULE_MORE, source.id) }
        onMain { duplicateRule() }
        val after = awaitState("the duplicate never landed") { it.rules.size == 2 }
        val copy = after.rules.first { it.id != source.id }
        assertNotEquals(source.id, copy.id)
        assertEquals("a copy can only do what this build performs", RECORD_ONLY_ACTION, copy.action)
    }

    @Test
    fun theListenerReadsCaptureSettingsWithoutWaitingForAnActivity() {
        startModel()
        onMain { setSetting(SignalPreferences.HISTORY_STORAGE_SETTING, HistoryStorage.OFF.label) }
        awaitState("the setting never landed") {
            it.settings[SignalPreferences.HISTORY_STORAGE_SETTING] == HistoryStorage.OFF.label
        }

        // The platform can start the listener with no Activity ever having run, so the setting has
        // to come back from storage rather than from whatever the last screen set in memory.
        awaitPersistedSetting(SignalPreferences.HISTORY_STORAGE_SETTING, HistoryStorage.OFF.label)
        HistoryStorageSettings.set(HistoryStorage.METADATA_ONLY.label)
        val restarted = restartModel()
        assertEquals(
            HistoryStorage.OFF.label,
            restarted.settings[SignalPreferences.HISTORY_STORAGE_SETTING],
        )
        assertEquals(
            "a cold start must apply the stored setting, not the default",
            HistoryStorage.OFF,
            HistoryStorageSettings.get(),
        )
    }

    @Test
    fun historyPagesThroughEverythingItKeptAndCountsTheWholeSet() {
        val total = HISTORY_PAGE_SIZE + 12
        insertHistory(total)
        startModel()

        val firstPage = awaitState("the first page never arrived") { it.history.size == HISTORY_PAGE_SIZE }
        assertEquals("the count must describe the set, not the page", total, firstPage.historyTotalCount)
        assertEquals(total, firstPage.historyFilteredCount)
        assertTrue(firstPage.hasMoreHistory)

        onMain { loadMoreHistory() }
        val second = awaitState("the second page never arrived") { it.history.size == total }
        assertFalse("there is nothing left to load", second.hasMoreHistory)

        // A filter has to reset the window, or the second page of an old filter decides how much
        // of the new one is shown.
        onMain { setHistorySearch("com.example.app") }
        val filtered = awaitState("the filter never applied") {
            it.historyLimit == HISTORY_PAGE_SIZE && it.history.size == HISTORY_PAGE_SIZE
        }
        assertEquals(total, filtered.historyFilteredCount)
    }

    @Test
    fun exportWritesEveryRetainedRecordRatherThanThePageOnScreen() {
        val total = HISTORY_PAGE_SIZE + 7
        insertHistory(total)
        startModel()
        awaitState("the first page never arrived") { it.history.size == HISTORY_PAGE_SIZE }

        onMain { beginHistoryExport() }
        val ready = awaitState("the export was never prepared") { it.transferExportRequest > 0 }
        assertTrue(ready.transferExportIsHistory)

        val destination = File(application.cacheDir, "history-export-test.csv")
        destination.delete()
        onMain { writeExport(Uri.fromFile(destination)) }
        awaitState("the export never reported a result") { it.transientMessage != null }

        val lines = destination.readLines()
        // One header plus one line per record. The old export stopped at the page limit.
        assertEquals(total + 1, lines.size)
        destination.delete()
    }

    @Test
    fun anOversizedRuleFileIsRefusedWithTheReasonAndNothingIsImported() {
        startModel()
        val oversized = File(application.cacheDir, "oversized-rules.json")
        oversized.writeText("x".repeat((RuleTransferLimits.MAX_ENCODED_BYTES + 1_024L).toInt()))

        onMain { beginImport(Uri.fromFile(oversized)) }
        val refused = awaitState("the import never reported anything") {
            it.transientMessage != null && it.transientMessage != "Reading rule file…"
        }
        assertEquals(ImportRejection.TOO_LARGE.message, refused.transientMessage)
        assertTrue("nothing may be imported from a refused file", refused.rules.isEmpty())
        oversized.delete()
    }

    @Test
    fun aFailedHistoryWriteNeverReportsSuccess() {
        insertHistory(1)
        startModel()
        val record = awaitState("the record never arrived") { it.history.size == 1 }.history.single()

        // The row goes while the screen still shows it, which is what a retention pass or a second
        // tap on a stale list does.
        runBlocking { SignalDatabase.get(application).notificationDao().deleteById(record.id) }

        onMain { setHistoryStarred(record.id, true) }
        val starFailure = awaitState("starring said nothing at all") { it.transientMessage != null }
        assertEquals("That record could not be updated.", starFailure.transientMessage)

        onMain { clearTransient() }
        onMain { deleteHistoryRecord(record.id) }
        val deleteFailure = awaitState("deleting said nothing at all") { it.transientMessage != null }
        assertEquals("That record could not be deleted.", deleteFailure.transientMessage)
        assertNull("a failed delete has nothing to undo", deleteFailure.transientUndo)
    }

    @Test
    fun aUserJourneyFromOnboardingThroughRulesHistoryAndSettingsHoldsItsState() {
        insertHistory(3)
        startModel()
        assertEquals("a fresh install starts at onboarding", Route.ONBOARDING, model.state.value.route)

        onMain { completeOnboarding() }
        assertEquals(Route.ROOT, model.state.value.route)
        assertEquals(RootTab.RULES, model.state.value.rootTab)

        onMain { newRule() }
        onMain { updateDraft { it.copy(name = "Journey", app = "com.example.app", action = RECORD_ONLY_ACTION) } }
        onMain { saveRule() }
        awaitState("the rule never saved") { it.rules.size == 1 }

        onMain { selectRoot(RootTab.HISTORY) }
        val history = awaitState("history never loaded") { it.history.size == 3 }
        assertEquals(RootTab.HISTORY, history.rootTab)

        onMain { setHistoryStarred(history.history.first().id, true) }
        awaitState("starring said nothing") { it.transientMessage != null }
        assertEquals("Kept until you unstar it.", model.state.value.transientMessage)

        onMain { selectRoot(RootTab.SETTINGS) }
        onMain { setSetting(SignalPreferences.HISTORY_RETENTION_SETTING, "7 days") }
        awaitState("the setting never landed") {
            it.settings[SignalPreferences.HISTORY_RETENTION_SETTING] == "7 days"
        }
        awaitPersistedSetting(SignalPreferences.HISTORY_RETENTION_SETTING, "7 days")

        // Everything the journey established has to come back after a cold start.
        awaitPersistedRules("the rule never reached storage") { it.rules.size == 1 }
        val restarted = restartModel()
        assertEquals(Route.ROOT, restarted.route)
        assertEquals("Journey", restarted.rules.single().name)
        assertEquals("7 days", restarted.settings[SignalPreferences.HISTORY_RETENTION_SETTING])
        val restoredHistory = awaitState("history never came back") { it.history.isNotEmpty() }
        assertTrue("the starred record survived", restoredHistory.history.any { it.starred })
    }
}
