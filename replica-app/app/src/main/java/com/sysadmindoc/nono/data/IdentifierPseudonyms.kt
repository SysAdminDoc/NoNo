package com.sysadmindoc.nono.data

import java.io.File
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Replaces app-controlled notification identifiers with stable per-install pseudonyms.
 *
 * Android builds a notification key out of the user id, the package, the app's own id and its
 * `tag`, and the tag is a string the posting app chooses freely. Channel and group identifiers
 * are the same: apps routinely put a conversation id, an account address or a thread name in
 * them. A store that calls itself metadata-only cannot hold those verbatim.
 *
 * The mapping is a keyed HMAC rather than a plain hash, because the identifier space is small
 * enough to enumerate: anyone holding a copy of the database could otherwise hash a guess and
 * confirm it. The key never leaves the install, so a pseudonym means nothing off this device,
 * while repeated posts of one notification still land on one row.
 */
class IdentifierPseudonyms(key: ByteArray) {

    private val secret = SecretKeySpec(key.copyOf(), ALGORITHM)

    init {
        require(key.isNotEmpty()) { "pseudonym key must not be empty" }
    }

    /**
     * @return null for null, "" for "", and otherwise a hex pseudonym that is stable for this
     * install and reveals nothing about the input.
     */
    fun pseudonym(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val mac = Mac.getInstance(ALGORITHM).apply { init(secret) }
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .copyOf(PSEUDONYM_BYTES)
            .joinToString("") { byte -> HEX[(byte.toInt() shr 4) and 0xF].toString() + HEX[byte.toInt() and 0xF] }
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        /** 128 bits. Long enough that a collision across one device's history is not a concern. */
        private const val PSEUDONYM_BYTES = 16
        private const val HEX = "0123456789abcdef"

        /** Length of every non-empty pseudonym, which is what makes them recognisable as one. */
        const val PSEUDONYM_LENGTH = PSEUDONYM_BYTES * 2
    }
}

/**
 * The per-install HMAC key.
 *
 * Kept in `noBackupFilesDir` beside the database so a device transfer or cloud backup carries
 * neither. Losing the key only means older rows stop matching newer ones, which is why it is
 * generated once and then read.
 */
object PseudonymKeyStore {

    private const val FILE_NAME = "identifier-pseudonym.key"
    private const val KEY_BYTES = 32

    @Volatile
    private var cached: IdentifierPseudonyms? = null

    fun get(noBackupFilesDir: File): IdentifierPseudonyms = cached ?: synchronized(this) {
        cached ?: IdentifierPseudonyms(loadKey(noBackupFilesDir)).also { cached = it }
    }

    /** Reads the install's key, creating it on first use. */
    fun loadKey(noBackupFilesDir: File, random: SecureRandom = SecureRandom()): ByteArray {
        val file = File(noBackupFilesDir, FILE_NAME)
        val existing = runCatching { file.readBytes() }.getOrNull()
        if (existing != null && existing.size == KEY_BYTES) return existing
        val generated = ByteArray(KEY_BYTES).also(random::nextBytes)
        noBackupFilesDir.mkdirs()
        file.writeBytes(generated)
        // Best effort: on most Android filesystems the app's own directory is already private.
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
        return generated
    }
}
