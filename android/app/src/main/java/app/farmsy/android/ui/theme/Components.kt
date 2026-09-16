package app.farmsy.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import app.farmsy.android.R

/// Headline. Kept for its call sites; the italic-word treatment went with the
/// serif, so the whole line now renders as one bold title (iOS DisplayTitle).
@Composable
fun DisplayTitle(
    leading: String,
    emphasis: String,
    trailing: String = "",
    size: TextUnit = 32.sp,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    // Own the spacing between the three parts rather than trusting each caller to
    // remember a trailing space — Android's XML parser strips trailing whitespace.
    val text = listOf(leading, emphasis, trailing).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    Text(
        text, modifier = modifier, style = ui(size, FontWeight.Bold), letterSpacing = (-0.4).sp,
        color = FarmsyColors.ink, textAlign = textAlign, lineHeight = size * 1.15,
    )
}

/// Small green uppercase kicker line above titles (iOS Kicker).
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = ui(12.sp, FontWeight.SemiBold),
        letterSpacing = 0.6.sp,
        color = FarmsyColors.farmGreen
    )
}

/// Primary pill: ink fill, 56dp (iOS PrimaryButtonStyle). `fill` stays for the few
/// on-map callers that pass one.
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = FarmsyColors.ink,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = Color.White,
            disabledContainerColor = fill.copy(alpha = 0.55f),
            disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = Space.s6, vertical = 16.dp)
    ) {
        Text(text, style = ui(17.sp, FontWeight.SemiBold))
    }
}

/// Secondary pill: tile fill, ink label (iOS SecondaryButtonStyle).
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FarmsyColors.creamFill,
            contentColor = FarmsyColors.ink,
        ),
        contentPadding = PaddingValues(horizontal = Space.s6, vertical = 16.dp)
    ) {
        Text(text, style = ui(17.sp, FontWeight.SemiBold))
    }
}

/// Card: white, radius 20, flat (iOS .card()). `edged` adds a hairline ring for a
/// card that has to hold its own on a white ground; `soft` is the accent wash.
fun Modifier.card(padding: Int = 20, edged: Boolean = false, soft: Boolean = false): Modifier =
    this.background(if (soft) FarmsyColors.farmGreenSoft else FarmsyColors.surface, CardShape)
        .then(if (edged) Modifier.border(1.dp, FarmsyColors.hairline, CardShape) else Modifier)
        .padding(padding.dp)

/// White card container (legacy name; same as `card`).
fun Modifier.whiteCard(padding: Int = 16): Modifier = card(padding)

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

// MARK: - tapCard (iOS App/TapCard.swift) — a tap that does NOT fire when the
// finger was actually scrolling, with an optional top-trailing exclusion for a
// corner control (a save heart / close). Compose's detectTapGestures already
// cancels the tap when the parent scrollable consumes the drag, so this reproduces
// iOS TapActivate; `excludeTopTrailing` carves a square out of the top-right corner.
// Fires a light haptic before the action, matching iOS `Haptics.tap()`
// (UIImpactFeedback .light) — `TextHandleMove` is Compose's closest light tick.
@Composable
fun Modifier.tapCard(
    excludeTopTrailing: androidx.compose.ui.unit.Dp? = null,
    onTap: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return this.then(
        Modifier.pointerInput(excludeTopTrailing) {
            val excludePx = excludeTopTrailing?.toPx()
            detectTapGestures(onTap = { pos: androidx.compose.ui.geometry.Offset ->
                val inExcluded = excludePx != null &&
                    pos.x > size.width - excludePx && pos.y < excludePx
                if (!inExcluded) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                }
            })
        }
    )
}

