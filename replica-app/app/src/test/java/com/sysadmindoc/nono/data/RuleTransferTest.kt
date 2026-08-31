package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.data.BoundedReadResult
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
        val committed = RuleTransfer.commit(existing, incoming, nextRuleId = 2L)!!
        assertEquals(listOf("Local", "New"), committed.rules.map { it.name })
        assertEquals(
            "Imported",
            RuleTransfer.commit(existing, incoming, mapOf(1L to ConflictResolution.REPLACE_EXISTING), 2L)
                ?.rules?.first()?.name,
        )
    }

    @Test
    fun `an imported rule is renumbered from this device's counter, not the file's`() {
        // The user created rules 1, 2 and 3 here, a history record names rule 3, then rule 3 was
        // deleted. A file that happens to carry id 3 must not inherit what that record says.
        val local = listOf(SignalRule(id = 1, name = "Local"))
        val fromAnotherDevice = listOf(SignalRule(id = 3, name = "From the file"))

        val committed = RuleTransfer.commit(local, fromAnotherDevice, nextRuleId = 4L)!!

        val added = committed.rules.single { it.name == "From the file" }
        assertNotEquals("a deleted rule's id must not be reissued to an import", 3L, added.id)
        assertEquals(4L, added.id)
        assertEquals("the counter must move past what it allocated", 5L, committed.nextRuleId)
    }

    @Test
    fun `an import cannot take an id a live rule already holds`() {
        val local = listOf(SignalRule(id = 1, name = "Local"), SignalRule(id = 2, name = "Also local"))
        val incomingFile = listOf(SignalRule(id = 9, name = "One"), SignalRule(id = 10, name = "Two"))

        val committed = RuleTransfer.commit(local, incomingFile, nextRuleId = 3L)!!

        assertEquals(4, committed.rules.size)
        assertEquals(
            "every id in the result is distinct",
            committed.rules.size,
            committed.rules.map { it.id }.toSet().size,
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), committed.rules.map { it.id })
    }

    @Test
    fun `an id at the top of the range cannot arrive by import`() {
        // A file naming Long.MAX_VALUE used to put that id into the live set, after which the
        // allocator permanently fell back to reusing the lowest free id.
        val local = listOf(SignalRule(id = 1, name = "Local"))
        val hostile = listOf(SignalRule(id = Long.MAX_VALUE, name = "Extreme"))

        val committed = RuleTransfer.commit(local, hostile, nextRuleId = 2L)!!

        assertTrue(committed.rules.none { it.id == Long.MAX_VALUE })
        assertEquals(2L, committed.rules.single { it.name == "Extreme" }.id)
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
        // The reason is what proves the check ran. Both files fail GCM authentication too, so
        // asserting only that the import failed would pass with the length checks removed.
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())

        listOf("salt", "iv").forEach { field ->
            val start = encoded.indexOf("\"$field\":\"") + field.length + 4
            val end = encoded.indexOf('"', start)
            val hostile = encoded.replaceRange(start, end, "A".repeat(4096))

            assertEquals(
                "an oversized $field must be refused as a parameter, not as a bad passphrase",
                RuleImportResult.InvalidFile(ImportRejection.BAD_PARAMETERS),
                RuleTransfer.importRules(hostile, "correct horse".toCharArray()),
            )
        }
    }

    @Test
    fun `an unencrypted file with an unreadable payload is not blamed on encryption`() {
        val file = """{"formatVersion":2,"encrypted":false,"payload":"!!!not base64!!!","privacyWarning":"x"}"""

        assertEquals(
            RuleImportResult.InvalidFile(ImportRejection.UNREADABLE),
            RuleTransfer.importRules(file),
        )
    }

    @Test
    fun `only a key or ciphertext failure is reported as a passphrase problem`() {
        val encoded = RuleTransfer.exportRules(incoming, "correct horse".toCharArray())

        assertEquals(
            RuleImportResult.InvalidFile(ImportRejection.WRONG_PASSPHRASE),
            RuleTransfer.importRules(encoded, "wrong horse".toCharArray()),
        )
        assertEquals(
            RuleImportResult.InvalidFile(ImportRejection.BAD_PARAMETERS),
            RuleTransfer.importRules(encoded.replace("\"kdf\":\"$PBKDF2_KDF_NAME\"", "\"kdf\":\"scrypt\""), "correct horse".toCharArray()),
        )
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
    fun `a stream that cannot be opened is not reported as too large`() {
        // Choosing the message from whether the provider volunteered a size told the user their
        // file was too big when the real problem was a stream that would not open.
        assertEquals(BoundedReadResult.Unreadable, readBoundedUtf8(2048L) { null })
        assertEquals(
            BoundedReadResult.Unreadable,
            readBoundedUtf8(2048L) { throw java.io.IOException("permission revoked") },
        )
    }

    @Test
    fun `a stream over the cap is reported as too large however it was opened`() {
        assertEquals(BoundedReadResult.TooLarge, readBoundedUtf8(2048L) { ByteArray(2049).inputStream() })
        assertEquals(
            BoundedReadResult.Text("ok"),
            readBoundedUtf8(2048L) { "ok".byteInputStream() },
        )
    }

    @Test
    fun `a bounded read returns a file that sits exactly on the cap`() {
        val exact = ByteArray(2048) { 'b'.code.toByte() }.inputStream()

        assertEquals("b".repeat(2048), readBoundedUtf8(exact, maxBytes = 2048))
    }
}
