package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.DEFAULT_MATCH_TYPE
import com.sysadmindoc.nono.model.NEGATED_MATCH_TYPE
import com.sysadmindoc.nono.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleCodecTest {

    private val rules = listOf(
        SignalRule(
            id = 1L,
            name = "First",
            app = "Messages",
            appPackageName = "com.google.android.apps.messaging",
            phrase = "urgent",
            action = "Mute",
        ),
        SignalRule(id = 2L, name = "Second", enabled = false, priority = "High", folder = "Work"),
    )

    @Test
    fun `round trips a multi rule store`() {
        assertEquals(rules, decodeRules(encodeRules(rules)))
    }

    @Test
    fun `round trips the dialog-driven fields`() {
        val configured = listOf(
            SignalRule(id = 5L, matchType = NEGATED_MATCH_TYPE, extras = listOf("Image", "Category"), filterOperator = "Contains all", enabledFor = "6 hours"),
        )

        assertEquals(configured, decodeRules(encodeRules(configured)))
    }

    @Test
    fun `legacy phrase-group operators collapse onto an operator the evaluator implements`() {
        val legacy = listOf(
            SignalRule(id = 1L, matchType = "contains any of"),
            SignalRule(id = 2L, matchType = "contains all of"),
            SignalRule(id = 3L, matchType = "doesn't contain any of"),
            SignalRule(id = 4L, matchType = "doesn't contain all of"),
        )

        val decoded = decodeRules(encodeRules(legacy)).orEmpty()

        assertEquals(
            listOf(DEFAULT_MATCH_TYPE, DEFAULT_MATCH_TYPE, NEGATED_MATCH_TYPE, NEGATED_MATCH_TYPE),
            decoded.map { it.matchType },
        )
        // Deterministic: decoding what was already decoded changes nothing further.
        assertEquals(decoded, decodeRules(encodeRules(decoded)))
    }

    @Test
    fun `rules that arrive without an id are given one instead of collapsing`() {
        // A hand-written or third-party rule file can omit the id. The model default is the
        // unsaved sentinel, so all three used to share id 0: distinctBy kept one, and the
        // survivor could never be edited because every save saw the sentinel and appended a copy.
        val encoded = """
            {"version":3,"rules":[
              {"name":"A","app":"any app","phrase":"alpha","action":"Mute"},
              {"name":"B","app":"any app","phrase":"beta","action":"Mute"},
              {"name":"C","app":"any app","phrase":"gamma","action":"Mute"}
            ]}
        """.trimIndent()

        val decoded = decodeRules(encoded).orEmpty()

        assertEquals(listOf("A", "B", "C"), decoded.map { it.name })
        assertEquals(listOf(1L, 2L, 3L), decoded.map { it.id })
        // Deterministic, and stable once allocated.
        assertEquals(decoded, decodeRules(encoded))
        assertEquals(decoded, decodeRules(encodeRules(decoded)))
    }

    @Test
    fun `an allocated id survives a file naming the largest possible one`() {
        // max + 1 wraps to Long.MIN_VALUE. A file holding both ends would then allocate an id
        // already in use, and distinctBy would drop a rule with no error and no reason given.
        val encoded = """
            {"version":3,"rules":[
              {"id":9223372036854775807,"name":"MAX","app":"any app","phrase":"a","action":"Mute"},
              {"id":-9223372036854775808,"name":"MIN","app":"any app","phrase":"b","action":"Mute"},
              {"name":"NEW","app":"any app","phrase":"c","action":"Mute"}
            ]}
        """.trimIndent()

        val decoded = decodeRules(encoded).orEmpty()

        assertEquals(listOf("MAX", "MIN", "NEW"), decoded.map { it.name })
        assertEquals(3, decoded.map { it.id }.distinct().size)
    }

    @Test
    fun `an allocated id never lands on one the file already used`() {
        val encoded = """
            {"version":3,"rules":[
              {"id":4,"name":"Existing","app":"any app","phrase":"alpha","action":"Mute"},
              {"name":"New","app":"any app","phrase":"beta","action":"Mute"}
            ]}
        """.trimIndent()

        val decoded = decodeRules(encoded).orEmpty()

        assertEquals(2, decoded.size)
        // The lowest free id, not one past the maximum. Counting up from the maximum is what
        // wrapped a file naming Long.MAX_VALUE onto an id already in use.
        assertEquals(listOf(4L, 1L), decoded.map { it.id })
        assertEquals(listOf("Existing", "New"), decoded.map { it.name })
    }

    @Test
    fun `round trips an empty store`() {
        assertEquals(emptyList<SignalRule>(), decodeRules(encodeRules(emptyList())))
    }

    @Test
    fun `absent or blank storage decodes to null so the caller can fall back`() {
        assertNull(decodeRules(null))
        assertNull(decodeRules(""))
        assertNull(decodeRules("   "))
    }

    @Test
    fun `malformed storage decodes to null instead of throwing`() {
        assertNull(decodeRules("not json at all"))
        assertNull(decodeRules("{\"version\":1,\"rules\":"))
        assertNull(decodeRules("{\"version\":1,\"rules\":[{\"id\":\"not-a-number\"}]}"))
    }

    @Test
    fun `a store from a newer build is refused rather than misread`() {
        assertNull(decodeRules("{\"version\":99,\"rules\":[]}"))
    }

    @Test
    fun `unknown fields from a newer build are ignored`() {
        val decoded = decodeRules("{\"version\":1,\"rules\":[{\"id\":7,\"name\":\"X\",\"future\":true}]}")

        assertEquals(1, decoded?.size)
        assertEquals(7L, decoded?.first()?.id)
    }

    @Test
    fun `duplicate ids collapse so addressing stays unambiguous`() {
        val decoded = decodeRules("{\"version\":1,\"rules\":[{\"id\":1,\"name\":\"A\"},{\"id\":1,\"name\":\"B\"}]}")

        assertEquals(1, decoded?.size)
        assertEquals("A", decoded?.first()?.name)
    }

    @Test
    fun `v1 store migrates blanks and duplicate ids into the v2 shape`() {
        val decoded = decodeRules(
            """{"version":1,"rules":[{"id":3,"name":"","app":"","phrase":"","extras":["Image","Image"],"enabledFor":""},{"id":3,"name":"discarded"}]}"""
        )

        assertEquals(1, decoded?.size)
        assertEquals("Imported rule", decoded?.first()?.name)
        assertEquals("any app", decoded?.first()?.app)
        assertEquals("anything", decoded?.first()?.phrase)
        assertEquals(listOf("Image"), decoded?.first()?.extras)
        assertEquals(null, decoded?.first()?.enabledFor)
    }

    @Test
    fun `v2 label selections migrate to stable package identity`() {
        val decoded = decodeRules(
            """{"version":2,"rules":[{"id":8,"app":"Messages","phrase":"urgent"}]}""",
        )

        assertEquals("Messages", decoded?.single()?.app)
        assertEquals("com.google.android.apps.messaging", decoded?.single()?.appPackageName)
    }

    @Test
    fun `unknown legacy labels remain readable and explicitly unresolved`() {
        val decoded = decodeRules(
            """{"version":2,"rules":[{"id":9,"app":"Unknown app"}]}""",
        )

        assertEquals("Unknown app", decoded?.single()?.app)
        assertEquals(null, decoded?.single()?.appPackageName)
    }
}
