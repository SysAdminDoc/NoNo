package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleTransferTest {
    private val existing = listOf(SignalRule(id = 1, name = "Local"))
    private val incoming = listOf(
        SignalRule(id = 1, name = "Imported"),
        SignalRule(id = 2, name = "New"),
    )

    @Test
    fun `encrypted export round trips and does not expose rule json`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())

        assertTrue(encoded.contains("\"encrypted\":true"))
        assertTrue(!encoded.contains("Imported"))
        assertEquals(incoming, (RuleTransfer.importRules(encoded, "correct horse".toCharArray()) as RuleImportResult.Success).rules)
    }

    @Test
    fun `encrypted import requires a passphrase and rejects the wrong one`() {
        val encoded = RuleTransfer.exportRules(incoming, "secret".toCharArray())

        assertEquals(RuleImportResult.NeedsPassphrase, RuleTransfer.importRules(encoded))
        assertEquals(RuleImportResult.InvalidFile, RuleTransfer.importRules(encoded, "wrong".toCharArray()))
    }

    @Test
    fun `plaintext export remains explicit and importable`() {
        val encoded = RuleTransfer.exportRules(incoming)

        assertTrue(encoded.contains("\"encrypted\":false"))
        assertEquals(incoming, (RuleTransfer.importRules(encoded) as RuleImportResult.Success).rules)
    }

    @Test
    fun `preview identifies additions and same-id conflicts without mutating`() {
        val preview = RuleTransfer.preview(existing, incoming)

        assertEquals(listOf(incoming[1]), preview.additions)
        assertEquals(listOf(existing[0]), preview.conflicts.map { it.existing })
        assertEquals(existing + incoming[1], RuleTransfer.commit(existing, incoming))
        assertEquals("Imported", RuleTransfer.commit(existing, incoming, mapOf(1L to ConflictResolution.REPLACE_EXISTING))?.first()?.name)
    }

    @Test
    fun `cancelled import and commit leave caller state alone`() {
        val encoded = RuleTransfer.exportRules(incoming)

        assertEquals(RuleImportResult.Cancelled, RuleTransfer.importRules(encoded, cancelled = { true }))
        assertNull(RuleTransfer.commit(existing, incoming, cancelled = { true }))
        assertNotEquals(existing, incoming)
    }
}