/// A description clamped to `lineLimit` lines with "… View more" at the end of the
/// last visible line, expanding *inline* to the full text with "View less" when
/// tapped (no modal). 1:1 with iOS ExpandableText (C7): truncation is detected from
/// the real layout (hasVisualOverflow), the control appends at the exact end of the
/// clamped text, and a tapCard toggles it. Shared component — S6's C4 teaser and S7's
/// description both use this.
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    lineLimit: Int = 3,
    style: TextStyle = geist(15.sp),
    color: Color = FarmsyColors.ink,
    moreColor: Color = FarmsyColors.farmGreen,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var truncated by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var lastLineEnd by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    val moreLabel = "  … View more"
    val lessLabel = "  View less"

    val display: AnnotatedString = buildAnnotatedString {
        when {
            expanded -> {
                withStyle(SpanStyle(color = color)) { append(text) }
                withStyle(SpanStyle(color = moreColor, fontWeight = FontWeight.Bold)) { append(lessLabel) }
            }
            truncated -> {
                // Trim the clamped text back enough to fit "… View more" on the last
                // line, matching iOS's truncate-to-fit.
                val cut = lastLineEnd.coerceIn(0, text.length)
                val head = text.substring(0, cut)
                    .dropLast(moreLabel.length.coerceAtMost(cut))
                    .trimEnd()
                withStyle(SpanStyle(color = color)) { append(head) }
                withStyle(SpanStyle(color = moreColor, fontWeight = FontWeight.Bold)) { append(moreLabel) }
            }
            else -> withStyle(SpanStyle(color = color)) { append(text) }
        }
    }

    Text(
        text = display,
        style = style,
        maxLines = if (expanded) Int.MAX_VALUE else lineLimit,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { res ->
            if (!expanded && !truncated && res.hasVisualOverflow) {
                truncated = true
                lastLineEnd = res.getLineEnd(lineLimit - 1, visibleEnd = true)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .then(if (truncated || expanded) Modifier.tapCard { expanded = !expanded } else Modifier),
    )
}

// MARK: - The primitive kit (iOS UI.swift), measured from Nime and drawn with
// Farmsy's tokens. The floating tab bar lives with AppTab in features/main.

/// Light tick before an action, matching iOS `Haptics.tap()`.
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

/// Two forms. Large: 28sp title left, icon buttons right — tab roots. Compact:
/// 52dp bar with a back button in a fixed side slot and a centred title — every
/// pushed or sheet screen.
@Composable
fun ScreenHeader(
    title: String,
    compact: Boolean = false,
    onBack: (() -> Unit)? = null,
    onAtmosphere: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val tone = if (onAtmosphere) Color.White else FarmsyColors.ink
    if (compact) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.s4), contentAlignment = Alignment.Center) {
            Text(
                title, style = role(TextRole.SUBHEADING), color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 88.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                if (onBack != null) {
                    IconButton(Icons.AutoMirrored.Filled.ArrowBack, label = stringResource(R.string.back), overMedia = onAtmosphere, onClick = onBack)
                }
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s4, top = Space.s2, bottom = Space.s3),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            Text(title, style = role(TextRole.TITLE), color = tone)
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/// Section header inside a scroll: heading left, optional text action right.
@Composable
fun SectionHeader(title: String, action: Pair<String, () -> Unit>? = null, top: Dp = Space.s6) {
    Row(
        Modifier.fillMaxWidth().padding(top = top, bottom = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = role(TextRole.HEADING), color = FarmsyColors.ink)
        Spacer(Modifier.weight(1f))
        action?.let { (label, run) -> PillButton(label, PillVariant.TEXT, PillSize.SMALL, onClick = run) }
    }
}

enum class PillVariant { PRIMARY, SECONDARY, GHOST, SOFT, DESTRUCTIVE, TEXT, SURFACE }
enum class PillSize(val height: Dp) { LARGE(56.dp), MEDIUM(48.dp), SMALL(40.dp) }

/// The one button. `PRIMARY` is ink; `SECONDARY` is the vivid green with ink
/// text; `GHOST` is a hairline; `SOFT` is the accent wash; `TEXT` is a link.
@Composable
fun PillButton(
    text: String,
    variant: PillVariant = PillVariant.PRIMARY,
    size: PillSize = PillSize.LARGE,
    block: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fill = when (variant) {
        PillVariant.PRIMARY -> FarmsyColors.ink
        PillVariant.SECONDARY -> FarmsyColors.vivid
        PillVariant.GHOST, PillVariant.TEXT -> Color.Transparent
        PillVariant.SOFT -> FarmsyColors.farmGreenSoft
        PillVariant.DESTRUCTIVE -> FarmsyColors.critical
        PillVariant.SURFACE -> FarmsyColors.surface
    }
    val label = when (variant) {
        PillVariant.PRIMARY, PillVariant.DESTRUCTIVE -> Color.White
        PillVariant.TEXT -> FarmsyColors.farmGreen
        else -> FarmsyColors.ink
    }
    val tap = rememberTapHaptic()
    val isText = variant == PillVariant.TEXT
    Row(
        modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .then(if (isText) Modifier else Modifier.heightIn(min = size.height))
            .alpha(if (enabled) 1f else 0.55f)
            .background(fill, PillShape)
            .then(if (variant == PillVariant.GHOST) Modifier.border(1.dp, FarmsyColors.hairline, PillShape) else Modifier)
            .clip(PillShape)
            .clickable(enabled = enabled) { tap(); onClick() }
            .padding(horizontal = if (isText) 0.dp else Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, tint = label, modifier = Modifier.size(18.dp)) }
        Text(text, style = ui(if (isText && size == PillSize.SMALL) 15.sp else 17.sp, FontWeight.SemiBold), color = label, maxLines = 1)
    }
}

