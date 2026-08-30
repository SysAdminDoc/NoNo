package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.CURRENT_RULE_STORE_VERSION
import com.sysadmindoc.nono.model.RuleStore
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.ANY_APP_LABEL
import com.sysadmindoc.nono.model.appOptionForLabel
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
    ruleJson.encodeToString(RuleStore.serializer(), RuleStore(rules = normalizeRules(rules)))

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
            .map(::normalizeRule)
            .distinctBy { it.id }
        2, CURRENT_RULE_STORE_VERSION -> normalizeRules(rules)
        else -> emptyList()
    }

private fun normalizeRules(rules: List<SignalRule>): List<SignalRule> =
    rules.map(::normalizeRule).distinctBy { it.id }

/** Adds package identity for labels emitted by the app selector without guessing unknown apps. */
private fun normalizeRule(rule: SignalRule): SignalRule {
    val normalizedApp = rule.app.ifBlank { ANY_APP_LABEL }
    val packageName = when {
        normalizedApp.equals(ANY_APP_LABEL, ignoreCase = true) -> null
        !rule.appPackageName.isNullOrBlank() -> rule.appPackageName
        else -> appOptionForLabel(normalizedApp)?.packageName
    }
    return rule.copy(
        name = rule.name.ifBlank { "Imported rule" },
        app = normalizedApp,
        appPackageName = packageName,
        phrase = rule.phrase.ifBlank { "anything" },
        extras = rule.extras.distinct(),
        enabledFor = rule.enabledFor?.ifBlank { null },
    )
}
