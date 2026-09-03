package com.sysadmindoc.nono.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.2 contrast over the palettes the app actually ships.
 *
 * Checked as arithmetic rather than by screenshot: a rendered capture proves one device at one
 * font scale, while the ratios are a property of the colours themselves and hold everywhere.
 */
class PaletteContrastTest {

    /** WCAG 1.4.3: normal-size text. */
    private val normalText = 4.5

    /** WCAG 1.4.11: a UI component or graphical object that carries meaning. */
    private val component = 3.0

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Double) {
        val measured = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below %.1f:1".format(measured, minimum),
            measured >= minimum,
        )
    }

    /** Every surface text can sit on, not just the page background. */
    private fun surfacesOf(palette: SignalPalette) = listOf(
        "background" to palette.background,
        "surface" to palette.surface,
        "selected surface" to palette.surfaceSelected,
    )

    private fun checkPalette(theme: String, palette: SignalPalette) {
        surfacesOf(palette).forEach { (surfaceName, surface) ->
            // Body and title text.
            assertContrast("$theme primary text on $surfaceName", palette.white, surface, normalText)
            assertContrast("$theme secondary text on $surfaceName", palette.secondary, surface, normalText)
            // Accent text: the yellow is used for labels and selected values, not decoration.
            assertContrast("$theme accent text on $surfaceName", palette.yellow, surface, normalText)
            assertContrast("$theme error text on $surfaceName", palette.error, surface, normalText)
            // Muted reads as body text in a dozen places - timestamps, the enabled/disabled label,
            // the bottom-navigation labels - so it is held to the text threshold, not the
            // component one it used to be checked against.
            assertContrast("$theme muted text on $surfaceName", palette.muted, surface, normalText)

            // Meaningful non-text: the outline that identifies a control, secondary icon tints,
            // and the accent used on rule cards. `border` is deliberately absent: it is a
            // decorative hairline beside a fill that already separates the two surfaces, and
            // WCAG 1.4.11 covers what a user needs in order to operate something.
            assertContrast("$theme control outline on $surfaceName", palette.controlOutline, surface, component)
            assertContrast("$theme rule accent on $surfaceName", palette.ruleBlue, surface, component)
        }
    }

    @Test
    fun theDarkPaletteMeetsWcagOnEverySurface() = checkPalette("dark", DarkPalette)

    @Test
    fun theLightPaletteMeetsWcagOnEverySurface() = checkPalette("light", LightPalette)

    @Test
    fun textOnTheAccentColourIsReadable() {
        // A filled primary button puts onPrimary on primary. Getting this pair wrong makes the
        // most prominent control on the screen the least readable thing on it.
        assertContrast("dark on-primary", DarkPalette.background, DarkPalette.yellow, normalText)
        assertContrast("light on-primary", LightPalette.background, LightPalette.yellow, normalText)
    }

    @Test
    fun theTwoThemesAreActuallyDifferent() {
        // Light mode kept dark hard-coded values for a while, which read as a broken theme rather
        // than a light one.
        assertTrue("light background must be lighter", luminance(LightPalette.background) > 0.5)
        assertTrue("dark background must be darker", luminance(DarkPalette.background) < 0.1)
    }

    @Test
    fun theSystemBarIconsAreLegibleOnTheScrimTheirThemeSupplies() {
        // Both bars take their theme's background as their scrim, and the icons come from the
        // platform: black where the app asks for light-appearance bars, white where it asks for
        // dark. Those two choices used to be made independently, so the Light theme asked for dark
        // icons over a scrim fixed at the dark palette's near-black. On a three-button device that
        // is a row of controls you cannot see.
        assertContrast("dark theme system bar icons", Color.White, DarkPalette.background, component)
        assertContrast("light theme system bar icons", Color.Black, LightPalette.background, component)
    }
}
