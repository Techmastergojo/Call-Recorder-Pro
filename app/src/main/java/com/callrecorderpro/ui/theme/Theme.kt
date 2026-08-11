package com.callrecorderpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand Palette ────────────────────────────────────────────────────────────
val NavyDeep      = Color(0xFF0D1B2A)  // Deep navy background
val NavyMid       = Color(0xFF1A2D42)  // Card background
val NavySurface   = Color(0xFF223350)  // Elevated surface
val ElectricBlue  = Color(0xFF1E90FF)  // Primary accent
val CyanGlow      = Color(0xFF00D4FF)  // Secondary accent / highlights
val SoftWhite     = Color(0xFFF0F4FF)  // Primary text
val SubtleGray    = Color(0xFF8899AA)  // Secondary text
val SuccessGreen  = Color(0xFF00E5A0)  // Incoming call indicator
val WarnAmber     = Color(0xFFFFB83F)  // Outgoing call indicator
val DangerRed     = Color(0xFFFF4D6D)  // Delete / missed call

private val DarkColors = darkColorScheme(
    primary          = ElectricBlue,
    onPrimary        = Color.White,
    primaryContainer = NavySurface,
    secondary        = CyanGlow,
    onSecondary      = NavyDeep,
    background       = NavyDeep,
    onBackground     = SoftWhite,
    surface          = NavyMid,
    onSurface        = SoftWhite,
    surfaceVariant   = NavySurface,
    onSurfaceVariant = SubtleGray,
    error            = DangerRed,
    outline          = NavySurface
)

private val LightColors = lightColorScheme(
    primary          = ElectricBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFDCEEFF),
    secondary        = Color(0xFF0066CC),
    onSecondary      = Color.White,
    background       = Color(0xFFF2F6FF),
    onBackground     = Color(0xFF0D1B2A),
    surface          = Color.White,
    onSurface        = Color(0xFF0D1B2A),
    surfaceVariant   = Color(0xFFE8F0FE),
    onSurfaceVariant = Color(0xFF445566),
    error            = DangerRed
)

@Composable
fun RecordProTheme(
    darkTheme: Boolean = true, // Default dark — looks premium
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
