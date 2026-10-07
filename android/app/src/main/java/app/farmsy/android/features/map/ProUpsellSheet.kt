package app.farmsy.android.features.map

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalPurchases
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.FitText
import app.farmsy.android.ui.theme.Haptics
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import com.revenuecat.purchases.Package
import kotlinx.coroutines.launch

enum class PendingBuyOutcome { ALREADY_MEMBER, PROCEED, ABANDON }

/// What a pending buy does once sign-in completes — the Android twin of iOS
/// `ProUpsellSheet.pendingBuyOutcome`. One place, so the rule can be tested
/// without a store or a session: never charge an existing member, and never
/// charge on a profile we could not read. `hasFullAccess` is false for a profile
/// that never loaded, which is exactly why the loaded flag is a separate argument.
fun pendingBuyOutcome(profileLoaded: Boolean, hasFullAccess: Boolean): PendingBuyOutcome = when {
    !profileLoaded -> PendingBuyOutcome.ABANDON
    hasFullAccess -> PendingBuyOutcome.ALREADY_MEMBER
    else -> PendingBuyOutcome.PROCEED
}

/// The one Plus sheet — the Android twin of iOS `ProUpsellSheet`, presented
/// everywhere via `LocalShell.current.openPlus`. Farm-free: it takes no farm. Story
/// is "Farmsy finds it, plans it, tells you when it's fresh" (owner decision,
/// 2026-09-21): finding the right farms, the route, alerts and live availability are
/// Plus; looking (map, farm details, filters) stays free. The plan buttons use the
/// SAME native Play purchase flow (`purchases.purchase(activity, pkg, userId)`) those
/// 19 Play purchases came through — NOT a web billing URL (Aviah later-8). Copy is the
/// web's `account.gate*` keys, localized in `pro_*` string resources (nl/fr/de) — P0-7.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpsellSheet(trigger: AnalyticsValue.Trigger? = null, onDismiss: () -> Unit) {
    val session = LocalSession.current
    val purchases = LocalPurchases.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val requestAuth = LocalRequestAuth.current
    val currentSession by session.session.collectAsState()
    val userId = currentSession?.user?.id
    // The tap that was waiting for sign-in; consumed when the session appears.
    // The one tap that was waiting for sign-in: a package to buy, or a restore.
    // One value, so a plan tap followed by a restore tap cannot start both.
    var pendingPkg by remember { mutableStateOf<Package?>(null) }
    var pendingRestore by remember { mutableStateOf(false) }

    val isPurchasing by purchases.isPurchasing.collectAsState()
    val purchaseError by purchases.purchaseError.collectAsState()
    val yearlyPkg by purchases.yearly.collectAsState()
    val lifetimePkg by purchases.lifetime.collectAsState()
    val didLoadOffering by purchases.didLoadOffering.collectAsState()
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { purchases.loadOffering() }

    // The one place `paywall_viewed` is captured. Call sites used to capture it
    // themselves, which reported a paywall for signed-out taps that actually got
    // the sign-in sheet, and reported nothing at all for the entry points that
    // never had a capture. This effect runs once per presentation, so one shown
    // paywall is one event.
    LaunchedEffect(Unit) {
        trigger?.let {
            Observability.capture(AnalyticsEvent.PAYWALL_VIEWED, mapOf(AnalyticsProp.TRIGGER to it.key))
        }
    }

    // Poll the profile after a purchase — the grant lands a few seconds after the call
    // returns (RevenueCat's webhook writes subscription_status). On success, close.
    suspend fun awaitGrant() {
        isChecking = true
        // A `for` loop, not `repeat`: `return@repeat` returns from the lambda
        // for that one iteration and the loop carries on, so once the grant
        // landed this skipped the delay and ran the remaining iterations
        // back-to-back — up to 12 calls to /profile/status with no pause.
        // `break` leaves the loop, which is what iOS already does.
        for (attempt in 0 until 12) {
            session.refreshProfile()
            if (session.hasFullAccess) break
            if (attempt < 11) kotlinx.coroutines.delay(1500)
        }
        isChecking = false
        if (session.hasFullAccess) onDismiss()
    }

    /// Sign-in first, then the store. Signed in already: straight to the store.
    fun buy(pkg: Package?) {
        val uid = userId
        if (uid == null) {
            pendingPkg = pkg; pendingRestore = false
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to (trigger ?: AnalyticsValue.Trigger.HOME_ROW).key))
            requestAuth()
            return
        }
        val activity = context as? Activity ?: return
        scope.launch { if (purchases.purchase(activity, pkg, uid)) awaitGrant() }
    }

    fun restore() {
        if (userId == null) {
            pendingRestore = true; pendingPkg = null
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to AnalyticsValue.Trigger.RESTORE.key))
            requestAuth()
            return
        }
        scope.launch { if (purchases.restore()) awaitGrant() }
    }

    // A tap that waited for sign-in must not charge someone who is already a
    // member — reinstalled, or bought on the web or the other store. The session
    // lands before the profile does, so wait for one before deciding. On doubt,
    // do nothing: a second tap costs a tap, a second charge costs money.
    suspend fun resumePendingBuy(pkg: Package) {
        isChecking = true
        // A `for` loop, not `repeat`: `return@repeat` only ends the current
        // iteration, so the poll would keep running after the profile arrived.
        for (attempt in 0 until 6) {
            if (attempt > 0) kotlinx.coroutines.delay(500)
            session.refreshProfile()
            if (session.profile.value != null) break
        }
        isChecking = false
        when (pendingBuyOutcome(session.profile.value != null, session.hasFullAccess)) {
            PendingBuyOutcome.ALREADY_MEMBER -> onDismiss()
            PendingBuyOutcome.PROCEED -> buy(pkg)
            // Leave the sheet open rather than guess un-subscribed: the person taps again.
            PendingBuyOutcome.ABANDON -> Unit
        }
    }

    // The tap that was waiting for sign-in continues on its own once the session
    // appears — no second tap. A restore is safe for an existing member; a buy is not.
    LaunchedEffect(userId) {
        if (userId == null) return@LaunchedEffect
        pendingPkg?.let { pendingPkg = null; resumePendingBuy(it) }
        if (pendingRestore) { pendingRestore = false; restore() }
    }

    // Owner copy, 2026-09-21: looking is free, Farmsy doing the work is Plus — the
    // sheet sells finding, planning and freshness, not filters (those are free).
    val features = listOf(
        stringResource(R.string.pro_feature_open_now),
        stringResource(R.string.pro_feature_filter),
        stringResource(R.string.pro_feature_email),
        stringResource(R.string.pro_feature_everything),
    )
    val productsUnavailable = didLoadOffering && yearlyPkg == null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FarmsyColors.cream) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: "Farmsy" kicker + the one Plus title, everywhere it's sold.
            Spacer(Modifier.size(4.dp))
            Kicker("Farmsy")
            Text(stringResource(R.string.pro_unlock_title), style = display(28.sp, FontWeight.SemiBold), color = FarmsyColors.ink, textAlign = TextAlign.Center)
            // Subheading — the one Plus story, everywhere it's sold.
            Text(
                stringResource(R.string.pro_unlock_sub),
                style = geist(14.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
            )

            purchaseError?.let {
                Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed, textAlign = TextAlign.Center)
            }

            val fallback = stringResource(R.string.become_a_member)
            when {
                isPurchasing || isChecking -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                productsUnavailable -> Text(
                    stringResource(R.string.memberships_cant_load),
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { purchases.loadOffering(force = true) }
                    },
                )
                purchases.yearlyPrice == null -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                else -> {
                    // Trial only offered to someone who's never had one — ineligible is
                    // trialDays == null, so the free-days copy just doesn't show (Aviah:
                    // never advertise a trial someone won't get, and per P0-4b never a
                    // "0 days free" — no offer means no trial sentence at all). The trial
                    // count is parameterised (free_trial_days_arg / then_price_per_year_arg
                    // / trial_terms_arg, already translated nl/fr/de) so the number lands
                    // in the right place per language — never a hardcoded "3".
                    val trialDays = purchases.yearlyFreeTrialDays
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanButton(
                            label = if (trialDays != null) stringResource(R.string.free_trial_days_arg, trialDays) else stringResource(R.string.pro_get_yearly),
                            detail = purchases.yearlyPrice?.let { if (trialDays != null) stringResource(R.string.then_price_per_year_arg, it) else stringResource(R.string.price_per_year_arg, it) },
                            filled = true, fallbackLabel = fallback,
                        ) {
                            buy(yearlyPkg)
                        }
                        purchases.lifetimePrice?.let { price ->
                            PlanButton(
                                label = stringResource(R.string.pro_buy_lifetime),
                                detail = "$price · ${stringResource(R.string.pro_lifetime_onetime)}",
                                filled = false, fallbackLabel = fallback,
                            ) {
                                buy(lifetimePkg)
                            }
                        }
                    }
                    // Trial-terms disclosure (App Review 3.1.2) — only when there is an
                    // actual offer; never rendered for a no-trial user (parity with iOS
                    // ProUpsellSheet).
                    val yPrice = purchases.yearlyPrice
                    if (trialDays != null && yPrice != null) {
                        Text(
                            stringResource(R.string.trial_terms_arg, trialDays, yPrice),
                            style = geist(12.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.restore_purchases),
                        style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            restore()
                        },
                    )
                }
            }

            // "Included in both plans" — unboxed ticks (a list inside a card inside a
            // sheet is a third frame around something framed twice).
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = FarmsyColors.hairline)
                Text(stringResource(R.string.pro_included_in_both), style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
                features.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Check, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(13.dp).padding(top = 2.dp))
                        Text(line, style = geist(14.sp), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/// One purchasable plan — filled primary (yearly) or outlined secondary (lifetime).
/// iOS PlanButton (FarmDetailView.swift): radius16, filled `farmGreenMap`, outlined
/// white + `farmGreen@45` 1.5 stroke, vpad16, light haptic. Moved here from the
/// now-deleted `LockedAccessView.kt` (dead paywall removed 2026-09-21) — this is the
/// only remaining caller, so it's private rather than internal.
@Composable
private fun PlanButton(
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
            ) { if (Haptics.enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
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
