package com.familyshield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// FamilyShield "Digital Guardianship" palette (per the Stitch design system):
// Deep Navy authority, Sky-Blue action, Mint-Green positive status, cool neutrals.
val Navy = Color(0xFF000666)
val NavyContainer = Color(0xFF1A237E)
val Sky = Color(0xFF0A6CDB)        // primary action accent
val SkyBright = Color(0xFF51B2FE)
val SkyTint = Color(0xFFE4F0FF)
val Green = Color(0xFF2E9E4F)
val GreenTint = Color(0xFFDFF3E3)
val Orange = Color(0xFFF57C00)
val Ink = Color(0xFF191C1E)
val Muted = Color(0xFF454652)
val Danger = Color(0xFFBA1A1A)
val DangerTint = Color(0xFFFFDAD6)
val Bg = Color(0xFFF7F9FC)

// Aliases so existing call sites keep compiling against the new palette.
val Blue = Sky
val BlueDeep = Navy
val Indigo = NavyContainer
val Cyan = SkyBright
val Mint = Green
val Amber = Orange
val Ok = Green
val OkTint = GreenTint
val BlueTint = SkyTint

/** Brand gradient — navy → sky, used for hero headers / CTAs. */
val BrandGradient = Brush.linearGradient(listOf(Navy, Sky, SkyBright))
val BrandGradientSoft = Brush.linearGradient(listOf(Sky, SkyBright))

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = Color(0xFF8690EE),
    secondary = Color(0xFF00639A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6FF),
    onSecondaryContainer = Color(0xFF00436A),
    tertiary = Green,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC7F0CA),
    onTertiaryContainer = Color(0xFF00390A),
    background = Bg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE0E3E6),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    surfaceContainerHighest = Color(0xFFE0E3E6),
    outline = Color(0xFF767683),
    outlineVariant = Color(0xFFC6C5D4),
    error = Danger,
    onError = Color.White,
    errorContainer = DangerTint,
    onErrorContainer = Color(0xFF93000A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBDC2FF),
    onPrimary = Color(0xFF000767),
    secondary = SkyBright,
    tertiary = Color(0xFF78DC77),
    background = Color(0xFF0E1116),
    surface = Color(0xFF1A1D22),
    error = Danger,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FamilyShieldTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
