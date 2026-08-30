package com.sysadmindoc.nono.data

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
            SignalRule(id = 5L, matchType = "doesn't contain all of", extras = listOf("Image", "Category"), filterOperator = "Contains all", enabledFor = "6 hours"),
        )

        assertEquals(configured, decodeRules(encodeRules(configured)))
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
