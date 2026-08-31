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
        assertEquals(
            RuleImportResult.InvalidFile(ImportRejection.WRONG_PASSPHRASE),
            RuleTransfer.importRules(encoded, "wrong".toCharArray()),
        )
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

    @Test
    fun `a file larger than the cap is refused before it is parsed`() {
        val oversized = "x".repeat((RuleTransferLimits.MAX_ENCODED_BYTES + 1).toInt())

        assertEquals(
            RuleImportResult.InvalidFile(ImportRejection.TOO_LARGE),
            RuleTransfer.importRules(oversized),
        )
    }

    @Test
    fun `a payload that would decode past the cap is refused before the decode`() {
        // The base64 length is what decides how much the decode allocates, so it is what is
        // checked. The payload here is never valid base64; refusing it before finding that out
        // is the point.
        val huge = "A".repeat((RuleTransferLimits.MAX_BASE64_CHARS + 4).toInt())
        val file = """{"formatVersion":2,"encrypted":false,"payload":"$huge","privacyWarning":"x"}"""

        val result = RuleTransfer.importRules(file)

        assertEquals(RuleImportResult.InvalidFile(ImportRejection.TOO_LARGE), result)
    }

    @Test
    fun `a file declaring more rules than the cap is refused`() {
        val many = (1..RuleTransferLimits.MAX_RULES + 1).map { SignalRule(id = it.toLong(), name = "R$it", action = "Mute") }

        assertEquals(ImportRejection.TOO_MANY_RULES, RuleTransfer.rejectionFor(many))
        assertNull(RuleTransfer.rejectionFor(many.take(RuleTransferLimits.MAX_RULES)))
    }

    @Test
    fun `a rule carrying an overlong field is refused`() {
        val long = "a".repeat(RuleTransferLimits.MAX_FIELD_CHARS + 1)

        assertEquals(ImportRejection.FIELD_TOO_LONG, RuleTransfer.rejectionFor(listOf(SignalRule(id = 1L, phrase = long))))
        assertEquals(ImportRejection.FIELD_TOO_LONG, RuleTransfer.rejectionFor(listOf(SignalRule(id = 1L, name = long))))
        assertEquals(ImportRejection.FIELD_TOO_LONG, RuleTransfer.rejectionFor(listOf(SignalRule(id = 1L, extras = listOf("ok", long)))))
        assertNull(RuleTransfer.rejectionFor(listOf(SignalRule(id = 1L, phrase = "a".repeat(RuleTransferLimits.MAX_FIELD_CHARS)))))
    }

    @Test
    fun `a hostile salt or iv is refused before any key is derived`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())
        val saltStart = encoded.indexOf("\"salt\":\"") + 8
        val saltEnd = encoded.indexOf('"', saltStart)
        val longSalt = encoded.replaceRange(saltStart, saltEnd, "A".repeat(4096))

        val result = RuleTransfer.importRules(longSalt, "correct horse".toCharArray())

        assertEquals(RuleImportResult.InvalidFile(ImportRejection.WRONG_PASSPHRASE), result)
    }

    @Test
    fun `no rejection message repeats anything from the file`() {
        // An error that quotes the file is how a hostile file gets its own text on screen.
        val canary = "CANARY-abcdef"
        val file = """{"formatVersion":9,"encrypted":false,"payload":"$canary","privacyWarning":"$canary"}"""

        val result = RuleTransfer.importRules(file) as RuleImportResult.InvalidFile

        assertEquals(ImportRejection.UNSUPPORTED_VERSION, result.rejection)
        ImportRejection.entries.forEach { rejection ->
            assertTrue("${rejection.name} echoes input", !rejection.message.contains(canary))
        }
    }

    @Test
    fun `a bounded read refuses a stream longer than the cap`() {
        val withinCap = "a".repeat(1024).byteInputStream()
        val overCap = ByteArray(2049).inputStream()

        assertEquals("a".repeat(1024), readBoundedUtf8(withinCap, maxBytes = 2048))
        assertNull(readBoundedUtf8(overCap, maxBytes = 2048))
    }

    @Test
    fun `a bounded read returns a file that sits exactly on the cap`() {
        val exact = ByteArray(2048) { 'b'.code.toByte() }.inputStream()

        assertEquals("b".repeat(2048), readBoundedUtf8(exact, maxBytes = 2048))
    }
}
