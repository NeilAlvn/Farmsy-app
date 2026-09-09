package app.farmsy.android.features.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalTrip
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.core.FarmDetail
import app.farmsy.android.core.FarmDetailApi
import app.farmsy.android.core.FarmDetailException
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.FarmTeaser
import app.farmsy.android.core.FarmFilters
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.features.claim.ClaimSheet
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import app.farmsy.android.ui.theme.FitText
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.NearMe

/// Farm detail — mirrors iOS FarmDetailView. The full payload only exists
/// behind the farmsy.app API's subscription check; without access we show the
/// locked state. No purchase button by design — membership is on the web.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FarmDetailScreen(pin: FarmPin, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val farms = LocalFarms.current
    val trip = LocalTrip.current
    // The PUBLIC photo gallery (farm_images, anon-read), loaded once and shared — the
    // same source iOS's photoStrip uses. detail.images (the members' payload) is empty
    // for a signed-out user and for many farms, so without this a farm with several
    // photos showed only its single cover (iOS loadGallery / farms.galleries).
    val galleries by farms.galleries.collectAsState()
    val galleriesLoaded by farms.galleriesLoaded.collectAsState()
    LaunchedEffect(Unit) { farms.loadGalleriesIfNeeded() }
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()
    val tripStops by trip.stopIds.collectAsState()
    val inTrip = tripStops.contains(pin.osmId)

    val requestAuth = LocalRequestAuth.current
    var detail by remember { mutableStateOf<FarmDetail?>(null) }
    var lightbox by remember { mutableStateOf<LightboxSource?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    // `isLocked` now means SIGNED OUT (the sign-up wall), not "no subscription" — farm
    // details are free for any signed-in account. loadFailed is a transient error for a
    // signed-in user, which must NEVER read as locked (see reload). (Android port of the
    // iOS build 19 sign-up-wall — this screen was still the old paywall.)
    var isLocked by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var teaser by remember { mutableStateOf<FarmTeaser?>(null) }
    var showClaim by remember { mutableStateOf(false) }

    suspend fun reload() {
        isLoading = true; isLocked = false; loadFailed = false
        session.refreshProfile()
        // No session → the sign-up wall (a 401 from fetch means the same thing).
        val token = session.accessToken()
        if (token == null) {
            isLocked = true
            teaser = runCatching { FarmDetailApi.teaser(pin.osmId) }.getOrNull()
            isLoading = false
            return
        }
        // The farmsy.app API is the source of truth — details are a sign-up wall now, so
        // a signed-in account (free or paid) gets the data; only a 401 (no valid session)
        // locks.
        try {
            detail = FarmDetailApi.fetch(pin.osmId, token)
            isLocked = false
        } catch (e: FarmDetailException.Locked) {
            isLocked = true
            teaser = runCatching { FarmDetailApi.teaser(pin.osmId) }.getOrNull()
        } catch (e: Exception) {
            // A signed-in transient failure. NEVER a lock — every signed-in account is
            // entitled to the details, so a network blip must not send a free user to a
            // paywall they're past. Show a retry, whatever the subscription status.
            loadFailed = true
        }
        isLoading = false
    }

    LaunchedEffect(pin.osmId) { reload() }

    // Open the farm the moment the user signs in. Under the sign-up-wall contract
    // `isLocked` means "signed out", so a session appearing (they came back from the
    // auth sheet) is what unlocks it — the screen reloads itself and the details fill in.
    val currentSession by session.session.collectAsState()
    LaunchedEffect(currentSession?.user?.id) {
        if (isLocked && currentSession != null) reload()
    }

    // Photos to show: the members' payload if we have it, else the public gallery, else
    // just the cover — all merged with the cover and de-duplicated, so a farm with a
    // cover plus a gallery shows them all (iOS stripImages).
    val stripImages = remember(detail, galleries, pin) {
        val source = (detail?.images?.takeIf { it.isNotEmpty() } ?: (galleries[pin.osmId] ?: emptyList())).toMutableList()
        pin.image?.let { if (it !in source) source.add(0, it) }
        source.distinct()
    }
    // Photos are ready once the members' payload has them OR the public gallery finished
    // loading — so we skeleton until then instead of flashing one image then popping the
    // rest in (iOS photosReady).
    val photosReady = (detail?.images?.isNotEmpty() == true) || galleriesLoaded
    val isSaved = savedIds.contains(pin.osmId)

    // 1:1 with iOS FarmDetailView: a compact PINNED header (name + heart + share + close),
    // a scrolling body (subheader → trip button → photo strip → details), and a PINNED
    // footer (Directions + Call/Website). The old build put a full-width photo pager at
    // the very top and the action buttons mid-content — a different screen entirely.
    Column(Modifier.fillMaxSize().background(FarmsyColors.cream)) {

        // Pinned header — name + heart / share / close (iOS pinnedHeader).
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pin.name, style = geist(19.sp, FontWeight.Bold), color = FarmsyColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            HeaderCircle(if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isSaved) FarmsyColors.warnRed else Color(0xFF6B7280)) {
                val uid = session.session.value?.user?.id ?: return@HeaderCircle
                scope.launch { favorites.toggle(pin.osmId, uid) }
            }
            Spacer(Modifier.width(6.dp))
            HeaderCircle(Icons.Filled.Share, tint = Color(0xFF6B7280)) {
                val url = "https://www.farmsy.app/farm/${pin.osmId}"
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${pin.name}\n$url") },
                    null,
                ))
            }
            Spacer(Modifier.width(6.dp))
            HeaderCircle(Icons.Filled.Close, tint = Color(0xFF6B7280), onClick = onBack)
        }

        // Scrolling body.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sub-header: rating row + location line + badge row (iOS subHeader).
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Rating — "★ 4.5 (12)", or "No reviews yet" when there are none.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pin.avgRating != null) {
                        Icon(Icons.Filled.Star, null, tint = FarmsyColors.star, modifier = Modifier.size(13.dp))
                        Text("%.1f".format(pin.avgRating), style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
                        Text("(${pin.reviewCount})", style = geist(12.sp), color = FarmsyColors.inkMuted)
                    } else {
                        Text(stringResource(R.string.no_reviews_yet), style = geist(13.sp), color = FarmsyColors.inkMuted)
                    }
                }
                // Public location — town + country (the full street address stays in the
                // members' details list).
                val locationLine = listOfNotNull(pin.city, pin.country).joinToString(", ")
                if (locationLine.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.LocationOn, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(12.dp))
                        Text(locationLine, style = geist(14.sp), color = FarmsyColors.inkMuted)
                    }
                }
                // Badge row — category capsules (in their colours) + Verified + Open now,
                // one line that scrolls horizontally (iOS badgeRow).
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pin.categories.take(4).forEach { cat ->
                        Text(
                            "${cat.emoji} ${stringResource(cat.labelRes)}",
                            style = geist(11.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1,
                            modifier = Modifier.background(cat.color, CircleShape).padding(vertical = 4.dp, horizontal = 10.dp),
                        )
                    }
                    if (pin.isVerified) {
                        Row(
                            Modifier.border(1.dp, FarmsyColors.farmGreenMap.copy(alpha = 0.4f), CircleShape).padding(vertical = 4.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Verified, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(10.dp))
                            Text(stringResource(R.string.verified), style = geist(11.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreenMap, maxLines = 1)
                        }
                    }
                    if (FarmFilters.isOpenToday(pin.openingHours)) {
                        Row(
                            Modifier.background(Color(0xFFECFDF5), CircleShape).padding(vertical = 4.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            Text(stringResource(R.string.open_now), style = geist(11.sp, FontWeight.SemiBold), color = Color(0xFF047857), maxLines = 1)
                        }
                    }
                }
            }

            // Trip button — outlined "Add to trip" / filled "In your trip" (iOS tripButton).
            Box(Modifier.padding(horizontal = 14.dp)) {
                if (isLoading && detail == null && teaser == null) {
                    SkeletonBox(16.dp, Modifier.fillMaxWidth().height(44.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth()
                            .then(
                                if (inTrip) Modifier.background(FarmsyColors.farmGreenMap, RoundedCornerShape(16.dp))
                                else Modifier.border(1.5.dp, FarmsyColors.farmGreen, RoundedCornerShape(16.dp))
                            )
                            .clickable { if (session.isAuthenticated) trip.toggle(pin.osmId) else requestAuth() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (inTrip) Icons.Filled.Check else Icons.Filled.Add, null,
                            tint = if (inTrip) Color.White else FarmsyColors.farmGreen, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (inTrip) R.string.in_your_trip else R.string.add_to_trip),
                            style = geist(14.sp, FontWeight.SemiBold),
                            color = if (inTrip) Color.White else FarmsyColors.farmGreen,
                        )
                    }
                }
            }

            // Photo strip — 1 big cover + 2 thumbnails + "+N", tapping opens the lightbox
            // (iOS photoStrip). Skeleton while loading; gradient + emoji when no photos.
            Box(Modifier.padding(horizontal = 14.dp)) {
                PhotoStrip(
                    images = stripImages,
                    loading = !photosReady && stripImages.isEmpty(),
                    emoji = pin.primaryCategory.emoji,
                    onOpen = { start ->
                        lightbox = LightboxSource(
                            images = stripImages, startIndex = start,
                            eyebrow = context.getString(R.string.farm_photo), title = pin.name,
                        )
                    },
                )
            }

            // The middle block — one of: loading skeleton / sign-up wall / retry / details.
            Column(Modifier.padding(horizontal = 14.dp)) {
                when {
                    isLoading -> CardSkeleton()
                    // Signed out → the sign-up wall SECTION (teaser + blurred faux bars +
                    // "See this farm, free"). Details are free for any signed-in account.
                    isLocked -> LockedSections(pin = pin, teaser = teaser, onSignIn = requestAuth)
                    // Signed-in transient failure — retry, never a lock.
                    loadFailed -> InlineLoadError(onRetry = { scope.launch { reload() } })
                    else -> {
                        // iOS detailSections = description + FarmMemberSections, nothing
                        // else. The Hours/Address/Phone/Email/Produce rows live INSIDE
                        // FarmMemberSections (with icons + phone/email links) — the screen
                        // was rendering its own duplicate, worse copy of them on top, and
                        // an invented "no details yet" card iOS doesn't have. Removed both.
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            detail?.description?.takeIf { it.isNotEmpty() }?.let {
                                Text(it, style = geist(16.sp).copy(lineHeight = 22.sp), color = FarmsyColors.ink)
                            }
                            FarmMemberSections(pin = pin, detail = detail, onClaim = { showClaim = true })
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Pinned footer — Directions (outline) + Call/Website (filled) (iOS footer).
        val phone = detail?.phone ?: pin.phone
        val site = detail?.website ?: pin.website
        Row(
            Modifier.fillMaxWidth()
                .drawBehind { drawLine(FarmsyColors.hairline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f) }
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FooterButton(Icons.Filled.NearMe, stringResource(R.string.directions), filled = false, modifier = Modifier.weight(1f)) {
                if (isLocked) requestAuth()
                else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${pin.name})")))
            }
            if (phone != null) {
                FooterButton(Icons.Filled.Call, stringResource(R.string.call), filled = true, modifier = Modifier.weight(1f)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
            } else if (site != null) {
                FooterButton(Icons.Filled.Public, stringResource(R.string.website), filled = true, modifier = Modifier.weight(1f)) {
                    val u = if (site.startsWith("http")) site else "https://$site"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                }
            }
        }
    }

    lightbox?.let { src -> ImageLightbox(source = src, onClose = { lightbox = null }) }
    if (showClaim) ClaimSheet(pin = pin, onDismiss = { showClaim = false })
}

/// iOS photoStrip — 1 big cover (160) + a 84-wide column of two 76-tall thumbnails, the
/// last carrying "+N" when there are more. One photo = just the cover; none = a green
/// gradient with the category emoji; loading = a matching 3-slot skeleton.
@Composable
private fun PhotoStrip(images: List<String>, loading: Boolean, emoji: String, onOpen: (Int) -> Unit) {
    when {
        loading -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(16.dp, Modifier.weight(1f).height(160.dp))
            Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(12.dp, Modifier.fillMaxWidth().height(76.dp))
                SkeletonBox(12.dp, Modifier.fillMaxWidth().height(76.dp))
            }
        }
        images.isEmpty() -> Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(FarmsyColors.farmGreen.copy(alpha = 0.85f), FarmsyColors.farmGreenDeep)
                    ),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 56.sp) }
        images.size == 1 -> PhotoTile(images[0], 16.dp, Modifier.fillMaxWidth().height(160.dp)) { onOpen(0) }
        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoTile(images[0], 16.dp, Modifier.weight(1f).height(160.dp)) { onOpen(0) }
            Column(Modifier.width(84.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoTile(images[1], 12.dp, Modifier.fillMaxWidth().height(76.dp)) { onOpen(1) }
                if (images.size > 2) {
                    val plusN = if (images.size > 3) images.size - 3 else null
                    PhotoTile(images[2], 12.dp, Modifier.fillMaxWidth().height(76.dp), plusN = plusN) {
                        onOpen(if (images.size > 3) 3 else 2)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(76.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp)))
                }
            }
        }
    }
}

