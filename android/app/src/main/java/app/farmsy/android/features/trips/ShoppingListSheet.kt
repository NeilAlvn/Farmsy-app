package app.farmsy.android.features.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.LanguageStore
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.ShoppingPlanner
import app.farmsy.android.features.shopping.LockedSample
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.geist
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import java.util.Locale

/// The planner's own radius — `ShoppingPlanner.plan`'s default, spelled here
/// because the coverage sentence has to name it.
private const val RADIUS_KM = 25.0

/// "I need eggs, milk and potatoes — where do I drive?" — mirrors iOS
/// ShoppingListSheet.
///
/// The map answers where a farm is. This answers which farms to visit for a
/// list, and in what order. The result is ordinary trip stops, so the route
/// line, the totals, the corridor list and the Google Maps hand-off all pick it
/// up unchanged.
///
/// A picker rather than a text field. The words that mean a product are served
/// (GET /api/shopping/items) because guessing them locally is what sends
/// somebody asking for lamb to a beef farm.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShoppingListSheet(origin: LatLng, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val farms = LocalFarms.current
    val trip = LocalTrip.current
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val wanted by trip.wantedProducts.collectAsState()
    val pins by farms.pins.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val loadFailed by ShoppingItems.loadFailed.collectAsState()
    // Collected, not read off the store, so buying Plus mid-session unlocks
    // this sheet instead of leaving it blurred (final review #2).
    val profile by session.profile.collectAsState()
    val hasFullAccess = profile?.hasFullAccess == true

    var plan by remember { mutableStateOf<ShoppingPlanner.Plan?>(null) }
    var isPlanning by remember { mutableStateOf(false) }

    // Dutch or English, the same rule the website applies; fr and de fall back
    // to English because the labels only exist in two.
    val language = remember {
        LanguageStore.current(context).code.ifEmpty { Locale.getDefault().language }
    }

    // The produce text is what every match is made against — without it the sheet
    // would honestly report that nothing is sold anywhere.
    LaunchedEffect(Unit) {
        farms.loadFlagsIfNeeded()
        ShoppingItems.loadIfNeeded()
    }

    /// Ids back to the words on the chips, so the answer is read in the same
    /// language it was asked in.
    fun labels(ids: List<String>): String =
        ids.joinToString(" · ") { id -> ShoppingItems.item(id)?.label(language) ?: id }

    fun buildPlan() {
        isPlanning = true
        scope.launch {
            farms.loadFlagsIfNeeded()
            ShoppingItems.loadIfNeeded()
            val picked = wanted.mapNotNull { ShoppingItems.item(it) }
            val candidates = pins.map {
                ShoppingPlanner.Candidate(it.osmId, LatLng(it.lat, it.lng), farms.produceFor(it.osmId) ?: "")
            }
            plan = ShoppingPlanner.plan(wanted = picked, farms = candidates, origin = origin, radiusKm = RADIUS_KM)
            isPlanning = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FarmsyColors.cream) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.shopping_list), style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink)
                    Text(stringResource(R.string.shopping_list_sub), style = geist(12.sp), color = FarmsyColors.inkMuted)
                }
                Icon(
                    Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
                    modifier = Modifier.size(22.dp).clickable { onDismiss() },
                )
            }

            if (catalogue.isEmpty()) {
                if (loadFailed) {
                    Text(
                        stringResource(R.string.shopping_list_catalogue_failed),
                        style = geist(14.sp), color = FarmsyColors.inkMuted,
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FarmsyColors.farmGreen)
                    }
                }
            } else {
                // The picker
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    catalogue.forEach { item ->
                        val on = item.id in wanted
                        Row(
                            Modifier
                                .background(if (on) FarmsyColors.farmGreenMap else Color.White, RoundedCornerShape(20.dp))
                                .border(1.dp, if (on) Color.Transparent else FarmsyColors.hairline, RoundedCornerShape(20.dp))
                                .clickable { trip.toggleProduct(item.id); plan = null }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (on) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                item.label(language),
                                style = geist(14.sp, FontWeight.Medium),
                                color = if (on) Color.White else FarmsyColors.ink,
                            )
                        }
                    }
                }

                // The answer. Which farms cover the list is the paid half — the
                // same answer the Shopping tab blurs — so a free visitor gets
                // the identical locked sample instead of the named farms
                // (final review #1).
                plan?.let { p ->
                    if (hasFullAccess || p.isEmpty) {
                        ResultCard(p, pins, ::labels)
                    } else {
                        LockedSample(
                            coverage = stringResource(
                                R.string.shopping_sample_coverage_arg,
                                p.coveredCount, wanted.size, p.picks.size, RADIUS_KM.toInt(),
                            ),
                            label = stringResource(R.string.see_which_farms),
                            onUnlock = onUnlock,
                        ) {
                            ResultCard(p, pins, ::labels)
                        }
                    }
                }

                // Action. Planning is free (it is what draws the sample);
                // adding the farms it found to the trip is the paid half, so
                // for a free visitor the button says what it does and opens Plus.
                val ready = plan?.isEmpty == false
                val locked = ready && !hasFullAccess
                if (isPlanning) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FarmsyColors.farmGreen)
                    }
                } else {
                    PrimaryButton(
                        when {
                            locked -> stringResource(R.string.see_which_farms)
                            ready -> stringResource(R.string.shopping_list_add_stops_arg, plan!!.picks.size)
                            else -> stringResource(R.string.shopping_list_plan)
                        },
                        enabled = wanted.isNotEmpty(),
                    ) {
                        when {
                            locked -> onUnlock()
                            ready -> {
                                trip.addStops(plan!!.picks.map { it.osmId })
                                trip.requestFit()
                                onDismiss()
                            }
                            else -> buildPlan()
                        }
                    }
                }

                if (wanted.isNotEmpty()) {
                    Text(
                        stringResource(R.string.shopping_list_clear),
                        style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                            trip.clearProducts(); plan = null
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/// The numbered farms the plan picked, and what it could not find. Drawn sharp
/// for a member and blurred inside `LockedSample` for everybody else — the same
/// rows either way, because the sample has to be the real answer.
@Composable
private fun ResultCard(
    plan: ShoppingPlanner.Plan,
    pins: List<FarmPin>,
    labels: (List<String>) -> String,
) {
    Column(
        Modifier.fillMaxWidth().card(16),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (plan.isEmpty) {
            Text(stringResource(R.string.shopping_list_none), style = geist(14.sp), color = FarmsyColors.inkMuted)
        } else {
            plan.picks.forEachIndexed { i, pick ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(24.dp).background(FarmsyColors.farmGreenMap, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = geist(13.sp, FontWeight.Bold), color = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            pins.firstOrNull { it.osmId == pick.osmId }?.name ?: pick.osmId,
                            style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 1,
                        )
                        Text(
                            labels(pick.covers),
                            style = geist(13.sp), color = FarmsyColors.inkMuted, maxLines = 2,
                        )
                    }
                }
            }
        }
        // Said out loud rather than quietly dropped: a list that half worked is
        // only useful if you know which half.
        if (plan.missing.isNotEmpty()) {
            Text(
                stringResource(R.string.shopping_list_missing_arg, labels(plan.missing)),
                style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.inkMuted,
            )
        }
    }
}
