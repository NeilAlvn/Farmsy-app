package app.farmsy.android.features.whatsnew

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.Ping
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import app.farmsy.android.ui.theme.tapCard
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

// The feed cards (iOS PingCard.swift / FeaturedFarmCard.swift) and the shared
// skeleton. The What's New sheet they used to sit in is gone: Community holds
// the posts, Discover the featured farms.

// MARK: - C3 · PingCard

@Composable
fun PingCard(
    ping: Ping,
    farmName: String?,
    onOpenFarm: () -> Unit,
    onOpenImage: ((Int) -> Unit)? = null,
) {
    val initials = remember(ping.authorName) {
        ping.authorName.split(" ").mapNotNull { it.firstOrNull() }.take(2)
            .joinToString("").uppercase().ifEmpty { "?" }
    }
    val timeParts = remember(ping.date) { timeAgoParts(ping.date) }
    val timeAgo = timeAgoText(timeParts)

    Column(
        Modifier.fillMaxWidth()
            .background(FarmsyColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header + text = one tap region (opens the farm) — tapCard so a scroll
        // through the card doesn't fire it (iOS PingCard uses two sibling tapCards).
        Column(
            Modifier.fillMaxWidth().tapCard { onOpenFarm() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).background(FarmsyColors.farmGreen.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(initials, style = geist(14.sp, FontWeight.Bold), color = FarmsyColors.farmGreen) }
                // iOS author/farm VStack(spacing: 1) (DiscoverFeedView.swift:334).
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(ping.authorName, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 1)
                    farmName?.let {
                        Text(it, style = geist(12.sp, FontWeight.Medium), color = FarmsyColors.farmGreenMap, maxLines = 1)
                    }
                }
                Text(timeAgo, style = geist(11.sp), color = FarmsyColors.inkMuted)
            }
            if (ping.body.isNotEmpty()) {
                Text(
                    ping.body, style = geist(14.sp), color = FarmsyColors.ink,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                )
            }
        }

        if (ping.images.isNotEmpty()) {
            FixedImageRow(urls = ping.images.take(3), height = 100.dp, onTap = onOpenImage)
        }

        // Like row (read-only in S6; iOS opens the farm on tap here too).
        Row(
            Modifier.tapCard { onOpenFarm() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Filled.FavoriteBorder, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(12.dp))
            if (ping.likeCount > 0) Text("${ping.likeCount}", style = geist(12.sp), color = FarmsyColors.inkMuted)
        }
    }
}

/// iOS PingCard.timeAgo bucketing: just now / Nm / Nh / Nd. The value is resolved to
/// a localized string with plural handling in `timeAgoText`.
private enum class TimeUnit { NOW, MIN, HOUR, DAY }

private fun timeAgoParts(date: OffsetDateTime?): Pair<TimeUnit, Int>? {
    date ?: return null
    val mins = ChronoUnit.MINUTES.between(date, OffsetDateTime.now())
    return when {
        mins < 1 -> TimeUnit.NOW to 0
        mins < 60 -> TimeUnit.MIN to mins.toInt()
        mins < 60 * 24 -> TimeUnit.HOUR to (mins / 60).toInt()
        else -> TimeUnit.DAY to (mins / (60 * 24)).toInt()
    }
}

@Composable
private fun timeAgoText(parts: Pair<TimeUnit, Int>?): String {
    parts ?: return ""
    val (unit, n) = parts
    return when (unit) {
        TimeUnit.NOW -> stringResource(R.string.time_just_now)
        TimeUnit.MIN -> androidx.compose.ui.res.pluralStringResource(R.plurals.time_minutes, n, n)
        TimeUnit.HOUR -> androidx.compose.ui.res.pluralStringResource(R.plurals.time_hours, n, n)
        TimeUnit.DAY -> androidx.compose.ui.res.pluralStringResource(R.plurals.time_days, n, n)
    }
}

// MARK: - C4 · MultiImageFarmCard (featured farm as a post)

