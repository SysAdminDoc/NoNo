package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.SignalRule
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TRANSFER_FORMAT_VERSION = 1
private const val PBKDF2_ITERATIONS = 120_000
private const val KEY_BITS = 256
private const val SALT_BYTES = 16
private const val IV_BYTES = 12

@Serializable
data class PortableRuleFile(
    val formatVersion: Int = TRANSFER_FORMAT_VERSION,
    val encrypted: Boolean,
    val payload: String,
    val salt: String? = null,
    val iv: String? = null,
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
            PortableRuleFile(encrypted = false, payload = Base64.getEncoder().encodeToString(rulePayload))
        } else {
            require(passphrase.isNotEmpty()) { "passphrase must not be empty" }
            val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            val encrypted = runCatching { cipher.doFinal(rulePayload) }
                .also { passphrase.fill('\u0000') }
                .getOrThrow()
            PortableRuleFile(
                encrypted = true,
                payload = Base64.getEncoder().encodeToString(encrypted),
                salt = Base64.getEncoder().encodeToString(salt),
                iv = Base64.getEncoder().encodeToString(iv),
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
        if (file.formatVersion != TRANSFER_FORMAT_VERSION) return RuleImportResult.InvalidFile
        if (file.encrypted && passphrase == null) return RuleImportResult.NeedsPassphrase

        val bytes = runCatching {
            val payload = Base64.getDecoder().decode(file.payload)
            if (!file.encrypted) return@runCatching payload
            val salt = Base64.getDecoder().decode(file.salt ?: return@runCatching null)
            val iv = Base64.getDecoder().decode(file.iv ?: return@runCatching null)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase!!, salt), GCMParameterSpec(128, iv))
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

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
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
