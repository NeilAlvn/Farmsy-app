package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

/// The two-per-page recommendation shelf — mirrors iOS TripRecommendations: a
/// horizontal pager showing two cover-photo cards at a time, with save hearts,
/// category tags, and a `< • • • >` pager. Picks photo'd farms near the user
/// (falling back to a random set), minus anything already hearted; renders
/// nothing when there's nothing sensible to show. Reused by Trips once it lands.
@Composable
fun RecommendationCarousel(
    onOpenFarm: (FarmPin) -> Unit,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 150.dp,
) {
    val farms = LocalFarms.current
    val favorites = LocalFavorites.current
    val locationHelper = LocalLocationHelper.current
    val trip = LocalTrip.current
    val pins by farms.pins.collectAsState()
    val loc by locationHelper.location.collectAsState()
    val galleries by farms.galleries.collectAsState()
    val stopIds by trip.stopIds.collectAsState()
    val originCoord by trip.originCoord.collectAsState()
    val plannedFarmIds by trip.plannedFarmIds.collectAsState()

    var shown by remember { mutableStateOf<List<FarmPin>>(emptyList()) }
    var built by remember { mutableStateOf(false) }

    // The anchor to recommend around — GPS, else the draft origin, else the centroid
    // of the draft's stops, else the centroid of every already-planned farm. 1:1 with
    // iOS TripRecommendations.anchor.
    fun centroid(ids: Collection<String>): LatLng? {
        val coords = ids.mapNotNull { farms.pinForOsmId(it)?.let { p -> LatLng(p.lat, p.lng) } }
        if (coords.isEmpty()) return null
        return LatLng(coords.sumOf { it.latitude } / coords.size, coords.sumOf { it.longitude } / coords.size)
    }
    val anchor: LatLng? = loc?.let { LatLng(it.latitude, it.longitude) }
        ?: originCoord
        ?: centroid(stopIds)
        ?: centroid(plannedFarmIds)
    val hasAnchor = anchor != null

    // Build once the pins are in; rebuild when the anchor/inputs change. Selection is
    // 1:1 with iOS TripRecommendations.select(): exclude planned + stops + favourites;
    // pool = nearbyWithImages(anchor, 100km) [else random photo'd, shuffled]; partition
    // into described (has a gallery) and plain; described.take(6)+plain.take(6),
    // shuffled, take 6.
    LaunchedEffect(pins.size, anchor?.latitude, anchor?.longitude, stopIds, plannedFarmIds, galleries) {
        // iOS load(): make sure the galleries are in *before* selecting (so the
        // described/plain partition is real), then guard on pins. Idempotent — the
        // store no-ops once loaded, and the `galleries` key re-runs this when they land.
        farms.loadGalleriesIfNeeded()
        if (pins.isEmpty()) return@LaunchedEffect
        val excluded = plannedFarmIds + stopIds.toSet() + favorites.osmIds.value
        val pool: List<FarmPin> = if (anchor != null) {
            farms.nearbyWithImages(anchor.latitude, anchor.longitude, radiusKm = 100.0)
                .filter { it.osmId !in excluded }
        } else {
            pins.filter { it.image != null && it.osmId !in excluded }.shuffled()
        }
        fun hasGallery(p: FarmPin) = (galleries[p.osmId]?.isEmpty() == false)
        val described = pool.filter { hasGallery(it) }
        val plain = pool.filter { !hasGallery(it) }
        val picked = described.take(6) + plain.take(6)
        shown = picked.shuffled().take(6)
        built = true
    }

    // Nothing sensible → render nothing (no empty state), same as iOS.
    if (built && shown.isEmpty()) return

    val pages = remember(shown) { shown.chunked(2) }
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            // "RECOMMENDATIONS NEAR YOU" when there's an anchor, else "RECOMMENDATION"
            // — keyed on hasAnchor, matching iOS (not GPS-only).
            stringResource(if (hasAnchor) R.string.rec_header_near_you else R.string.rec_header),
            style = geist(11.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        if (!built) {
            // iOS loading: 2× SkeletonBox at radius 16, height = cardHeight.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    SkeletonBox(
                        cornerRadius = 16.dp,
                        modifier = Modifier.weight(1f).height(cardHeight),
                    )
                }
            }
        } else {
            HorizontalPager(state = pager, pageSpacing = 12.dp) { idx ->
                val pair = pages[idx]
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { pin ->
                        RecCard(pin, cardHeight, Modifier.weight(1f), onOpenFarm)
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (pages.size > 1) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val page = pager.currentPage
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft, null,
                        tint = if (page == 0) FarmsyColors.inkMuted.copy(alpha = 0.35f) else FarmsyColors.ink,
                        modifier = Modifier.size(22.dp).clickable(enabled = page > 0) {
                            scope.launch { pager.animateScrollToPage(page - 1) }
                        },
                    )
                    Spacer(Modifier.size(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pages.size) { i ->
                            Box(
                                Modifier.size(7.dp).background(
                                    if (i == page) FarmsyColors.farmGreen else FarmsyColors.inkMuted.copy(alpha = 0.3f),
                                    CircleShape,
                                )
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = if (page >= pages.size - 1) FarmsyColors.inkMuted.copy(alpha = 0.35f) else FarmsyColors.ink,
                        modifier = Modifier.size(22.dp).clickable(enabled = page < pages.size - 1) {
                            scope.launch { pager.animateScrollToPage(page + 1) }
                        },
                    )
                }
            }
        }
    }
}

/// A recommended farm as a cover-photo card: full-bleed photo, save heart,
/// category tags, name + city over a legibility scrim.
@Composable
private fun RecCard(
    pin: FarmPin,
    cardHeight: Dp,
    modifier: Modifier,
    onOpenFarm: (FarmPin) -> Unit,
) {
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()
    val saved = savedIds.contains(pin.osmId)

    Box(
        modifier.height(cardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEDE7DD))
            .clickable { onOpenFarm(pin) },
    ) {
        if (pin.image != null) {
            AsyncImage(
                model = pin.image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(pin.primaryCategory.emoji, fontSize = 30.sp)
            }
        }
        // Legibility scrim under the label.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
            )
        )
        // Save heart
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                .clickable {
                    val uid = session.session.value?.user?.id
                    if (uid == null) requestAuth() else scope.launch { favorites.toggle(pin.osmId, uid) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                tint = if (saved) FarmsyColors.warnRed else FarmsyColors.ink,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                pin.name, style = geist(14.sp, FontWeight.Bold), color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            pin.city?.let {
                Text(it, style = geist(12.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            val tags = pin.categories.take(2)
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tags.forEach { cat ->
                        Text(
                            stringResource(cat.labelRes).uppercase(),
                            style = geist(9.sp, FontWeight.Bold), color = Color.White,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.22f), CircleShape)
                                .padding(vertical = 3.dp, horizontal = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
