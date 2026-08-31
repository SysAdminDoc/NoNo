package com.sysadmindoc.nono.data

import com.sysadmindoc.nono.model.SignalRule
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleCodecGoldenTest {
    @Test
    fun `v1 fixture migrates deterministic blanks and duplicate ids`() {
        val decoded = decodeRules(resource("rules-v1.json"))

        assertEquals(1, decoded?.size)
        assertEquals("Imported rule", decoded?.single()?.name)
        assertEquals("any app", decoded?.single()?.app)
        assertEquals(listOf("Image"), decoded?.single()?.extras)
        assertEquals(null, decoded?.single()?.enabledFor)
    }

    @Test
    fun `v2 fixture restores known package identity`() {
        val decoded = decodeRules(resource("rules-v2.json"))

        assertEquals("Messages", decoded?.single()?.app)
        assertEquals("com.google.android.apps.messaging", decoded?.single()?.appPackageName)
    }

    @Test
    fun `v3 fixture ignores unknown fields and round trips current fields`() {
        val decoded = decodeRules(resource("rules-v3.json"))

        assertEquals(
            listOf(
                SignalRule(
                    id = 12L,
                    name = "Stable package rule",
                    app = "Messages",
                    appPackageName = "com.google.android.apps.messaging",
                    phrase = "urgent",
                    action = "Mute",
                ),
            ),
            decoded,
        )
        assertEquals(decoded, decodeRules(encodeRules(decoded.orEmpty())))
        // A store written before schedules existed reads with none, so the rule keeps matching at
        // any time rather than being limited to a window nobody chose.
        assertNull("a v3 rule has no schedule", decoded?.single()?.schedule)
        // A store from a build that knows more than this one is refused rather than half-read. The
        // number moved because version 5 is this build's own; the guard is unchanged.
        assertNull(decodeRules("{\"version\":6,\"rules\":[]}"))
    }

    @Test
    fun v4FreeStringExtrasRemainVisibleAndUnsupportedAfterMigration() {
        val decoded = decodeRules(resource("rules-v4.json"))

        assertEquals(listOf("Image", "Phone number"), decoded?.single()?.extras)
        assertTrue(decoded?.single()?.metadataConditions?.isEmpty() == true)
        assertEquals(decoded, decodeRules(encodeRules(decoded.orEmpty())))
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("migrations/$name"))
            .use { it.readBytes().toString(StandardCharsets.UTF_8) }
}
