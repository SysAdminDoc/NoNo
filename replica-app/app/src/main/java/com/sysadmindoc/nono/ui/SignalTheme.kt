package com.sysadmindoc.nono.ui

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class SignalPalette(
    val background: Color,
    val surface: Color,
    val surfaceSelected: Color,
    val yellow: Color,
    val white: Color,
    val secondary: Color,
    val muted: Color,
    /** Decorative hairline: card edges and dividers, always beside a fill that already separates them. */
    val border: Color,
    /**
     * The outline of a control whose boundary is the only thing identifying it, such as a text
     * field. WCAG 2.2 1.4.11 asks for 3:1 there, which [border] does not meet and is not required
     * to: a card edge carries no information a sighted user needs to operate anything.
     */
    val controlOutline: Color,
    val error: Color,
    val ruleBlue: Color,
    val ruleDisabled: Color,
    val suggestionGreen: Color,
    val suggestionPurple: Color,
)

/** Exposed for the contrast test, which has to read the real values the app ships. */
internal val DarkPalette = SignalPalette(
    background = Color(0xFF050607), surface = Color(0xFF121417), surfaceSelected = Color(0xFF1B1D18),
    yellow = Color(0xFFF4F45D), white = Color(0xFFF7F7F3), secondary = Color(0xFFB5B7C1),
    muted = Color(0xFF858892), border = Color(0xFF383B42), controlOutline = Color(0xFF6C6F78),
    error = Color(0xFFFF6B76),
    ruleBlue = Color(0xFF9ADAF5), ruleDisabled = Color(0xFF4B4E56),
    suggestionGreen = Color(0xFF85D69B), suggestionPurple = Color(0xFFB9A5FF),
)

internal val LightPalette = SignalPalette(
    background = Color(0xFFF6F6F1), surface = Color(0xFFFFFFFF), surfaceSelected = Color(0xFFF0F0CF),
    yellow = Color(0xFF5B5C00), white = Color(0xFF171817), secondary = Color(0xFF52545A),
    muted = Color(0xFF676A72), border = Color(0xFFC6C8CC), controlOutline = Color(0xFF83868D),
    error = Color(0xFFB42330),
    ruleBlue = Color(0xFF006685), ruleDisabled = Color(0xFF858891),
    suggestionGreen = Color(0xFF1E6F3A), suggestionPurple = Color(0xFF6049A9),
)

object SignalColors {
    var Background by mutableStateOf(DarkPalette.background)
    var Surface by mutableStateOf(DarkPalette.surface)
    var SurfaceSelected by mutableStateOf(DarkPalette.surfaceSelected)
    var Yellow by mutableStateOf(DarkPalette.yellow)
    var White by mutableStateOf(DarkPalette.white)
    var Secondary by mutableStateOf(DarkPalette.secondary)
    var Muted by mutableStateOf(DarkPalette.muted)
    var Border by mutableStateOf(DarkPalette.border)
    var ControlOutline by mutableStateOf(DarkPalette.controlOutline)
    var Error by mutableStateOf(DarkPalette.error)
    var RuleBlue by mutableStateOf(DarkPalette.ruleBlue)
    var RuleDisabled by mutableStateOf(DarkPalette.ruleDisabled)
    var SuggestionGreen by mutableStateOf(DarkPalette.suggestionGreen)
    var SuggestionPurple by mutableStateOf(DarkPalette.suggestionPurple)

    fun apply(palette: SignalPalette) {
        Background = palette.background; Surface = palette.surface; SurfaceSelected = palette.surfaceSelected
        Yellow = palette.yellow; White = palette.white; Secondary = palette.secondary; Muted = palette.muted
        Border = palette.border; ControlOutline = palette.controlOutline; Error = palette.error; RuleBlue = palette.ruleBlue; RuleDisabled = palette.ruleDisabled
        SuggestionGreen = palette.suggestionGreen; SuggestionPurple = palette.suggestionPurple
    }
}

