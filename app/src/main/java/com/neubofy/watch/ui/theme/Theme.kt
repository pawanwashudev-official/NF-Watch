package com.neubofy.watch.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════
//  NF Watch — Black & Gold Premium Theme
//  Sand-scattered luxury with glassmorphism
// ═══════════════════════════════════════════

// Gold palette
val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFE8D48B)
val GoldDark = Color(0xFFB8860B)
val GoldAccent = Color(0xFFFFC107)
val GoldMuted = Color(0xFF8B7D3C)

// Base surfaces remain consistent
val SurfaceBlack = Color(0xFF0A0A0C)
val SurfaceDark = Color(0xFF111114)
val SurfaceElevated = Color(0xFF1A1A1F)
val SurfaceCard = Color(0xFF1E1E24)
val SurfaceGlass = Color(0xFF222229)

// Text
val TextPrimary = Color(0xFFF5F0E5)
val TextSecondary = Color(0xFFB0A98E)
val TextMuted = Color(0xFF7A7565)

// System accents
val AccentRed = Color(0xFFFF6B6B)
val AccentTeal = Color(0xFF03DAC6)
val AccentPurple = Color(0xFF9C7CFF)
val AccentBlue = Color(0xFF2196F3)
val AccentPink = Color(0xFFE91E63)

object AccentColors {
    val Gold = Color(0xFFD4AF37)
    val Teal = Color(0xFF009688)
    val Purple = Color(0xFF651FFF)
    val Blue = Color(0xFF2962FF)
    val Red = Color(0xFFD50000)
    val Green = Color(0xFF00C853)
    val Orange = Color(0xFFFF6D00)
    val Pink = Color(0xFFC51162)

    fun fromName(name: String): Color {
        return when (name) {
            "Teal" -> Teal
            "Purple" -> Purple
            "Blue" -> Blue
            "Red" -> Red
            "Green" -> Green
            "Orange" -> Orange
            "Pink" -> Pink
            else -> Gold
        }
    }
}

fun createDarkScheme(accentColor: Color) = darkColorScheme(
    primary = accentColor,
    onPrimary = Color.Black,
    primaryContainer = accentColor.copy(alpha = 0.2f),
    onPrimaryContainer = accentColor.copy(alpha = 0.8f),
    background = SurfaceBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = accentColor.copy(alpha = 0.3f),
    error = AccentRed
)

fun createLightScheme(accentColor: Color) = lightColorScheme(
    primary = accentColor,
    onPrimary = Color.White,
    primaryContainer = accentColor.copy(alpha = 0.1f),
    background = Color(0xFFFAF6ED),
    onBackground = Color(0xFF1A1A0F),
    surface = Color.White,
    onSurface = Color(0xFF1A1A0F),
    surfaceVariant = Color(0xFFF5F0E2),
    onSurfaceVariant = Color(0xFF5A553C),
    outline = accentColor.copy(alpha = 0.3f),
)

private val NfWatchTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        color = Color.Unspecified
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
)

@Composable
fun NFWatchTheme(
    darkTheme: Boolean = true, // Always dark for premium feel
    accentName: String = "Gold",
    content: @Composable () -> Unit
) {
    val accent = AccentColors.fromName(accentName)
    val colorScheme = if (darkTheme) createDarkScheme(accent) else createLightScheme(accent)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NfWatchTypography,
        content = content
    )
}
