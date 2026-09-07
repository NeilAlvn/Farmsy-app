package app.farmsy.android.features.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmContentApi
import app.farmsy.android.core.FarmDetail
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.Ping
import app.farmsy.android.core.Review
import app.farmsy.android.features.whatsnew.FixedImageRow
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// S9 · FarmMemberSections (iOS `FarmMemberSections.swift`) — everything below the
/// description for a *member*: what people are saying → details → what's new (with
/// the C8 PostComposer) → reviews (with the C8 ReviewComposer) → claim → report.
/// A non-member never sees this (the caller shows the locked block instead).
/// Rendered into FarmDetailScreen7's DetailSections. `onClaim` opens the web claim
/// route (decided: claim → web, matching iOS SafariView).
@Composable
fun FarmMemberSections(
    pin: FarmPin,
    detail: FarmDetail?,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val uid = session.session.collectAsState().value?.user?.id

    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var posts by remember { mutableStateOf<List<Ping>>(emptyList()) }
    var likedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lightbox by remember { mutableStateOf<LightboxSource?>(null) }

    // iOS loads reviews on one .task and posts+likedIds on another.
    LaunchedEffect(pin.osmId) { reviews = FarmContentApi.reviews(pin.osmId) }
    LaunchedEffect(pin.osmId, uid) {
        posts = FarmContentApi.posts(pin.osmId)
        uid?.let { likedIds = FarmContentApi.likedPingIds(it) }
    }

    suspend fun reload() {
        reviews = FarmContentApi.reviews(pin.osmId)
        posts = FarmContentApi.posts(pin.osmId)
        uid?.let { likedIds = FarmContentApi.likedPingIds(it) }
    }

    val fromAPost = stringResource(R.string.lightbox_from_a_post)
    fun openLightbox(imgs: List<String>, start: Int, author: String) {
        lightbox = LightboxSource(images = imgs, startIndex = start, eyebrow = fromAPost, title = author, subtitle = pin.name)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {

        // 1 · reviewsSummary
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(stringResource(R.string.section_people_saying))
            if (reviews.isEmpty()) {
                DashedNote(stringResource(R.string.reviews_be_first))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val avg = reviews.sumOf { it.rating }.toDouble() / reviews.size
                    Icon(Icons.Filled.Star, null, tint = FarmsyColors.star, modifier = Modifier.size(15.dp))
                    Text(String.format("%.1f", avg), style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink)
                    Text(
                        pluralStringResource(R.plurals.reviews_count, reviews.size, reviews.size),
                        style = geist(14.sp), color = FarmsyColors.inkMuted,
                    )
                }
            }
        }

        // 2 · detailsList
        val rows = detailRowsOf(pin, detail)
        if (rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.details))
                Column(
                    Modifier.fillMaxWidth()
                        .background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
                        .padding(6.dp),
                ) {
                    rows.forEach { row ->
                        InfoRow(
                            icon = row.icon, label = row.label, value = row.value, isLink = row.url != null,
                            onTap = row.url?.let { url -> { haptics.tapTick(); openUrl(context, url) } },
                        )
                    }
                }
            }
        }

        // 3 · whatsNew (PostComposer + posts)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.section_whats_new))
            PostComposer(pin = pin) { reload() }
            if (posts.isEmpty()) {
                DashedNote(stringResource(R.string.posts_empty_today))
            } else {
                posts.forEach { ping ->
                    FarmPostRow(
                        ping = ping,
                        liked = likedIds.contains(ping.id),
                        onOpenImage = { idx -> openLightbox(ping.images, idx, ping.authorName) },
                        onLike = {
                            uid?.let { u ->
                                val wasLiked = likedIds.contains(ping.id)
                                likedIds = if (wasLiked) likedIds - ping.id else likedIds + ping.id
                                scope.launch { FarmContentApi.toggleLike(ping.id, u, wasLiked) }
                            }
                        },
                        onReport = {
                            uid?.let { u -> scope.launch { FarmContentApi.reportPing(ping.id, u); view.successTick() } }
                        },
                    )
                }
            }
        }

        // 4 · reviewsSection (ReviewComposer + reviews)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.reviews))
            val myReview = uid?.let { u -> reviews.firstOrNull { it.userId?.lowercase() == u.lowercase() } }
            ReviewComposer(pin = pin, existing = myReview) { reload() }
            if (reviews.isEmpty()) {
                Text(
                    stringResource(R.string.reviews_none_be_first),
                    style = geist(13.sp), color = FarmsyColors.inkMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            } else {
                reviews.forEach { r -> ReviewRow(r) }
            }
        }

        // 5 · claimBlock
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.claim_is_your_farm), style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink)
            Row(
                Modifier.fillMaxWidth()
                    .background(FarmsyColors.farmGreenMap, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.tapTick(); onClaim()
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // shield → Material Outlined Shield (SF substitution).
                Icon(Icons.Outlined.Shield, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.claim_this_farm), style = geist(15.sp, FontWeight.SemiBold), color = Color.White)
            }
            Text(
                stringResource(R.string.claim_fine_print),
                style = geist(12.sp), color = FarmsyColors.inkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        // 6 · reportLink → farmsy.app/messages
        Row(
            Modifier.fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptics.tapTick(); openUrl(context, "https://www.farmsy.app/messages")
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // flag → Material Outlined Flag (SF substitution).
            Icon(Icons.Outlined.Flag, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.report_incorrect_info), style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.inkMuted)
        }
    }

    lightbox?.let { src -> ImageLightbox(source = src, onClose = { lightbox = null }) }
}

