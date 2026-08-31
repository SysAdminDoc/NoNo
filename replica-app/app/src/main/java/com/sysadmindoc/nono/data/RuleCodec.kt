package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.CURRENT_RULE_STORE_VERSION
import com.sysadmindoc.nono.model.RuleStore
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.UNSAVED_RULE_ID
import com.sysadmindoc.nono.model.ANY_APP_LABEL
import com.sysadmindoc.nono.model.appOptionForLabel
import com.sysadmindoc.nono.model.normalizeMatchType
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

fun encodeRules(rules: List<SignalRule>, nextRuleId: Long = 1L): String {
    val normalized = normalizeRules(rules)
    return ruleJson.encodeToString(
        RuleStore.serializer(),
        RuleStore(rules = normalized, nextRuleId = raiseCounter(nextRuleId, normalized)),
    )
}

/**
 * @return the stored rules and the id counter, or null when there is nothing readable to restore.
 */
fun decodeRuleStore(encoded: String?): RuleStore? {
    if (encoded.isNullOrBlank()) return null
    val store = try {
        ruleJson.decodeFromString(RuleStore.serializer(), encoded)
    } catch (error: IllegalArgumentException) {
        // Covers SerializationException, which is an IllegalArgumentException subtype.
        return null
    }
    if (store.version > CURRENT_RULE_STORE_VERSION) return null
    val rules = migrateRules(store.version, store.rules)
    return RuleStore(
        version = CURRENT_RULE_STORE_VERSION,
        rules = rules,
        nextRuleId = raiseCounter(store.nextRuleId, rules),
    )
}

/**
 * @return the stored rules, or null when there is nothing readable to restore.
 */
fun decodeRules(encoded: String?): List<SignalRule>? = decodeRuleStore(encoded)?.rules

/**
 * Lifts the counter above every id present.
 *
 * A store written before the counter existed defaults it to 1, and a hand-edited file could set
 * it anywhere. Either way it has to end up past the highest live id, or the next allocation would
 * collide with a rule that already exists.
 */
private fun raiseCounter(counter: Long, rules: List<SignalRule>): Long {
    val highest = rules.maxOfOrNull { it.id } ?: 0L
    if (highest == Long.MAX_VALUE) return Long.MAX_VALUE
    return maxOf(counter, highest + 1)
}

/**
 * Migrates the v1 file shape into the current normalized form. V1 already contained the
 * authoring fields, but did not define normalization for blank values or duplicate IDs.
 * Duplicate IDs intentionally keep the first entry, preserving file order and making all later
 * addressing deterministic.
 */
private fun migrateRules(version: Int, rules: List<SignalRule>): List<SignalRule> =
    when (version) {
        // Every version so far has only added optional fields, so an older store reads with the
        // new ones at their defaults: schedule null, which means the rule keeps matching at any
        // time rather than suddenly being limited to a window nobody chose.
        1, 2, 3, CURRENT_RULE_STORE_VERSION -> normalizeRules(rules)
        else -> emptyList()
    }

/**
 * Gives every rule a real id before duplicates are dropped.
 *
 * A rule file can omit the id, and the model default is the unsaved sentinel, so an import of
 * three id-less rules used to collapse into one and the survivor could never be edited: every
 * save saw the sentinel, decided the rule was new, and appended a copy. Ids are allocated in
 * file order from above whatever the file already used, so the result is deterministic.
 */
private fun allocateMissingIds(rules: List<SignalRule>): List<SignalRule> {
    if (rules.none { it.id == UNSAVED_RULE_ID }) return rules
    // Taken ids are skipped rather than assumed to end at the maximum: a file naming
    // Long.MAX_VALUE would otherwise wrap the counter round to a value already in use, and the
    // duplicate would be dropped without a word.
    val taken = rules.mapTo(mutableSetOf()) { it.id }
    var next = 1L
    return rules.map { rule ->
        if (rule.id != UNSAVED_RULE_ID) return@map rule
        while (next in taken || next == UNSAVED_RULE_ID) next++
        taken += next
        rule.copy(id = next)
    }
}

private fun normalizeRules(rules: List<SignalRule>): List<SignalRule> =
    allocateMissingIds(rules.map(::normalizeRule)).distinctBy { it.id }

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
        // Older stores carry the four-value phrase-group vocabulary. Collapsing it here means a
        // decoded rule always names an operator the evaluator implements.
        matchType = normalizeMatchType(rule.matchType),
        extras = rule.extras.distinct(),
        enabledFor = rule.enabledFor?.ifBlank { null },
    )
}
