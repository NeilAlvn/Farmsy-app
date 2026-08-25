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

// Farmsy design tokens — the exact iOS Theme.swift hex values (measured out of the
// web app's DESIGN-SYSTEM.md, oklch→sRGB, not eyeballed): warm cream background,
// deep forest-green primary, warm near-black ink, serif display type.
//
// Two greens on purpose (same as iOS): `farmGreen` is the deep brand green for
// surfaces away from the map; `farmGreenMap` is lighter, for controls sitting *on*
// the map where the dark green reads as a heavy block.
object FarmsyColors {
    val farmGreen = Color(0xFF234725)      // --primary
    val farmGreenDeep = Color(0xFF18321A)  // darker, for gradients
    val farmGreenMap = Color(0xFF4E7F54)   // --primary-soft (on-map controls)
    val farmGreenSoft = Color(0x1A234725)  // primary at 10%, no new swatch
    val cream = Color(0xFFFCFAF6)          // --background, warm off-white
    val creamCard = Color(0xFFFDFCF9)      // --card, a hair lighter than ground
    val creamFill = Color(0xFFF3EAD9)      // --cream, marketing blocks only
    val ink = Color(0xFF15110D)            // --foreground, warm near-black
    val inkMuted = Color(0xFF68625E)       // --muted-foreground, warm grey
    val hairline = Color(0xFFE1DDD8)       // --border
    val star = Color(0xFFFBBF24)           // amber — ratings read as stars, not brand
    val warnRed = Color(0xFFBA2B28)        // --destructive
}

// iOS cards/buttons use a 16pt continuous radius; pills are capsules.
val CardShape = RoundedCornerShape(16.dp)
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
