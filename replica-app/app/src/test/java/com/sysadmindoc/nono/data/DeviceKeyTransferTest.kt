package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.SignalRule
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduled backup's file format.
 *
 * The real key lives in the Android Keystore, which a JVM test cannot reach. What the keystore
 * supplies is an AES key, and that is what these substitute: the format, the header binding and
 * the refusals are the parts that can be wrong.
 */
class DeviceKeyTransferTest {

    private val rules = listOf(
        SignalRule(id = 1L, name = "Group chats", phrase = "standup"),
        SignalRule(id = 2L, name = "Deliveries"),
    )

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `a device backup round trips with the key that wrote it`() {
        val deviceKey = key()

        val encoded = RuleTransfer.exportRulesForDevice(rules, deviceKey)

        assertTrue(encoded.contains("\"kdf\":\"$DEVICE_KEY_KDF_NAME\""))
        assertTrue("rule text must not be readable in the file", !encoded.contains("standup"))
        val result = RuleTransfer.importRules(encoded, deviceKey = deviceKey)
        assertEquals(rules, (result as RuleImportResult.Success).rules)
    }

    @Test
    fun `two backups of the same rules are different files`() {
        // The key never rotates, so a repeated nonce would leak the difference between two
        // backups. The cipher picks the nonce, and this is what proves it is not picking one.
        val deviceKey = key()

        val first = RuleTransfer.exportRulesForDevice(rules, deviceKey)
        val second = RuleTransfer.exportRulesForDevice(rules, deviceKey)

        assertNotEquals(first, second)
        assertNotEquals(ivOf(first), ivOf(second))
    }

    private fun ivOf(encoded: String): String =
        Regex("\"iv\":\"([^\"]+)\"").find(encoded)?.groupValues?.get(1).orEmpty()

    @Test
    fun `another device's key cannot open the backup`() {
        val encoded = RuleTransfer.exportRulesForDevice(rules, key())

        val result = RuleTransfer.importRules(encoded, deviceKey = key())

        assertEquals(
            ImportRejection.DEVICE_KEY_UNAVAILABLE,
            (result as RuleImportResult.InvalidFile).rejection,
        )
    }

    @Test
    fun `with no key at all the file is refused rather than prompting for a passphrase`() {
        // A passphrase prompt here would be a dead end: no passphrase can open this file, and the
        // user would be left trying to remember one they never set.
        val encoded = RuleTransfer.exportRulesForDevice(rules, key())

        val result = RuleTransfer.importRules(encoded, deviceKey = null)

        assertEquals(
            ImportRejection.DEVICE_KEY_UNAVAILABLE,
            (result as RuleImportResult.InvalidFile).rejection,
        )
    }

    @Test
    fun `a passphrase file is unaffected by a device key being available`() {
        val encoded = RuleTransfer.exportRules(rules, "correct horse".toCharArray())

        val result = RuleTransfer.importRules(encoded, "correct horse".toCharArray(), deviceKey = key())

        assertEquals(rules, (result as RuleImportResult.Success).rules)
    }

    @Test
    fun `a device file relabelled as a passphrase file is refused`() {
        // The header is bound into the ciphertext, so switching the kdf name breaks the tag rather
        // than routing the file into the passphrase branch and leaking a different error.
        val deviceKey = key()
        val encoded = RuleTransfer.exportRulesForDevice(rules, deviceKey)
            .replace("\"kdf\":\"$DEVICE_KEY_KDF_NAME\"", "\"kdf\":\"$PBKDF2_KDF_NAME\"")

        val result = RuleTransfer.importRules(encoded, "anything".toCharArray(), deviceKey = deviceKey)

        assertTrue(result is RuleImportResult.InvalidFile)
    }

    @Test
    fun `a device file carrying passphrase parameters is refused`() {
        // Claiming both derivations at once is a file describing two different keys. Only one of
        // them encrypted it, and accepting the claim would mean guessing which.
        val deviceKey = key()
        val encoded = RuleTransfer.exportRulesForDevice(rules, deviceKey)
            .replace("\"iterations\":null", "\"iterations\":600000")
        assertTrue("the mutation must land, or this asserts nothing", encoded.contains("\"iterations\":600000"))

        val result = RuleTransfer.importRules(encoded, deviceKey = deviceKey)

        assertEquals(
            ImportRejection.BAD_PARAMETERS,
            (result as RuleImportResult.InvalidFile).rejection,
        )
    }

    @Test
    fun `a truncated key length cannot decrypt what a full key wrote`() {
        val full = key()
        val truncated = SecretKeySpec(full.encoded.copyOf(16), "AES")

        val encoded = RuleTransfer.exportRulesForDevice(rules, full)

        assertTrue(RuleTransfer.importRules(encoded, deviceKey = truncated) is RuleImportResult.InvalidFile)
    }
}
