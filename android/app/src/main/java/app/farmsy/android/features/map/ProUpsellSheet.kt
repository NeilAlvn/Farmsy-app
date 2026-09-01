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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalPurchases
import app.farmsy.android.LocalSession
import app.farmsy.android.features.detail.PlanButton
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// A farm-free membership panel — the Android twin of iOS `ProUpsellSheet` and the
/// web `SubscriptionGateModal` (title, a line on what Pro is, the four feature lines,
/// the plan buttons, a close). Presented from a locked Pro filter tap (Aviah later-7,
/// option 2): unlike `LockedAccessView` it takes no farm. The plan buttons use the
/// SAME native Play purchase flow (`purchases.purchase(activity, pkg, userId)`) those
/// 19 Play purchases came through — NOT a web billing URL (Aviah later-8). Copy is the
/// web's `account.gate*` keys, authored inline en (matching the survey chrome approach).
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

    val features = listOf(
        "An email when a farm you saved posts something new",
        "The filters we are building next, as they land",
        "Everything new we add to Pro, included",
        "You keep a small independent project going",
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
            Spacer(Modifier.size(4.dp))
            Kicker("Farmsy Pro")
            DisplayTitle(leading = "Unlock the", emphasis = "Pro", trailing = "filters", size = 30.sp)

            Column(
                Modifier.fillMaxWidth()
                    .background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
                    .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                features.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                        Text(line, style = geist(14.sp), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                    }
                }
            }

            purchaseError?.let {
                Text(it, style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.warnRed, textAlign = TextAlign.Center)
            }

            val fallback = "Become a member"
            when {
                isPurchasing || isChecking -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                productsUnavailable -> Text(
                    "Memberships can't be loaded right now.",
                    style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { purchases.loadOffering(force = true) }
                    },
                )
                purchases.yearlyPrice == null -> CircularProgressIndicator(color = FarmsyColors.farmGreen)
                else -> {
                    val trialDays = purchases.yearlyFreeTrialDays
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanButton(
                            label = if (trialDays != null) "$trialDays days free" else "Yearly",
                            detail = purchases.yearlyPrice?.let { if (trialDays != null) "then $it / year" else "$it / year" },
                            filled = true, fallbackLabel = fallback,
                        ) {
                            val activity = context as? Activity ?: return@PlanButton
                            scope.launch { if (purchases.purchase(activity, yearlyPkg, userId)) awaitGrant() }
                        }
                        purchases.lifetimePrice?.let { price ->
                            PlanButton(
                                label = "Lifetime",
                                detail = "$price · One payment, yours forever",
                                filled = false, fallbackLabel = fallback,
                            ) {
                                val activity = context as? Activity ?: return@PlanButton
                                scope.launch { if (purchases.purchase(activity, lifetimePkg, userId)) awaitGrant() }
                            }
                        }
                    }
                    Text(
                        "Restore purchases",
                        style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { if (purchases.restore()) awaitGrant() }
                        },
                    )
                }
            }
        }
    }
}