// MARK: - Detail rows

private data class DetailRow(val icon: ImageVector, val label: String, val value: String, val url: String? = null)

/// Opening hours, one segment per line — split on `;` or newline (never a comma).
private fun formatHours(raw: String): String =
    raw.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

/// iOS webURL: absolute http passes through; else prepend https:// (or a base host).
private fun webURL(s: String, base: String = ""): String {
    val v = s.trim()
    if (v.startsWith("http")) return v
    if (base.isEmpty()) return "https://$v"
    return base + v.trim('@', '/')
}

@Composable
private fun detailRowsOf(pin: FarmPin, detail: FarmDetail?): List<DetailRow> {
    val out = mutableListOf<DetailRow>()
    (detail?.openingHours ?: pin.openingHours)?.let {
        out += DetailRow(Icons.Filled.Schedule, stringResource(R.string.hours), formatHours(it))
    }
    val address = detail?.address ?: pin.address
    if (address != null) {
        val value = listOfNotNull(address, detail?.postalCode ?: pin.postalCode, pin.city).joinToString(", ")
        out += DetailRow(Icons.Filled.Place, stringResource(R.string.address), value)
    }
    detail?.phone?.let {
        out += DetailRow(Icons.Filled.Phone, stringResource(R.string.phone), it, "tel:${it.filterNot { c -> c.isWhitespace() }}")
    }
    detail?.website?.let {
        out += DetailRow(Icons.Filled.Public, stringResource(R.string.website), it, webURL(it))
    }
    detail?.email?.let {
        out += DetailRow(Icons.Filled.Email, stringResource(R.string.email), it, "mailto:$it")
    }
    detail?.facebook?.let {
        out += DetailRow(Icons.Filled.Link, "Facebook", it, webURL(it, "https://facebook.com/"))
    }
    detail?.instagram?.let {
        out += DetailRow(Icons.Filled.Link, "Instagram", it, webURL(it, "https://instagram.com/"))
    }
    if (detail?.organic == true) {
        out += DetailRow(Icons.Filled.Eco, stringResource(R.string.organic), stringResource(R.string.organic_yes))
    }
    detail?.produce?.takeIf { it.isNotEmpty() }?.let {
        out += DetailRow(Icons.Filled.ShoppingBasket, stringResource(R.string.produce), it)
    }
    return out
}

