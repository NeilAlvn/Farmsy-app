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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
    val trip = LocalTrip.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()
    val tripStops by trip.stopIds.collectAsState()
    val inTrip = tripStops.contains(pin.osmId)

    val requestAuth = LocalRequestAuth.current
    var detail by remember { mutableStateOf<FarmDetail?>(null) }
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

    Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        if (isLocked) {
            // Signed out → the sign-up wall (teaser + "create a free account"), NOT the
            // purchase paywall. Farm details are free for any signed-in account.
            LockedSignUpContent(pin = pin, teaser = teaser, onSignIn = requestAuth, onBack = onBack)
        } else if (loadFailed) {
            // A signed-in transient failure — offer a retry, never a lock.
            DetailLoadFailed(onRetry = { scope.launch { reload() } }, onBack = onBack)
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Gallery
                val urls = (detail?.images?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(detail?.image ?: pin.image))
                if (urls.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(210.dp).background(FarmsyColors.farmGreen),
                        contentAlignment = Alignment.Center
                    ) { Text(pin.primaryCategory.emoji, fontSize = 56.sp) }
                } else {
                    // Swipe through every photo, with dots — Android was showing only
                    // the first one while iOS paged through all of them, so a farm
                    // with five pictures had four of them invisible on half our users'
                    // phones.
                    val pager = rememberPagerState { urls.size }
                    Box {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        ) { page ->
                            AsyncImage(
                                model = urls[page],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (urls.size > 1) {
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(urls.size) { i ->
                                    Box(
                                        Modifier
                                            .size(if (i == pager.currentPage) 8.dp else 6.dp)
                                            .background(
                                                Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f),
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                Column(Modifier.padding(20.dp)) {
                    // Wrap, don't squeeze. A fixed Row gave the last chip whatever
                    // width was left over, so "Wine" got crushed to a couple of
                    // characters wide and its label broke across two lines, turning
                    // the pill into a blob. Chips keep their natural width and spill
                    // onto a second line instead.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pin.categories.take(4).forEach { cat ->
                            Text(
                                "${cat.emoji} ${stringResource(cat.labelRes)}",
                                style = geist(12.sp, FontWeight.SemiBold), color = FarmsyColors.ink,
                                maxLines = 1,
                                modifier = Modifier.background(cat.color.copy(alpha = 0.14f), CircleShape)
                                    .padding(vertical = 5.dp, horizontal = 9.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // Farm names run long ("Kerstbomen Van Ginhoven"); shrink rather
                    // than wrap under the chips.
                    FitText(pin.name, style = display(30.sp), color = FarmsyColors.ink)
                    pin.city?.let {
                        Text(it, style = geist(15.sp), color = FarmsyColors.inkMuted)
                    }
                    Spacer(Modifier.height(16.dp))

                    // Action buttons. Icons match the iOS row (phone / globe / turn
                    // arrow); when a farm has no number and no website — plenty
                    // don't — Directions was left alone at full width, which read as
                    // a mistake rather than an absence.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val phone = detail?.phone ?: pin.phone
                        val site = detail?.website ?: pin.website
                        val lonely = phone == null && site == null

                        if (phone != null) ActionButton(
                            Icons.Filled.Call, stringResource(R.string.call),
                            Color(0xFF2563EB), Modifier.weight(1f)
                        ) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }
                        if (site != null) ActionButton(
                            Icons.Filled.Public, stringResource(R.string.web),
                            Color(0xFFF97316), Modifier.weight(1f)
                        ) {
                            val u = if (site.startsWith("http")) site else "https://$site"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        }
                        ActionButton(
                            Icons.Filled.NearMe, stringResource(R.string.directions),
                            FarmsyColors.farmGreen,
                            if (lonely) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f)
                        ) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${pin.name})"))
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Add-to-trip — the one entry point into the trip planner: filled
                    // green to add, soft green with a check once it's a stop.
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (inTrip) FarmsyColors.farmGreenSoft else FarmsyColors.farmGreen, RoundedCornerShape(14.dp))
                            .clickable { trip.toggle(pin.osmId) }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (inTrip) Icons.Filled.Check else Icons.Filled.Add, null,
                            tint = if (inTrip) FarmsyColors.farmGreen else Color.White, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(if (inTrip) R.string.in_trip else R.string.add_to_trip),
                            style = geist(14.sp, FontWeight.Bold),
                            color = if (inTrip) FarmsyColors.farmGreen else Color.White,
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FarmsyColors.farmGreen)
                        }
                    } else {
                        detail?.description?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = geist(16.sp), color = FarmsyColors.ink)
                            Spacer(Modifier.height(16.dp))
                        }

                        // Gather the rows first. The card used to render regardless,
                        // so a farm with no hours, address, email, owner or produce —
                        // and plenty have none — got an empty grey slab sitting under
                        // the buttons, which reads as a broken component rather than
                        // an absence of data.
                        val rows = listOfNotNull(
                            (detail?.openingHours ?: pin.openingHours)
                                ?.let { stringResource(R.string.opening_hours) to it },
                            (detail?.address ?: pin.address)?.let {
                                stringResource(R.string.address) to
                                    listOfNotNull(it, detail?.postalCode ?: pin.postalCode, pin.city)
                                        .joinToString(", ")
                            },
                            detail?.email?.let { stringResource(R.string.email) to it },
                            detail?.operatorName?.let { stringResource(R.string.run_by) to it },
                            detail?.produce?.takeIf { it.isNotEmpty() }
                                ?.let { stringResource(R.string.produce) to it },
                        )

                        if (rows.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().card(6)) {
                                rows.forEach { (label, value) -> InfoRow(label, value) }
                            }
                        } else {
                            // Say the thing out loud instead of leaving a void: this
                            // farm simply hasn't been filled in yet, and the person
                            // best placed to fix that is the owner reading this.
                            Column(
                                Modifier.fillMaxWidth().card(20),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🌾", fontSize = 34.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.no_details_yet),
                                    style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.no_details_yet_body),
                                    style = geist(14.sp), color = FarmsyColors.inkMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // S9 · Member sections — the reviews block (summary + list +
                        // your-own composer), the farm's What's-New posts (+ composer),
                        // and the claim block. 1:1 with iOS FarmDetailView.detailSections.
                        // Supersedes the old standalone claim link: FarmMemberSections
                        // carries its own claim block, wired to the same native ClaimSheet
                        // through showClaim.
                        FarmMemberSections(
                            pin = pin,
                            detail = detail,
                            onClaim = { showClaim = true },
                        )
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        // Top bar: back + favorite. No statusBarsPadding: this always renders inside
        // the detented sheet (capped at 0.92, never full-screen), whose top is well
        // below the status bar — status-bar padding here just added phantom top space.
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            val isSaved = savedIds.contains(pin.osmId)
            CircleIconButton(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isSaved) FarmsyColors.warnRed else FarmsyColors.ink
            ) {
                val uid = session.session.value?.user?.id ?: return@CircleIconButton
                scope.launch { favorites.toggle(pin.osmId, uid) }
            }
        }
    }

    if (showClaim) ClaimSheet(pin = pin, onDismiss = { showClaim = false })
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    fill: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.background(fill, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label, style = geist(15.sp, FontWeight.Bold), color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(12.dp)) {
        Text(label, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
        Text(value, style = geist(15.sp), color = FarmsyColors.ink)
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = FarmsyColors.ink,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(38.dp).background(Color.White.copy(alpha = 0.95f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/// The sign-up wall shown when a signed-OUT user opens a farm — the Android port of
/// iOS build 19 (FarmDetailView.lockedSections/lockedBlock). Teaser text if we have it,
/// then an OPEN-padlock block: "See this farm, free" → create a free account. NOT the
/// purchase paywall — details are free for any signed-in account, so the ask is to sign
/// up, not to pay.
@Composable
private fun LockedSignUpContent(
    pin: FarmPin,
    teaser: FarmTeaser?,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(pin.name, style = display(24.sp, FontWeight.Bold), color = FarmsyColors.ink)

            teaser?.let { t ->
                Text(
                    t.text + if (t.truncated) " …" else "",
                    style = geist(15.sp).copy(lineHeight = 21.sp),
                    color = FarmsyColors.ink,
                )
            }

            // The open-padlock sign-up block (iOS lockedBlock).
            Column(
                Modifier.fillMaxWidth().card(22),
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
            Spacer(Modifier.height(30.dp))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
        }
    }
}

/// A signed-in transient load failure — a farm we couldn't reach, offered with a retry.
/// Never a lock: a signed-in account is entitled to the details, so a network blip must
/// not read as a paywall.
@Composable
private fun DetailLoadFailed(onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
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
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
        }
    }
}
