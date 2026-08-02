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
    return store.rules.distinctBy { it.id }
}
