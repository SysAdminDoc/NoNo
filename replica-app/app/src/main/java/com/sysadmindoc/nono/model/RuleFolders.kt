package com.sysadmindoc.nono.model

/** The value a rule carries when the user has not put it anywhere. */
const val NO_FOLDER = "No folder"

/**
 * One heading and the rules under it.
 *
 * @property heading null when nothing is filed, which is the case where the list renders exactly
 * as it always has and no heading should appear at all.
 */
data class RuleGroup(val heading: String?, val rules: List<SignalRule>)

/**
 * Groups the rules list by folder, and only when a folder is actually in use.
 *
 * A user who has never touched folders must see exactly the list they saw before, in the same
 * order, with nothing extra on the screen. Once anything is filed, the list gains headings, and
 * unfiled rules keep their own heading at the end rather than floating above the first one with
 * no explanation.
 *
 * Order inside a group is the order the user arranged, never re-sorted. Folder headings appear in
 * the order the rules themselves introduce them, so filing a rule does not rearrange the rest of
 * the screen.
 */
fun groupRulesByFolder(rules: List<SignalRule>): List<RuleGroup> {
    if (rules.isEmpty()) return emptyList()
    val filed = rules.filter { folderOf(it) != NO_FOLDER }
    if (filed.isEmpty()) return listOf(RuleGroup(heading = null, rules = rules))

    val groups = LinkedHashMap<String, MutableList<SignalRule>>()
    rules.forEach { rule -> groups.getOrPut(folderOf(rule)) { mutableListOf() }.add(rule) }
    // Unfiled last. A heading of its own rather than no heading: rules sitting above the first
    // folder with nothing said about them read as a rendering mistake.
    val unfiled = groups.remove(NO_FOLDER)
    return groups.map { (name, contents) -> RuleGroup(name, contents) } +
        listOfNotNull(unfiled?.let { RuleGroup(NO_FOLDER, it) })
}

/** A folder is whatever the user typed, with blanks and whitespace treated as unfiled. */
private fun folderOf(rule: SignalRule): String = rule.folder.trim().ifBlank { NO_FOLDER }
