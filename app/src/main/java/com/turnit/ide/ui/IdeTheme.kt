package com.turnit.ide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

// =========================================================================
// TurnIt-IDE TERMINAL COLOUR PALETTE
// Built for dark ambience, ANSI contrast, and long coding sessions.
// =========================================================================

object IdeColors {
    // Base
    val Bg            = Color(0xFF0D1117)  // GitHub dark
    val BgSurface     = Color(0xFF161B22)  // panel surface
    val BgElevated    = Color(0xFF21262D)  // elevated card
    val BgInput       = Color(0xFF0D1117)

    // Accents
    val AccentGreen   = Color(0xFF3FB950)  // build success / run
    val AccentBlue    = Color(0xFF58A6FF)  // links / selection
    val AccentOrange  = Color(0xFFD29922)  // warnings
    val AccentRed     = Color(0xFFF85149)  // errors / stop
    val AccentPurple  = Color(0xFFBC8CFF)  // variables / types
    val AccentCyan    = Color(0xFF39C5CF)  // strings / info

    // Text
    val TextPrimary   = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)
    val TextMuted     = Color(0xFF484F58)

    // Borders
    val Border        = Color(0xFF30363D)
    val BorderFocus   = Color(0xFF58A6FF)

    // Terminal ANSI base
    val TermBg        = Color(0xFF0A0C10)
    val TermGreen     = Color(0xFF4ADE80)
    val TermYellow    = Color(0xFFFBBF24)
    val TermRed       = Color(0xFFF87171)
    val TermCyan      = Color(0xFF22D3EE)
    val TermWhite     = Color(0xFFE5E7EB)

    // Progress
    val ProgressTrack = Color(0xFF21262D)
    val ProgressFill  = Color(0xFF3FB950)
}

val IdeColorScheme = darkColorScheme(
    primary            = IdeColors.AccentBlue,
    onPrimary          = IdeColors.Bg,
    primaryContainer   = IdeColors.BgElevated,
    onPrimaryContainer = IdeColors.AccentBlue,
    secondary          = IdeColors.AccentGreen,
    onSecondary        = IdeColors.Bg,
    background         = IdeColors.Bg,
    onBackground       = IdeColors.TextPrimary,
    surface            = IdeColors.BgSurface,
    onSurface          = IdeColors.TextPrimary,
    surfaceVariant     = IdeColors.BgElevated,
    onSurfaceVariant   = IdeColors.TextSecondary,
    outline            = IdeColors.Border,
    error              = IdeColors.AccentRed
)

// Monospace: JetBrains Mono or system fallback.
// Drop jetbrains_mono_regular.ttf into res/font/ to activate.
val MonoFamily = FontFamily.Monospace

val IdeTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize   = 14.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize   = 13.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MonoFamily,
        fontSize   = 12.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize   = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.05.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontSize   = 10.sp,
        letterSpacing = 0.04.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize   = 15.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize   = 13.sp,
        fontWeight = FontWeight.Medium
    )
)

@Composable
fun TurnItIdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IdeColorScheme,
        typography  = IdeTypography,
        content     = content
    )
}
