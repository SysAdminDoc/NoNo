package com.sysadmindoc.nono.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.2 contrast for normal text. */
const val TEXT_CONTRAST_MINIMUM = 4.5

/** WCAG 2.2 contrast for a control boundary or a focus indicator. */
const val COMPONENT_CONTRAST_MINIMUM = 3.0

/** Relative luminance, as WCAG defines it. */
fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** The contrast ratio between two colours, from 1:1 to 21:1. */
fun contrastRatio(foreground: Color, background: Color): Double {
    val a = relativeLuminance(foreground)
    val b = relativeLuminance(background)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/**
 * Chooses an accent from the wallpaper, or keeps the built-in one.
 *
 * Material You derives its colours from whatever the user's wallpaper happens to be, and a
 * wallpaper is not designed against a contrast requirement. Applying the derived accent
 * unconditionally would produce a build that passes its own contrast tests on the developer's
 * phone and fails on somebody else's, silently, in the one part of the app that carries meaning:
 * the accent is used for the enabled state, for counts, and for the primary button.
 *
 * So the wallpaper is treated as a suggestion. The candidates are tried in order and the first one
 * that clears the text threshold against every surface the accent is drawn on wins. If none does,
 * the built-in accent stays, which is the same app the user had before they turned this on.
 *
 * @param candidates accent colours from the platform's dynamic scheme, best first.
 * @param surfaces every background the accent is drawn against.
 * @param fallback the palette's own accent, which is known to pass.
 */
fun dynamicAccentOrFallback(
    candidates: List<Color>,
    surfaces: List<Color>,
    fallback: Color,
    minimum: Double = TEXT_CONTRAST_MINIMUM,
): Color = candidates.firstOrNull { candidate ->
    surfaces.all { surface -> contrastRatio(candidate, surface) >= minimum }
} ?: fallback

/**
 * The colour drawn on top of the accent, such as the label of the primary button.
 *
 * Picked rather than derived, from the two the palette already contains, because a wallpaper accent
 * can land anywhere on the lightness range and the fixed choice that suits the built-in yellow puts
 * dark text on a dark button as soon as the accent changes.
 */
fun onAccentFor(accent: Color, candidates: List<Color>): Color =
    candidates.maxByOrNull { contrastRatio(it, accent) } ?: candidates.first()

/**
 * Replaces a palette's accent with one derived from the wallpaper, when one is usable.
 *
 * Only the accent moves. Backgrounds, text and outlines stay as they are: those are the values the
 * contrast tests cover, and letting the wallpaper decide them would put the readability of every
 * screen at the mercy of a photograph.
 */
fun SignalPalette.withDynamicAccent(candidates: List<Color>): SignalPalette {
    val surfaces = listOf(background, surface, surfaceSelected)
    val accent = dynamicAccentOrFallback(candidates, surfaces, yellow)
    return copy(yellow = accent)
}
