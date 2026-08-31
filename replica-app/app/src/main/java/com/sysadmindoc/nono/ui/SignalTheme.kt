package com.sysadmindoc.nono.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class SignalPalette(
    val background: Color,
    val surface: Color,
    val surfaceSelected: Color,
    val yellow: Color,
    val white: Color,
    val secondary: Color,
    val muted: Color,
    val border: Color,
    val error: Color,
    val ruleBlue: Color,
    val ruleDisabled: Color,
    val suggestionGreen: Color,
    val suggestionPurple: Color,
)

private val DarkPalette = SignalPalette(
    background = Color(0xFF050607), surface = Color(0xFF121417), surfaceSelected = Color(0xFF1B1D18),
    yellow = Color(0xFFF4F45D), white = Color(0xFFF7F7F3), secondary = Color(0xFFB5B7C1),
    muted = Color(0xFF858892), border = Color(0xFF383B42), error = Color(0xFFFF6B76),
    ruleBlue = Color(0xFF9ADAF5), ruleDisabled = Color(0xFF4B4E56),
    suggestionGreen = Color(0xFF85D69B), suggestionPurple = Color(0xFFB9A5FF),
)

private val LightPalette = SignalPalette(
    background = Color(0xFFF6F6F1), surface = Color(0xFFFFFFFF), surfaceSelected = Color(0xFFF0F0CF),
    yellow = Color(0xFF5B5C00), white = Color(0xFF171817), secondary = Color(0xFF52545A),
    muted = Color(0xFF676A72), border = Color(0xFFC6C8CC), error = Color(0xFFB42330),
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
    var Error by mutableStateOf(DarkPalette.error)
    var RuleBlue by mutableStateOf(DarkPalette.ruleBlue)
    var RuleDisabled by mutableStateOf(DarkPalette.ruleDisabled)
    var SuggestionGreen by mutableStateOf(DarkPalette.suggestionGreen)
    var SuggestionPurple by mutableStateOf(DarkPalette.suggestionPurple)

    fun apply(palette: SignalPalette) {
        Background = palette.background; Surface = palette.surface; SurfaceSelected = palette.surfaceSelected
        Yellow = palette.yellow; White = palette.white; Secondary = palette.secondary; Muted = palette.muted
        Border = palette.border; Error = palette.error; RuleBlue = palette.ruleBlue; RuleDisabled = palette.ruleDisabled
        SuggestionGreen = palette.suggestionGreen; SuggestionPurple = palette.suggestionPurple
    }
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

@Composable
fun SignalTheme(theme: String = "Dark", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "Light" -> false
        "System default" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    val view = LocalView.current
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.yellow, onPrimary = palette.background, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            surfaceVariant = palette.surfaceSelected, onSurfaceVariant = palette.secondary,
            secondary = palette.ruleBlue, outline = palette.border, error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.yellow, onPrimary = palette.white, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            surfaceVariant = palette.surfaceSelected, onSurfaceVariant = palette.secondary,
            secondary = palette.ruleBlue, outline = palette.border, error = palette.error,
        )
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
