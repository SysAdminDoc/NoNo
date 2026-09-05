package com.sysadmindoc.nono.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseMatchingTest {

    private val sample = MatchableFields(
        title = "Parcel Tracker",
        text = "Your order is out for delivery",
        bigText = "Your order is out for delivery and arrives before 18:00",
        conversation = "Deliveries",
    )

    private fun condition(
        vararg phrases: String,
        quantifier: PhraseQuantifier = PhraseQuantifier.ANY,
        mode: MatchMode = MatchMode.CONTAINS,
        fields: Set<MatchField> = MatchField.ALL,
        caseSensitive: Boolean = false,
    ) = PhraseCondition(phrases.toList(), quantifier, mode, fields, caseSensitive)

    @Test
    fun `no phrase matches everything`() {
        assertTrue(evaluatePhrase(condition(), sample).matched)
        assertTrue(evaluatePhrase(condition("", "   "), sample).matched)
    }

    @Test
    fun `a field selection narrows where a phrase is looked for`() {
        // The phrase is in the text and not in the title. A rule that searches only the title has
        // to say no, and one that searches only the text has to say yes.
        val titleOnly = evaluatePhrase(condition("delivery", fields = setOf(MatchField.TITLE)), sample)
        val textOnly = evaluatePhrase(condition("delivery", fields = setOf(MatchField.TEXT)), sample)

        assertFalse(titleOnly.matched)
        assertTrue(textOnly.matched)
        assertEquals(listOf(MatchField.TEXT), textOnly.matchedFields)
    }

    @Test
    fun `the expanded body and the conversation name are separately searchable`() {
        assertTrue(evaluatePhrase(condition("18:00", fields = setOf(MatchField.BIG_TEXT)), sample).matched)
        assertTrue(evaluatePhrase(condition("Deliveries", fields = setOf(MatchField.CONVERSATION)), sample).matched)
        assertFalse(evaluatePhrase(condition("18:00", fields = setOf(MatchField.TEXT)), sample).matched)
    }

    @Test
    fun `case is ignored unless the rule says otherwise`() {
        assertTrue(evaluatePhrase(condition("PARCEL"), sample).matched)
        assertFalse(evaluatePhrase(condition("PARCEL", caseSensitive = true), sample).matched)
        assertTrue(evaluatePhrase(condition("Parcel", caseSensitive = true), sample).matched)
    }

    @Test
    fun `any, all and none combine several phrases`() {
        val present = arrayOf("parcel", "delivery")
        val mixed = arrayOf("parcel", "refund")

        assertTrue(evaluatePhrase(condition(*mixed, quantifier = PhraseQuantifier.ANY), sample).matched)
        assertFalse(evaluatePhrase(condition(*mixed, quantifier = PhraseQuantifier.ALL), sample).matched)
        assertTrue(evaluatePhrase(condition(*present, quantifier = PhraseQuantifier.ALL), sample).matched)
        assertFalse(evaluatePhrase(condition(*mixed, quantifier = PhraseQuantifier.NONE), sample).matched)
        assertTrue(evaluatePhrase(condition("refund", quantifier = PhraseQuantifier.NONE), sample).matched)
    }

    @Test
    fun `a pattern matches what a regular expression matches`() {
        assertTrue(evaluatePhrase(condition("out for \\w+", mode = MatchMode.REGEX), sample).matched)
        assertTrue(evaluatePhrase(condition("^Parcel", mode = MatchMode.REGEX, fields = setOf(MatchField.TITLE)), sample).matched)
        assertFalse(evaluatePhrase(condition("^delivery", mode = MatchMode.REGEX, fields = setOf(MatchField.TEXT)), sample).matched)
    }

    @Test
    fun `a pattern that will not compile reports that rather than not matching`() {
        // "It did not match" and "it was never tested" are different answers, and the second one
        // is the one the user can act on.
        val result = evaluatePhrase(condition("(unclosed", mode = MatchMode.REGEX), sample)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.INVALID_PATTERN, result.failure)
        assertFalse(isValidPattern("(unclosed"))
        assertTrue(isValidPattern("out for \\w+"))
    }

    @Test
    fun `case sensitivity applies to patterns too`() {
        assertTrue(evaluatePhrase(condition("parcel", mode = MatchMode.REGEX), sample).matched)
        assertFalse(evaluatePhrase(condition("parcel", mode = MatchMode.REGEX, caseSensitive = true), sample).matched)
    }

    @Test
    fun `invisible format characters do not stop a phrase matching`() {
        // A zero-width space inside a word renders as nothing, so the text on screen is exactly
        // what the user typed into the rule and does not contain it. Stripping both sides is the
        // only way a rule written by reading the screen can work.
        val sneaky = MatchableFields(text = "Your order is out for de​li­very")

        assertTrue(evaluatePhrase(condition("delivery"), sneaky).matched)
        assertTrue(evaluatePhrase(condition("out for delivery"), sneaky).matched)
        assertEquals("delivery", stripFormatCharacters("de​li­very"))
        assertEquals("hello", stripFormatCharacters("﻿hel‍lo‎"))
    }

    @Test
    fun `a phrase carrying its own format characters still matches plain text`() {
        val plain = MatchableFields(text = "out for delivery")
        assertTrue(evaluatePhrase(condition("de​livery"), plain).matched)
    }

    @Test
    fun `a pattern is never edited, only the text it is applied to`() {
        // The text is stripped; the pattern is not, because a pattern is a program. A user who
        // wants to match a zero-width space can still ask for one.
        val withZeroWidth = MatchableFields(text = "a​b")

        assertTrue("the text is stripped, so ab is what is searched", evaluatePhrase(condition("ab", mode = MatchMode.REGEX), withZeroWidth).matched)
        assertFalse(
            "and a pattern demanding the character finds it gone",
            evaluatePhrase(condition("a​b", mode = MatchMode.REGEX), withZeroWidth).matched,
        )
    }

    @Test
    fun `text that is not there is not evidence a phrase is absent`() {
        // The failure that made every negated rule match every metadata-only history row.
        val nothing = MatchableFields()
        val result = evaluatePhrase(condition("anything at all", quantifier = PhraseQuantifier.NONE), nothing)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.NO_TEXT, result.failure)
    }

    @Test
    fun `an empty field selection is reported rather than silently matching`() {
        val result = evaluatePhrase(condition("parcel", fields = emptySet()), sample)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.NO_FIELD_SELECTED, result.failure)
    }

    @Test
    fun `an absent field is not an empty one`() {
        // A rule looking for an empty conversation name must not match every notification that has
        // no conversation at all.
        val noConversation = MatchableFields(title = "Parcel", conversation = null)
        val result = evaluatePhrase(condition("Deliveries", fields = setOf(MatchField.CONVERSATION)), noConversation)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.NO_TEXT, result.failure)
    }

    @Test
    fun `the result names the field a phrase was found in`() {
        val result = evaluatePhrase(condition("Parcel", "delivery", quantifier = PhraseQuantifier.ALL), sample)

        assertTrue(result.matched)
        assertTrue(MatchField.TITLE in result.matchedFields)
        assertTrue(MatchField.TEXT in result.matchedFields)
        assertNull(result.failure)
    }

    @Test
    fun `a rule written before any of this reads with the same meaning`() {
        // The migration case: title and text joined, one substring, case-insensitive, and
        // "doesn't contain" meaning absent.
        val legacy = SignalRule(id = 1L, phrase = "delivery", matchType = "contains")
        val negated = SignalRule(id = 2L, phrase = "refund", matchType = "doesn't contain")
        val anything = SignalRule(id = 3L, phrase = "anything")

        val legacyCondition = phraseConditionFor(legacy)
        assertEquals(listOf("delivery"), legacyCondition.phrases)
        assertEquals(PhraseQuantifier.ANY, legacyCondition.quantifier)
        assertEquals(MatchMode.CONTAINS, legacyCondition.mode)
        assertEquals(MatchField.ALL, legacyCondition.fields)
        assertFalse(legacyCondition.caseSensitive)
        assertEquals(PhraseQuantifier.NONE, phraseConditionFor(negated).quantifier)
        assertTrue(phraseConditionFor(anything).isEmpty)

        assertTrue(evaluatePhrase(legacyCondition, sample).matched)
        assertTrue(evaluatePhrase(phraseConditionFor(negated), sample).matched)
        assertTrue(evaluatePhrase(phraseConditionFor(anything), sample).matched)
    }

    @Test
    fun `storing a condition keeps the older fields saying the same thing`() {
        // The rule card and a build older than this one both read the legacy fields.
        val rule = SignalRule(id = 1L).withPhraseCondition(
            condition("urgent", "asap", quantifier = PhraseQuantifier.NONE),
        )

        assertEquals("urgent, asap", rule.phrase)
        assertEquals("doesn't contain", rule.matchType)
        assertEquals(PhraseQuantifier.NONE, phraseConditionFor(rule).quantifier)

        val cleared = rule.withPhraseCondition(condition())
        assertEquals("anything", cleared.phrase)
        assertEquals("contains", cleared.matchType)
    }

    @Test
    fun `a rule naming a pattern that will not compile cannot be saved`() {
        val rule = SignalRule(id = 1L, app = "com.example.app", action = RECORD_ONLY_ACTION)
            .withPhraseCondition(condition("(unclosed", mode = MatchMode.REGEX))

        assertEquals(INVALID_PATTERN_MESSAGE, validateRule(rule))

        val fixed = rule.withPhraseCondition(condition("valid", mode = MatchMode.REGEX))
        assertNull(validateRule(fixed))
    }

    @Test
    fun `a rule searching no field cannot be saved`() {
        val rule = SignalRule(id = 1L, app = "com.example.app", action = RECORD_ONLY_ACTION)
            .withPhraseCondition(condition("urgent", fields = emptySet()))

        assertEquals(NO_FIELD_SELECTED_MESSAGE, validateRule(rule))
    }

    @Test
    fun `a condition describes itself`() {
        assertEquals("Anything", describePhraseCondition(condition()))
        assertEquals(
            "Contains the phrase urgent, in any field",
            describePhraseCondition(condition("urgent")),
        )
        assertEquals(
            "Does not contain the phrase urgent, in any field",
            describePhraseCondition(condition("urgent", quantifier = PhraseQuantifier.NONE)),
        )
        assertEquals(
            "All of these urgent, asap, in title, case must match",
            describePhraseCondition(
                condition(
                    "urgent",
                    "asap",
                    quantifier = PhraseQuantifier.ALL,
                    fields = setOf(MatchField.TITLE),
                    caseSensitive = true,
                ),
            ),
        )
    }

    @Test
    fun `a catastrophic pattern is abandoned rather than left to run`() {
        // (x+x+)+y over a run of x's does not finish. Measured on this JDK, 4,096 x's was still
        // running after 90 seconds, so the 4KB cap alone does not save the listener's thread; the
        // deadline is what makes this return. The result says the pattern was abandoned rather
        // than claiming the text does not contain a match, which nothing established.
        val condition = condition("(x+x+)+y", mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "x".repeat(100_000))

        val started = System.nanoTime()
        val result = evaluatePhrase(condition, fields)
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.PATTERN_ABANDONED, result.failure)
        assertTrue("took ${elapsedMillis}ms", elapsedMillis < 1_000)
    }

    @Test
    fun `a phrase that does match still matches when another one ran out of budget`() {
        val condition = condition("(x+x+)+y", "xxx", mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "x".repeat(100_000))

        val result = evaluatePhrase(condition, fields)

        assertTrue(result.matched)
        assertNull(result.failure)
    }

    @Test
    fun `text under the cap still matches to its end`() {
        val tail = "arrives at last"
        val fields = MatchableFields(bigText = "b".repeat(MAX_MATCHED_CHARS - tail.length) + tail)

        assertTrue(evaluatePhrase(condition(tail, fields = setOf(MatchField.BIG_TEXT)), fields).matched)
    }

    // No test asserts that a merely slow pattern finishes *without* the deadline firing. (a+)+b
    // over the capped 4KB measured around 120ms against a 250ms budget on this machine, which is
    // close enough that the answer changes under load.
    @Test
    fun `text past the cap is not searched`() {
        val fields = MatchableFields(bigText = "b".repeat(MAX_MATCHED_CHARS) + "arrives at last")

        assertFalse(
            evaluatePhrase(condition("arrives at last", fields = setOf(MatchField.BIG_TEXT)), fields).matched,
        )
    }

    @Test
    fun `the cap counts what a person can see, not what an app padded the field with`() {
        // A zero-width space renders as nothing, so an app can put thousands of them in front of
        // the real message and the notification looks the same on screen. Capping before
        // stripping would have let that push the message out of range of every rule.
        val zeroWidth = "​"
        val fields = MatchableFields(bigText = zeroWidth.repeat(MAX_MATCHED_CHARS * 2) + "your parcel")

        assertTrue(
            evaluatePhrase(condition("your parcel", fields = setOf(MatchField.BIG_TEXT)), fields).matched,
        )
    }

    @Test
    fun `a negated rule does not fire because the pattern was given up on`() {
        // NONE asks whether the phrase is absent. A pattern that never finished says nothing
        // about that, and reading it as "absent" would fire the rule on the strength of a
        // question nobody answered.
        val condition = condition("(x+x+)+y", quantifier = PhraseQuantifier.NONE, mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "x".repeat(100_000))

        val result = evaluatePhrase(condition, fields)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.PATTERN_ABANDONED, result.failure)
    }

    @Test
    fun `a negated rule still fires when the phrase that was decided settles it`() {
        // "xxx" is present, so NONE is false whatever the abandoned phrase would have said. The
        // outcome does not rest on the part that was never established, so it is not a failure.
        val condition = condition("xxx", "(x+x+)+y", quantifier = PhraseQuantifier.NONE, mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "x".repeat(100_000))

        val result = evaluatePhrase(condition, fields)

        assertFalse(result.matched)
        assertNull(result.failure)
    }

    @Test
    fun `many rules together cost about what one rule costs`() {
        // A budget made inside evaluatePhrase multiplied by rule count: twenty rules carrying a
        // slow pattern spent five seconds on the listener's thread, which is an ANR rather than a
        // bounded cost. A slice each keeps the total in the same place.
        val condition = condition("(x+x+)+y", mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "x".repeat(100_000))
        val slice = matchBudgetSliceMillis(20)

        val started = System.nanoTime()
        repeat(20) { evaluatePhrase(condition, fields, PhraseMatchBudget(slice)) }
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("twenty conditions took ${elapsedMillis}ms", elapsedMillis < 1_000)
    }

    @Test
    fun `a slow rule does not disable the rules after it`() {
        // With one budget shared between them, the first condition spent it all and the second
        // was abandoned even though its pattern is trivial. That made a rule's behaviour depend
        // on where it happened to sit in the list, silently and on every notification.
        val slice = matchBudgetSliceMillis(2)
        val text = MatchableFields(text = "x".repeat(100_000))

        evaluatePhrase(condition("(x+x+)+y", mode = MatchMode.REGEX), text, PhraseMatchBudget(slice))
        val after = evaluatePhrase(condition("x+", mode = MatchMode.REGEX), text, PhraseMatchBudget(slice))

        assertTrue(after.matched)
        assertNull(after.failure)
    }

    @Test
    fun `the slice shrinks with the rule count but never below the floor`() {
        assertEquals(MATCH_BUDGET_MILLIS, matchBudgetSliceMillis(0))
        assertEquals(MATCH_BUDGET_MILLIS, matchBudgetSliceMillis(1))
        assertEquals(MATCH_BUDGET_MILLIS / 10, matchBudgetSliceMillis(10))
        assertEquals(MIN_MATCH_BUDGET_MILLIS, matchBudgetSliceMillis(10_000))
    }

    @Test
    fun `the surviving text never ends half way through a character`() {
        // An emoji is two chars in UTF-16. Cutting between them leaves a lone high surrogate,
        // which is neither the character that was there nor any other.
        val emoji = "😀"
        val split = ("b".repeat(MAX_MATCHED_CHARS - 1) + emoji + "tail").takeMatchable()
        val whole = ("b".repeat(MAX_MATCHED_CHARS - 2) + emoji + "tail").takeMatchable()

        assertEquals(MAX_MATCHED_CHARS - 1, split.length)
        assertFalse(split.last().isHighSurrogate())
        // A pair that fits is kept whole, so the cap is not shortened when it need not be.
        assertEquals(MAX_MATCHED_CHARS, whole.length)
        assertTrue(whole.endsWith(emoji))
        // Text under the cap is untouched, surrogates and all.
        assertEquals("hi $emoji", "hi $emoji".takeMatchable())
    }

    @Test
    fun `a pattern that overflows the stack is given up on rather than crashing`() {
        // java.util.regex recurses once per repetition of an alternation, so (a|b)*c throws a
        // StackOverflowError somewhere around two thousand characters - inside the cap, and well
        // inside what an app can send. Uncaught, that unwinds through the listener callback.
        val condition = condition("(a|b)*c", mode = MatchMode.REGEX)
        val fields = MatchableFields(text = "a".repeat(MAX_MATCHED_CHARS))

        val result = evaluatePhrase(condition, fields)

        assertFalse(result.matched)
        assertEquals(PhraseMatchFailure.PATTERN_ABANDONED, result.failure)
    }

    @Test
    fun `reads spread across derived subsequences still reach the budget check`() {
        // The sampling counter reads the clock once every few thousand characters. Held per text,
        // every subsequence would start its own grace period, so reads spread thinly across many
        // short subsequences could add up without limit and never consult the deadline. Held on
        // the budget, the reads accumulate however the text was sliced.
        //
        // This is the positive control for that: with a per-text counter no slice below gets
        // anywhere near the interval on its own, so nothing is ever checked and the budget is
        // never spent. Deliberately expired, so any check at all must stop it.
        val budget = PhraseMatchBudget(millis = 0L)
        val text: CharSequence = BudgetedText("x".repeat(SLICE), budget)

        var reads = 0
        val stopped = try {
            repeat(SLICES) {
                val slice = text.subSequence(0, SLICE)
                for (i in 0 until SLICE) {
                    slice[i]
                    reads++
                }
            }
            false
        } catch (_: RuntimeException) {
            true
        }

        assertTrue("the budget was never consulted across $reads reads", stopped)
        assertTrue(budget.spent)
    }

    private companion object {
        /** Short enough that no single subsequence approaches the sampling interval on its own. */
        const val SLICE = 64

        /** Enough slices that the reads together pass the interval several times over. */
        const val SLICES = 400
    }
}
