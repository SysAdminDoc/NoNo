package com.sysadmindoc.nono.model

/**
 * Filtering saved rules by what the user can see on the card.
 *
 * The fields searched are exactly the ones a rule card shows: the app it names, the phrase it
 * matches, the operator, the action, the folder, and the priority. Anything the screen does not
 * show is not searched, because a result the user cannot account for reads as a bug.
 *
 * Order is never changed. A search narrows the list the user already knows; re-ranking it by
 * relevance would move rules around under a query and leave them somewhere new when it clears.
 */
fun filterRules(rules: List<SignalRule>, query: String): List<SignalRule> {
    val needle = query.trim()
    if (needle.isEmpty()) return rules
    return rules.filter { rule -> ruleMatchesSearch(rule, needle) }
}

/** True when any searchable field of [rule] contains [query], ignoring case. */
fun ruleMatchesSearch(rule: SignalRule, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return searchableFields(rule).any { it.contains(needle, ignoreCase = true) }
}

/**
 * Every string a rule can be found by.
 *
 * `appPackageName` is included even though the card shows the label: two apps can call themselves
 * the same thing, and the package is how the user tells those two rules apart elsewhere in the
 * app. `enabledFor` is included because an imported rule can carry an expiry that the card states.
 */
private fun searchableFields(rule: SignalRule): List<String> = buildList {
    add(rule.name)
    add(rule.app)
    rule.appPackageName?.let(::add)
    add(rule.phrase)
    add(normalizeMatchType(rule.matchType))
    add(renderActionSummary(rule.action))
    add(rule.action)
    add(rule.folder)
    add(rule.priority)
    addAll(rule.extras)
    rule.enabledFor?.let(::add)
}
