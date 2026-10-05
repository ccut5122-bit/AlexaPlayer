package com.alexaplayer.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alexaplayer.core.model.ThemeMode

/** Access to the parts of the palette that Material's scheme does not expose. */
object AlexaTheme {
    val extended: AlexaExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

private val ExtendedDark = AlexaExtendedColors(
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

private val ExtendedAmoled = ExtendedDark.copy(
    isAmoled = true,
    divider = Color(0xFF16191F),
    elevatedBorder = Color(0xFF1C2029),
    shimmerBase = Color(0xFF0E1016),
    shimmerHighlight = Color(0xFF191C24),
)

private val ExtendedLight = AlexaExtendedColors(
    accentCyan = Color(0xFF00A8CC),
    accentBlue = Color(0xFF2E6BE0),
    accentViolet = Color(0xFF6D4DE6),
    textTertiary = TextTertiaryLight,
    divider = LightDivider,
    success = Color(0xFF12855C),
    warning = Color(0xFFB26A00),
    isDark = false,
    isAmoled = false,
    elevatedBorder = LightOutline,
    favoriteActive = Color(0xFFD81B60),
    shimmerBase = Color(0xFFE7EAF2),
    shimmerHighlight = Color(0xFFF6F7FB),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Root theme. Dark by default because that is how people listen to music, with an
 * AMOLED variant that removes the last light from the screen.
 */
@Composable
fun AlexaPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark && amoled -> AlexaAmoledColorScheme
        dark -> AlexaDarkColorScheme
        else -> AlexaLightColorScheme
    }

    val extended = when {
        useDynamic || !dark -> ExtendedLight.copy(
            isAmoled = false,
            accentCyan = colorScheme.primary,
            accentBlue = colorScheme.tertiary,
            accentViolet = colorScheme.secondary,
        )
        amoled -> ExtendedAmoled
        else -> ExtendedDark
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlexaTypography,
            shapes = AlexaShapes,
            content = content,
        )
    }
}