/// 44dp circle (40 when `small`). On the canvas: white with a hairline. Over a
/// photo or the atmosphere band: white at 92% with a floating shadow.
@Composable
fun IconButton(
    icon: ImageVector,
    label: String,
    overMedia: Boolean = false,
    small: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tap = rememberTapHaptic()
    val d = if (small) 40.dp else 44.dp
    Box(
        modifier
            .then(if (overMedia) Modifier.shadow(12.dp, CircleShape, ambientColor = FarmsyColors.ink.copy(alpha = 0.10f), spotColor = FarmsyColors.ink.copy(alpha = 0.10f)) else Modifier)
            .size(d)
            .background(if (overMedia) Color.White.copy(alpha = 0.92f) else FarmsyColors.surface, CircleShape)
            .then(if (overMedia) Modifier else Modifier.border(1.dp, FarmsyColors.hairline, CircleShape))
            .clip(CircleShape)
            .clickable { tap(); onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = FarmsyColors.ink, modifier = Modifier.size(if (small) 18.dp else 20.dp))
    }
}

/// 36dp pill. Selected = ink fill; a `dot` colour draws an 8dp fill-only state
/// dot (open / uncertain / closed) before the label.
@Composable
fun Chip(
    label: String,
    icon: ImageVector? = null,
    emoji: String? = null,
    selected: Boolean = false,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    val tap = rememberTapHaptic()
    val fg = if (selected) Color.White else FarmsyColors.ink
    Row(
        Modifier
            .height(36.dp)
            .background(if (selected) FarmsyColors.ink else FarmsyColors.surface, PillShape)
            .then(if (selected) Modifier else Modifier.border(1.dp, FarmsyColors.hairline, PillShape))
            .clip(PillShape)
            .clickable { tap(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
        emoji?.let { Text(it, fontSize = 14.sp) }
        icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(13.dp)) }
        Text(label, style = ui(15.sp, FontWeight.Medium), color = fg, maxLines = 1)
    }
}

/// 22dp pill label. Accent by default; pass a vivid `fill` for a state badge.
@Composable
fun Badge(text: String, fill: Color = FarmsyColors.farmGreen, ink: Color = Color.White) {
    Box(Modifier.height(22.dp).background(fill, PillShape).padding(horizontal = Space.s2), contentAlignment = Alignment.Center) {
        Text(text, style = ui(12.sp, FontWeight.SemiBold), letterSpacing = 0.3.sp, color = ink)
    }
}

