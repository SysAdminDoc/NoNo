package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.advanceRuleCounter
import com.sysadmindoc.nono.model.nextRuleId
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TRANSFER_FORMAT_VERSION = 2

/** Format 1 derived its key with a fixed cost that the file never recorded. */
private const val LEGACY_FORMAT_VERSION = 1
private const val LEGACY_PBKDF2_ITERATIONS = 120_000

const val PBKDF2_KDF_NAME = "pbkdf2-sha256"

/** OWASP's floor for PBKDF2-HMAC-SHA256. Written into the file so it can be raised again later. */
const val PBKDF2_ITERATIONS = 600_000

/** An import must not be talked into an unbounded key derivation by a hostile file. */
private const val MAX_PBKDF2_ITERATIONS = 4_000_000
private const val KEY_BITS = 256
private const val SALT_BYTES = 16
private const val IV_BYTES = 12

const val PORTABLE_TRANSFER_PRIVACY_WARNING =
    "Portable rule files can reveal notification filters; store and share them carefully."

@Serializable
data class PortableRuleFile(
    val formatVersion: Int = TRANSFER_FORMAT_VERSION,
    val encrypted: Boolean,
    val payload: String,
    val salt: String? = null,
    val iv: String? = null,
    val kdf: String? = null,
    val iterations: Int? = null,
    val privacyWarning: String = PORTABLE_TRANSFER_PRIVACY_WARNING,
)

/**
 * Bounds on what an import will accept.
 *
 * A rule file arrives through the document picker, so it is whatever the user chose and can be
 * hostile. Without caps, `readBytes` and an unbounded decode let one file allocate as much memory
 * as the process can get before a single value has been validated.
 */
object RuleTransferLimits {
    /** The encoded file on disk. Real exports are a few kilobytes. */
    const val MAX_ENCODED_BYTES = 5L * 1024 * 1024

    /** The decoded payload, before and after decryption. */
    const val MAX_DECODED_BYTES = 4L * 1024 * 1024

    /** Base64 costs four characters per three bytes, so this bounds the decode itself. */
    const val MAX_BASE64_CHARS = (MAX_DECODED_BYTES + 2) / 3 * 4

    const val MAX_RULES = 10_000

    /** Every string a rule carries, including each entry in its extras list. */
    const val MAX_FIELD_CHARS = 4 * 1024
}

/**
 * Why an import was refused.
 *
 * The messages say what happened without quoting any of the file: an error that echoes input is
 * how a hostile file gets its own text onto someone's screen.
 */
enum class ImportRejection(val message: String) {
    UNREADABLE("That file could not be read as a NoNo rule file."),
    TOO_LARGE("That file is larger than NoNo will import."),
    UNSUPPORTED_VERSION("That file was written by a version NoNo does not understand."),
    BAD_PARAMETERS("That file's encryption settings are not valid."),
    WRONG_PASSPHRASE("The passphrase or the file is not valid."),
    TOO_MANY_RULES("That file declares more rules than NoNo will import."),
    FIELD_TOO_LONG("A value in that file is longer than NoNo will import."),
}

sealed interface RuleImportResult {
    data class Success(val rules: List<SignalRule>) : RuleImportResult
    data object NeedsPassphrase : RuleImportResult
    data object Cancelled : RuleImportResult
    data class InvalidFile(val rejection: ImportRejection) : RuleImportResult
}

enum class ConflictResolution { KEEP_EXISTING, REPLACE_EXISTING }

data class RuleConflict(val existing: SignalRule, val incoming: SignalRule)

data class RuleImportPreview(
    val additions: List<SignalRule>,
    val conflicts: List<RuleConflict>,
) {
    val totalChanges: Int get() = additions.size + conflicts.size
}

/**
 * Explicit portable transfer boundary. The default local storage is unchanged; callers choose
 * encryption for each export and receive a preview before any imported rule replaces local data.
 */
/**
 * The result of an import, with the id counter advanced past everything it allocated.
 *
 * The counter is part of the result rather than recomputed from the rules, because a rule that
 * has been deleted is not in the list and its id must still never be handed out again.
 */
data class RuleCommit(val rules: List<SignalRule>, val nextRuleId: Long)

object RuleTransfer {
    private val transferJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val secureRandom = SecureRandom()

