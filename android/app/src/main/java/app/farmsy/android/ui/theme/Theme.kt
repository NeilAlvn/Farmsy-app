package app.farmsy.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/// How far we let the system font scale push our type.
///
/// SwiftUI shrinks text that doesn't fit (`minimumScaleFactor`); Compose has no
/// such thing — it wraps instead, so a line that fits on iOS silently becomes two
/// lines here and every fixed-height container it lives in bursts. A phone set to
/// 1.25 (a common default on Xiaomi) was enough to break the search pill, the tab
/// bar and the paywall buttons.
///
/// So: honour the user's preference, but stop it running away. Text still grows —
/// just not past the point where the layout stops being the layout. Anything that
/// must hold a single line uses [FitText] on top of this.
private const val MAX_FONT_SCALE = 1.15f

/// The app is light-only by design (same as iOS) — cream is the brand.
@Composable
fun FarmsyTheme(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val clamped = Density(
        density = base.density,
        fontScale = base.fontScale.coerceAtMost(MAX_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides clamped) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = FarmsyTypography,
            content = content
        )
    }
}
