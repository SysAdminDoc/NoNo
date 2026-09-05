package com.sysadmindoc.nono.model

import kotlinx.serialization.Serializable

/** How a phrase is compared against a field. */
enum class MatchMode(val label: String) {
    /** Plain substring containment. */
    CONTAINS("Contains"),

    /** A Java regular expression, as `java.util.regex` understands it. */
    REGEX("Matches a pattern"),
}

/**
 * Which part of a notification a phrase is tested against.
 *
 * Named separately rather than searching one joined string, because "the sender said urgent" and
 * "the message said urgent" are different questions, and a rule that cannot tell them apart is a
 * rule that fires on the wrong notifications. The old behaviour, title and text joined with a
 * space, is still what a rule with every field selected does.
 */
enum class MatchField(val label: String) {
    TITLE("Title"),
    TEXT("Text"),

    /** The expanded body, when the app supplied one. */
    BIG_TEXT("Expanded text"),

    /** The conversation or sender name, when the app supplied one. */
    CONVERSATION("Conversation"),
    ;

    companion object {
        /** Every field. What a rule written before fields existed was doing. */
        val ALL: Set<MatchField> = entries.toSet()
    }
}

/** How several phrases combine. */
enum class PhraseQuantifier(val label: String) {
    /** Any one of them is enough. */
    ANY("Any of these"),

    /** All of them have to be present. */
    ALL("All of these"),

    /** None of them may be present. */
    NONE("None of these"),
    ;

    /** True when this is satisfied by a phrase being absent rather than present. */
    val isNegated: Boolean get() = this == NONE
}

/**
 * The phrase side of a rule.
 *
 * Held apart from [SignalRule] so the matcher and the tester take the same value, and so a rule
 * written before any of this existed can be read into it without the two drifting.
 */
@Serializable
data class PhraseCondition(
    val phrases: List<String> = emptyList(),
    val quantifier: PhraseQuantifier = PhraseQuantifier.ANY,
    val mode: MatchMode = MatchMode.CONTAINS,
    val fields: Set<MatchField> = MatchField.ALL,
    val caseSensitive: Boolean = false,
) {
    /** True when there is nothing to test, which every notification satisfies. */
    val isEmpty: Boolean get() = phrases.none { it.isNotBlank() }
}

/**
 * The text of a notification, field by field.
 *
 * Null means the app supplied nothing for that field, which is different from an empty string and
 * has to stay different: a rule looking for an empty conversation name would otherwise match every
 * notification that has no conversation at all.
 */
data class MatchableFields(
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val conversation: String? = null,
) {
    fun valueOf(field: MatchField): String? = when (field) {
        MatchField.TITLE -> title
        MatchField.TEXT -> text
        MatchField.BIG_TEXT -> bigText
        MatchField.CONVERSATION -> conversation
    }

    val isEmpty: Boolean get() = MatchField.entries.all { valueOf(it).isNullOrEmpty() }
}

/**
 * Removes Unicode format characters.
 *
 * Zero-width spaces, joiners, directional marks, soft hyphens and the byte-order mark all render
 * as nothing. An app that puts one inside a word, deliberately or by accident, produces text that
 * looks exactly like what the user typed into their rule and does not contain it. Stripping them
 * from both sides is the only way a rule written by looking at the screen can work.
 *
 * Applies to the text being searched in both modes, and to the phrase only in [MatchMode.CONTAINS]:
 * a regular expression is a program, and silently editing it would change what it means.
 */
fun stripFormatCharacters(value: String): String =
    value.filterNot { Character.getType(it) == Character.FORMAT.toInt() }

/**
 * The first [MAX_MATCHED_CHARS] characters, never ending mid-character.
 *
 * A `String` is UTF-16, so an emoji is two chars. Cutting between them leaves a lone high
 * surrogate, which is not the character that was there and not any other character either.
 */
