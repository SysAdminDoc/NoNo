package com.sysadmindoc.nono.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wallpaper-derived accents.
 *
 * A wallpaper is not designed against a contrast requirement, so the accent it produces is treated
 * as a suggestion and checked before it is used. Without that, this feature would pass its own
 * tests on one phone and fail silently on another, in the part of the interface that carries
 * meaning: the accent marks a rule as enabled and labels the primary button.
 */
class DynamicAccentTest {

    private val darkSurfaces = listOf(DarkPalette.background, DarkPalette.surface, DarkPalette.surfaceSelected)
    private val lightSurfaces = listOf(LightPalette.background, LightPalette.surface, LightPalette.surfaceSelected)

    /**
     * Accents drawn from real Material You schemes.
     *
     * Each pair is a plausible wallpaper: a bright one, a muted one, a dark one, a saturated one,
     * and the two extremes. Between them they cover the range where the derived accent goes from
     * comfortably readable to unusable.
     */
    private val darkCandidates = listOf(
        "sand" to Color(0xFFE8C48F),
        "sage" to Color(0xFFB6CCA1),
        "slate" to Color(0xFF9FC8E8),
        "plum" to Color(0xFFD9B8E8),
        "deep teal" to Color(0xFF0B3B3C),
        "near black" to Color(0xFF101114),
    )

    private val lightCandidates = listOf(
        "ink" to Color(0xFF2A3A6B),
        "forest" to Color(0xFF14532D),
        "wine" to Color(0xFF6B1F3B),
        "pale sand" to Color(0xFFF3E4C4),
        "near white" to Color(0xFFFBFBFA),
    )

    @Test
    fun `the chosen accent always clears the text threshold on every surface`() {
        for (surfaces in listOf(darkSurfaces, lightSurfaces)) {
            val fallback = if (surfaces === darkSurfaces) DarkPalette.yellow else LightPalette.yellow
            val pool = if (surfaces === darkSurfaces) darkCandidates else lightCandidates
            for ((name, candidate) in pool) {
                val accent = dynamicAccentOrFallback(listOf(candidate), surfaces, fallback)
                for (surface in surfaces) {
                    val ratio = contrastRatio(accent, surface)
                    assertTrue(
                        "$name resolved to an accent at %.2f:1".format(ratio),
                        ratio >= TEXT_CONTRAST_MINIMUM,
                    )
                }
            }
        }
    }

    @Test
    fun `a wallpaper accent that cannot be read is refused, not softened`() {
        // Deep teal on the dark background is about 1.4:1. There is no adjustment that keeps this
        // the user's colour and makes it legible, so the built-in accent stays.
        val unusable = Color(0xFF0B3B3C)
        assertTrue(contrastRatio(unusable, DarkPalette.background) < TEXT_CONTRAST_MINIMUM)

        val accent = dynamicAccentOrFallback(listOf(unusable), darkSurfaces, DarkPalette.yellow)

        assertEquals(DarkPalette.yellow, accent)
    }

    @Test
    fun `a usable wallpaper accent is used`() {
        val sand = Color(0xFFE8C48F)
        assertEquals(sand, dynamicAccentOrFallback(listOf(sand), darkSurfaces, DarkPalette.yellow))
    }

    @Test
    fun `the first usable candidate wins, so a poor primary falls through to the next`() {
        // The platform's primary is often close to the surface. The rest of the scheme is still the
        // user's wallpaper, so it is tried before giving up on the idea.
        val tooDark = Color(0xFF101114)
        val usable = Color(0xFFB6CCA1)

        assertEquals(usable, dynamicAccentOrFallback(listOf(tooDark, usable), darkSurfaces, DarkPalette.yellow))
    }

    @Test
    fun `no candidates at all leaves the palette alone`() {
        assertEquals(
            DarkPalette,
            DarkPalette.withDynamicAccent(emptyList()),
        )
    }

    @Test
    fun `only the accent moves`() {
        val sand = Color(0xFFE8C48F)
        val themed = DarkPalette.withDynamicAccent(listOf(sand))

        assertEquals(sand, themed.yellow)
        // Everything the contrast tests cover is untouched: a photograph does not get to decide
        // whether the text on a screen can be read.
        assertEquals(DarkPalette.copy(yellow = sand), themed)
    }

    @Test
    fun `a component threshold is available for boundaries and focus rings`() {
        // 1.4.11 asks for 3:1 rather than 4.5:1 where the colour identifies a control rather than
        // spelling something out.
        val outline = dynamicAccentOrFallback(
            listOf(Color(0xFF7A8C6A)),
            darkSurfaces,
            DarkPalette.controlOutline,
            minimum = COMPONENT_CONTRAST_MINIMUM,
        )
        for (surface in darkSurfaces) {
            assertTrue(contrastRatio(outline, surface) >= COMPONENT_CONTRAST_MINIMUM)
        }
    }

    @Test
    fun `the label on the accent is picked, not fixed`() {
        val candidates = listOf(DarkPalette.background, DarkPalette.white)
        // A pale accent takes the dark label and a dark accent takes the pale one. A fixed choice
        // puts dark text on a dark button the moment the accent changes.
        assertEquals(DarkPalette.background, onAccentFor(Color(0xFFE8C48F), candidates))
        assertEquals(DarkPalette.white, onAccentFor(Color(0xFF2A3A6B), candidates))
        for (accent in listOf(Color(0xFFE8C48F), Color(0xFF2A3A6B), DarkPalette.yellow)) {
            assertTrue(contrastRatio(onAccentFor(accent, candidates), accent) >= TEXT_CONTRAST_MINIMUM)
        }
    }

    @Test
    fun `dynamic is offered only where the platform supports it`() {
        assertFalse("Match my wallpaper" in themeCatalog(sdkInt = 30))
        assertFalse(DYNAMIC_THEME in themeCatalog(sdkInt = 24))
        assertTrue(DYNAMIC_THEME in themeCatalog(sdkInt = 31))
        assertTrue(DYNAMIC_THEME in themeCatalog(sdkInt = 37))
    }

    @Test
    fun `the static choices are unchanged and still come first`() {
        assertEquals(listOf("Dark", "Light", "System default"), themeCatalog(sdkInt = 30))
        assertEquals(listOf("Dark", "Light", "System default", DYNAMIC_THEME), themeCatalog(sdkInt = 31))
    }

    @Test
    fun `contrast is measured the way WCAG defines it`() {
        // Anchors, so a wrong formula cannot pass the rest of this file by being wrong everywhere.
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.001)
        assertEquals(0.0, relativeLuminance(Color.Black), 0.0001)
        assertEquals(1.0, relativeLuminance(Color.White), 0.0001)
    }
}
