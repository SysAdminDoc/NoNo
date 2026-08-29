package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.SignalRule
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

sealed interface RuleImportResult {
    data class Success(val rules: List<SignalRule>) : RuleImportResult
    data object NeedsPassphrase : RuleImportResult
    data object Cancelled : RuleImportResult
    data object InvalidFile : RuleImportResult
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
        val file = runCatching {
            transferJson.decodeFromString(PortableRuleFile.serializer(), encodedFile)
        }.getOrNull() ?: return RuleImportResult.InvalidFile
        if (file.formatVersion != TRANSFER_FORMAT_VERSION && file.formatVersion != LEGACY_FORMAT_VERSION) {
            return RuleImportResult.InvalidFile
        }
        if (file.encrypted && passphrase == null) return RuleImportResult.NeedsPassphrase

        val bytes = runCatching {
            val payload = decodeBase64(file.payload)
            if (!file.encrypted) return@runCatching payload
            val salt = decodeBase64(file.salt ?: return@runCatching null)
            val iv = decodeBase64(file.iv ?: return@runCatching null)
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
        if (bytes == null || cancelled()) return if (cancelled()) RuleImportResult.Cancelled else RuleImportResult.InvalidFile
        return decodeRules(bytes.toString(StandardCharsets.UTF_8))
            ?.let(RuleImportResult::Success)
            ?: RuleImportResult.InvalidFile
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

    /** Pure commit: an exception or cancellation leaves the caller's original list untouched. */
    fun commit(
        existing: List<SignalRule>,
        incoming: List<SignalRule>,
        resolutions: Map<Long, ConflictResolution> = emptyMap(),
        cancelled: () -> Boolean = { false },
    ): List<SignalRule>? {
        if (cancelled()) return null
        val preview = preview(existing, incoming)
        val replacementById = preview.conflicts.associate { conflict ->
            conflict.existing.id to when (resolutions[conflict.existing.id]) {
                ConflictResolution.REPLACE_EXISTING -> conflict.incoming
                ConflictResolution.KEEP_EXISTING, null -> conflict.existing
            }
        }
        return (existing.map { replacementById[it.id] ?: it } + preview.additions)
            .distinctBy { it.id }
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

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.Default.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64(value: String): ByteArray = Base64.Default.decode(value)
