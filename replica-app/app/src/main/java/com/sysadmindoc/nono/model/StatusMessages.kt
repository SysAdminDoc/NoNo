package com.sysadmindoc.nono.model

/**
 * The wording for every message that reports the result of a database write.
 *
 * These live apart from the ViewModel so the decision can be tested without a device: the rule
 * being enforced is that a success sentence is only ever reachable from an outcome the database
 * actually confirmed. A screen that says "Record deleted." over a row that is still there teaches
 * the user to distrust everything else the app says, and that failure is silent by nature - the
 * only way it shows up is if something asserts on it.
 */
object StatusMessages {
    /**
     * @param updated whether the write reported a row.
     * @return null when there is nothing to say, which is the case for a restore that worked.
     */
    fun starOutcome(updated: Boolean, starred: Boolean): String = when {
        !updated -> "That record could not be updated."
        starred -> "Kept until you unstar it."
        else -> "No longer kept."
    }

    fun deleteOutcome(removed: Boolean): String =
        if (removed) "Record deleted." else "That record could not be deleted."

    fun restoreOutcome(restored: Boolean): String? =
        if (restored) null else "That record could not be restored; it is back on this device."

    fun acknowledgementOutcome(acknowledged: Boolean): String? =
        if (acknowledged) null else "Those counts could not be dismissed."

    /** Every sentence above that claims a write succeeded. Used to prove failures never reach one. */
    val successPhrases: List<String> = listOf(
        "Kept until you unstar it.",
        "No longer kept.",
        "Record deleted.",
    )
}
