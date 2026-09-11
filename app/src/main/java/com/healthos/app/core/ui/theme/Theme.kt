package com.healthos.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// HealthOS palette: warm paper / deep charcoal-navy neutrals with a single
// considered accent (a muted training teal), used sparingly for emphasis.
private val LightBackground = Color(0xFFFAF7F2)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceHigh = Color(0xFFF1ECE3)
private val LightInk = Color(0xFF1C1F1D)
private val LightInkMuted = Color(0xFF5B615C)
private val Teal = Color(0xFF1F6F5C)
private val Gold = Color(0xFFB98A2E)

private val DarkBackground = Color(0xFF12151A)
private val DarkSurface = Color(0xFF1B1F26)
private val DarkSurfaceHigh = Color(0xFF242A33)
private val DarkInk = Color(0xFFEDEEF0)
private val DarkInkMuted = Color(0xFFA8AEB6)
private val TealDark = Color(0xFF52B79A)
private val GoldDark = Color(0xFFE0B25C)

private val HealthOSLightColors = lightColorScheme(
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightInkMuted,
    primary = Teal,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Color.White,
    error = Color(0xFFB3261E)
)

private val HealthOSDarkColors = darkColorScheme(
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkInkMuted,
    primary = TealDark,
    onPrimary = Color(0xFF00251E),
    secondary = GoldDark,
    onSecondary = Color(0xFF3A2A00),
    error = Color(0xFFFFB4AB)
)

private val DefaultTypography = Typography()
private val HealthOSTypography = DefaultTypography.copy(
    headlineLarge = DefaultTypography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    titleLarge = DefaultTypography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    titleMedium = DefaultTypography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    labelMedium = DefaultTypography.labelMedium.copy(letterSpacing = 0.2.sp),
    labelSmall = DefaultTypography.labelSmall.copy(letterSpacing = 0.2.sp)
)

val HealthOSShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HealthOSTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) HealthOSDarkColors else HealthOSLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = HealthOSTypography,
        shapes = HealthOSShapes,
        content = content
    )
}
