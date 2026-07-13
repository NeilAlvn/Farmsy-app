package app.farmsy.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment

/// Serif headline with the one-italic-word treatment (iOS DisplayTitle).
@Composable
fun DisplayTitle(
    leading: String,
    emphasis: String,
    trailing: String = "",
    size: TextUnit = 32.sp,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium)) { append(leading) }
        withStyle(
            SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic)
        ) { append(emphasis) }
        withStyle(SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium)) { append(trailing) }
    }
    Text(text, modifier = modifier, fontSize = size, color = FarmsyColors.ink, textAlign = textAlign, lineHeight = size * 1.15)
}

private fun AnnotatedString.Builder.withStyle(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style); block(); pop()
}

/// Small green uppercase kicker line above serif titles (iOS Kicker).
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = geist(14.sp, FontWeight.SemiBold),
        letterSpacing = 1.6.sp,
        color = FarmsyColors.farmGreen
    )
}

/// Big rounded primary CTA (iOS PrimaryButtonStyle).
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = FarmsyColors.farmGreen,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = Color.White,
            disabledContainerColor = fill.copy(alpha = 0.55f),
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(vertical = 17.dp)
    ) {
        Text(text, style = geist(18.sp, FontWeight.SemiBold))
    }
}

/// Grey secondary pill (iOS SecondaryButtonStyle).
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE5E4DF),
            contentColor = FarmsyColors.ink,
        ),
        contentPadding = PaddingValues(vertical = 17.dp)
    ) {
        Text(text, style = geist(18.sp, FontWeight.SemiBold))
    }
}

/// Cream card container (iOS .card() modifier).
fun Modifier.card(padding: Int = 16): Modifier =
    this.background(FarmsyColors.creamCard, CardShape).padding(padding.dp)

/// White card container (used for form sections).
fun Modifier.whiteCard(padding: Int = 16): Modifier =
    this.background(Color.White, CardShape).padding(padding.dp)

/// Vertical stack of content on the cream background with standard padding.
@Composable
fun SectionColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

/// Text that shrinks rather than wraps — Compose's missing `minimumScaleFactor`.
///
/// SwiftUI will scale a label down to fit its box; Compose only ever wraps, which
/// is how "You're browsing as a guest" turned into two lines on a phone with a
/// larger font scale. This measures the string at the requested size and steps the
/// size down (never below [minSize]) until it fits on one line, so the label keeps
/// its shape and the surrounding layout doesn't move.
///
/// Use it for single-line labels. Body copy that is *meant* to wrap should stay a
/// plain Text.
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = FarmsyColors.ink,
    minSize: TextUnit = 11.sp,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val fitted = remember(text, style, maxWidth) {
            var size = style.fontSize
            // Step down in 0.5sp increments; bail out at minSize and let it clip.
            while (size > minSize) {
                val result = measurer.measure(
                    text = AnnotatedString(text),
                    style = style.copy(fontSize = size),
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidth),
                )
                if (!result.hasVisualOverflow) break
                size = (size.value - 0.5f).sp
            }
            size
        }
        Text(
            text,
            style = style.copy(fontSize = fitted),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
        )
    }
}

/// One purchasable plan — a filled primary or an outlined secondary.
///
/// Deliberately two lines, label above and price below, and both auto-shrink: a
/// single line like "Upgrade to Lifetime · ₱3,950.00" is too long for a full-width
/// button and ends up cramped or clipped, especially at a large font scale.
/// `detail` is null until the store hands back a localized price, in which case we
/// fall back to the generic CTA.
@Composable
fun PlanCard(
    label: String,
    detail: String?,
    filled: Boolean,
    fallbackLabel: String,
    onClick: () -> Unit,
) {
    val fg = if (filled) Color.White else FarmsyColors.farmGreen
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (filled) Modifier.background(FarmsyColors.farmGreen, shape)
                else Modifier
                    .background(Color.White, shape)
                    .border(1.5.dp, FarmsyColors.farmGreen.copy(alpha = 0.45f), shape)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FitText(
            if (detail == null) fallbackLabel else label,
            style = geist(17.sp, FontWeight.SemiBold), color = fg,
            textAlign = TextAlign.Center,
        )
        detail?.let {
            FitText(
                it, style = geist(14.sp),
                color = if (filled) Color.White.copy(alpha = 0.9f) else FarmsyColors.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