/// One photo tile — a base rectangle carries the size, the image fills and is clipped, so
/// it can never push past its bounds; an optional "+N" veil on the last thumbnail.
@Composable
private fun PhotoTile(url: String, radius: Dp, modifier: Modifier = Modifier, plusN: Int? = null, onOpen: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Color(0xFFF3F4F6)).clickable { onOpen() },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (plusN != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Text("+$plusN", style = geist(14.sp, FontWeight.Bold), color = Color.White)
            }
        }
    }
}

/// A 3-line grey skeleton for the detail rows while they load (iOS cardSkeleton).
@Composable
private fun CardSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFECEBE8), RoundedCornerShape(6.dp)))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).background(Color(0xFFECEBE8), RoundedCornerShape(6.dp)))
        Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(Color(0xFFECEBE8), RoundedCornerShape(6.dp)))
    }
}

/// A header circle button (heart / share / close) — 32dp, grey fill (iOS headerCircle).
@Composable
private fun HeaderCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).background(Color(0xFFF3F4F6), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp)) }
}

/// A pinned-footer action — filled green (Call/Website) or outlined (Directions).
@Composable
private fun FooterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.then(
            if (filled) Modifier.background(FarmsyColors.farmGreenMap, RoundedCornerShape(16.dp))
            else Modifier.border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
        ).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else FarmsyColors.ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = geist(14.sp, FontWeight.SemiBold), color = if (filled) Color.White else FarmsyColors.ink, maxLines = 1)
    }
}

