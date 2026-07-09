package app.farmsy.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Farmsy design tokens — mirrors iOS Theme.swift (same hex values):
// warm cream background, forest-green primary, serif display type.
object FarmsyColors {
    val farmGreen = Color(0xFF3F5E3A)
    val farmGreenDeep = Color(0xFF2E4A2B)
    val farmGreenSoft = Color(0x243F5E3A) // green at 14% opacity
    val cream = Color(0xFFF8F6F0)
    val creamCard = Color(0xFFF1EEE5)
    val ink = Color(0xFF16211B)
    val inkMuted = Color(0xFF6B7280)
    val warnRed = Color(0xFFDC2626)
}

val CardShape = RoundedCornerShape(18.dp)
val PillShape = RoundedCornerShape(50)

private val LightColors = lightColorScheme(
    primary = FarmsyColors.farmGreen,
    onPrimary = Color.White,
    background = FarmsyColors.cream,
    onBackground = FarmsyColors.ink,
    surface = Color.White,
    onSurface = FarmsyColors.ink,
    surfaceVariant = FarmsyColors.creamCard,
    onSurfaceVariant = FarmsyColors.inkMuted,
    error = FarmsyColors.warnRed,
)

/// The app is light-only by design (same as iOS) — cream is the brand.
@Composable
fun FarmsyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = FarmsyTypography,
        content = content
    )
}
