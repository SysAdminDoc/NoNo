package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.CURRENT_RULE_STORE_VERSION
import com.anm.signalrules.reconstruction.model.RuleStore
import com.anm.signalrules.reconstruction.model.SignalRule
import kotlinx.serialization.json.Json

/**
 * Serialization for the persisted rule list.
 *
 * `ignoreUnknownKeys` lets a store written by a newer build load in an older one rather than
 * throwing; an unreadable or future-versioned payload decodes to null so the caller can fall
 * back to defaults instead of losing the process.
 */
val ruleJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeRules(rules: List<SignalRule>): String =
    ruleJson.encodeToString(RuleStore.serializer(), RuleStore(rules = rules))

/**
 * @return the stored rules, or null when there is nothing readable to restore.
 */
fun decodeRules(encoded: String?): List<SignalRule>? {
    if (encoded.isNullOrBlank()) return null
    val store = try {
        ruleJson.decodeFromString(RuleStore.serializer(), encoded)
    } catch (error: IllegalArgumentException) {
        // Covers SerializationException, which is an IllegalArgumentException subtype.
        return null
    }
    if (store.version > CURRENT_RULE_STORE_VERSION) return null
    return migrateRules(store.version, store.rules)
}

/**
 * Migrates the v1 file shape into the current normalized form. V1 already contained the
 * authoring fields, but did not define normalization for blank values or duplicate IDs.
 * Duplicate IDs intentionally keep the first entry, preserving file order and making all later
 * addressing deterministic.
 */
private fun migrateRules(version: Int, rules: List<SignalRule>): List<SignalRule> =
    when (version) {
        1 -> rules
            .map { rule ->
                rule.copy(
                    name = rule.name.ifBlank { "Imported rule" },
                    app = rule.app.ifBlank { "any app" },
                    phrase = rule.phrase.ifBlank { "anything" },
                    extras = rule.extras.distinct(),
                    enabledFor = rule.enabledFor?.ifBlank { null },
                )
            }
            .distinctBy { it.id }
        CURRENT_RULE_STORE_VERSION -> rules.distinctBy { it.id }
        else -> emptyList()
    }