/// A details row: icon (farmGreenMap) + label over value; link rows draw the value
/// green with an up-right arrow (iOS InfoRow, FarmDetailView.swift:664). `arrow.up.right`
/// → Material `NorthEast` (SF substitution).
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, isLink: Boolean, onTap: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(
                if (onTap != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onTap() } else Modifier
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // iOS: 16pt glyph in a 26-wide column (`.frame(width: 26)`).
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
            Text(
                value, style = geist(15.sp),
                color = if (isLink) FarmsyColors.farmGreenMap else FarmsyColors.ink,
                maxLines = if (isLink) 1 else Int.MAX_VALUE,
            )
        }
        if (isLink) {
            Icon(Icons.Filled.NorthEast, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(12.dp))
        }
    }
}

// MARK: - Post row

/// A farm's own post: initials avatar + author, body, photo strip, like + report
/// (iOS FarmPostRow, FarmMemberSections.swift:322). SF substitutions: heart.fill/heart
/// → Favorite/FavoriteBorder(13); flag → Outlined Flag(12).
@Composable
private fun FarmPostRow(
    ping: Ping,
    liked: Boolean,
    onOpenImage: (Int) -> Unit,
    onLike: () -> Unit,
    onReport: () -> Unit,
) {
    var reported by remember { mutableStateOf(false) }
    val initials = ping.authorName.split(" ").mapNotNull { it.firstOrNull() }.take(2)
        .joinToString("").uppercase().ifEmpty { "?" }
    // The count adjusted for the viewer's own optimistic like.
    val displayCount = if (liked && ping.likeCount == 0) 1 else ping.likeCount

    Column(
        Modifier.fillMaxWidth()
            .background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).background(FarmsyColors.farmGreen.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, style = geist(13.sp, FontWeight.Bold), color = FarmsyColors.farmGreen)
            }
            Text(ping.authorName, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
        }
        if (ping.body.isNotEmpty()) {
            Text(ping.body, style = geist(14.sp), color = FarmsyColors.ink, modifier = Modifier.fillMaxWidth())
        }
        if (ping.images.isNotEmpty()) {
            FixedImageRow(urls = ping.images.take(3), height = 100.dp, onTap = onOpenImage)
        }
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLike() },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null,
                    tint = if (liked) FarmsyColors.farmGreen else FarmsyColors.inkMuted, modifier = Modifier.size(13.dp),
                )
                if (displayCount > 0) {
                    Text("$displayCount", style = geist(12.sp), color = if (liked) FarmsyColors.farmGreen else FarmsyColors.inkMuted)
                }
            }
            Row(
                Modifier.then(
                    if (reported) Modifier else Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { reported = true; onReport() }
                ),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Flag, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(12.dp))
                Text(
                    stringResource(if (reported) R.string.post_reported else R.string.post_report),
                    style = geist(12.sp), color = FarmsyColors.inkMuted,
                )
            }
        }
    }
}

// MARK: - Review row

/// A single review: name + 5 stars, body, card (iOS ReviewRow, :353).
@Composable
private fun ReviewRow(review: Review) {
    Column(
        Modifier.fillMaxWidth()
            .background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(review.reviewerName, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..5).forEach { i ->
                    Icon(
                        if (i <= review.rating) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                        tint = FarmsyColors.star, modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
        review.body?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = geist(14.sp), color = FarmsyColors.ink, modifier = Modifier.fillMaxWidth())
        }
    }
}

// MARK: - Small helpers

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = geist(17.sp, FontWeight.Bold), color = FarmsyColors.ink)
}

/// A muted note inside a dashed rounded border (iOS dashedNote, :247).
@Composable
private fun DashedNote(text: String) {
    val stroke = Color(0xFFE5E7EB)
    Box(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = geist(13.sp), color = FarmsyColors.inkMuted)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

// Haptics — light tap + success, the S8/C8-established platform-View route.
private fun HapticFeedback.tapTick() = performHapticFeedback(HapticFeedbackType.TextHandleMove)

private fun android.view.View.successTick() {
    if (Build.VERSION.SDK_INT >= 30) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
