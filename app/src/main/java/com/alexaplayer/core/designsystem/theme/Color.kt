package com.alexaplayer.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Alexa Player palette.
 *
 * The interface is deliberately restrained: near-black surfaces carry the UI, and the
 * brand accents (cyan -> violet) are reserved for active controls, progress and
 * selection so that colour always means "this is live".
 */

// Brand accents ------------------------------------------------------------------
internal val Cyan = Color(0xFF3ED0F0)
internal val CyanBright = Color(0xFF6BEEFF)
internal val Blue = Color(0xFF2E86FF)
internal val Violet = Color(0xFF9B7BFF)

// Dark surfaces ------------------------------------------------------------------
internal val DarkBackground = Color(0xFF0B0D14)
internal val DarkAmoledBackground = Color(0xFF000000)
internal val DarkSurface = Color(0xFF12151F)
internal val DarkSurfaceElevated = Color(0xFF1A1E2B)
internal val DarkSurfaceContainer = Color(0xFF161A26)
internal val DarkOutline = Color(0xFF262B3A)
internal val DarkDivider = Color(0xFF1D2130)

// Light surfaces -----------------------------------------------------------------
internal val LightBackground = Color(0xFFF7F8FC)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceElevated = Color(0xFFFFFFFF)
internal val LightSurfaceContainer = Color(0xFFEFF1F7)
internal val LightOutline = Color(0xFFD9DEE9)
internal val LightDivider = Color(0xFFE6E9F1)

// Shared text --------------------------------------------------------------------
internal val TextPrimaryDark = Color(0xFFF2F4F8)
internal val TextSecondaryDark = Color(0xFFA9B0C2)
internal val TextTertiaryDark = Color(0xFF798196)
internal val TextPrimaryLight = Color(0xFF101420)
internal val TextSecondaryLight = Color(0xFF4C5468)
internal val TextTertiaryLight = Color(0xFF6C7488)

// Status ------------------------------------------------------------------------
internal val ErrorDark = Color(0xFFFF8A8A)
internal val ErrorLight = Color(0xFFC0392B)
internal val Success = Color(0xFF3DDC97)
internal val Warning = Color(0xFFFFC24B)

val AlexaDarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00232C),
    primaryContainer = Color(0xFF0E3A46),
    onPrimaryContainer = CyanBright,
    secondary = Violet,
    onSecondary = Color(0xFF1E1233),
    secondaryContainer = Color(0xFF2A2050),
    onSecondaryContainer = Color(0xFFCFC0FF),
    tertiary = Blue,
    onTertiary = Color(0xFF001B33),
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = Color(0xFF212636),
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = Color(0xFF090B11),
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    error = ErrorDark,
    onError = Color(0xFF3A0909),
    scrim = Color(0xFF000000),
)

/** AMOLED variant: identical hierarchy, pure black floor. */
val AlexaAmoledColorScheme = AlexaDarkColorScheme.copy(
    background = DarkAmoledBackground,
    surface = Color(0xFF05060A),
    surfaceContainerLow = Color(0xFF05060A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainer = Color(0xFF0D1017),
    surfaceContainerHigh = Color(0xFF12151F),
    surfaceContainerHighest = Color(0xFF181C28),
)

val AlexaLightColorScheme = lightColorScheme(
    primary = Color(0xFF0087B0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEF9),
    onPrimaryContainer = Color(0xFF003544),
    secondary = Color(0xFF6D4DE6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7DEFF),
    onSecondaryContainer = Color(0xFF23005C),
    tertiary = Color(0xFF2E6BE0),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = Color(0xFFE7EAF2),
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = Color.White,
    outline = LightOutline,
    outlineVariant = LightDivider,
    error = ErrorLight,
    onError = Color.White,
    scrim = Color(0xFF000000),
)

/**
 * Colours that Material's scheme has no slot for, exposed once so that components can
 * stay consistent instead of each component inventing its own accent.
 */
@Immutable
data class AlexaExtendedColors(
    val accentCyan: Color,
    val accentBlue: Color,
    val accentViolet: Color,
    val textTertiary: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val isDark: Boolean,
    val isAmoled: Boolean,
    /** Subtle sheen used on cards and sheets that sit on top of scrolling content. */
    val elevatedBorder: Color,
    val favoriteActive: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    AlexaExtendedColors(
        accentCyan = Cyan,
        accentBlue = Blue,
        accentViolet = Violet,
        textTertiary = TextTertiaryDark,
        divider = DarkDivider,
        success = Success,
        warning = Warning,
        isDark = true,
        isAmoled = false,
        elevatedBorder = DarkOutline,
        favoriteActive = Color(0xFFFF6B95),
        shimmerBase = Color(0xFF171B27),
        shimmerHighlight = Color(0xFF232838),
    )
}