/// The signed-out sign-up SECTION — the middle block of the detail scaffold, not a whole
/// screen (iOS FarmDetailView.lockedSections/lockedBlock). Teaser text if we have it,
/// then the membership ask floating over BLURRED faux-content bars, so the region reads
/// as "there is more here". NOT the purchase paywall — details are free for any signed-in
/// account, so the ask is to sign up, not to pay.
@Composable
private fun LockedSections(pin: FarmPin, teaser: FarmTeaser?, onSignIn: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        teaser?.let { t ->
            Text(
                t.text + if (t.truncated) " …" else "",
                style = geist(15.sp).copy(lineHeight = 21.sp),
                color = FarmsyColors.ink,
            )
        }

        // The ask sits in the middle of a blurred region — grey bars falling away above
        // and below it, so the area reads as real (hidden) content, not an empty card.
        // Nothing behind the blur is real: the paid values are never sent, so these are
        // empty bars (iOS lockedBlock + lockedBarsBackground).
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp), contentAlignment = Alignment.Center) {
            LockedBarsBackground(
                Modifier.matchParentSize()
                    .blur(7.dp)
                    .alpha(0.6f),
            )
            Column(
                Modifier.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(48.dp).background(FarmsyColors.farmGreen.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.LockOpen, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(20.dp)) }
                Text(
                    stringResource(R.string.see_this_farm_free),
                    style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    stringResource(R.string.one_free_account_opens_every_farm),
                    style = geist(14.sp).copy(lineHeight = 20.sp), color = FarmsyColors.inkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(FarmsyColors.farmGreenMap, RoundedCornerShape(16.dp))
                        .clickable { onSignIn() }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.create_a_free_account), style = geist(15.sp, FontWeight.SemiBold), color = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/// Faux content behind the lock: uneven grey bars top and bottom, so the blurred area
/// reads as "there is more here" around the centred ask (iOS lockedBarsBackground).
@Composable
private fun LockedBarsBackground(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.78f, 0.95f, 0.6f, 0.88f).forEach { fraction ->
                Box(Modifier.fillMaxWidth(fraction).height(14.dp).background(FarmsyColors.hairline, RoundedCornerShape(4.dp)))
            }
        }
        Spacer(Modifier.height(60.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.7f, 0.9f, 0.5f).forEach { fraction ->
                Box(Modifier.fillMaxWidth(fraction).height(14.dp).background(FarmsyColors.hairline, RoundedCornerShape(4.dp)))
            }
        }
    }
}

/// A signed-in transient load failure, as an inline section — a retry, never a lock: a
/// signed-in account is entitled to the details, so a network blip must not read as a wall.
@Composable
private fun InlineLoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().card(20),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌾", fontSize = 34.sp)
        Text(
            stringResource(R.string.couldn_t_load_this_farm),
            style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(
            Modifier.background(FarmsyColors.farmGreenSoft, RoundedCornerShape(14.dp))
                .clickable { onRetry() }.padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.try_again), style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
        }
    }
}
