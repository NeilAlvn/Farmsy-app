package app.farmsy.android.features.detail

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Verified
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalPurchases
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.FitText
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// S8 · LockedAccessView — the paywall shown inside the FarmDetail gate when a
/// non-member opens a farm. Ported 1:1 from iOS `LockedAccessView` (FarmDetailView.
/// swift:716). Presented from `FarmDetailScreen7`'s `showPaywall` in a
/// ModalBottomSheet. Reads FarmsStore / PurchaseStore / SessionStore; presented in
/// the detented sheet (no status-bar padding).
///
/// (History: an earlier design of this view lived as a private helper in the old
/// `FarmDetailScreen.kt`; that file was deleted at S7 Pass 3 and this replaced it.)
///
/// `onRecheck` mirrors the iOS param — it isn't called from the body (the profile
/// poll in `awaitGrant` nudges the profile and the host FarmDetail watches it and
/// opens the farm itself), kept for signature parity with the call site.
///
@Composable
fun LockedAccessView(pin: FarmPin, onClaim: () -> Unit = {}, onRecheck: suspend () -> Unit = {}) {
    val context = LocalContext.current
    val session = LocalSession.current
    val purchases = LocalPurchases.current
    val farms = LocalFarms.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    // iOS Haptics.success() (UINotificationFeedbackGenerator .success). The platform
    // View exposes CONFIRM on API 30+; below that fall back to the light tick (same
    // approach SplashScreen uses for a platform-level haptic).
    val successHaptic: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    var isChecking by remember { mutableStateOf(false) }

    val isPurchasing by purchases.isPurchasing.collectAsState()
    val purchaseError by purchases.purchaseError.collectAsState()
    val yearlyPkg by purchases.yearly.collectAsState()
    val lifetimePkg by purchases.lifetime.collectAsState()
    val didLoadOffering by purchases.didLoadOffering.collectAsState()
    val pins by farms.pins.collectAsState()
    val profile by session.profile.collectAsState()
    val userId = session.session.collectAsState().value?.user?.id

    // A returning member whose subscription lapsed — frame as "welcome back /
    // resubscribe", not a first-time "become a member" (iOS isExpired:749).
    val status = profile?.subscriptionStatus
    val isExpired = !session.hasFullAccess && (status == "canceled" || status == "expired")
    // iOS productsUnavailable: the offering loaded but the store returned no yearly
    // package (PurchaseStore.productsUnavailable = didLoadOffering && yearly == null).
    val productsUnavailable = didLoadOffering && yearlyPkg == null

    // Usually a no-op: prices are prefetched at launch (iOS `.task { loadOffering() }`).
    LaunchedEffect(Unit) { purchases.loadOffering() }

    // Access is granted server-side after RevenueCat's webhook writes
    // subscription_status — a few seconds AFTER the purchase call returns. Poll the
    // profile so the screen unlocks on its own when the grant lands (iOS awaitGrant:731).
    suspend fun awaitGrant() {
        isChecking = true
        for (attempt in 0 until 12) {
            session.refreshProfile()
            if (session.hasFullAccess) break
            delay(1500)
        }
        isChecking = false
    }

    Column(
        // No statusBarsPadding: rendered inside the detented sheet, not full-screen.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1-2 · Kicker + DisplayTitle (pad top 30)
        Column(
            Modifier.padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(if (isExpired) R.string.welcome_back else R.string.members_only))
            DisplayTitle(
                leading = stringResource(R.string.unlock_every_farm_s),
                emphasis = stringResource(R.string.full_story),
                size = 32.sp,
            )
        }

        // 3 · Stat row (3 tiles + dividers), .card(14)
        Row(
            Modifier.fillMaxWidth().card(14),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatTile(
                value = if (pins.isEmpty()) stringResource(R.string.stat_thousands) else "%,d+".format(pins.size),
                caption = stringResource(R.string.farm_shops),
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            StatTile(value = "10", caption = stringResource(R.string.categories), modifier = Modifier.weight(1f))
            StatDivider()
            StatTile(value = "NL + BE", caption = stringResource(R.string.coverage), modifier = Modifier.weight(1f))
        }

        // 4 · Emoji grid — 6 columns × 12 emoji (iOS LazyVGrid; a static 2×6 grid of
        // Rows is the Compose equivalent inside a vertical scroll).
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EMOJI_GRID.chunked(6).forEach { rowEmojis ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowEmojis.forEach { e ->
                        Text(e, style = geist(28.sp), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 5 · Lock card, .card(22)
        Column(
            Modifier.fillMaxWidth().card(22),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(34.dp))
            Text(
                stringResource(if (isExpired) R.string.membership_expired_title else R.string.unlock_every_farm),
                style = geist(19.sp, FontWeight.Bold), color = FarmsyColors.ink, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (isExpired) R.string.lock_body_expired else R.string.lock_body_normal, pin.name),
                // iOS .geist(15).lineSpacing(2) → natural 19.50 + 2 = 21.5sp, Trim.Both.
                style = geist(15.sp).copy(
                    lineHeight = 21.5.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
            )
        }

        // 6 · Error
        purchaseError?.let {
            Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed, textAlign = TextAlign.Center)
        }

        // 7 · Purchase area
        when {
            isPurchasing || isChecking -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
            // Fetched, but the store handed back nothing to sell — a real message +
            // retry, never an endless spinner (iOS productsUnavailable, App Review 2.1).
            productsUnavailable -> Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.memberships_cant_load), style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink, textAlign = TextAlign.Center)
                Text(stringResource(R.string.memberships_load_retry), style = geist(13.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.try_again),
                    style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                    modifier = Modifier.padding(top = 2.dp).noRippleClick(haptics) {
                        scope.launch { purchases.loadOffering(force = true) }
                    },
                )
            }
            // Offering still loading — a spinner, not a half-drawn paywall.
            purchases.yearlyPrice == null -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
            else -> {
                // With a trial, lead with the free days and put the converted price
                // underneath; without one, just the price (iOS :843).
                val trialDays = purchases.yearlyFreeTrialDays
                val fallback = stringResource(R.string.become_a_member)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanButton(
                        label = if (trialDays != null) stringResource(R.string.free_trial_days_arg, trialDays)
                        else stringResource(R.string.plan_yearly),
                        detail = purchases.yearlyPrice?.let {
                            if (trialDays != null) stringResource(R.string.then_price_per_year_arg, it)
                            else stringResource(R.string.price_per_year_arg, it)
                        },
                        filled = true,
                        fallbackLabel = fallback,
                    ) {
                        val activity = context as? Activity ?: return@PlanButton
                        scope.launch {
                            if (purchases.purchase(activity, yearlyPkg, userId)) { successHaptic(); awaitGrant() }
                        }
                    }
                    purchases.lifetimePrice?.let { price ->
                        PlanButton(
                            label = stringResource(R.string.plan_lifetime),
                            detail = "$price · ${stringResource(R.string.one_payment_yours_forever)}",
                            filled = false,
                            fallbackLabel = fallback,
                        ) {
                            val activity = context as? Activity ?: return@PlanButton
                            scope.launch {
                                if (purchases.purchase(activity, lifetimePkg, userId)) { successHaptic(); awaitGrant() }
                            }
                        }
                    }
                }

                // 8 · Trial terms (full disclosure before purchase, App Review 3.1.2).
                val price = purchases.yearlyPrice
                if (trialDays != null && price != null) {
                    Text(
                        stringResource(R.string.trial_terms_arg, trialDays, price),
                        style = geist(12.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // 9 · Restore
                Text(
                    stringResource(R.string.restore_purchases),
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                    modifier = Modifier.noRippleClick(haptics) {
                        scope.launch { if (purchases.restore()) awaitGrant() }
                    },
                )
            }
        }

        // 10 · Claim (checkmark.seal → Material Outlined Verified, SF substitution)
        Row(
            Modifier.padding(bottom = 26.dp).noRippleClick(haptics) { onClaim() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Verified, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(13.dp))
            Text(stringResource(R.string.is_arg_yours_claim_it, pin.name), style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
        }
    }
}

private val EMOJI_GRID = listOf("🥬", "🥛", "🧀", "🥚", "🥩", "🐟", "🍯", "🍷", "🧺", "🌱", "🍎", "🥔")

/// One stat: big green value (shrinks rather than wraps) over a muted caption
/// (iOS StatTile, Theme.swift:200 — used only by this paywall).
@Composable
private fun StatTile(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        FitText(
            value, style = geist(22.sp, FontWeight.Bold), color = FarmsyColors.farmGreen,
            minSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(caption, style = geist(13.sp), color = FarmsyColors.inkMuted)
    }
}

/// The hairline divider between stat tiles (iOS `Divider().frame(height: 40)`).
@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(40.dp).background(FarmsyColors.hairline))
}

