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
import androidx.compose.foundation.layout.statusBarsPadding
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
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmDetail
import app.farmsy.android.core.FarmDetailApi
import app.farmsy.android.core.FarmDetailException
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.claim.ClaimSheet
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import app.farmsy.android.ui.theme.FitText

/// Farm detail — mirrors iOS FarmDetailView. The full payload only exists
/// behind the farmsy.app API's subscription check; without access we show the
/// locked state. No purchase button by design — membership is on the web.
@Composable
fun FarmDetailScreen(pin: FarmPin, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()

    var detail by remember { mutableStateOf<FarmDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showClaim by remember { mutableStateOf(false) }

    suspend fun reload() {
        isLoading = true; isLocked = false
        val token = session.accessToken()
        if (token == null) { isLocked = true; isLoading = false; return }
        try {
            detail = FarmDetailApi.fetch(pin.osmId, token)
        } catch (e: FarmDetailException.Locked) {
            isLocked = true
        } catch (e: Exception) {
            if (!session.hasFullAccess) isLocked = true
        }
        isLoading = false
    }

    LaunchedEffect(pin.osmId) { session.refreshProfile(); reload() }

    Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        if (isLocked) {
            LockedAccessView(pin = pin, onClaim = { showClaim = true }) { reload() }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Gallery
                val urls = (detail?.images?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(detail?.image ?: pin.image))
                if (urls.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(210.dp).background(FarmsyColors.farmGreen),
                        contentAlignment = Alignment.Center
                    ) { Text(pin.primaryCategory.emoji, fontSize = 64.sp) }
                } else {
                    AsyncImage(
                        model = urls.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }

                Column(Modifier.padding(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pin.categories.take(4).forEach { cat ->
                            Text(
                                "${cat.emoji} ${stringResource(cat.labelRes)}",
                                style = geist(12.sp, FontWeight.SemiBold), color = FarmsyColors.ink,
                                modifier = Modifier.background(cat.color.copy(alpha = 0.14f), CircleShape)
                                    .padding(vertical = 5.dp, horizontal = 9.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(pin.name, style = display(30.sp), color = FarmsyColors.ink)
                    pin.city?.let {
                        Text(it, style = geist(15.sp), color = FarmsyColors.inkMuted)
                    }
                    Spacer(Modifier.height(16.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val phone = detail?.phone ?: pin.phone
                        if (phone != null) ActionButton(stringResource(R.string.call), Color(0xFF2563EB), Modifier.weight(1f)) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }
                        val site = detail?.website ?: pin.website
                        if (site != null) ActionButton(stringResource(R.string.web), Color(0xFFF97316), Modifier.weight(1f)) {
                            val u = if (site.startsWith("http")) site else "https://$site"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        }
                        ActionButton(stringResource(R.string.directions), FarmsyColors.farmGreen, Modifier.weight(1f)) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${pin.name})"))
                            )
                        }
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
                        Column(Modifier.fillMaxWidth().card(6)) {
                            (detail?.openingHours ?: pin.openingHours)?.let {
                                InfoRow(stringResource(R.string.opening_hours), it)
                            }
                            (detail?.address ?: pin.address)?.let {
                                InfoRow(
                                    stringResource(R.string.address),
                                    listOfNotNull(it, detail?.postalCode ?: pin.postalCode, pin.city).joinToString(", ")
                                )
                            }
                            detail?.email?.let { InfoRow(stringResource(R.string.email), it) }
                            detail?.operatorName?.let { InfoRow(stringResource(R.string.run_by), it) }
                            detail?.produce?.takeIf { it.isNotEmpty() }?.let {
                                InfoRow(stringResource(R.string.produce), it)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Claim link
                        Row(
                            Modifier.fillMaxWidth()
                                .background(FarmsyColors.farmGreenSoft, RoundedCornerShape(14.dp))
                                .clickable { showClaim = true }.padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.is_this_your_farm_claim_it),
                                style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen
                            )
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        // Top bar: back + favorite
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
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

/// One purchasable plan on the paywall.
///
/// Deliberately two lines — label above, price below — so the text stays short
/// enough to survive a large system font scale. `detail` is null until the store
/// hands back a localized price, in which case we fall back to the generic CTA.
@Composable
private fun PlanCard(
    label: String,
    detail: String?,
    filled: Boolean,
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
            if (detail == null) stringResource(R.string.become_a_member) else label,
            style = geist(17.sp, FontWeight.SemiBold), color = fg,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        detail?.let {
            FitText(
                it, style = geist(14.sp),
                color = if (filled) Color.White.copy(alpha = 0.9f) else FarmsyColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, fill: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.background(fill, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, style = geist(15.sp, FontWeight.Bold), color = Color.White)
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

/// Members-only state — mirrors iOS LockedAccessView. No purchase CTA, no
/// external links; owners can still claim.
@Composable
private fun LockedAccessView(pin: FarmPin, onClaim: () -> Unit, onRecheck: suspend () -> Unit) {
    val context = LocalContext.current
    val session = app.farmsy.android.LocalSession.current
    val purchases = app.farmsy.android.LocalPurchases.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    val isPurchasing by purchases.isPurchasing.collectAsState()
    val purchaseError by purchases.purchaseError.collectAsState()
    val yearlyPkg by purchases.yearly.collectAsState()
    val lifetimePkg by purchases.lifetime.collectAsState()
    val offeringFailed by purchases.offeringFailed.collectAsState()
    val currentSession by session.session.collectAsState()
    val userId = currentSession?.user?.id
    // Usually a no-op: prices are prefetched at launch. Only actually fetches if
    // that failed (offline at start, say).
    LaunchedEffect(Unit) { purchases.loadOffering() }

    // Access is granted by the server after RevenueCat's webhook writes
    // subscription_status — which lands a few seconds *after* the purchase call
    // returns. Re-checking once, immediately, races the webhook and finds the
    // profile still 'free', leaving a paid-up buyer staring at the paywall. Poll
    // until the grant shows up, and stop the moment it does rather than sitting on
    // a spinner for the full budget.
    suspend fun awaitGrant() {
        checking = true
        repeat(8) {
            onRecheck()
            if (session.hasFullAccess) return@repeat
            kotlinx.coroutines.delay(1500)
        }
        checking = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        app.farmsy.android.ui.theme.Kicker(stringResource(R.string.members_only))
        app.farmsy.android.ui.theme.DisplayTitle(
            leading = stringResource(R.string.unlock_every_farm_s),
            emphasis = stringResource(R.string.full_story),
            size = 32.sp
        )
        Column(Modifier.fillMaxWidth().card(22), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.your_account_doesn_t_have_full_access_yet),
                style = geist(19.sp, FontWeight.Bold), color = FarmsyColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.full_access_opening_hours_contact_details_photos_and_more_fo, pin.name),
                style = geist(15.sp), color = FarmsyColors.inkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        purchaseError?.let {
            Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed)
        }

        // Buy. The server grants access (RevenueCat webhook writes
        // subscription_status), so after a purchase we re-ask the API rather
        // than trusting the client.
        when {
            isPurchasing || checking -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
            // The store never gave us prices. Say so and offer a retry — a spinner
            // that never resolves is worse than an honest failure.
            offeringFailed && purchases.yearlyPrice == null -> {
                FitText(
                    stringResource(R.string.membership_unavailable),
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(stringResource(R.string.try_again)) {
                    scope.launch { purchases.loadOffering(force = true) }
                }
            }
            // Offering still loading — a spinner rather than a half-drawn paywall
            // (the old code flashed a lone "Become a member" button, then the real
            // cards once prices arrived).
            purchases.yearlyPrice == null -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
            else -> {
                // Two matched cards, tight together: a short label on top, price
                // below. Both go through PlanCard so they're the same height and
                // shape — one filled, one outlined.
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlanCard(
                        label = stringResource(R.string.plan_yearly),
                        detail = purchases.yearlyPrice?.let { stringResource(R.string.price_per_year_arg, it) },
                        filled = true,
                    ) {
                        val activity = context as? android.app.Activity ?: return@PlanCard
                        scope.launch { if (purchases.purchase(activity, yearlyPkg, userId)) awaitGrant() }
                    }
                    purchases.lifetimePrice?.let { price ->
                        PlanCard(
                            label = stringResource(R.string.plan_lifetime),
                            detail = "$price · ${stringResource(R.string.one_payment_yours_forever)}",
                            filled = false,
                        ) {
                            val activity = context as? android.app.Activity ?: return@PlanCard
                            scope.launch { if (purchases.purchase(activity, lifetimePkg, userId)) awaitGrant() }
                        }
                    }
                }
                // Restore only. "I subscribed on the web" is gone — the app already
                // re-checks the server on open, so web subscribers get access without
                // it, and a manual "I paid elsewhere" control reads as sketchy.
                FitText(
                    stringResource(R.string.restore_purchases),
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                    modifier = Modifier.clickable {
                        scope.launch { if (purchases.restore()) awaitGrant() }
                    }
                )
            }
        }

        FitText(
            stringResource(R.string.is_arg_yours_claim_it, pin.name),
            style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
            modifier = Modifier.clickable { onClaim() }
        )
    }
}
