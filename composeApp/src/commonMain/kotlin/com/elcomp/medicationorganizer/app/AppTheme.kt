package com.elcomp.medicationorganizer.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import com.elcomp.medicationorganizer.features.profile.data.AccessibilityPreferences

@Composable
internal fun AppTheme(
    preferences: AccessibilityPreferences = AccessibilityPreferences.Default,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val colors = remember(dark, preferences.highContrast) {
        when {
            dark && preferences.highContrast -> highContrastDark
            preferences.highContrast -> highContrastLight
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    // Scale the typography itself so Android and iOS still apply the system text conversion.
    val typography = remember(preferences.textScale) {
        val default = Typography()
        if (preferences.textScale == 100) default else default.scaled(preferences.textScale / 100f)
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

private fun Typography.scaled(factor: Float) = copy(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)

private fun TextStyle.scaled(factor: Float) = copy(
    fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
)

private val highContrastLight = lightColorScheme(
    primary = Color(0xFF002C65),
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E8F4),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color.Black,
    surfaceTint = Color.Transparent,
    outline = Color.Black,
    error = Color(0xFF8B0016),
    onError = Color.White,
)

private val highContrastDark = darkColorScheme(
    primary = Color(0xFFFFE97A),
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF292929),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color.White,
    surfaceTint = Color.Transparent,
    outline = Color.White,
    error = Color(0xFFFFB8B8),
    onError = Color.Black,
)
