package app.farmsy.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.farmsy.android.R

/// Plus Jakarta Sans — the one family, same as iOS Theme.swift. Weight resolves to
/// a file, never to a synthetic weight, so bold is real bold on both platforms.
val PlusJakartaSans = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold),
)

/// Mirrors `.ui(size, weight)` on iOS.
fun ui(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = weight, fontSize = size
)

/// Legacy names kept for their call sites; the serif and the mono went with the
/// redesign, so all three resolve to the one family.
fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = ui(size, weight).copy(color = FarmsyColors.ink)
fun displayItalic(size: TextUnit, weight: FontWeight = FontWeight.Normal) = ui(size, weight)
fun geist(size: TextUnit, weight: FontWeight = FontWeight.Normal) = ui(size, weight)

/// The type scale (iOS TextRole): display 40/700, title 28/700, heading 22/600,
/// subheading 17/600, body 16, bodySm 15, caption 13, label 12/600.
enum class TextRole(val size: TextUnit, val weight: FontWeight, val tracking: TextUnit, val lineHeight: TextUnit) {
    DISPLAY(40.sp, FontWeight.Bold, (-0.8).sp, TextUnit.Unspecified),
    TITLE(28.sp, FontWeight.Bold, (-0.4).sp, TextUnit.Unspecified),
    HEADING(22.sp, FontWeight.SemiBold, (-0.2).sp, TextUnit.Unspecified),
    SUBHEADING(17.sp, FontWeight.SemiBold, 0.sp, TextUnit.Unspecified),
    BODY(16.sp, FontWeight.Normal, 0.sp, 24.sp),
    BODY_SM(15.sp, FontWeight.Normal, 0.sp, 21.sp),
    CAPTION(13.sp, FontWeight.Normal, 0.sp, 18.sp),
    LABEL(12.sp, FontWeight.SemiBold, 0.3.sp, 17.sp),
}

/// Type role as a style: `Text("…", style = role(TextRole.HEADING))`.
fun role(r: TextRole) = ui(r.size, r.weight).copy(letterSpacing = r.tracking, lineHeight = r.lineHeight)

val FarmsyTypography = Typography(
    displayLarge = display(34.sp),
    displayMedium = display(30.sp),
    headlineMedium = display(26.sp, FontWeight.SemiBold),
    titleLarge = display(22.sp, FontWeight.SemiBold),
    bodyLarge = ui(16.sp),
    bodyMedium = ui(14.sp),
    labelLarge = ui(15.sp, FontWeight.SemiBold),
    labelMedium = ui(13.sp, FontWeight.Medium),
    labelSmall = ui(11.sp, FontWeight.SemiBold),
)