/// One purchasable plan — filled primary (yearly) or outlined secondary (lifetime).
/// iOS PlanButton (FarmDetailView.swift:924): radius16, filled `farmGreenMap`,
/// outlined white + `farmGreen@45` 1.5 stroke, vpad16, light haptic. (The older
/// shared `PlanCard` — radius22/`farmGreen` — was deleted as dead code; this private
/// PlanButton is the faithful current-iOS shape.)
/// Internal (not private) so the farm-free ProUpsellSheet reuses the same plan
/// buttons + native purchase shape rather than duplicating them.
@Composable
internal fun PlanButton(
    label: String,
    detail: String?,
    filled: Boolean,
    fallbackLabel: String,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val fg = if (filled) Color.White else FarmsyColors.farmGreen
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .then(
                if (filled) Modifier.background(FarmsyColors.farmGreenMap, shape)
                else Modifier.background(Color.White, shape).border(1.5.dp, FarmsyColors.farmGreen.copy(alpha = 0.45f), shape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FitText(
            if (detail == null) fallbackLabel else label,
            style = geist(17.sp, FontWeight.SemiBold), color = fg,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        detail?.let {
            FitText(
                it, style = geist(14.sp),
                color = if (filled) Color.White.copy(alpha = 0.9f) else FarmsyColors.ink,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/// A no-ripple tap that fires a light haptic first (iOS `Button { Haptics.tap() }`).
private fun Modifier.noRippleClick(
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: () -> Unit,
): Modifier = this.composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
    ) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}
