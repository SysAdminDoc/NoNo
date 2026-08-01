package com.anm.signalrules.reconstruction.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SignalColors {
    val Background = Color(0xFF0A0B0D)
    val Surface = Color(0xFF1A1C21)
    val SurfaceSelected = Color(0xFF1D1D17)
    val Yellow = Color(0xFFFFF387)
    val White = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF858586)
    val Muted = Color(0xFF535559)
    val Border = Color(0xFF3F414B)
    val Error = Color(0xFFFF7070)
    val RuleBlue = Color(0xFF93D1F3)
    val RuleDisabled = Color(0xFF3F414B)
    val SuggestionGreen = Color(0xFF80DB94)
    val SuggestionPurple = Color(0xFFA16FFF)
}

private val SignalScheme = darkColorScheme(
    primary = SignalColors.Yellow,
    onPrimary = SignalColors.Background,
    background = SignalColors.Background,
    onBackground = SignalColors.White,
    surface = SignalColors.Surface,
    onSurface = SignalColors.White,
    secondary = SignalColors.RuleBlue,
    error = SignalColors.Error,
)

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
fun SignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SignalScheme, typography = SignalTypography, content = content)
}
