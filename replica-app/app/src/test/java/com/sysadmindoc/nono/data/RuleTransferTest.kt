package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.SignalRule
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
        assertTrue(encoded.contains(PORTABLE_TRANSFER_PRIVACY_WARNING))
        assertTrue(!encoded.contains("Imported"))
        assertEquals(incoming, (RuleTransfer.importRules(encoded, "correct horse".toCharArray()) as RuleImportResult.Success).rules)
    }

    @Test
    fun `exports record the derivation cost instead of hiding it in the code`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())

        assertTrue(encoded.contains("\"formatVersion\":2"))
        assertTrue(encoded.contains("\"kdf\":\"$PBKDF2_KDF_NAME\""))
        assertTrue(encoded.contains("\"iterations\":$PBKDF2_ITERATIONS"))
        // OWASP's floor for PBKDF2-HMAC-SHA256.
        assertTrue(PBKDF2_ITERATIONS >= 600_000)
    }

    @Test
    fun `a file written by the previous format still imports`() {
        val legacy = checkNotNull(javaClass.getResourceAsStream("/transfer/portable-rules-v1.json"))
            .bufferedReader()
            .use { it.readText() }

        val result = RuleTransfer.importRules(legacy, "legacy-transfer-passphrase".toCharArray())

        val rules = (result as RuleImportResult.Success).rules
        assertEquals(1, rules.size)
        assertEquals("Legacy rule", rules.single().name)
        assertEquals("invoice", rules.single().phrase)
    }

    @Test
    fun `editing the recorded derivation cost fails authentication`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())
        val tampered = encoded.replace(
            "\"iterations\":$PBKDF2_ITERATIONS",
            "\"iterations\":${PBKDF2_ITERATIONS - 1}",
        )
        assertNotEquals(encoded, tampered)

        assertTrue(
            RuleTransfer.importRules(tampered, "correct horse".toCharArray()) is RuleImportResult.InvalidFile,
        )
    }

    @Test
    fun `an import will not be talked into an unbounded key derivation`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())
        val hostile = encoded.replace(
            "\"iterations\":$PBKDF2_ITERATIONS",
            "\"iterations\":2000000000",
        )

        assertTrue(
            RuleTransfer.importRules(hostile, "correct horse".toCharArray()) is RuleImportResult.InvalidFile,
        )
    }

    @Test
    fun `the legacy format cannot be used to name an expensive derivation`() {
        // Format 1 predates the authenticated header, so a file claiming to be format 1 with a
        // cost of its own choosing would otherwise be honoured without anything to check it.
        val legacy = checkNotNull(javaClass.getResourceAsStream("/transfer/portable-rules-v1.json"))
            .bufferedReader()
            .use { it.readText() }
        val hostile = legacy.replace(
            "\"formatVersion\": 1,",
            "\"formatVersion\": 1,\n  \"iterations\": 3000000,",
        )
        assertNotEquals(legacy, hostile)

        assertTrue(
            RuleTransfer.importRules(hostile, "legacy-transfer-passphrase".toCharArray())
                is RuleImportResult.InvalidFile,
        )
    }

    @Test
    fun `a current file relabelled as the legacy format is refused`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())
        val downgraded = encoded.replace("\"formatVersion\":2", "\"formatVersion\":1")

        assertTrue(
            RuleTransfer.importRules(downgraded, "correct horse".toCharArray()) is RuleImportResult.InvalidFile,
        )
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