/**
 * Accents the platform derived from the wallpaper, best first.
 *
 * Primary is what Material You means by the accent; the container and tertiary roles are offered
 * after it because primary is often too close to the surface to be readable, and a second-choice
 * wallpaper colour is still the user's wallpaper. All of them are filtered on contrast before use.
 */
private fun wallpaperAccentCandidates(context: android.content.Context, dark: Boolean): List<Color> {
    if (Build.VERSION.SDK_INT < 31) return emptyList()
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer)
}

private val SignalTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
)

/**
 * The theme choices this device can offer.
 *
 * Dynamic colour needs the platform to derive a scheme from the wallpaper, which arrived in API
 * 31. Below that the option is not shown at all rather than shown and quietly ignored.
 */
fun themeCatalog(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = buildList {
    add("Dark")
    add("Light")
    add("System default")
    if (sdkInt >= 31) add(DYNAMIC_THEME)
}

const val DYNAMIC_THEME = "Match my wallpaper"

/**
 * A bar style that reads [dark] rather than the system's night mode.
 *
 * `SystemBarStyle.light(scrim, darkScrim)` picks its scrim from the same predicate it uses for the
 * icon appearance, so passing [scrim] twice makes both halves of the answer come from the app's
 * theme setting. Someone running the app in Light on a phone in dark mode gets a light bar with
 * dark icons, which is what the rest of the screen looks like.
 */
internal fun systemBarStyle(dark: Boolean, scrim: Int): SystemBarStyle =
    if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim)

@Composable
fun SignalTheme(theme: String = "Dark", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "Light" -> false
        "System default", DYNAMIC_THEME -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }
    val context = LocalContext.current
    val basePalette = if (dark) DarkPalette else LightPalette
    // A stored choice of Dynamic survives a downgrade or a restore onto an older phone, so the
    // level is checked here as well as in the list the settings screen offers.
    val palette = if (theme == DYNAMIC_THEME && Build.VERSION.SDK_INT >= 31) {
        basePalette.withDynamicAccent(wallpaperAccentCandidates(context, dark))
    } else {
        basePalette
    }
    // Picked rather than fixed. A wallpaper accent can land anywhere on the lightness range, and
    // the value that suits the built-in yellow puts dark text on a dark button as soon as it moves.
    val onAccent = onAccentFor(palette.yellow, listOf(palette.background, palette.white))
    val view = LocalView.current
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.yellow, onPrimary = onAccent, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            surfaceVariant = palette.surfaceSelected, onSurfaceVariant = palette.secondary,
            secondary = palette.ruleBlue, outline = palette.controlOutline, error = palette.error,
        )
    } else {
        lightColorScheme(
            // onPrimary sits on the accent, so it is whichever of the two palette values reads
            // against it. It used to take `white`, which in the light palette is near-black text,
            // and put dark text on a dark button.
            primary = palette.yellow, onPrimary = onAccent, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            surfaceVariant = palette.surfaceSelected, onSurfaceVariant = palette.secondary,
            secondary = palette.ruleBlue, outline = palette.controlOutline, error = palette.error,
        )
    }
    // The system bars follow the app's own theme, not the system's. Their scrim is set here rather
    // than once in onCreate, because a scrim fixed at the dark palette's near-black left the Light
    // theme asking for dark icons on a near-black bar, which on a three-button device is close to
    // invisible. Window.setNavigationBarColor is a no-op at this targetSdk, so the scrim has to go
    // through enableEdgeToEdge, which is safe to call again.
    val componentActivity = view.context as? ComponentActivity
    val barScrim = palette.background.toArgb()
    DisposableEffect(componentActivity, dark, barScrim) {
        componentActivity?.enableEdgeToEdge(
            statusBarStyle = systemBarStyle(dark, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = systemBarStyle(dark, barScrim),
        )
        onDispose {}
    }
    SideEffect {
        SignalColors.apply(palette)
        val activity = view.context as? Activity
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = SignalTypography, content = content)
}
