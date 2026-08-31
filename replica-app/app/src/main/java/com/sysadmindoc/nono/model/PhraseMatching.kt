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
}

/**
 * Evaluates [condition] against [fields].
 *
 * A quantifier of [PhraseQuantifier.NONE] over text that is not there is deliberately *not* a
 * match: absent text is not evidence that a phrase is absent from it, and treating it as such made
 * every negated rule match every metadata-only history row.
 */
fun evaluatePhrase(condition: PhraseCondition, fields: MatchableFields): PhraseMatchResult {
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
            MatchMode.REGEX -> compileRegex(phrase, condition.caseSensitive)
                ?: return PhraseMatchResult(false, emptyList(), PhraseMatchFailure.INVALID_PATTERN)
        }
    }

    val fieldMatches = buildList {
        for (field in selected) {
            val value = fields.valueOf(field)
            for ((index, matcher) in matchers.withIndex()) {
                add(
                    FieldMatch(
                        field = field,
                        phrase = phrases[index],
                        matched = value != null && matcher.matches(value),
                        available = !value.isNullOrEmpty(),
                    ),
                )
            }
        }
    }

    val presentPerPhrase = phrases.indices.map { index ->
        fieldMatches.filterIndexed { position, _ -> position % phrases.size == index }.any { it.matched }
    }
    val matched = when (condition.quantifier) {
        PhraseQuantifier.ANY -> presentPerPhrase.any { it }
        PhraseQuantifier.ALL -> presentPerPhrase.all { it }
        PhraseQuantifier.NONE -> presentPerPhrase.none { it }
    }
    return PhraseMatchResult(matched, fieldMatches)
}

/** True when [pattern] is something `java.util.regex` will accept. */
fun isValidPattern(pattern: String): Boolean = compileRegex(pattern, caseSensitive = false) != null

private interface PhraseMatcher {
    fun matches(value: String): Boolean
}

private class ContainsMatcher(phrase: String, private val caseSensitive: Boolean) : PhraseMatcher {
    // Both sides are stripped: the phrase because the user may have pasted it out of a
    // notification that carried a zero-width character, the text for the same reason in reverse.
    private val needle = stripFormatCharacters(phrase.trim())

    override fun matches(value: String): Boolean =
        stripFormatCharacters(value).contains(needle, ignoreCase = !caseSensitive)
}

private class RegexMatcher(private val regex: Regex) : PhraseMatcher {
    // The text is stripped, the pattern is not. A pattern is a program: editing it here would
    // change what the user wrote, and \\p{Cf} is something they can ask for themselves.
    override fun matches(value: String): Boolean = regex.containsMatchIn(stripFormatCharacters(value))
}

private fun compileRegex(pattern: String, caseSensitive: Boolean): PhraseMatcher? = runCatching {
    val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    RegexMatcher(Regex(pattern, options))
}.getOrNull()
