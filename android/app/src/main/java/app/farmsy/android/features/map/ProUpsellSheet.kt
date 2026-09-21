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
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.FitText
import app.farmsy.android.ui.theme.Haptics
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

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
fun ProUpsellSheet(onDismiss: () -> Unit) {
    val session = LocalSession.current
    val purchases = LocalPurchases.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isPurchasing by purchases.isPurchasing.collectAsState()
    val purchaseError by purchases.purchaseError.collectAsState()
    val yearlyPkg by purchases.yearly.collectAsState()
    val lifetimePkg by purchases.lifetime.collectAsState()
    val didLoadOffering by purchases.didLoadOffering.collectAsState()
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { purchases.loadOffering() }

    // Poll the profile after a purchase — the grant lands a few seconds after the call
    // returns (RevenueCat's webhook writes subscription_status). On success, close.
    suspend fun awaitGrant() {
        isChecking = true
        repeat(12) {
            session.refreshProfile()
            if (session.hasFullAccess) return@repeat
            kotlinx.coroutines.delay(1500)
        }
        isChecking = false
        if (session.hasFullAccess) onDismiss()
    }

    // Owner copy, 2026-09-21: looking is free, Farmsy doing the work is Plus — the
    // sheet sells finding, planning and freshness, not filters (those are free).
    val features = listOf(
        stringResource(R.string.pro_feature_open_now),
        stringResource(R.string.pro_feature_filter),
        stringResource(R.string.pro_feature_email),
        stringResource(R.string.pro_feature_everything),
    )
    val userId = session.session.collectAsState().value?.user?.id
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
                            val activity = context as? Activity ?: return@PlanButton
                            scope.launch { if (purchases.purchase(activity, yearlyPkg, userId)) awaitGrant() }
                        }
                        purchases.lifetimePrice?.let { price ->
                            PlanButton(
                                label = stringResource(R.string.pro_buy_lifetime),
                                detail = "$price · ${stringResource(R.string.pro_lifetime_onetime)}",
                                filled = false, fallbackLabel = fallback,
                            ) {
                                val activity = context as? Activity ?: return@PlanButton
                                scope.launch { if (purchases.purchase(activity, lifetimePkg, userId)) awaitGrant() }
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
                            scope.launch { if (purchases.restore()) awaitGrant() }
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