/// A white radius-20 container with hairline dividers inset by 16 between its rows.
@Composable
fun RowGroup(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        title?.let { Text(it.uppercase(), style = role(TextRole.LABEL), color = FarmsyColors.inkFaint) }
        val hairline = FarmsyColors.hairline
        // Divider positions come out of measure and are only read in draw, so
        // setting them there redraws without a second layout pass.
        val dividers = remember { mutableStateListOf<Int>() }
        Layout(
            content = content,
            modifier = Modifier.fillMaxWidth().clip(CardShape).background(FarmsyColors.surface).drawBehind {
                val inset = Space.s4.toPx()
                dividers.forEach { y ->
                    drawLine(hairline, Offset(inset, y.toFloat()), Offset(size.width, y.toFloat()), 1.dp.toPx())
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
            val ys = ArrayList<Int>()
            var y = 0
            placeables.forEachIndexed { i, p -> if (i > 0) ys += y; y += p.height }
            if (ys != dividers.toList()) { dividers.clear(); dividers.addAll(ys) }
            layout(constraints.maxWidth, y) {
                var yy = 0
                placeables.forEach { it.placeRelative(0, yy); yy += it.height }
            }
        }
    }
}

/// 56dp row: leading icon, title + optional subtitle, trailing value or chevron.
/// Pressed rows show the tile colour instead of dimming.
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    tint: Color = FarmsyColors.ink,
    onClick: () -> Unit,
) {
    val tap = rememberTapHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier.fillMaxWidth()
            .background(if (pressed) FarmsyColors.creamFill else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null) { tap(); onClick() }
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.s4, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Icon(icon, null, tint = if (tint == FarmsyColors.ink) FarmsyColors.farmGreen else tint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = role(TextRole.BODY), color = tint)
            subtitle?.let { Text(it, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted) }
        }
        value?.let {
            Text(it, style = role(TextRole.BODY), color = FarmsyColors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        }
        if (chevron) Icon(Icons.Filled.ChevronRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
    }
}

/// 48dp pill on the tile colour. `onAtmosphere` draws it white at 92%.
@Composable
fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    onAtmosphere: Boolean = false,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(48.dp)
            .background(if (onAtmosphere) Color.White.copy(alpha = 0.92f) else FarmsyColors.creamFill, PillShape)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        Icon(Icons.Filled.Search, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = text, onValueChange = onTextChange, singleLine = true,
            textStyle = ui(16.sp).copy(color = FarmsyColors.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = ui(16.sp), color = FarmsyColors.inkMuted, maxLines = 1)
                inner()
            },
        )
        if (text.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel, stringResource(R.string.clear_search), tint = FarmsyColors.inkMuted,
                modifier = Modifier.size(20.dp).clickable { onTextChange("") },
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.s6, vertical = Space.s12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(44.dp))
        Text(title, style = role(TextRole.HEADING), color = FarmsyColors.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.s4))
        Text(text, style = role(TextRole.BODY), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.s2))
        action?.let { (label, run) ->
            PillButton(label, PillVariant.PRIMARY, PillSize.MEDIUM, modifier = Modifier.padding(top = Space.s6), onClick = run)
        }
    }
}

/// The one gradient in the system: accent fading into the canvas with a hard
/// stop, behind the Home header. Reads as a horizon, not a hero.
@Composable
fun AtmosphereBand(modifier: Modifier = Modifier, stop: Float = 0.72f) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to FarmsyColors.farmGreen, stop to FarmsyColors.farmGreen, 1f to FarmsyColors.cream,
            )
        )
    )
}

/// The `farmsy` wordmark: bold, tight.
@Composable
fun Wordmark(onAtmosphere: Boolean = false, size: TextUnit = 22.sp) {
    Text(
        "farmsy", style = ui(size, FontWeight.Bold), letterSpacing = (-0.5).sp,
        color = if (onAtmosphere) Color.White else FarmsyColors.farmGreen,
    )
}

/// A locked Plus feature shown in place, not hidden: what it is, and one
/// button to the membership sheet. Frosts nothing — the copy is the teaser.
@Composable
fun PlusLockCard(title: String, text: String, modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Row(modifier.fillMaxWidth().card(soft = true), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        Box(Modifier.size(36.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text(title, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink)
                Badge("PLUS")
            }
            Text(text, style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
            PillButton(stringResource(R.string.plus_unlock), PillVariant.PRIMARY, PillSize.SMALL, modifier = Modifier.padding(top = Space.s1), onClick = onUnlock)
        }
    }
}
