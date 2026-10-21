package app.farmsy.android.features.map

import android.app.Activity
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import app.farmsy.android.core.PushRegistrar
import app.farmsy.android.features.auth.AuthSheetMode
import app.farmsy.android.ui.theme.Badge
import java.text.NumberFormat
import java.util.Currency
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
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.FitText
import app.farmsy.android.ui.theme.Haptics
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
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    // Asked right after a trial starts, for the trial-ending reminder.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
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
            AuthSheetMode.nextSignUp = true
            Observability.capture(AnalyticsEvent.AUTH_PROMPTED, mapOf(AnalyticsProp.TRIGGER to (trigger ?: AnalyticsValue.Trigger.HOME_ROW).key))
            requestAuth()
            return
        }
        val activity = context as? Activity ?: return
        // Read before buying: once the trial is used, Play stops offering it.
        val startsTrial = pkg != null && pkg == yearlyPkg && purchases.yearlyFreeTrialDays != null
        scope.launch {
            if (purchases.purchase(activity, pkg, uid)) {
                // For the trial-ending reminder (TrialReminder). A no is fine.
                if (startsTrial && Build.VERSION.SDK_INT >= 33 && !PushRegistrar.notificationsAllowed(context)) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                awaitGrant()
            }
        }
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

    // Free against Plus, row by row. Looking is free; Farmsy doing the work is
    // Plus (owner, 2026-09-21). The table is the value story — it sits above the
    // prices, Cropsy's v3 order (owner, 2026-10-15). Twin of iOS `rows`.
    val rows = listOf(
        stringResource(R.string.pro2_row_map) to true,
        stringResource(R.string.pro2_row_open) to false,
        stringResource(R.string.pro2_row_list) to false,
        stringResource(R.string.pro2_row_route) to false,
        stringResource(R.string.pro2_row_alerts) to false,
    )
    val productsUnavailable = didLoadOffering && yearlyPkg == null
    var choice by remember { mutableStateOf(PlanChoice.YEARLY) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = FarmsyColors.cream, dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // Green band, Nime's shape: brand + PLUS, then what Plus does for you.
            Column(
                Modifier.fillMaxWidth().background(FarmsyColors.farmGreen)
                    .padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("farmsy", style = geist(20.sp, FontWeight.SemiBold), color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Badge("PLUS", fill = FarmsyColors.vivid, ink = FarmsyColors.ink)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                Text(stringResource(R.string.pro2_title), style = display(28.sp, FontWeight.SemiBold), color = Color.White,
                    modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.pro2_sub), style = geist(15.sp), color = Color.White.copy(alpha = 0.82f))
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ComparisonTable(rows)

                purchaseError?.let {
                    Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed, textAlign = TextAlign.Center)
                }
                val yearlyPrice = purchases.yearlyPrice
                when {
                    isPurchasing || isChecking -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                    productsUnavailable -> Text(
                        stringResource(R.string.memberships_cant_load),
                        style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { purchases.loadOffering(force = true) }
                        },
                    )
                    yearlyPrice == null -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                    else -> {
                        // Trial only offered to someone who's never had one — ineligible is
                        // trialDays == null, so every free-days line just doesn't show (Aviah:
                        // never advertise a trial someone won't get).
                        val trialDays = purchases.yearlyFreeTrialDays
                        if (trialDays != null) {
                            // Hidden, not removed, off the yearly card — the cards must not jump.
                            Row(
                                Modifier.fillMaxWidth().alpha(if (choice == PlanChoice.YEARLY) 1f else 0f)
                                    .background(FarmsyColors.farmGreenSoft, RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Verified, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pro2_nothing_today), style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = if (trialDays != null) 8.dp else 0.dp).height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val pick: (PlanChoice) -> Unit = {
                                choice = it
                                Observability.capture(AnalyticsEvent.PLAN_SELECTED, mapOf(AnalyticsProp.PLAN to it.key))
                            }
                            PlanCard(PlanChoice.FREE, choice, stringResource(R.string.pro2_col_free),
                                zeroPrice(yearlyPkg), stringResource(R.string.pro2_just_map), null, pick)
                            PlanCard(PlanChoice.YEARLY, choice, stringResource(R.string.pro2_yearly), yearlyPrice,
                                yearlyPkg?.product?.pricePerMonth()?.formatted?.let { stringResource(R.string.pro2_per_month_arg, it) },
                                trialDays?.let { stringResource(R.string.pro2_days_free_arg, it) }, pick)
                            purchases.lifetimePrice?.let {
                                PlanCard(PlanChoice.LIFETIME, choice, stringResource(R.string.pro2_lifetime), it,
                                    stringResource(R.string.pro2_pay_once), null, pick)
                            }
                        }
                        val ctaLabel = when (choice) {
                            PlanChoice.FREE -> stringResource(R.string.pro2_continue_free)
                            PlanChoice.LIFETIME -> stringResource(R.string.pro_buy_lifetime)
                            // The length comes from the store; "week" only when it is one.
                            PlanChoice.YEARLY -> when (trialDays) {
                                null -> stringResource(R.string.pro_get_yearly)
                                7 -> stringResource(R.string.pro2_start_free_week)
                                else -> stringResource(R.string.free_trial_days_arg, trialDays)
                            }
                        }
                        val filled = choice != PlanChoice.FREE
                        Text(
                            ctaLabel, style = geist(17.sp, FontWeight.SemiBold),
                            color = if (filled) Color.White else FarmsyColors.farmGreen, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(CircleShape)
                                .background(if (filled) FarmsyColors.farmGreenMap else FarmsyColors.surface)
                                .border(if (filled) 0.dp else 1.5.dp, FarmsyColors.farmGreenMap, CircleShape)
                                .clickable {
                                    if (Haptics.enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (choice) {
                                        PlanChoice.FREE -> onDismiss()
                                        PlanChoice.LIFETIME -> buy(lifetimePkg)
                                        PlanChoice.YEARLY -> buy(yearlyPkg)
                                    }
                                }
                                .padding(vertical = 17.dp),
                        )
                        // Play's auto-renew disclosure for the picked plan.
                        Text(
                            when (choice) {
                                PlanChoice.FREE -> stringResource(R.string.pro2_legal_free)
                                PlanChoice.LIFETIME -> stringResource(R.string.pro2_legal_lifetime)
                                PlanChoice.YEARLY -> trialDays?.let { stringResource(R.string.pro2_legal_trial_arg, it, yearlyPrice) }
                                    ?: stringResource(R.string.pro2_legal_year_arg, yearlyPrice)
                            },
                            style = geist(12.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val link = geist(13.sp)
                            Text(stringResource(R.string.restore_purchases), style = link, color = FarmsyColors.inkMuted,
                                modifier = Modifier.clickable { restore() })
                            Text(stringResource(R.string.pro2_terms), style = link, color = FarmsyColors.inkMuted,
                                modifier = Modifier.clickable { uriHandler.openUri("https://farmsy.app/terms") })
                            Text(stringResource(R.string.pro2_privacy), style = link, color = FarmsyColors.inkMuted,
                                modifier = Modifier.clickable { uriHandler.openUri("https://farmsy.app/privacy") })
                        }
                    }
                }
            }
        }
    }
}

