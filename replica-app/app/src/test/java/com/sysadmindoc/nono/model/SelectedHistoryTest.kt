package com.sysadmindoc.nono.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The record menu acts on the selected record, so the selection has to resolve correctly. */
class SelectedHistoryTest {

    private val state = UiState(
        history = listOf(
            HistoryRecord(id = 1L, app = "com.example.chat", appPackageName = "com.example.chat"),
            HistoryRecord(id = 2L, app = "Shell", appPackageName = null),
        ),
    )

    @Test
    fun theSelectedRecordSuppliesItsPackage() {
        assertEquals("com.example.chat", state.copy(selectedHistoryId = 1L).selectedHistoryPackageName)
    }

    @Test
    fun aRecordWithoutAPackageResolvesToNothingToOpen() {
        assertNull(state.copy(selectedHistoryId = 2L).selectedHistoryPackageName)
    }

    @Test
    fun noSelectionAndAStaleSelectionBothResolveToNothing() {
        assertNull(state.selectedHistoryPackageName)
        assertNull(state.copy(selectedHistoryId = 404L).selectedHistoryPackageName)
    }
}
