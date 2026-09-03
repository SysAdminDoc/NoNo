package com.sysadmindoc.nono.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A search box takes text, not a pattern.
 *
 * `%` and `_` are wildcards to SQLite, so a history search for `100%` used to return everything
 * and `_` matched any single character. The escaping is arithmetic on a string, which is worth
 * pinning here rather than only through a database: the character that goes wrong is the escape
 * character itself, and it goes wrong at the end of the term where a database test is least
 * likely to look.
 */
class LikeTermEscapingTest {

    @Test
    fun ordinaryTextIsLeftAlone() {
        assertEquals("", escapeLikeTerm(""))
        assertEquals("com.example.chat", escapeLikeTerm("com.example.chat"))
    }

    @Test
    fun bothWildcardsBecomeLiterals() {
        assertEquals("100\\%", escapeLikeTerm("100%"))
        assertEquals("\\_", escapeLikeTerm("_"))
        assertEquals("a\\%b\\_c", escapeLikeTerm("a%b_c"))
    }

    @Test
    fun theEscapeCharacterEscapesItself() {
        // Without this, a term ending in a backslash escapes the pattern's own trailing '%' and
        // the query stops matching anything at all.
        assertEquals("\\\\", escapeLikeTerm("\\"))
        assertEquals("a\\\\", escapeLikeTerm("a\\"))
        assertEquals("\\\\\\%", escapeLikeTerm("\\%"))
    }
}
