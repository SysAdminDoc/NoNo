package com.sysadmindoc.nono.runtime

import android.os.Build
import com.sysadmindoc.nono.model.CategoryCondition
import com.sysadmindoc.nono.model.ChannelCondition
import com.sysadmindoc.nono.model.ConversationCondition
import com.sysadmindoc.nono.model.ImportanceCondition
import com.sysadmindoc.nono.model.MetadataCondition
import com.sysadmindoc.nono.model.NotificationContentState
import com.sysadmindoc.nono.model.OngoingCondition
import com.sysadmindoc.nono.model.RuleMatchState
import com.sysadmindoc.nono.model.SignalRule
import com.sysadmindoc.nono.model.SummaryCondition
import com.sysadmindoc.nono.model.MatchableFields
import com.sysadmindoc.nono.model.PhraseMatchBudget
import com.sysadmindoc.nono.model.matchBudgetSliceMillis
import com.sysadmindoc.nono.model.PhraseMatchFailure
import com.sysadmindoc.nono.model.categoryLabel
import com.sysadmindoc.nono.model.displayValue
import com.sysadmindoc.nono.model.evaluatePhrase
import com.sysadmindoc.nono.model.importanceLabel
import com.sysadmindoc.nono.model.normalizedNotificationCategory
import com.sysadmindoc.nono.model.phraseConditionFor
import com.sysadmindoc.nono.model.matchesSchedule
import java.util.TimeZone

/** Why a rule was not selected during a dry-run evaluation. */
enum class EvaluationReason {
    DISABLED,
    APP_MISMATCH,
    CONTENT_HIDDEN_BY_SYSTEM,
    CONTENT_NOT_AVAILABLE,
    PHRASE_MISMATCH,
    METADATA_MISMATCH,
    METADATA_NOT_AVAILABLE,
    INVALID_METADATA_CONDITION,
    /** A free-string condition preserved from rule-store versions 1 through 4. */
    EXTRA_FILTER_UNSUPPORTED,

    /** The rule's pattern is not valid, so nothing could be tested against it. */
    INVALID_PATTERN,

    /** No field is selected, so there is nothing to search. */
    NO_FIELD_SELECTED,

    /**
     * The rule's pattern could not be finished on this text, so what it would have said is not
     * known. A rule never fires on that: the evidence was never gathered.
     */
    PATTERN_ABANDONED,

    /** The notification arrived outside the rule's schedule window. */
    OUTSIDE_SCHEDULE,
}

data class RuleConditionTrace(
    val ruleId: Long,
    val matched: Boolean,
    val reasons: List<EvaluationReason> = emptyList(),
    val metadataConditions: List<MetadataConditionTrace> = emptyList(),
    val specificity: Int = 0,
)

/** Why one typed metadata condition could not match. */
enum class MetadataConditionFailure {
    VALUE_MISMATCH,
    METADATA_NOT_AVAILABLE,
    INVALID_CONDITION,
}

