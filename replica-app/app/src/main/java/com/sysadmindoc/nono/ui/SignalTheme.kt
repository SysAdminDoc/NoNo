package com.sysadmindoc.nono.ui

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
    background = Color(0xFF0A0B0D), surface = Color(0xFF1A1C21), surfaceSelected = Color(0xFF1D1D17),
    yellow = Color(0xFFFFF387), white = Color(0xFFFFFFFF), secondary = Color(0xFF858586),
    muted = Color(0xFF9C9EA3), border = Color(0xFF3F414B), error = Color(0xFFFF7070),
    ruleBlue = Color(0xFF93D1F3), ruleDisabled = Color(0xFF3F414B),
    suggestionGreen = Color(0xFF80DB94), suggestionPurple = Color(0xFFA16FFF),
)

private val LightPalette = SignalPalette(
    background = Color(0xFFF9F9FB), surface = Color(0xFFFFFFFF), surfaceSelected = Color(0xFFFFF9C4),
    yellow = Color(0xFF675F00), white = Color(0xFF1A1B20), secondary = Color(0xFF4D4D52),
    muted = Color(0xFF5E5E65), border = Color(0xFF77777F), error = Color(0xFFBA1A1A),
    ruleBlue = Color(0xFF005F7A), ruleDisabled = Color(0xFF77777F),
    suggestionGreen = Color(0xFF006E2C), suggestionPurple = Color(0xFF5B38A8),
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
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun SignalTheme(theme: String = "Dark", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "Light" -> false
        "System default" -> androidx.compose.foundation.isSystemInDarkTheme()
        else -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.yellow, onPrimary = palette.background, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            secondary = palette.ruleBlue, error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.yellow, onPrimary = palette.white, background = palette.background,
            onBackground = palette.white, surface = palette.surface, onSurface = palette.white,
            secondary = palette.ruleBlue, error = palette.error,
        )
    }
    SideEffect { SignalColors.apply(palette) }
    MaterialTheme(colorScheme = scheme, typography = SignalTypography, content = content)
}