internal fun String.takeMatchable(): String {
    if (length <= MAX_MATCHED_CHARS) return this
    val end = if (this[MAX_MATCHED_CHARS - 1].isHighSurrogate()) MAX_MATCHED_CHARS - 1 else MAX_MATCHED_CHARS
    return substring(0, end)
}

/**
 * How much of one field is searched.
 *
 * Rule evaluation happens synchronously on the listener's callback thread, and a REGEX rule runs
 * whatever pattern the user wrote over text a third-party app supplied. `java.util.regex` has no
 * step budget, so a backtracking-prone pattern over a long `bigText` can hold that thread for
 * minutes and take the listener down with it. Bounding the input bounds the backtracking without
 * moving evaluation off the payload's stack frame, which the privacy design forbids.
 *
 * The same 4KB a rule's own fields are limited to on import (`RuleTransferLimits.MAX_FIELD_CHARS`),
 * which is far more than any notification a person reads. Counted after format characters are
 * stripped, so padding a field with zero-width characters cannot push readable text past it.
 */
const val MAX_MATCHED_CHARS = 4 * 1024

/** Why one phrase did or did not match one field. */
data class FieldMatch(
    val field: MatchField,
    val phrase: String,
    val matched: Boolean,
    val available: Boolean,
)

/** What a phrase condition did against one notification, in enough detail to explain it. */
data class PhraseMatchResult(
    val matched: Boolean,
    val fieldMatches: List<FieldMatch>,
    /** Set when the condition could not be evaluated at all, which is not the same as not matching. */
    val failure: PhraseMatchFailure? = null,
) {
    /** The fields that actually carried a match, which is what the tester lists. */
    val matchedFields: List<MatchField> get() = fieldMatches.filter { it.matched }.map { it.field }.distinct()
}

enum class PhraseMatchFailure(val message: String) {
    NO_TEXT("The notification carried no text in the selected fields."),
    INVALID_PATTERN("That pattern is not valid, so nothing was tested against it."),
    NO_FIELD_SELECTED("No field is selected, so there is nothing to search."),
    PATTERN_ABANDONED("That pattern could not be finished on this text, so it was given up on part way through."),
}

/**
 * Evaluates [condition] against [fields].
 *
 * A quantifier of [PhraseQuantifier.NONE] over text that is not there is deliberately *not* a
 * match: absent text is not evidence that a phrase is absent from it, and treating it as such made
 * every negated rule match every metadata-only history row.
 *
 * @param budget shared across every rule evaluated for one notification. A caller testing a single
 * condition, such as the rule tester, gets a fresh one.
 */