/** The result for one condition, kept separate so two failures never collapse into one label. */
data class MetadataConditionTrace(
    val condition: MetadataCondition,
    val matched: Boolean,
    val failure: MetadataConditionFailure? = null,
    val expectedValue: String = condition.displayValue(),
    val actualValue: String? = null,
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
    atEpochMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): RuleEvaluationTrace {
    val contentState = classifyNotificationContent(payload, sdkInt)
    val matchableFields = matchableNotificationFields(payload, sdkInt)
    // A share of the notification's budget each, rather than one budget they race for. Sharing
    // one bounded the total but let a single pathological pattern spend it and leave every later
    // rule abandoned; a slice bounds the total too and keeps the outcome independent of the order
    // the rules happen to be in.
    val slice = matchBudgetSliceMillis(rules.size)
    val conditions = rules.map { rule ->
        evaluateRule(rule, payload, contentState, matchableFields, atEpochMillis, zone, PhraseMatchBudget(slice))
    }
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

/**
 * What a capture should record about the rules that matched it.
 *
 * Rule ids and an evaluation state only. No notification content and no action: this says which
 * rules would have acted, which is the same boundary the dry-run evaluator already holds.
 */
data class CaptureEvaluation(
    val matchedRuleIds: List<Long>,
    val state: RuleMatchState,
)

/**
 * Evaluates a live notification while its payload is still in scope.
 *
 * Every matching rule is recorded rather than only the conflict winner, so a rule can report how
 * often it would have fired without re-reading anything the notification contained.
 */
fun evaluateCapture(
    rules: List<SignalRule>,
    payload: NotificationPayload,
    sdkInt: Int = Build.VERSION.SDK_INT,
    atEpochMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): CaptureEvaluation {
    if (rules.isEmpty()) return CaptureEvaluation(emptyList(), RuleMatchState.NOT_EVALUATED)
    val trace = evaluateRules(rules, payload, sdkInt, atEpochMillis = atEpochMillis, zone = zone)
    return CaptureEvaluation(
        matchedRuleIds = trace.conditions.filter { it.matched }.map { it.ruleId }.sorted(),
        state = if (trace.contentState == NotificationContentState.HIDDEN_BY_SYSTEM) {
            RuleMatchState.CONTENT_HIDDEN
        } else {
            RuleMatchState.EVALUATED
        },
    )
}

/**
 * The single policy the listener applies to an arriving notification.
 *
 * Extracted from the service so it can be exercised without one. A group summary stands for its
 * group rather than being an arrival of its own, so only rules that explicitly test summary state
 * see it. Stored counts still exclude summaries.
 *
 * @param rules null when the saved rules have not been read from disk yet, which the platform can
 * cause by delivering a notification before the store is loaded.
 */
fun captureEvaluationFor(
    sanitized: SanitizedNotification,
    rules: List<SignalRule>?,
    payload: NotificationPayload,
    sdkInt: Int = Build.VERSION.SDK_INT,
    atEpochMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): CaptureEvaluation {
    if (rules == null) {
        return if (groupingFor(sanitized).shouldEvaluate) {
            CaptureEvaluation(emptyList(), RuleMatchState.RULES_NOT_LOADED)
        } else {
            CaptureEvaluation(emptyList(), RuleMatchState.GROUP_SUMMARY)
        }
    }

    val payloadWithMetadata = payload.copy(
        channelId = sanitized.channelId,
        importance = sanitized.importance,
        category = sanitized.category,
        isConversation = sanitized.isConversation,
        isOngoing = sanitized.isOngoing,
        isGroupSummary = sanitized.isGroupSummary,
    )
    if (groupingFor(sanitized).shouldEvaluate) {
        return evaluateCapture(rules, payloadWithMetadata, sdkInt, atEpochMillis, zone)
    }

    // Existing rules never saw summaries. Preserve that behavior unless a rule explicitly asks
    // for summary state; this makes summary matching opt-in instead of broadening old rules.
    val summaryRules = rules.filter { rule ->
        rule.metadataConditions.any { it is SummaryCondition }
    }
    if (summaryRules.isEmpty()) return CaptureEvaluation(emptyList(), RuleMatchState.GROUP_SUMMARY)
    val result = evaluateCapture(summaryRules, payloadWithMetadata, sdkInt, atEpochMillis, zone)
    return result.copy(state = RuleMatchState.GROUP_SUMMARY_EVALUATED)
}

private fun evaluateRule(
    rule: SignalRule,
    payload: NotificationPayload,
    contentState: NotificationContentState,
    matchableFields: MatchableFields?,
    atEpochMillis: Long,
    zone: TimeZone,
    budget: PhraseMatchBudget,
): RuleConditionTrace {
    if (!rule.enabled) return RuleConditionTrace(rule.id, matched = false, reasons = listOf(EvaluationReason.DISABLED))

    val metadataTrace = evaluateMetadataConditions(rule.metadataConditions, payload)
    val reasons = buildList {
        if (!matchesApp(rule, payload)) add(EvaluationReason.APP_MISMATCH)
        // Recorded as its own reason rather than folded into a mismatch: "your rule is right but
        // this arrived at the wrong time" is the answer to a different question from "your phrase
        // did not match", and the Activity screen has to be able to tell them apart.
        if (!matchesSchedule(rule.schedule, atEpochMillis, zone)) add(EvaluationReason.OUTSIDE_SCHEDULE)
        // A rule that tests no phrase needs no content, so absent content is not a reason to
        // refuse it. Redaction stays a refusal either way: content the system hid might have
        // matched, and this build will not guess which way.
        val condition = phraseConditionFor(rule)
        when {
            contentState == NotificationContentState.HIDDEN_BY_SYSTEM -> add(EvaluationReason.CONTENT_HIDDEN_BY_SYSTEM)
            condition.isEmpty -> Unit
            // The text itself decides, not the provenance. A metadata-only history row is stored
            // as AVAILABLE and replays with no text at all, and a negated rule would otherwise
            // read that absence as proof the phrase was not there.
            matchableFields == null -> add(EvaluationReason.CONTENT_NOT_AVAILABLE)
            contentState != NotificationContentState.AVAILABLE -> add(EvaluationReason.CONTENT_NOT_AVAILABLE)
            else -> {
                val result = evaluatePhrase(condition, matchableFields, budget)
                when (result.failure) {
                    PhraseMatchFailure.INVALID_PATTERN -> add(EvaluationReason.INVALID_PATTERN)
                    PhraseMatchFailure.NO_FIELD_SELECTED -> add(EvaluationReason.NO_FIELD_SELECTED)
                    PhraseMatchFailure.NO_TEXT -> add(EvaluationReason.CONTENT_NOT_AVAILABLE)
                    PhraseMatchFailure.PATTERN_ABANDONED -> add(EvaluationReason.PATTERN_ABANDONED)
                    null -> if (!result.matched) add(EvaluationReason.PHRASE_MISMATCH)
                }
            }
        }
        metadataTrace.mapNotNull { it.failure }.distinct().forEach { failure ->
            add(
                when (failure) {
                    MetadataConditionFailure.VALUE_MISMATCH -> EvaluationReason.METADATA_MISMATCH
                    MetadataConditionFailure.METADATA_NOT_AVAILABLE -> EvaluationReason.METADATA_NOT_AVAILABLE
                    MetadataConditionFailure.INVALID_CONDITION -> EvaluationReason.INVALID_METADATA_CONDITION
                },
            )
        }
        if (rule.extras.isNotEmpty()) add(EvaluationReason.EXTRA_FILTER_UNSUPPORTED)
    }
    return RuleConditionTrace(
        ruleId = rule.id,
        matched = reasons.isEmpty(),
        reasons = reasons,
        metadataConditions = metadataTrace,
        specificity = specificity(rule),
    )
}

/** Evaluates every typed condition independently so the dry-run can explain each failure. */
fun evaluateMetadataConditions(
    conditions: List<MetadataCondition>,
    payload: NotificationPayload,
): List<MetadataConditionTrace> = conditions.map { condition ->
    when (condition) {
        is ChannelCondition -> metadataTrace(
            condition = condition,
            actual = payload.channelId,
            expected = condition.channelPseudonym,
            actualLabel = payload.channelId,
            valid = !condition.needsReselection && condition.channelPseudonym.isNotBlank(),
        )
        is ImportanceCondition -> metadataTrace(
            condition = condition,
            actual = payload.importance,
            expected = condition.level,
            actualLabel = payload.importance?.let { importanceLabel(it) ?: "Unknown ($it)" },
            valid = importanceLabel(condition.level) != null,
        )
        is CategoryCondition -> metadataTrace(
            condition = condition,
            actual = payload.category,
            expected = condition.category,
            actualLabel = payload.category?.let(::categoryLabel),
            valid = normalizedNotificationCategory(condition.category) != null,
        )
        is ConversationCondition -> metadataTrace(
            condition = condition,
            actual = payload.isConversation,
            expected = condition.required,
            actualLabel = payload.isConversation?.yesNo(),
        )
        is OngoingCondition -> metadataTrace(
            condition = condition,
            actual = payload.isOngoing,
            expected = condition.required,
            actualLabel = payload.isOngoing?.yesNo(),
        )
        is SummaryCondition -> metadataTrace(
            condition = condition,
            actual = payload.isGroupSummary,
            expected = condition.required,
            actualLabel = payload.isGroupSummary?.yesNo(),
        )
    }
}

private fun <T> metadataTrace(
    condition: MetadataCondition,
    actual: T?,
    expected: T,
    actualLabel: String?,
    valid: Boolean = true,
): MetadataConditionTrace = when {
    !valid -> MetadataConditionTrace(
        condition = condition,
        matched = false,
        failure = MetadataConditionFailure.INVALID_CONDITION,
        actualValue = actualLabel,
    )
    actual == null -> MetadataConditionTrace(
        condition = condition,
        matched = false,
        failure = MetadataConditionFailure.METADATA_NOT_AVAILABLE,
    )
    actual != expected -> MetadataConditionTrace(
        condition = condition,
        matched = false,
        failure = MetadataConditionFailure.VALUE_MISMATCH,
        actualValue = actualLabel,
    )
    else -> MetadataConditionTrace(
        condition = condition,
        matched = true,
        actualValue = actualLabel,
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

/**
 * A negated rule still needs the text. Absence cannot be asserted about content the app never
 * saw, so an unreadable notification refuses both operators rather than matching by default.
 */
/**
 * How specific a rule is, for conflict resolution.
 *
 * Counts the phrase condition rather than the legacy field, so a rule carrying three phrases is
 * not treated as equally specific to one carrying none.
 */

private fun specificity(rule: SignalRule): Int =
    (if (rule.app.isBlank() || rule.app.equals("any app", ignoreCase = true)) 0 else 1) +
        phraseConditionFor(rule).phrases.count { it.isNotBlank() } +
        rule.metadataConditions.size +
        rule.extras.size

private fun Boolean.yesNo(): String = if (this) "Yes" else "No"

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
