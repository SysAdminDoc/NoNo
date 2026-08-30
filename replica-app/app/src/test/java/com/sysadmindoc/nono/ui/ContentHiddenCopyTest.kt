package com.sysadmindoc.nono.ui

import com.sysadmindoc.nono.model.HistoryRecord
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHiddenCopyTest {

    @Test
    fun theCommandNamesThisAppAndTheSensitiveNotificationsPermission() {
        val command = sensitiveNotificationsAppOpsCommand("com.sysadmindoc.nono.debug")

        assertEquals(
            "adb shell cmd appops set --user 0 com.sysadmindoc.nono.debug " +
                "RECEIVE_SENSITIVE_NOTIFICATIONS allow",
            command,
        )
    }

    @Test
    fun theCommandCarriesNoTypographyThatBreaksATerminalPaste() {
        val command = sensitiveNotificationsAppOpsCommand("com.example.app")

        // Smart dashes and quotes are the usual way a copied command stops working.
        assertTrue(command.none { it in setOf('—', '–', '‘', '’', '“', '”') })
        assertTrue(command.all { it.code in 32..126 })
    }

    @Test
    fun theExplainerIsOfferedOnlyForARecordTheSystemRedacted() {
        val hidden = HistoryRecord(id = 1L, contentState = NotificationContentState.HIDDEN_BY_SYSTEM)
        val ordinary = HistoryRecord(id = 2L, contentState = NotificationContentState.NOT_STORED)
        val state = UiState(history = listOf(hidden, ordinary))

        assertEquals(
            NotificationContentState.HIDDEN_BY_SYSTEM,
            state.copy(selectedHistoryId = 1L).selectedHistoryContentState,
        )
        assertEquals(
            NotificationContentState.NOT_STORED,
            state.copy(selectedHistoryId = 2L).selectedHistoryContentState,
        )
        assertNull(state.copy(selectedHistoryId = null).selectedHistoryContentState)
        assertNull(state.copy(selectedHistoryId = 99L).selectedHistoryContentState)
    }
}