/// Which card is picked. Yearly by default — it carries the trial.
private enum class PlanChoice(val key: String) {
    FREE(AnalyticsValue.Plan.FREE.key), YEARLY(AnalyticsValue.Plan.YEARLY.key), LIFETIME(AnalyticsValue.Plan.LIFETIME.key)
}

/// Free vs Plus. The Plus column is one green strip so the eye reads it as
/// "everything"; the free column shows what's locked. Twin of iOS `comparison`.
@Composable
private fun ComparisonTable(rows: List<Pair<String, Boolean>>) {
    val plusW = 64.dp
    val freeW = 56.dp
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FarmsyColors.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.pro2_col_free), style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                textAlign = TextAlign.Center, modifier = Modifier.width(freeW))
            Text(stringResource(R.string.pro2_col_plus), style = geist(13.sp, FontWeight.SemiBold), color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.width(plusW).background(FarmsyColors.farmGreenMap).padding(vertical = 10.dp))
        }
        val included = stringResource(R.string.pro2_included)
        val notIncluded = stringResource(R.string.pro2_not_included)
        rows.forEach { (label, free) ->
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = geist(14.sp), color = FarmsyColors.ink,
                    modifier = Modifier.weight(1f).padding(start = 16.dp, top = 11.dp, bottom = 11.dp))
                Icon(if (free) Icons.Filled.Check else Icons.Outlined.Lock, if (free) included else notIncluded,
                    tint = if (free) FarmsyColors.inkMuted else FarmsyColors.inkFaint,
                    modifier = Modifier.width(freeW).size(16.dp))
                Box(Modifier.width(plusW).fillMaxHeight().background(FarmsyColors.farmGreenMap), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CheckCircle, included, tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/// Free · Yearly · Lifetime. There is no weekly or monthly to sell, so the left
/// card is the free tier: €0 next to the per-month yearly price makes Plus read
/// as small, and picking it is the polite way out. Twin of iOS `planCard`.
@Composable
private fun RowScope.PlanCard(
    c: PlanChoice, choice: PlanChoice, title: String, price: String, detail: String?, badge: String?,
    onPick: (PlanChoice) -> Unit,
) {
    val on = c == choice
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(top = if (badge != null) 11.dp else 0.dp).fillMaxSize().clip(shape)
                .background(if (on) FarmsyColors.farmGreenSoft else FarmsyColors.surface)
                .border(if (on) 2.dp else 1.dp, if (on) FarmsyColors.farmGreenMap else FarmsyColors.hairline, shape)
                .selectable(selected = on) {
                    if (Haptics.enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPick(c)
                }
                .padding(horizontal = 4.dp).padding(top = if (badge != null) 18.dp else 14.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(title, style = geist(14.sp, if (on) FontWeight.SemiBold else FontWeight.Medium),
                color = if (on) FarmsyColors.farmGreen else FarmsyColors.ink)
            FitText(price, style = geist(if (c == PlanChoice.YEARLY) 22.sp else 19.sp, FontWeight.SemiBold),
                color = FarmsyColors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            detail?.let {
                FitText(it, style = geist(12.sp), color = if (on) FarmsyColors.farmGreenMap else FarmsyColors.inkMuted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        badge?.let {
            Text(it, style = geist(11.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(FarmsyColors.farmGreenMap).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

/// "€0" in the store's own currency, so the free card matches the other two.
private fun zeroPrice(pkg: Package?): String {
    val f = NumberFormat.getCurrencyInstance()
    pkg?.product?.price?.currencyCode?.let { runCatching { f.currency = Currency.getInstance(it) } }
    f.maximumFractionDigits = 0
    return f.format(0)
}
