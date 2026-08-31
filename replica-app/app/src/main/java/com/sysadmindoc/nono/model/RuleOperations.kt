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
 * The next id to hand out, from a counter that only ever moves forward.
 *
 * A rule id is not just a key into the rule list: every history record stores the ids that
 * matched it, permanently. Handing a deleted rule's id to a new rule therefore rewrites the past,
 * and an old record starts naming a rule that had nothing to do with it. So ids are never reused
 * while the counter can advance.
 *
 * `max + 1` alone is not enough either, in both directions: it reuses an id after the highest
 * rule is deleted, and a rule holding [Long.MAX_VALUE], which an imported file may carry, wraps
 * it to [Long.MIN_VALUE] and collides. The counter is taken as the floor and the highest live id
 * raises it, so a hand-edited file cannot make it collide.
 *
 * @param counter the store's saved counter.
 * @return an id no live rule holds. Only when the counter is genuinely exhausted, which needs
 * a file naming [Long.MAX_VALUE], does this fall back to reusing the lowest free id.
 */
fun nextRuleId(counter: Long, rules: List<SignalRule>): Long {
    val highest = rules.maxOfOrNull { it.id } ?: 0L
    val candidate = if (highest == Long.MAX_VALUE) counter else maxOf(counter, highest + 1)
    if (candidate != Long.MAX_VALUE && rules.none { it.id == candidate }) return candidate
    val taken = rules.mapTo(mutableSetOf()) { it.id }
    var lowest = 1L
    while (lowest in taken) lowest++
    return lowest
}

/** The counter to save after [allocated] was handed out. */
fun advanceRuleCounter(allocated: Long): Long =
    if (allocated == Long.MAX_VALUE) allocated else allocated + 1

/**
 * Decides what a Save writes: a fresh entry with an allocated id, or a replacement for the rule
 * the draft was opened from.
 *
 * Creation and editing are separated here rather than in the view model so both can be proved
 * without an Android runtime. Nothing about the draft's enabled state is touched: a disabled rule
 * that is edited stays disabled, which the toggle on the rule card is what changes.
 */
fun resolveSavedRule(rules: List<SignalRule>, draft: SignalRule, counter: Long): SignalRule {
    val isNew = draft.id == UNSAVED_RULE_ID || rules.none { it.id == draft.id }
    return draft.copy(
        id = if (isNew) nextRuleId(counter, rules) else draft.id,
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

/** A saved rule plus the position it occupied before an undoable deletion. */
data class RemovedRule(val index: Int, val rule: SignalRule)

/** Everything one snackbar action can restore, whether one rule or a delete-all batch. */
data class RuleDeletion(val entries: List<RemovedRule>) {
    val count: Int get() = entries.size
}

data class RuleDeletionResult(
    val remaining: List<SignalRule>,
    val deletion: RuleDeletion,
)

/** Removes one addressed rule and returns the exact snapshot needed to undo it. */
fun deleteRuleWithUndo(rules: List<SignalRule>, ruleId: Long?): RuleDeletionResult? {
    val index = rules.indexOfFirst { it.id == ruleId }
    if (index < 0) return null
    return RuleDeletionResult(
        remaining = rules.toMutableList().apply { removeAt(index) },
        deletion = RuleDeletion(listOf(RemovedRule(index, rules[index]))),
    )
}

/** Removes the full ordered list as one undoable batch. */
fun deleteAllRulesWithUndo(rules: List<SignalRule>): RuleDeletionResult? {
    if (rules.isEmpty()) return null
    return RuleDeletionResult(
        remaining = emptyList(),
        deletion = RuleDeletion(rules.mapIndexed(::RemovedRule)),
    )
}

/**
 * Restores a deletion without replacing a live rule that happens to carry the same id.
 *
 * New rules created while the snackbar is visible remain after the restored rules. A duplicate id
 * refuses the restore instead of silently overwriting either copy.
 */
fun restoreDeletedRules(rules: List<SignalRule>, deletion: RuleDeletion): List<SignalRule>? {
    val removedIds = deletion.entries.map { it.rule.id }
    if (removedIds.distinct().size != removedIds.size) return null
    if (rules.any { it.id in removedIds }) return null
    return rules.toMutableList().apply {
        deletion.entries.sortedBy { it.index }.forEach { entry ->
            add(entry.index.coerceIn(0, size), entry.rule)
        }
    }
}

/**
 * Inserts a copy of [ruleId] with a fresh id; returns the list unchanged when absent.
 *
 * The copy is a rule the user is creating now, so it cannot carry capabilities this build refuses
 * to save. Duplicating an imported rule used to write a brand-new saved rule naming a device
 * action and an expiry, without ever passing through validation.
 */
fun duplicateRule(rules: List<SignalRule>, ruleId: Long?, counter: Long): List<SignalRule> {
    val source = rules.firstOrNull { it.id == ruleId } ?: return rules
    return rules + source.copy(
        id = nextRuleId(counter, rules),
        name = "${source.name} copy",
        action = if (isExecutableAction(source.action)) RECORD_ONLY_ACTION else source.action,
        enabledFor = null,
    )
}
