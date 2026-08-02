package com.anm.signalrules.reconstruction.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRuleDraftTest {

    @Test
    fun `available title becomes an editable phrase with provenance`() {
        val draft = deriveRuleDraft(
            HistoryRecord(
                app = "Messages",
                title = "Build failed",
                body = "Details",
                contentState = NotificationContentState.AVAILABLE,
            ),
        )

        assertEquals("Messages", draft.app)
        assertEquals("Build failed", draft.phrase)
        assertEquals("Phrase copied from the captured notification title.", draft.provenanceMessage)
    }

    @Test
    fun `redacted content becomes anything and explains why`() {
        val draft = deriveRuleDraft(
            HistoryRecord(
                app = "Messages",
                title = "Content hidden by system",
                body = "Android redacted sensitive content before delivery.",
                contentState = NotificationContentState.HIDDEN_BY_SYSTEM,
            ),
        )

        assertEquals("anything", draft.phrase)
        assertEquals("Content hidden by system; no phrase was derived.", draft.provenanceMessage)
    }

    @Test
    fun `metadata-only placeholders are never copied into a phrase`() {
        val draft = deriveRuleDraft(HistoryRecord())

        assertEquals("anything", draft.phrase)
        assertEquals("Only notification metadata is stored; no phrase was derived.", draft.provenanceMessage)
    }
}