@Composable
fun MultiImageFarmCard(pin: FarmPin, images: List<String>, teaser: String?, onOpen: () -> Unit) {
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()
    val saved = savedIds.contains(pin.osmId)

    Column(
        Modifier.fillMaxWidth()
            .background(FarmsyColors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(20.dp))
            // tapCard opening the farm, excluding the top-right 52dp so the save heart
            // there doesn't also open it (iOS `.tapCard(excludeTopTrailing: 52)`).
            .tapCard(excludeTopTrailing = 52.dp) { onOpen() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Round profile = first gallery photo (also kept in the row below).
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center,
            ) {
                if (images.firstOrNull() != null) {
                    AsyncImage(images.first(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(pin.primaryCategory.emoji, fontSize = 18.sp)
                }
            }
            // iOS VStack(spacing: 1) for name/city (WhatsNewSheet.swift:164).
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(pin.name, style = geist(14.sp, FontWeight.Bold), color = FarmsyColors.ink, maxLines = 1)
                pin.city?.let { Text(it, style = geist(12.sp), color = FarmsyColors.inkMuted, maxLines = 1) }
            }
            // Save heart — own tapCard, in the excluded top-right corner.
            Box(
                Modifier.size(40.dp).tapCard {
                    val uid = session.session.value?.user?.id
                    if (uid == null) requestAuth() else scope.launch { favorites.toggle(pin.osmId, uid) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (saved) FarmsyColors.warnRed else FarmsyColors.inkMuted, modifier = Modifier.size(17.dp),
                )
            }
        }

        if (!teaser.isNullOrEmpty()) {
            // iOS C4: a STATIC 3-line clamp. A decorative "… View more" is appended only
            // when the teaser is long (count > 140) — it is not interactive; tapping the
            // card (not the text) opens the farm. C7 ExpandableText is reserved for S7.
            val teaserText = buildAnnotatedString {
                append(teaser)
                if (teaser.length > 140) {
                    withStyle(SpanStyle(color = FarmsyColors.farmGreen, fontWeight = FontWeight.Bold)) {
                        append("  … View more")
                    }
                }
            }
            Text(
                teaserText,
                // iOS .lineSpacing(2) (WhatsNewSheet.swift:195): +2pt *between* lines only.
                // Geist natural line height at 13sp = 16.90sp (typo metrics,
                // includeFontPadding=false), so lineHeight = 16.90 + 2 = 18.9sp, with
                // LineHeightStyle(Trim.Both) removing the leading above line 1 / below the
                // last so the extra sits only between lines — exact match, not a near value.
                style = geist(13.sp).copy(
                    lineHeight = 18.9.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                color = FarmsyColors.ink,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (images.isNotEmpty()) {
            FixedImageRow(urls = images.take(3), height = 96.dp)
        }
    }
}

// MARK: - C5 · FixedImageRow (shared equal-tile photo row)

@Composable
fun FixedImageRow(urls: List<String>, height: Dp = 100.dp, onTap: ((Int) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        urls.forEachIndexed { i, url ->
            Box(
                Modifier.weight(1f).height(height)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F6))
                    // iOS OptionalTap → tapCard when onTap is set (DiscoverFeedView.swift:431-435):
                    // scroll-aware tap + light haptic, so a scroll ending on a photo doesn't fire.
                    .then(if (onTap != null) Modifier.tapCard { onTap(i) } else Modifier),
            ) {
                // Per-tile shimmer while the image loads, then the photo — matches iOS
                // FixedImageRow (AsyncImage success else SkeletonBox).
                SubcomposeAsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { SkeletonBox(cornerRadius = 10.dp, modifier = Modifier.fillMaxSize()) },
                    error = { SkeletonBox(cornerRadius = 10.dp, modifier = Modifier.fillMaxSize()) },
                )
            }
        }
        // A single photo stays half-width (one tile), padded with a clear slot.
        if (urls.size == 1) Spacer(Modifier.weight(1f).height(height))
    }
}

// MARK: - SkeletonBox (§1.3 shared shimmer)

/// A shimmering skeleton block — grey base with a light band sweeping across.
/// 1:1 with iOS App/Shimmer.swift (base #ECEBE8, white band 0.55 width 0.6·w,
/// x −1→1 over 1.4s linear repeatForever).
@Composable
fun SkeletonBox(cornerRadius: Dp = 12.dp, modifier: Modifier = Modifier) {
    // A pulsing block is decorative motion; under "remove animations" it sits still.
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember {
        listOf(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE)
            .any { android.provider.Settings.Global.getFloat(context.contentResolver, it, 1f) == 0f }
    }
    val phase = if (reduceMotion) -1f else {
        val t = rememberInfiniteTransition(label = "shimmer")
        t.animateFloat(
            -1f, 1f, infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
            label = "phase",
        ).value
    }
    // Measure the box so the sweeping band is 0.6·width (iOS Shimmer.swift), not a
    // fixed pixel width; the band travels x = phase·1.4·width (iOS offset 1.4·w).
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(cornerRadius)).background(Color(0xFFECEBE8)).clipToBounds(),
    ) {
        val wPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val band = wPx * 0.6f
        val start = phase * wPx * 1.4f
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                    startX = start, endX = start + band,
                )
            )
        )
    }
}
