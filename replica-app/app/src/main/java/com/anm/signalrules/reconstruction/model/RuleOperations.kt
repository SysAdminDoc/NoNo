package com.anm.signalrules.reconstruction.model

/**
 * Pure list operations behind every rule mutation.
 *
 * These exist as free functions so the addressing rules are unit-testable without an
 * Android runtime. Every one of them is keyed by [SignalRule.id]: an earlier revision
 * mutated `rules.first()` regardless of which card the user touched, which silently
 * destroyed the other rules.
 */

fun nextRuleId(rules: List<SignalRule>): Long = (rules.maxOfOrNull { it.id } ?: 0L) + 1L

/** Replaces the rule with [ruleId], leaving every other entry and the ordering intact. */
fun applyToRule(rules: List<SignalRule>, ruleId: Long?, transform: (SignalRule) -> SignalRule): List<SignalRule> {
    if (ruleId == null || rules.none { it.id == ruleId }) return rules
    return rules.map { if (it.id == ruleId) transform(it) else it }
}

/** Appends [rule] when it is new, otherwise replaces the entry sharing its id. */
fun upsertRule(rules: List<SignalRule>, rule: SignalRule): List<SignalRule> =
    if (rules.none { it.id == rule.id }) rules + rule else rules.map { if (it.id == rule.id) rule else it }

fun removeRule(rules: List<SignalRule>, ruleId: Long?): List<SignalRule> =
    if (ruleId == null) rules else rules.filterNot { it.id == ruleId }

/** Inserts a copy of [ruleId] with a fresh id; returns the list unchanged when absent. */
fun duplicateRule(rules: List<SignalRule>, ruleId: Long?): List<SignalRule> {
    val source = rules.firstOrNull { it.id == ruleId } ?: return rules
    return rules + source.copy(id = nextRuleId(rules), name = "${source.name} copy")
}