    fun exportRules(rules: List<SignalRule>, passphrase: CharArray? = null): String {
        val rulePayload = encodeRules(rules).toByteArray(StandardCharsets.UTF_8)
        val file = if (passphrase == null) {
            PortableRuleFile(encrypted = false, payload = encodeBase64(rulePayload))
        } else {
            require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
            val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                deriveKey(passphrase, salt, PBKDF2_ITERATIONS),
                GCMParameterSpec(128, iv),
            )
            // Editing the recorded cost already breaks decryption, because the cost feeds the key.
            // Binding the header as well pins the parts that do not: it stops a format-2 file from
            // being relabelled as format 1 to reach the legacy branch.
            cipher.updateAAD(headerAad(TRANSFER_FORMAT_VERSION, PBKDF2_KDF_NAME, PBKDF2_ITERATIONS))
            val encrypted = runCatching { cipher.doFinal(rulePayload) }
                .also { passphrase.fill('\u0000') }
                .getOrThrow()
            PortableRuleFile(
                encrypted = true,
                payload = encodeBase64(encrypted),
                salt = encodeBase64(salt),
                iv = encodeBase64(iv),
                kdf = PBKDF2_KDF_NAME,
                iterations = PBKDF2_ITERATIONS,
            )
        }
        return transferJson.encodeToString(PortableRuleFile.serializer(), file)
    }

    fun importRules(
        encodedFile: String,
        passphrase: CharArray? = null,
        cancelled: () -> Boolean = { false },
    ): RuleImportResult {
        if (cancelled()) return RuleImportResult.Cancelled
        // Checked before parsing: a caller that read the file itself may not have bounded it.
        if (encodedFile.length.toLong() > RuleTransferLimits.MAX_ENCODED_BYTES) {
            return RuleImportResult.InvalidFile(ImportRejection.TOO_LARGE)
        }
        val file = runCatching {
            transferJson.decodeFromString(PortableRuleFile.serializer(), encodedFile)
        }.getOrNull() ?: return RuleImportResult.InvalidFile(ImportRejection.UNREADABLE)
        if (file.formatVersion != TRANSFER_FORMAT_VERSION && file.formatVersion != LEGACY_FORMAT_VERSION) {
            return RuleImportResult.InvalidFile(ImportRejection.UNSUPPORTED_VERSION)
        }
        // Before the base64 decode allocates anything, because the encoded length is what
        // decides how much it will allocate.
        if (file.payload.length.toLong() > RuleTransferLimits.MAX_BASE64_CHARS) {
            return RuleImportResult.InvalidFile(ImportRejection.TOO_LARGE)
        }
        if (file.encrypted && passphrase == null) return RuleImportResult.NeedsPassphrase

        // Set by whichever check refuses the file, so the message names what actually failed
        // rather than being guessed from whether the file claimed to be encrypted.
        var failure: ImportRejection? = null
        val bytes = runCatching {
            val payload = runCatching { decodeBase64(file.payload) }.getOrElse {
                failure = ImportRejection.UNREADABLE
                return@runCatching null
            }
            if (payload.size.toLong() > RuleTransferLimits.MAX_DECODED_BYTES) {
                failure = ImportRejection.TOO_LARGE
                return@runCatching null
            }
            if (!file.encrypted) return@runCatching payload
            failure = ImportRejection.BAD_PARAMETERS
            // Salt and IV are fixed-width by construction. Checking them here refuses a file that
            // would otherwise reach the key derivation with parameters no export ever writes.
            val salt = decodeBase64(file.salt ?: return@runCatching null)
            if (salt.size != SALT_BYTES) return@runCatching null
            val iv = decodeBase64(file.iv ?: return@runCatching null)
            if (iv.size != IV_BYTES) return@runCatching null
            val legacy = file.formatVersion == LEGACY_FORMAT_VERSION
            val kdf = file.kdf ?: if (legacy) PBKDF2_KDF_NAME else return@runCatching null
            if (kdf != PBKDF2_KDF_NAME) return@runCatching null
            // Every format-1 file was written with one fixed cost, so the legacy branch accepts
            // that and nothing else. Otherwise a file claiming to be format 1 could name any cost
            // it liked and be honoured without an authenticated header to check it against.
            val iterations = if (legacy) {
                LEGACY_PBKDF2_ITERATIONS.takeIf { file.iterations == null || file.iterations == it }
                    ?: return@runCatching null
            } else {
                file.iterations ?: return@runCatching null
            }
            if (iterations !in 1..MAX_PBKDF2_ITERATIONS) return@runCatching null
            // Everything about the file itself checked out. Anything that fails from here is the
            // key or the ciphertext, which is what a wrong passphrase looks like.
            failure = ImportRejection.WRONG_PASSPHRASE
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase!!, salt, iterations),
                GCMParameterSpec(128, iv),
            )
            // Format 1 predates authenticated headers and has none to bind.
            if (!legacy) cipher.updateAAD(headerAad(file.formatVersion, kdf, iterations))
            cipher.doFinal(payload)
        }.getOrNull()
        passphrase?.fill('\u0000')
        if (cancelled()) return RuleImportResult.Cancelled
        if (bytes == null) return RuleImportResult.InvalidFile(failure ?: ImportRejection.UNREADABLE)
        if (bytes.size.toLong() > RuleTransferLimits.MAX_DECODED_BYTES) {
            return RuleImportResult.InvalidFile(ImportRejection.TOO_LARGE)
        }
        val rules = decodeRules(bytes.toString(StandardCharsets.UTF_8))
            ?: return RuleImportResult.InvalidFile(ImportRejection.UNREADABLE)
        rejectionFor(rules)?.let { return RuleImportResult.InvalidFile(it) }
        return RuleImportResult.Success(rules)
    }

    /** @return why [rules] cannot be imported, or null when they are within every bound. */
    internal fun rejectionFor(rules: List<SignalRule>): ImportRejection? {
        if (rules.size > RuleTransferLimits.MAX_RULES) return ImportRejection.TOO_MANY_RULES
        val overlong = rules.any { rule ->
            val fields = listOf(
                rule.name,
                rule.app,
                rule.appPackageName.orEmpty(),
                rule.phrase,
                rule.action,
                rule.priority,
                rule.folder,
                rule.matchType,
                rule.filterOperator,
                rule.enabledFor.orEmpty(),
            ) + rule.extras
            fields.any { it.length > RuleTransferLimits.MAX_FIELD_CHARS }
        }
        return if (overlong) ImportRejection.FIELD_TOO_LONG else null
    }

    fun preview(existing: List<SignalRule>, incoming: List<SignalRule>): RuleImportPreview {
        val currentIds = existing.asSequence().map { it.id }.toSet()
        return RuleImportPreview(
            additions = incoming.filterNot { it.id in currentIds },
            conflicts = incoming.mapNotNull { candidate ->
                existing.firstOrNull { it.id == candidate.id }?.let { RuleConflict(it, candidate) }
            },
        )
    }