fun evaluatePhrase(
    condition: PhraseCondition,
    fields: MatchableFields,
    budget: PhraseMatchBudget = PhraseMatchBudget(),
): PhraseMatchResult {
    val phrases = condition.phrases.filter { it.isNotBlank() }
    if (phrases.isEmpty()) return PhraseMatchResult(matched = true, fieldMatches = emptyList())
    if (condition.fields.isEmpty()) {
        return PhraseMatchResult(false, emptyList(), PhraseMatchFailure.NO_FIELD_SELECTED)
    }
    val selected = MatchField.entries.filter { it in condition.fields }
    if (selected.all { fields.valueOf(it).isNullOrEmpty() }) {
        return PhraseMatchResult(false, emptyList(), PhraseMatchFailure.NO_TEXT)
    }

    val matchers = phrases.map { phrase ->
        when (condition.mode) {
            MatchMode.CONTAINS -> ContainsMatcher(phrase, condition.caseSensitive)
            MatchMode.REGEX -> compileRegex(phrase, condition.caseSensitive, budget)
                ?: return PhraseMatchResult(false, emptyList(), PhraseMatchFailure.INVALID_PATTERN)
        }
    }

    /** True where some field matched the phrase at that index. */
    val found = BooleanArray(phrases.size)

    /** True where some field could not be decided, so `found` may understate the answer. */
    val givenUp = BooleanArray(phrases.size)

    val fieldMatches = buildList {
        for (field in selected) {
            val raw = fields.valueOf(field)
            // Stripped first, then capped. The other order let an app pad a field with thousands
            // of zero-width characters and push everything a person can actually read past the
            // cap, which is the same evasion stripFormatCharacters exists to close.
            val value = raw?.let { stripFormatCharacters(it).takeMatchable() }
            for ((index, matcher) in matchers.withIndex()) {
                val outcome = if (value == null) false else matcher.matches(value)
                if (outcome == null) givenUp[index] = true
                if (outcome == true) found[index] = true
                add(
                    FieldMatch(
                        field = field,
                        phrase = phrases[index],
                        matched = outcome == true,
                        available = !raw.isNullOrEmpty(),
                    ),
                )
            }
        }
    }

    fun decide(undecidedAre: Boolean): Boolean {
        val present = phrases.indices.map { found[it] || (undecidedAre && givenUp[it]) }
        return when (condition.quantifier) {
            PhraseQuantifier.ANY -> present.any { it }
            PhraseQuantifier.ALL -> present.all { it }
            PhraseQuantifier.NONE -> present.none { it }
        }
    }

    // A phrase the matcher gave up on has no answer, and which way that matters depends on the
    // quantifier: NONE over one abandoned phrase would otherwise read as "the phrase is not
    // there" and fire the rule. Deciding it both ways says whether the outcome rests on the part
    // that was never established. Where it does, the condition failed rather than not matching.
    val optimistic = decide(undecidedAre = true)
    val pessimistic = decide(undecidedAre = false)
    return if (optimistic != pessimistic) {
        PhraseMatchResult(false, fieldMatches, PhraseMatchFailure.PATTERN_ABANDONED)
    } else {
        PhraseMatchResult(pessimistic, fieldMatches)
    }
}

/** True when [pattern] is something `java.util.regex` will accept. */
fun isValidPattern(pattern: String): Boolean =
    compileRegex(pattern, caseSensitive = false, budget = PhraseMatchBudget(millis = Long.MAX_VALUE / 2_000_000L)) != null

private interface PhraseMatcher {
    /** True, false, or null when the matcher gave up without deciding. */
    fun matches(value: String): Boolean?
}

/**
 * How long pattern matching may take, in total.
 *
 * Capping the text bounds ordinary matching, but not backtracking: `(a+)+b` over a few thousand
 * `a`s is exponential whatever the length. The listener evaluates rules synchronously, so the only
 * safe answer to a pattern that will not finish is to stop it.
 */
const val MATCH_BUDGET_MILLIS = 250L

/**
 * The least any one rule gets, however many rules there are.
 *
 * A legitimate pattern over the capped 4KB finishes in microseconds, so this is generous. It
 * exists so that someone with hundreds of rules does not end up with a slice so thin that normal
 * matching starts failing.
 */
const val MIN_MATCH_BUDGET_MILLIS = 5L

/**
 * How long one rule out of [ruleCount] may spend.
 *
 * A budget shared by every rule bounds the total, but makes the outcome depend on the order rules
 * happen to be in: one pathological pattern spends the lot and every later rule with a pattern is
 * abandoned, silently and on every notification. Dividing it instead means a slow rule can only
 * spend its own share, so it disables itself and nothing else.
 */
fun matchBudgetSliceMillis(ruleCount: Int): Long =
    (MATCH_BUDGET_MILLIS / maxOf(1, ruleCount)).coerceAtLeast(MIN_MATCH_BUDGET_MILLIS)

/** Thrown out of a running match. Carries no stack trace: it is control flow, not a fault. */
private class MatchBudgetSpent : RuntimeException(null, null, false, false)

/**
 * One budget, shared by everything evaluated for one arriving notification.
 *
 * Held by the caller rather than made fresh inside [evaluatePhrase], because a budget per
 * condition multiplies: someone with twenty rules carrying a slow pattern would have spent five
 * seconds on the listener's thread, which is an ANR rather than a bounded cost. Spending it stops
 * the remaining patterns instead, and a rule whose pattern was stopped does not fire.
 */
