package com.fesu.renjana.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

private val RenjanaLightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnBackground,
    tertiary = LightPrimary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightPrimaryContainer,
    onTertiaryContainer = LightOnPrimaryContainer,
    error = LightError,
    errorContainer = LightErrorContainer,
    onError = Color.White,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val RenjanaDarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnBackground,
    tertiary = DarkPrimary,
    onTertiary = DarkOnPrimary,
    tertiaryContainer = DarkPrimaryContainer,
    onTertiaryContainer = DarkOnPrimaryContainer,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    onError = Color.Black,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

/**
 * RenjanaTheme - "Stealth Container" design system.
 *
 * @param darkTheme True for OLED true-black dark mode
 * @param dynamicColor Always false — Renjana has its own identity, not Material You
 * @param accentColor Override primary color (for Settings accent picker). Null = default blue.
 */
@Composable
fun RenjanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) RenjanaDarkColors else RenjanaLightColors
    val colorScheme = if (accentColor != null) {
        val accentContainer = accentColor.copy(alpha = if (darkTheme) 0.24f else 0.16f)
        val onAccent = contrastOn(accentColor)
        baseScheme.copy(
            primary = accentColor,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            tertiary = accentColor,
            onTertiary = onAccent,
            tertiaryContainer = accentContainer
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RenjanaTypography,
        shapes = RenjanaShapes,
        content = content
    )
}

private fun contrastOn(background: Color): Color {
    val whiteContrast = contrastRatio(background, Color.White)
    val blackContrast = contrastRatio(background, Color.Black)
    return if (whiteContrast >= blackContrast) Color.White else Color.Black
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = relativeLuminance(foreground)
    val backgroundLuminance = relativeLuminance(background)
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) {
        value / 12.92f
    } else {
        ((value + 0.055f) / 1.055f).toFloat().pow(2.4)
    }

    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

private fun Float.pow(exponent: Double): Float = java.lang.Math.pow(this.toDouble(), exponent).toFloat()
