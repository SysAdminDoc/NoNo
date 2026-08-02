package com.anm.signalrules.reconstruction.runtime

import com.anm.signalrules.reconstruction.model.NotificationContentState
import com.anm.signalrules.reconstruction.model.SignalRule

/** Why a rule was not selected during a dry-run evaluation. */
enum class EvaluationReason {
    DISABLED,
    APP_MISMATCH,
    CONTENT_HIDDEN_BY_SYSTEM,
    CONTENT_NOT_AVAILABLE,
    PHRASE_MISMATCH,
    EXTRA_FILTER_UNSUPPORTED,
}

data class RuleConditionTrace(
    val ruleId: Long,
    val matched: Boolean,
    val reasons: List<EvaluationReason> = emptyList(),
    val specificity: Int = 0,
)

/** A conflict is retained as data so the UI can explain why one candidate won. */
data class RuleConflictPair(
    val leftRuleId: Long,
    val rightRuleId: Long,
    val winningRuleId: Long,
)

data class PriorityOverride(
    val ruleId: Long,
    val priority: String,
)

enum class DryRunActionResult {
    NOT_EXECUTED,
}

data class RuleEvaluationTrace(
    val traceId: String,
    val contentState: NotificationContentState,
    val conditions: List<RuleConditionTrace>,
    val matchedRuleId: Long?,
    val conflictPairs: List<RuleConflictPair>,
    val priorityOverrides: List<PriorityOverride>,
    val actionResult: DryRunActionResult = DryRunActionResult.NOT_EXECUTED,
)

/**
 * Evaluates the currently representable rule fields without executing an action.
 *
 * This is deliberately a pure dry-run boundary. It consumes only redaction-aware payloads,
 * records enough typed detail for an Activity screen, and never receives a notification or
 * action callback that could mutate the device.
 */
fun evaluateRules(
    rules: List<SignalRule>,
    payload: NotificationPayload,
    sdkInt: Int,
    traceId: String = newTraceId(),
): RuleEvaluationTrace {
    val contentState = classifyNotificationContent(payload, sdkInt)
    val matchableText = matchableNotificationText(payload, sdkInt)?.lowercase()
    val conditions = rules.map { rule -> evaluateRule(rule, payload, contentState, matchableText) }
    val matchingRules = rules.filter { rule ->
        conditions.first { it.ruleId == rule.id }.matched
    }
    val winner = matchingRules.maxWithOrNull(ruleComparator)
    val conflicts = matchingRules
        .sortedBy { it.id }
        .flatMapIndexed { index, left ->
            matchingRules.sortedBy { it.id }.drop(index + 1).map { right ->
                RuleConflictPair(left.id, right.id, winner?.id ?: left.id)
            }
        }
    val overrides = matchingRules
        .filter { it.priority != "Normal" }
        .sortedWith(compareByDescending<SignalRule> { priorityRank(it.priority) }.thenBy { it.id })
        .map { PriorityOverride(it.id, it.priority) }

    return RuleEvaluationTrace(
        traceId = traceId,
        contentState = contentState,
        conditions = conditions,
        matchedRuleId = winner?.id,
        conflictPairs = conflicts,
        priorityOverrides = overrides,
    )
}

private fun evaluateRule(
    rule: SignalRule,
    payload: NotificationPayload,
    contentState: NotificationContentState,
    matchableText: String?,
): RuleConditionTrace {
    if (!rule.enabled) return RuleConditionTrace(rule.id, matched = false, reasons = listOf(EvaluationReason.DISABLED))

    val reasons = buildList {
        if (!matchesApp(rule, payload)) add(EvaluationReason.APP_MISMATCH)
        when {
            contentState == NotificationContentState.HIDDEN_BY_SYSTEM -> add(EvaluationReason.CONTENT_HIDDEN_BY_SYSTEM)
            contentState != NotificationContentState.AVAILABLE -> add(EvaluationReason.CONTENT_NOT_AVAILABLE)
            !matchesPhrase(rule, matchableText) -> add(EvaluationReason.PHRASE_MISMATCH)
        }
        if (rule.extras.isNotEmpty()) add(EvaluationReason.EXTRA_FILTER_UNSUPPORTED)
    }
    return RuleConditionTrace(
        ruleId = rule.id,
        matched = reasons.isEmpty(),
        reasons = reasons,
        specificity = specificity(rule),
    )
}

private fun matchesApp(rule: SignalRule, payload: NotificationPayload): Boolean {
    if (rule.app.isBlank() || rule.app.equals("any app", ignoreCase = true)) return true
    rule.appPackageName?.takeIf(String::isNotBlank)?.let { packageName ->
        return payload.packageName?.equals(packageName, ignoreCase = true) == true
    }
    // Legacy rules may have only a display label. Keep them readable and testable while the
    // codec offers a repair path for known labels; real listener payloads should use packageName.
    return payload.appLabel?.toString()?.equals(rule.app, ignoreCase = true) == true
}

private fun matchesPhrase(rule: SignalRule, text: String?): Boolean {
    if (rule.phrase.isBlank() || rule.phrase.equals("anything", ignoreCase = true)) return true
    return text?.contains(rule.phrase.trim().lowercase()) == true
}

private fun specificity(rule: SignalRule): Int =
    (if (rule.app.isBlank() || rule.app.equals("any app", ignoreCase = true)) 0 else 1) +
        (if (rule.phrase.isBlank() || rule.phrase.equals("anything", ignoreCase = true)) 0 else 1) +
        rule.extras.size

private val ruleComparator = compareBy<SignalRule> { priorityRank(it.priority) }
    .thenBy { specificity(it) }
    .thenByDescending { it.id }

private fun priorityRank(priority: String): Int = when (priority.lowercase()) {
    "highest" -> 5
    "high" -> 4
    "normal" -> 3
    "low" -> 2
    "lowest" -> 1
    else -> 3
}