/**
 * Pure commit: an exception or cancellation leaves the caller's original list untouched.
 *
 * Every rule the file adds is given an id from this device's counter rather than the one it
 * carries. A file's id space is not this device's: the id it names may have belonged to a rule
 * the user deleted, and history records the ids that matched them, so honouring it would make an
 * old record claim it was caught by a rule that did not exist when it arrived. A rule the file
 * replaces keeps its id, because that is the rule the user is choosing to overwrite.
 *
 * @param nextRuleId the first id free to allocate.
 */
    fun commit(
        existing: List<SignalRule>,
        incoming: List<SignalRule>,
        resolutions: Map<Long, ConflictResolution> = emptyMap(),
        nextRuleId: Long = 1L,
        cancelled: () -> Boolean = { false },
    ): RuleCommit? {
        if (cancelled()) return null
        val preview = preview(existing, incoming)
        val replacementById = preview.conflicts.associate { conflict ->
            conflict.existing.id to when (resolutions[conflict.existing.id]) {
                ConflictResolution.REPLACE_EXISTING -> conflict.incoming
                ConflictResolution.KEEP_EXISTING, null -> conflict.existing
            }
        }
        val kept = existing.map { replacementById[it.id] ?: it }
        var counter = nextRuleId
        val renumbered = mutableListOf<SignalRule>()
        for (addition in preview.additions) {
            val allocated = nextRuleId(counter, kept + renumbered)
            counter = advanceRuleCounter(allocated)
            renumbered += addition.copy(id = allocated)
        }
        return RuleCommit(rules = kept + renumbered, nextRuleId = counter)
    }

    /** Binds the derivation parameters to the ciphertext. */
    private fun headerAad(formatVersion: Int, kdf: String, iterations: Int): ByteArray =
        "$formatVersion|$kdf|$iterations".toByteArray(StandardCharsets.UTF_8)

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES",
            )
        } finally {
            spec.clearPassword()
        }
    }
}

/** What a bounded read produced, so a caller can tell "too big" from "could not read". */
sealed interface BoundedReadResult {
    data class Text(val value: String) : BoundedReadResult
    data object TooLarge : BoundedReadResult
    data object Unreadable : BoundedReadResult
}

/**
 * Opens [uriOpener]'s stream and reads it under the cap, as a typed result.
 *
 * The two reasons a read produces nothing have to stay apart. Choosing the message from whether
 * the document provider happened to report a size told users their file was too large when the
 * real problem was a stream that could not be opened at all.
 */
fun readBoundedUtf8(
    maxBytes: Long = RuleTransferLimits.MAX_ENCODED_BYTES,
    uriOpener: () -> InputStream?,
): BoundedReadResult {
    val read = runCatching {
        val stream = uriOpener() ?: return BoundedReadResult.Unreadable
        stream.use { readBoundedUtf8(it, maxBytes) }
    }.getOrElse { return BoundedReadResult.Unreadable }
    // A null here is the cap; anything that threw was caught above.
    return read?.let(BoundedReadResult::Text) ?: BoundedReadResult.TooLarge
}

/**
 * Reads at most [maxBytes] from [input], refusing anything longer.
 *
 * `readBytes` sizes its buffer from the stream, so a hostile document allocates whatever it
 * declares. This reads in fixed chunks and stops one byte past the limit, which is enough to
 * know the file is too big without having held it.
 *
 * @return the decoded text, or null when the stream is longer than [maxBytes].
 */
fun readBoundedUtf8(input: InputStream, maxBytes: Long = RuleTransferLimits.MAX_ENCODED_BYTES): String? {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val collected = ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        collected.write(buffer, 0, read)
    }
    return collected.toString(StandardCharsets.UTF_8.name())
}

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.Default.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(value: String): ByteArray = Base64.Default.decode(value)