class PhraseMatchBudget(millis: Long = MATCH_BUDGET_MILLIS) {
    private val atNanos = System.nanoTime() + millis * 1_000_000L

    /**
     * Reads still owed before the clock is read again.
     *
     * This lives on the budget rather than on [BudgetedText] because `subSequence` derives a new
     * text against the same budget. A counter held per text hands every derived instance a fresh
     * full-length grace period, so reads spread thinly across many short subsequences could add up
     * without limit and never reach a check. Today's path never derives one — `containsMatchIn`
     * calls `find` and never extracts a group, and only group extraction takes a subsequence — so
     * this closes a hazard rather than a live leak, and it costs one shared field.
     */
    private var countdown = CHECK_INTERVAL

    internal var spent: Boolean = false
        private set

    /** One read of the searched text. Consults the clock every [CHECK_INTERVAL] reads. */
    internal fun sample() {
        if (--countdown > 0) return
        countdown = CHECK_INTERVAL
        check()
    }

    internal fun check() {
        if (System.nanoTime() >= atNanos) {
            spent = true
            throw MatchBudgetSpent()
        }
    }

    private companion object {
        /** Reads between clock checks, so ordinary matching does not pay for `nanoTime`. */
        const val CHECK_INTERVAL = 4096
    }
}

/**
 * The searched text, seen through the budget.
 *
 * `java.util.regex` offers no step limit and no way to interrupt a running match, but it reads its
 * input one character at a time, so a `CharSequence` that refuses to keep answering is the one
 * place a runaway pattern can be stopped. Every read is reported to [PhraseMatchBudget.sample],
 * which reads the clock periodically rather than on each character so that ordinary matching does
 * not pay for it. The count belongs to the budget, so a subsequence taken from this text cannot
 * start its own grace period.
 */
internal class BudgetedText(
    private val value: CharSequence,
    private val budget: PhraseMatchBudget,
) : CharSequence {
    override val length: Int get() = value.length

    override fun get(index: Int): Char {
        budget.sample()
        return value[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        BudgetedText(value.subSequence(startIndex, endIndex), budget)

    override fun toString(): String = value.toString()
}

private class ContainsMatcher(phrase: String, private val caseSensitive: Boolean) : PhraseMatcher {
    // Both sides are stripped: the phrase because the user may have pasted it out of a
    // notification that carried a zero-width character, the text for the same reason in reverse.
    // The value arrives already stripped from [evaluatePhrase], which caps what it strips.
    private val needle = stripFormatCharacters(phrase.trim())

    override fun matches(value: String): Boolean = value.contains(needle, ignoreCase = !caseSensitive)
}

private class RegexMatcher(private val regex: Regex, private val budget: PhraseMatchBudget) : PhraseMatcher {
    // The pattern is never edited, only the text it is applied to. A pattern is a program:
    // rewriting it here would change what the user wrote, and \\p{Cf} is something they can ask
    // for themselves.
    override fun matches(value: String): Boolean? = try {
        regex.containsMatchIn(BudgetedText(value, budget))
    } catch (_: MatchBudgetSpent) {
        null
    } catch (_: StackOverflowError) {
        // `java.util.regex` recurses once per repetition of an alternation, so (a|b)*c blows the
        // stack somewhere around two thousand characters — well inside the cap, and well inside
        // what an app can send. Catching an Error is otherwise indefensible; here the alternative
        // is a pattern the user typed killing the notification listener, and the stack has
        // already unwound by the time this runs.
        null
    }
}

private fun compileRegex(pattern: String, caseSensitive: Boolean, budget: PhraseMatchBudget): PhraseMatcher? =
    runCatching {
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        RegexMatcher(Regex(pattern, options), budget)
    }.getOrNull()
