package app.farmsy.android.features.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.Corridor
import app.farmsy.android.core.FarmFilters
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.Observability
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.LatLng
import kotlin.math.roundToInt

/// R4 — "farms on my way". The screen half of the corridor: it shows the farms the
/// R2 engine (Corridor.farmsAlongRoute) finds beside the current drive, grouped into
/// readable stretches, in driving order, with a radius the user can widen.
///
/// The corridor is fed the FILTERED pin set the caller hands in — never every farm —
/// so it can't offer a farm the map is hiding (Aviah's one hard contract). Adding a
/// stop calls onAddStop, which re-routes: the road updates, this recomputes, and the
/// farm lands where the *new* road passes it (traps R4-3). Rows are controls — tapping
/// one takes the map to that farm (R4-2). "Open when you pass" is R6's statusOnDay for
/// today, so it comes for free.
private const val FREE_ROWS = 40   // a cap so a 1,000-farm corridor doesn't scroll forever

@Composable
fun RouteCorridor(
    road: List<LatLng>,
    tripKm: Double?,
    filteredFarms: List<FarmPin>,
    stopIds: Set<String>,
    // R6 — the chosen day (0=Mon…6=Sun), the departure (minutes past midnight) and
    // the drive's total duration: together they answer "open when you pass, on the
    // day you're going" instead of "open at all today".
    dayMon: Int,
    departMinutes: Int,
    durationSeconds: Double?,
    onOpenFarm: (FarmPin) -> Unit,
    onAddStop: (FarmPin) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The radius follows the drive until the user touches the slider: a share of the
    // trip (~1/20th), floored at 2km so a short hop still finds something and capped at
    // 20km because past that "near the drive" stops meaning anything (R4-4). Once they
    // move it, chosenKm wins and it stops following. A slider that keeps resetting itself
    // is worse than one that starts wrong.
    var chosenKm by remember { mutableStateOf<Int?>(null) }
    val suggestedKm = if (tripKm == null) 10
        else maxOf(2, minOf(20, (tripKm * 0.05).roundToInt()))
    val radiusKm = chosenKm ?: suggestedKm

    // The corridor, recomputed whenever the road, the filtered set, the radius or the
    // stops change. Already-added stops are dropped — they're on the drive, not beside
    // it. farmsAlongRoute returns driving order; legsAlongRoute groups it.
    val near = remember(road, filteredFarms, radiusKm, stopIds) {
        if (road.size < 2) emptyList()
        else Corridor.farmsAlongRoute(
            road.map { Corridor.point(it.latitude, it.longitude) },
            filteredFarms,
            radiusKm * 1000.0,
        ).filter { it.farm.osmId !in stopIds }
    }

    // route_planned, once per pair of places — not once per radius drag. Keyed on
    // the drive's ends (origin + destination, rounded), which hold steady when a
    // waypoint is added and only change when an end does, so the effect relaunches
    // exactly when a new route is planned. No membership check — the corridor is free.
    val routeKey = remember(road) {
        if (road.size < 2) null
        else road.first().let { a -> road.last().let { b ->
            "%.3f,%.3f>%.3f,%.3f".format(a.latitude, a.longitude, b.latitude, b.longitude)
        } }
    }
    LaunchedEffect(routeKey) {
        if (routeKey != null) {
            Observability.capture(
                AnalyticsEvent.ROUTE_PLANNED,
                mapOf(AnalyticsProp.COUNT to near.size, AnalyticsProp.RADIUS_KM to radiusKm),
            )
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header — title + count.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.route_on_my_way), style = geist(17.sp, FontWeight.Bold), color = FarmsyColors.ink)
            Text(
                if (near.isEmpty()) stringResource(R.string.route_on_my_way_sub)
                else stringResource(R.string.route_count, near.size),
                style = geist(13.sp), color = FarmsyColors.inkMuted,
            )
        }

        // Radius control — "Farms within N km" + a slider (2..20).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.route_within), style = geist(13.sp, FontWeight.Medium), color = FarmsyColors.inkMuted)
                Spacer(Modifier.size(6.dp))
                Text("$radiusKm km", style = geist(13.sp, FontWeight.Bold), color = FarmsyColors.ink)
            }
            Slider(
                value = radiusKm.toFloat(),
                onValueChange = { chosenKm = it.roundToInt() },
                valueRange = 2f..20f,
                steps = 17,   // 2..20 inclusive
                colors = SliderDefaults.colors(
                    thumbColor = FarmsyColors.farmGreen,
                    activeTrackColor = FarmsyColors.farmGreen,
                    inactiveTrackColor = FarmsyColors.hairline,
                ),
            )
        }

        when {
            // Nothing this close to the road — say what to do (widen / clear a filter),
            // never a spinner or a blank (R4-4 empty state).
            near.isEmpty() -> Text(
                stringResource(R.string.route_none),
                style = geist(14.sp), color = FarmsyColors.inkMuted,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            else -> {
                // Too many to hold in your head — cap the render and say so (R4-4).
                val capped = near.size > FREE_ROWS
                val shown = if (capped) near.take(FREE_ROWS) else near
                val legs = if (tripKm != null && tripKm > 0)
                    Corridor.legsAlongRoute(shown, tripKm)
                else listOf(Corridor.RouteLeg(0.0, 0.0, shown))   // no distance yet: one flat list

                legs.forEach { leg ->
                    // A stretch heading, unless it's the single flat fallback leg.
                    if (leg.toKm > leg.fromKm) {
                        Text(
                            stringResource(
                                R.string.route_leg_range,
                                leg.fromKm.roundToInt(), leg.toKm.roundToInt(),
                            ) + " · " + stringResource(R.string.route_leg_count, leg.farms.size, radiusKm),
                            style = geist(12.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    leg.farms.forEach { n ->
                        CorridorRow(
                            farm = n.farm,
                            offRouteM = n.offRoute,
                            status = passStatus(n, dayMon, departMinutes, durationSeconds),
                            onOpen = { onOpenFarm(n.farm) },
                            onAdd = { onAddStop(n.farm) },
                        )
                    }
                }
                if (capped) {
                    Text(
                        stringResource(R.string.route_show_all_arg, near.size),
                        style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/// One corridor row: a control. Photo (or category tile), name + what it sells + how
/// far off the road, an open dot, and an add button. Tapping the row opens the farm on
/// the map (R4-2); the add button puts it on the drive (R4-3).
@Composable
private fun CorridorRow(
    farm: FarmPin,
    offRouteM: Double,
    status: FarmFilters.DayStatus,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Photo, or the category emoji on its tint when there's none.
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                .background(farm.primaryCategory.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (farm.image != null) {
                AsyncImage(farm.image, null, modifier = Modifier.fillMaxWidth().height(48.dp))
            } else {
                Text(farm.primaryCategory.emoji, fontSize = 22.sp)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(farm.name, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // R6 · open when you pass, on the day you're going. Unknown draws nothing
                // rather than a guess — calling an unknown farm open is how someone drives
                // to a locked gate.
                when (status) {
                    FarmFilters.DayStatus.OPEN -> OpenDot(FarmsyColors.farmGreen, stringResource(R.string.route_open_when_pass))
                    FarmFilters.DayStatus.CLOSED -> OpenDot(FarmsyColors.inkMuted, stringResource(R.string.route_closed_then))
                    FarmFilters.DayStatus.UNKNOWN -> {}
                }
                Text(
                    stringResource(R.string.route_off_road, formatDistance(offRouteM)),
                    style = geist(12.sp), color = FarmsyColors.inkMuted, maxLines = 1,
                )
            }
        }

        // Add to the drive — the plus. (Re-routes: the caller toggles the stop.)
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(FarmsyColors.farmGreenSoft)
                .clickable { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.route_add), tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OpenDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = geist(12.sp, FontWeight.Medium), color = color)
    }
}

/// A farm's open/closed status at the moment the drive reaches it. Arrival is
/// `depart + along · duration` (R6): `along` is the fraction of the drive at which the
/// farm sits, so a shop 80% of the way along is asked about late in the trip, not at
/// the start. A 30-minute visit window. With no duration (straight-line fallback) we
/// can't place the arrival, so we fall back to "open at all on the chosen day" — the
/// honest weaker answer. Mirrors iOS RouteCorridorView.passStatus.
private fun passStatus(
    n: Corridor.NearRoute<FarmPin>,
    dayMon: Int,
    departMinutes: Int,
    durationSeconds: Double?,
): FarmFilters.DayStatus {
    if (durationSeconds == null || durationSeconds <= 0.0)
        return FarmFilters.statusOnDay(n.farm.openingHours, dayMon)
    val arrival = departMinutes + (n.along * durationSeconds / 60.0).roundToInt()
    return FarmFilters.statusOnDayBetween(n.farm.openingHours, dayMon, arrival, arrival + 30)
}

/// "800 m" under a kilometre, "3.2 km" over — the same shape the rest of the app uses.
private fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m"
    else String.format("%.1f km", m / 1000)
