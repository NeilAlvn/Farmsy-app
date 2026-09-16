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

// Farmsy design tokens — the exact iOS Theme.swift hex values. Structure (type
// scale, spacing, radii) is the Vision Tech base system shared with Nime; only the
// brand slots are Farmsy's greens and cream. Two colour families per semantic on
// purpose — `positive/warning/critical` are text-safe, the `vivid*` set is for
// fills only (dots, rings, bars) and never for text.
object FarmsyColors {
    // Brand slots
    val farmGreen = Color(0xFF234725)      // accent
    val farmGreenDeep = Color(0xFF18321A)  // darker, for gradients
    val farmGreenMap = Color(0xFF4E7F54)   // lighter green for on-map controls
    val farmGreenSoft = Color(0xFFE3ECE0)  // accentSoft: one soft card per screen at most
    val vivid = Color(0xFF9BE15D)          // accentVivid: fills only, never text
    val cream = Color(0xFFFCFAF6)          // canvas
    val surface = Color.White              // cards, rows, the tab pill
    val creamCard = Color.White            // legacy name for `surface`
    val creamFill = Color(0xFFF3EAD9)      // tile: search field, image wells, skeletons
    val ink = Color(0xFF15110D)
    val inkMuted = Color(0xFF68625E)
    val inkFaint = Color(0xFF8A837D)
    val hairline = Color(0xFFE1DDD8)
    val star = Color(0xFFFBBF24)           // a rating reads as stars, not brand

    // Semantic, text-safe (WCAG AA on cream and white)
    val positive = Color(0xFF137A4A)
    val warning = Color(0xFF9A5B00)
    val critical = Color(0xFFBA2B28)
    val warnRed = Color(0xFFBA2B28)        // legacy name for `critical`
    val positiveSoft = Color(0xFFE4F3EA)
    val warningSoft = Color(0xFFFBEFD9)
    val criticalSoft = Color(0xFFFBE5E5)

    // Semantic, fills only (open dot, availability ring, closed pin)
    val vividPositive = Color(0xFF9BE15D)
    val vividWarning = Color(0xFFFF8A00)
    val vividCritical = Color(0xFFFF2D46)
}

/// Spacing scale, base 4. Gutter 16, card padding 20, section 24–32.
object Space {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp
    val s5 = 20.dp; val s6 = 24.dp; val s8 = 32.dp; val s12 = 48.dp
}

object Radius {
    val card = 20.dp; val tile = 16.dp
    val input = 14.dp; val sheet = 28.dp; val thumb = 12.dp
}

/// The bottom inset a scrolling tab screen reserves so its last row clears the
/// floating tab pill (64dp pill + 12dp gap + breathing room).
object TabBarInset {
    val height = 64.dp
    val content = height + 12.dp + 24.dp
}

/// Cards are radius 20; pills are capsules.
val CardShape = RoundedCornerShape(20.dp)
val TileShape = RoundedCornerShape(16.dp)
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
