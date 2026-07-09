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
