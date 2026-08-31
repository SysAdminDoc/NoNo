package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.SecureRandom

class IdentifierPseudonymsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val pseudonyms = IdentifierPseudonyms(ByteArray(32) { it.toByte() })

    /** What an app can put in a notification tag, a channel id, or a group key. */
    private val canaries = listOf(
        "0|com.example.messages|17|alice@example.com|10123",
        "thread-alice@example.com",
        "conversation-with-alice",
        "+1 555 0142",
        "order #90210 for Ada Lovelace",
    )

    @Test
    fun `the same identifier always maps to the same pseudonym`() {
        // Reposts of one notification have to land on one row, which is what the unique index
        // on notificationKey is for.
        canaries.forEach { value ->
            assertEquals(pseudonyms.pseudonym(value), pseudonyms.pseudonym(value))
        }
    }

    @Test
    fun `different identifiers map to different pseudonyms`() {
        val mapped = canaries.map { pseudonyms.pseudonym(it) }

        assertEquals(canaries.size, mapped.distinct().size)
    }

    @Test
    fun `a pseudonym contains nothing of what it replaced`() {
        canaries.forEach { value ->
            val mapped = pseudonyms.pseudonym(value).orEmpty()
            assertEquals(IdentifierPseudonyms.PSEUDONYM_LENGTH, mapped.length)
            assertTrue("$mapped is not hex", mapped.all { it in '0'..'9' || it in 'a'..'f' })
            // Every run of four or more characters from the input must be gone, not just the whole.
            value.windowed(4).forEach { fragment ->
                assertTrue("$mapped still carries $fragment", !mapped.contains(fragment, ignoreCase = true))
            }
        }
    }

    @Test
    fun `another install cannot reproduce this install's pseudonyms`() {
        // A plain hash would let anyone holding the file confirm a guessed identifier.
        val other = IdentifierPseudonyms(ByteArray(32) { (it + 1).toByte() })

        canaries.forEach { value ->
            assertNotEquals(pseudonyms.pseudonym(value), other.pseudonym(value))
        }
    }

    @Test
    fun `absent and empty identifiers stay absent and empty`() {
        assertNull(pseudonyms.pseudonym(null))
        assertEquals("", pseudonyms.pseudonym(""))
    }

    @Test
    fun `the install key is generated once and then reused`() {
        val directory = tempFolder.newFolder("no-backup")

        val first = PseudonymKeyStore.loadKey(directory, SecureRandom())
        val second = PseudonymKeyStore.loadKey(directory, SecureRandom())

        assertEquals(32, first.size)
        assertTrue("the key must survive a restart", first.contentEquals(second))
    }

    @Test
    fun `a key that never existed is not all zeroes`() {
        val key = PseudonymKeyStore.loadKey(tempFolder.newFolder("fresh"), SecureRandom())

        assertTrue("the key must be random", key.any { it != 0.toByte() })
    }

    @Test
    fun `an exported csv carries pseudonyms rather than the app's own identifiers`() {
        val record = HistoryRecord(
            id = 1L,
            app = "com.example.messages",
            appPackageName = "com.example.messages",
            notificationKey = pseudonyms.pseudonym(canaries[0]).orEmpty(),
            channelId = pseudonyms.pseudonym(canaries[1]),
            groupKey = pseudonyms.pseudonym(canaries[2]),
        )

        val csv = HistoryExport.toCsv(listOf(record))

        // The package is meant to be there: rules match on it and it is not app-authored text.
        assertTrue(csv.contains("com.example.messages"))
        canaries.forEach { canary ->
            assertTrue("the export still carries $canary", !csv.contains(canary))
        }
    }
}
