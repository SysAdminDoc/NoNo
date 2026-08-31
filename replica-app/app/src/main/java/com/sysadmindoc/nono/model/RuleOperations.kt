package com.sysadmindoc.nono.model

/**
 * Pure list operations behind every rule mutation.
 *
 * These exist as free functions so the addressing rules are unit-testable without an
 * Android runtime. Every one of them is keyed by [SignalRule.id]: an earlier revision
 * mutated `rules.first()` regardless of which card the user touched, which silently
 * destroyed the other rules.
 */

/**
 * The lowest id no saved rule is using.
 *
 * Not `max + 1`: a rule holding [Long.MAX_VALUE], which an imported file may legitimately carry,
 * wraps that to [Long.MIN_VALUE]. Every later allocation then returns the same colliding value,
 * so saving two new rules kept only the second and duplicating one dropped the copy, both without
 * a word to the user.
 */
fun nextRuleId(rules: List<SignalRule>): Long {
    val taken = rules.mapTo(mutableSetOf()) { it.id }
    var candidate = 1L
    while (candidate in taken) candidate++
    return candidate
}

/**
 * Decides what a Save writes: a fresh entry with an allocated id, or a replacement for the rule
 * the draft was opened from.
 *
 * Creation and editing are separated here rather than in the view model so both can be proved
 * without an Android runtime. Nothing about the draft's enabled state is touched: a disabled rule
 * that is edited stays disabled, which the toggle on the rule card is what changes.
 */
fun resolveSavedRule(rules: List<SignalRule>, draft: SignalRule): SignalRule {
    val isNew = draft.id == UNSAVED_RULE_ID || rules.none { it.id == draft.id }
    return draft.copy(
        id = if (isNew) nextRuleId(rules) else draft.id,
        name = draft.name.ifBlank { "Rule ${rules.size + 1}" },
    )
}

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

/**
 * Inserts a copy of [ruleId] with a fresh id; returns the list unchanged when absent.
 *
 * The copy is a rule the user is creating now, so it cannot carry capabilities this build refuses
 * to save. Duplicating an imported rule used to write a brand-new saved rule naming a device
 * action and an expiry, without ever passing through validation.
 */
fun duplicateRule(rules: List<SignalRule>, ruleId: Long?): List<SignalRule> {
    val source = rules.firstOrNull { it.id == ruleId } ?: return rules
    return rules + source.copy(
        id = nextRuleId(rules),
        name = "${source.name} copy",
        action = if (isExecutableAction(source.action)) RECORD_ONLY_ACTION else source.action,
        enabledFor = null,
    )
}
