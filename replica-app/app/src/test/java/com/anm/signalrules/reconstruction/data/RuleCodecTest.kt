package com.anm.signalrules.reconstruction.data

import com.anm.signalrules.reconstruction.model.SignalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleCodecTest {

    private val rules = listOf(
        SignalRule(id = 1L, name = "First", app = "Messages", phrase = "urgent", action = "Mute"),
        SignalRule(id = 2L, name = "Second", enabled = false, priority = "High", folder = "Work"),
    )

    @Test
    fun `round trips a multi rule store`() {
        assertEquals(rules, decodeRules(encodeRules(rules)))
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
}
