package com.kavach.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * CompositionLocal to provide theme-aware colors that aren't part of Material3 scheme.
 */
data class KavachColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val card: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean,
    val primaryAccent: Color = if (isDark) KavachGold else Color(0xFF00758F),
    val primaryDim: Color = if (isDark) KavachGoldDim else Color(0xFF00758F),
    val warningAmber: Color = if (isDark) CyberAmber else LightAmber
)

val LocalKavachColors = compositionLocalOf {
    KavachColors(
        background = DarkBackground,
        surface = DarkSurface,
        surfaceElevated = DarkSurfaceElevated,
        card = DarkCard,
        cardBorder = DarkCardBorder,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textMuted = TextMuted,
        isDark = true,
        primaryAccent = KavachGold,
        primaryDim = KavachGoldDim,
        warningAmber = CyberAmber
    )
}

private val KavachDarkColorScheme = darkColorScheme(
    primary = KavachGold,
    onPrimary = DarkBackground,
    primaryContainer = KavachGoldDim,
    onPrimaryContainer = KavachGoldLight,
    secondary = CyberPurple,
    onSecondary = DarkBackground,
    secondaryContainer = CyberPurpleDim,
    onSecondaryContainer = CyberPurple,
    tertiary = CyberAmber,
    onTertiary = DarkBackground,
    tertiaryContainer = CyberAmberDim,
    onTertiaryContainer = CyberAmber,
    error = CyberRed,
    onError = DarkBackground,
    errorContainer = CyberRedDim,
    onErrorContainer = CyberRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder,
    outlineVariant = TextMuted
)

private val KavachLightColorScheme = lightColorScheme(
    primary = Color(0xFF00758F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF00758F),
    secondary = CyberPurple,
    onSecondary = LightBackground,
    secondaryContainer = CyberPurpleDim,
    onSecondaryContainer = CyberPurple,
    tertiary = CyberAmber,
    onTertiary = LightBackground,
    tertiaryContainer = CyberAmberDim,
    onTertiaryContainer = CyberAmber,
    error = CyberRed,
    onError = LightBackground,
    errorContainer = CyberRedDim,
    onErrorContainer = CyberRed,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    outlineVariant = LightTextMuted
)

@Composable
fun KavachTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) KavachDarkColorScheme else KavachLightColorScheme

    val kavachColors = if (isDarkMode) {
        KavachColors(
            background = DarkBackground,
            surface = DarkSurface,
            surfaceElevated = DarkSurfaceElevated,
            card = DarkCard,
            cardBorder = DarkCardBorder,
            textPrimary = TextPrimary,
            textSecondary = TextSecondary,
            textMuted = TextMuted,
            isDark = true,
            primaryAccent = KavachGold,
            primaryDim = KavachGoldDim,
            warningAmber = CyberAmber
        )
    } else {
        KavachColors(
            background = LightBackground,
            surface = LightSurface,
            surfaceElevated = LightSurfaceElevated,
            card = LightCard,
            cardBorder = LightCardBorder,
            textPrimary = LightTextPrimary,
            textSecondary = LightTextSecondary,
            textMuted = LightTextMuted,
            isDark = false,
            primaryAccent = Color(0xFF00758F),
            primaryDim = Color(0xFF00758F),
            warningAmber = LightAmber
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgColor = kavachColors.background.toArgb()
            @Suppress("DEPRECATION")
            window.statusBarColor = bgColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = bgColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkMode
                isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }

    CompositionLocalProvider(LocalKavachColors provides kavachColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
