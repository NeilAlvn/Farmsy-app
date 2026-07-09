package app.farmsy.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.farmsy.android.R

// Same families as iOS Theme.swift: Fraunces = display serif, Geist = body/UI.
val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
    Font(R.font.fraunces_regularitalic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.fraunces_mediumitalic, FontWeight.Medium, FontStyle.Italic),
)

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geistmono_regular, FontWeight.Normal),
    Font(R.font.geistmono_medium, FontWeight.Medium),
)

/// Mirrors `.display(size, weight:)` on iOS.
fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = Fraunces, fontWeight = weight, fontSize = size, color = FarmsyColors.ink
)

/// Mirrors `.displayItalic(size, weight:)`.
fun displayItalic(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Fraunces, fontWeight = weight, fontStyle = FontStyle.Italic, fontSize = size
)

/// Mirrors `.geist(size, weight)`.
fun geist(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Geist, fontWeight = weight, fontSize = size
)

val FarmsyTypography = Typography(
    displayLarge = display(34.sp),
    displayMedium = display(30.sp),
    headlineMedium = display(26.sp, FontWeight.SemiBold),
    titleLarge = display(22.sp, FontWeight.SemiBold),
    bodyLarge = geist(16.sp),
    bodyMedium = geist(14.sp),
    labelLarge = geist(15.sp, FontWeight.SemiBold),
    labelMedium = geist(13.sp, FontWeight.Medium),
    labelSmall = geist(11.sp, FontWeight.SemiBold),
)
