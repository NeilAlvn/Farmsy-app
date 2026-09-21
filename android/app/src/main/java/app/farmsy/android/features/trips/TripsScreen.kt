package app.farmsy.android.features.trips

import android.location.Geocoder
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.farmsy.android.core.TripEndpoints
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.MapsHandoff
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SavedTrip
import app.farmsy.android.core.TravelMode
import app.farmsy.android.core.TripGeometry
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.TripStore
import app.farmsy.android.features.discover.RecommendationCarousel
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.place.PlaceSearchSheet
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.geist
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/// The trip planner — the Android twin of iOS TripsView, adapted to a full-screen
/// tab: a map showing the traced route on top, and a Plan / My trips panel below.
/// Stops come from "Add to trip" on a farm; the road route + totals come from
/// POST /api/route; My trips is Pro-gated.
@Composable
fun TripsScreen(collapsed: Boolean = false, onOpenFarm: (FarmPin) -> Unit) {
    val farms = LocalFarms.current
    val trip = LocalTrip.current
    val session = LocalSession.current
    val locationHelper = LocalLocationHelper.current
    val context = LocalContext.current
    val shell = LocalShell.current
    val scope = rememberCoroutineScope()

    val pins by farms.pins.collectAsState()
    val stopIds by trip.stopIds.collectAsState()
    // Collected, and the access flag derived from it, so a membership bought
    // mid-session recomposes this screen — `session.hasFullAccess` is a plain
    // read of the backing field and invalidates nothing on its own.
    val profile by session.profile.collectAsState()
    val hasFullAccess = profile?.hasFullAccess == true
    val originCoord by trip.originCoord.collectAsState()
    // R8: the drive has an end of its own. Read here so the Maps hand-off can
    // tell a stop apart from the destination.
    val destinationCoord by trip.destinationCoord.collectAsState()
    // R7: the day this drive is for. Null until somebody picks one.
    val tripDate by trip.tripDate.collectAsState()
    val originLabel by trip.originLabel.collectAsState()
    val mode by trip.mode.collectAsState()
    // traceProgress / fitToken are no longer read here — the route renders on the shared
    // map (MapScreen); "Show route" calls trip.requestFit(). routeLine IS read again now,
    // for R4: the corridor measures farms against this polyline.
    val shoppingChips by ShoppingItems.items.collectAsState()
    val routeLine by trip.routeLine.collectAsState()
    val distanceMeters by trip.distanceMeters.collectAsState()
    val durationSeconds by trip.durationSeconds.collectAsState()
    // R5 — the product chips picked for the corridor.
    val selectedProducts by trip.selectedProducts.collectAsState()
    val isRouting by trip.isRouting.collectAsState()
    val onRoads by trip.onRoads.collectAsState()
    val savedTrips by trip.savedTrips.collectAsState()
    val userSession by session.session.collectAsState()

    val uid = userSession?.user?.id
    val pinIndex = remember(pins) { pins.associateBy { it.osmId } }
    val stops = remember(stopIds, pinIndex) { stopIds.mapNotNull { pinIndex[it] } }
    val canRoute = (originCoord != null && stops.isNotEmpty()) || stops.size >= 2
    // Task 4: looking is free, ordering stops and drawing the road is Plus. A
    // single stop is a plain directions request either way, so it stays free.
    val isLocked = TripStore.isRouteLocked(hasFullAccess, stops.size)

    // The one place free users open Plus from the route preview — the unlock
    // button, the blurred stop block, and the locked Save/Show route/Maps
    // actions all call this. The Plus sheet reports the view with this trigger.
    fun openPlusFromSample() {
        shell.openPlus(AnalyticsValue.Trigger.ROUTE_PREVIEW)
    }

    /// The shopping-list sheet's lock sells which farms cover the list, not the
    /// route, so it reports as that sample.
    fun openPlusFromShoppingList() {
        shell.openPlus(AnalyticsValue.Trigger.SHOPPING_SAMPLE)
    }

    var planTab by remember { mutableStateOf(true) }
    var naming by remember { mutableStateOf(false) }
    var tripName by remember { mutableStateOf("") }
    var armedDelete by remember { mutableStateOf<String?>(null) }
    var reorderNote by remember { mutableStateOf<String?>(null) }
    // S19 origin picker (Photon). iOS OriginBar tap → showOriginSearch → PlaceSearchSheet.
    var showOriginSearch by remember { mutableStateOf(false) }
    var showShoppingList by remember { mutableStateOf(false) }
    val wantedProducts by trip.wantedProducts.collectAsState()

    // "Use my location" — GPS + reverse-geocode to a town label. Was inline in the
    // OriginRow's onLocate; now shared with the PlaceSearchSheet's own "use my location".
    fun useMyLocation() {
        scope.launch {
            if (!locationHelper.hasPermission()) { locationHelper.request(); return@launch }
            val loc = locationHelper.location.value ?: return@launch
            val label = withContext(Dispatchers.IO) {
                runCatching {
                    Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
                }.getOrNull()
            } ?: "%.3f, %.3f".format(loc.latitude, loc.longitude)
            trip.setOrigin(LatLng(loc.latitude, loc.longitude), label)
            trip.refreshRoute(pinIndex, locked = isLocked)
        }
    }

    // Recompute the route whenever the stops change; load saved trips once signed in.
    LaunchedEffect(stopIds, originCoord, isLocked) { trip.refreshRoute(pinIndex, locked = isLocked) }
    LaunchedEffect(uid) { uid?.let { trip.loadTrips(it) } }
    // R5 corridor chips read the same served catalogue as the shopping list
    // (GET /api/shopping/items) — one runtime source, no bundled table.
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }

    // TripsScreen is now sheet content over the ONE shared map (owned by MainScreen);
    // it no longer embeds its own GoogleMap. The route + numbered stops render on the
    // shared map, and "Show route" bumps trip.fitToken to frame the trip there.
    Column(
        Modifier.fillMaxSize().background(FarmsyColors.cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Collapsed (0.5 detent): a quiet "drag up" cue; the trip-overview list tucks
        // away so the header + actions stay on screen (iOS collapsed behaviour).
        // PORT NOTE: iOS ties this to `detent == .fraction(0.5)`; Compose has no
        // detent value, so MainScreen passes `collapsed` = the sheet's
        // PartiallyExpanded state instead.
        if (collapsed) {
            Icon(
                Icons.Filled.KeyboardArrowUp, null,
                tint = FarmsyColors.inkMuted.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp),
            )
        }
        // Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabButton(stringResource(R.string.plan_a_trip), planTab, Modifier.weight(1f)) { planTab = true }
            TabButton(stringResource(R.string.my_trips), !planTab, Modifier.weight(1f)) { planTab = false }
        }

        run {
            if (planTab) {
                // Origin row
                OriginRow(
                    label = originLabel,
                    // iOS OriginBar: tapping the bar opens the search sheet (not GPS
                    // directly). "Use my location" lives inside the sheet now.
                    onOpenSearch = { showOriginSearch = true },
                    onClear = { trip.clearOrigin(); scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) } },
                )

                Spacer(Modifier.size(14.dp))

                // R7 · the day. Under the start, because a drive is a place and
                // then a time, and that is the order it is decided in.
                DayRow(
                    date = tripDate,
                    onPick = { trip.setTripDate(it) },
                    onClear = { trip.clearTripDate() },
                )

                // Fill the trip from a shopping list rather than pin by pin. Sits
                // under the starting point because it needs one, and because that
                // is the order the trip is built in.
                if (!collapsed) {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
                            .clickable {
                                // A list is worth nothing without somewhere to drive
                                // from; fall back to the phone when no origin is set.
                                val from = originCoord
                                    ?: locationHelper.location.value?.let { LatLng(it.latitude, it.longitude) }
                                if (from == null) locationHelper.request() else showShoppingList = true
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = FarmsyColors.ink, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.shop_from_a_list), style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
                        if (wantedProducts.isNotEmpty()) {
                            Box(
                                Modifier.background(FarmsyColors.farmGreenMap, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text("${wantedProducts.size}", style = geist(12.sp, FontWeight.Bold), color = Color.White)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
                    }
                }

                // Trip overview (numbered stops, 5-slot minimum). Tucked away at the
                // collapsed (0.5) detent so the header + actions stay on screen — this
                // is reached by dragging the sheet down (collapsed = PartiallyExpanded).
                if (!collapsed) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp)),
                    ) {
                        Text(
                            stringResource(R.string.trip_overview), style = geist(16.sp, FontWeight.Bold),
                            color = FarmsyColors.ink, modifier = Modifier.padding(14.dp),
                        )
                        val rows = maxOf(stops.size, 5)
                        if (isLocked) {
                            // Task 4: the first stop is a real, interactive row like any
                            // other. Farmsy ordering the rest and drawing the road
                            // between them is the Plus work, so those rows — real, not
                            // placeholders — are blurred behind one unlock row rather
                            // than hidden outright (never a padlock on an empty screen).
                            StopRow(
                                index = 0, pin = stops[0], legLabel = legLabel(0, stops, originCoord, mode),
                                onRemove = { trip.remove(stops[0].osmId) },
                                onOpen = { onOpenFarm(stops[0]) },
                            )
                            LockedStopsBlock(rows, stops, originCoord, mode, onUnlock = ::openPlusFromSample)
                        } else {
                            for (i in 0 until rows) {
                                if (i < stops.size) {
                                    StopRow(
                                        index = i, pin = stops[i], legLabel = legLabel(i, stops, originCoord, mode),
                                        onRemove = { trip.remove(stops[i].osmId) },
                                        onOpen = { onOpenFarm(stops[i]) },
                                    )
                                } else {
                                    EmptyStopRow(i)
                                }
                            }
                        }
                    }
                }

                reorderNote?.let {
                    Text(it, style = geist(12.sp), color = FarmsyColors.farmGreen)
                }
                // Fix round 1 #3: reordering IS the paid work, so a locked trip
                // opens Plus instead of running it for free.
                if (stops.size >= 3) {
                    Text(
                        stringResource(R.string.best_order), style = geist(14.sp, FontWeight.SemiBold),
                        color = FarmsyColors.farmGreen,
                        modifier = Modifier.clickable {
                            if (isLocked) { openPlusFromSample(); return@clickable }
                            val saved = trip.optimise(pinIndex)
                            reorderNote = if (saved >= 0.5)
                                context.getString(R.string.reordered_km_shorter, saved.toInt())
                            else context.getString(R.string.already_shortest)
                            scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) }
                        },
                    )
                }

                // Mode selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(TravelMode.CAR, Icons.Filled.DirectionsCar, R.string.mode_drive, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) }
                    }
                    ModeButton(TravelMode.BIKE, Icons.Filled.DirectionsBike, R.string.mode_bike, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) }
                    }
                    ModeButton(TravelMode.WALK, Icons.Filled.DirectionsWalk, R.string.mode_walk, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) }
                    }
                }

                // Totals bar — Task 4: the totals are the full ordered route's
                // answer, Plus work, so the row is left out entirely for a locked
                // trip rather than shown with a blurred/fake distance.
                if (!isLocked) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFF3F6F2), RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.NearMe, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(10.dp))
                        val text = when {
                            !canRoute -> stringResource(R.string.add_farms_to_see)
                            isRouting -> stringResource(R.string.finding_the_road)
                            else -> totalsText(distanceMeters, durationSeconds, onRoads)
                        }
                        Text(
                            text,
                            style = if (canRoute && !isRouting) geist(14.sp, FontWeight.SemiBold) else geist(14.sp),
                            color = if (canRoute && !isRouting) FarmsyColors.ink else FarmsyColors.inkMuted,
                        )
                    }
                }

                // Actions. Locked: Save trip / Show route / Open in Maps would each
                // hand over the full ordered route, so they open Plus instead —
                // same look, same enabled state, different action.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineAction(
                        stringResource(R.string.save_trip), Icons.Filled.Bookmark, Modifier.weight(1f),
                        enabled = stops.isNotEmpty() && uid != null,
                    ) { if (isLocked) openPlusFromSample() else { tripName = ""; naming = true } }
                    OutlineAction(
                        stringResource(R.string.show_route), Icons.Filled.NearMe, Modifier.weight(1f),
                        enabled = canRoute,
                    ) { if (isLocked) openPlusFromSample() else trip.requestFit() }
                }
                // A Maps hand-off that silently becomes a paywall reads as
                // bait-and-switch, so the locked one wears the padlock. Save
                // trip / Show route stay as they are — they never promised to
                // leave the app.
                OutlineAction(
                    stringResource(R.string.open_in_google_maps),
                    if (isLocked) Icons.Filled.Lock else Icons.Filled.OpenInNew,
                    Modifier.fillMaxWidth(),
                    enabled = stops.isNotEmpty(),
                ) { if (isLocked) openPlusFromSample() else openGoogleMaps(context, originCoord, stops, destinationCoord, mode) }

                // R4 · farms on the way. Shown once there's a road to measure against.
                // Fed the FILTERED pin set (the map's own list) so it never offers a
                // farm the map is hiding. Adding a farm toggles it onto the drive, which
                // re-routes (LaunchedEffect(stopIds) above), so it re-positions against
                // the new road for free.
                if (routeLine.size >= 2) {
                    Spacer(Modifier.height(4.dp))
                    // R6 · the corridor answers "open when you pass" for the day picked
                    // in Luuk's day row (R7); departure is the 10:00 default (Android has
                    // no time picker). Derive the Mon-indexed weekday from his String date.
                    val resolvedDayMon = remember(tripDate) {
                        val d = TripEndpoints.day(tripDate) ?: TripEndpoints.day(TripEndpoints.nextSaturday())
                        (d?.dayOfWeek?.value ?: 6) - 1
                    }
                    RouteCorridor(
                        road = routeLine,
                        tripKm = distanceMeters?.let { it / 1000.0 },
                        filteredFarms = farms.filtered(),
                        stopIds = stopIds.toSet(),
                        dayMon = resolvedDayMon,
                        departMinutes = 600,
                        durationSeconds = durationSeconds,
                        produceByOsm = farms.produceByOsm,
                        chips = shoppingChips,
                        selectedProducts = selectedProducts,
                        onToggleProduct = { trip.toggleCorridorProduct(it) },
                        onOpenFarm = onOpenFarm,
                        onAddStop = { pin -> trip.toggle(pin.osmId); scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) } },
                    )
                }
            } else {
                MyTripsTab(
                    isAuthenticated = session.isAuthenticated,
                    draftCount = stopIds.size,
                    savedTrips = savedTrips,
                    armedDelete = armedDelete,
                    onArm = { armedDelete = it },
                    onDelete = { id -> scope.launch { trip.deleteTrip(id) }; armedDelete = null },
                    onOpen = { id -> scope.launch { trip.openTrip(id) }; planTab = true },
                    onGoPlan = { planTab = true },
                    onOpenFarm = onOpenFarm,
                )
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.name_your_trip)) },
            text = {
                TextField(
                    value = tripName, onValueChange = { tripName = it },
                    placeholder = { Text(stringResource(R.string.my_weekend_trip)) },
                    singleLine = true, keyboardOptions = KeyboardOptions.Default,
                    colors = TextFieldDefaults.colors(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = tripName.trim().isNotEmpty(),
                    onClick = {
                        val name = tripName.trim()
                        val u = uid
                        naming = false
                        if (u != null && name.isNotEmpty()) {
                            scope.launch {
                                trip.save(name, u, pinIndex)
                                trip.clear(); trip.clearOrigin()
                                planTab = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // S19 origin picker (Photon over the shared Ktor client — no SDK/key). onPick sets
    // the chosen place as the trip origin + refreshes the route; onLocate uses GPS.
    // iOS presents PlaceSearchSheet from the OriginBar.
    if (showOriginSearch) {
        PlaceSearchSheet(
            onPick = { coord, label ->
                trip.setOrigin(coord, label)
                scope.launch { trip.refreshRoute(pinIndex, locked = isLocked) }
            },
            onLocate = { useMyLocation() },
            onDismiss = { showOriginSearch = false },
        )
    }

    // Fill the trip from a shopping list. Needs somewhere to drive from: the
    // chosen origin, else the phone.
    if (showShoppingList) {
        val from = originCoord ?: locationHelper.location.value?.let { LatLng(it.latitude, it.longitude) }
        if (from != null) {
            ShoppingListSheet(
                origin = from,
                // The list sheet's lock sells the same thing the Shopping tab's
                // does — which farms cover the list — so it reports as that
                // sample, not as the route preview. The sheet closes first, the
                // way iOS has to, so both platforms land on the same screen.
                onUnlock = { showShoppingList = false; openPlusFromShoppingList() },
                onDismiss = { showShoppingList = false },
            )
        } else {
            showShoppingList = false
        }
    }
}

// MARK: - Rows / controls

@Composable
private fun TabButton(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) FarmsyColors.farmGreen else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, FarmsyColors.hairline),
    ) {
        Text(
            title, style = geist(15.sp, FontWeight.Bold),
            color = if (selected) Color.White else FarmsyColors.ink,
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayRow(date: String?, onPick: (String) -> Unit, onClear: () -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val clearTheDay = stringResource(R.string.clear_the_day)

    Surface(
        Modifier.fillMaxWidth().clickable { showPicker = true },
        shape = CircleShape, color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmsyColors.hairline),
    ) {
        Row(
            Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CalendarToday, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                date?.let { dayLabel(it) } ?: stringResource(R.string.choose_a_day),
                style = geist(15.sp), color = if (date == null) FarmsyColors.inkMuted else FarmsyColors.ink,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (date != null) {
                Icon(
                    Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
                    modifier = Modifier.size(18.dp)
                        .semantics { contentDescription = clearTheDay }
                        .clickable { onClear() },
                )
            }
        }
    }

    if (showPicker) {
        // The platform picker, not one of our own. It already knows the
        // reader's language, which day their week starts on, and how they
        // expect a date to be written — three things worth more than a
        // consistent brand colour on a control used once per trip.
        //
        // Seeded with the day already chosen, or the Saturday coming. That
        // default is offered and never stored: tripDate stays null until
        // somebody actually picks, so a trip that never had a day does not
        // start claiming it was planned for one.
        val seed = date ?: TripEndpoints.nextSaturday()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = TripEndpoints.day(seed)
                ?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = NotInThePast,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker hands back UTC midnight for the day the user
                    // tapped, so it is read back in UTC. Turning it into a local
                    // date here would move it by a day for anyone west of
                    // Greenwich, which is the classic version of this bug.
                    state.selectedDateMillis?.let { millis ->
                        val day = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onPick(TripEndpoints.dayString(day))
                    }
                    showPicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = state, title = { Text(stringResource(R.string.day_of_the_trip), Modifier.padding(24.dp)) })
        }
    }
}

/// A day, written the way this reader writes days. "Today" and "Tomorrow" are
/// spelled out because a date somebody can count on their fingers reads slower
/// than the word for it.
@Composable
private fun dayLabel(iso: String): String {
    val day = TripEndpoints.day(iso) ?: return iso
    val today = java.time.LocalDate.now(TripEndpoints.zone)
    return when (day) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> day.format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(java.util.Locale.getDefault())
        )
    }
}

/// A trip is planned, so it is in the future. Yesterday is not a plan.
@OptIn(ExperimentalMaterial3Api::class)
private object NotInThePast : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val day = java.time.Instant.ofEpochMilli(utcTimeMillis)
            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return !day.isBefore(java.time.LocalDate.now(TripEndpoints.zone))
    }

    override fun isSelectableYear(year: Int) = year >= java.time.LocalDate.now(TripEndpoints.zone).year
}

@Composable
private fun OriginRow(label: String?, onOpenSearch: () -> Unit, onClear: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable { if (label.isNullOrEmpty()) onOpenSearch() },
        shape = CircleShape, color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmsyColors.hairline),
    ) {
        Row(
            Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // iOS OriginBar (TripsView.swift:145): leading `magnifyingglass`(15) inkMuted —
            // it's a search bar, not a "use my location" row (the GPS option lives inside
            // the search sheet). SF magnifyingglass → Material Search (§7a).
            Icon(Icons.Filled.Search, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Text(
                label?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.choose_a_starting_point),
                style = geist(15.sp), color = if (label.isNullOrEmpty()) FarmsyColors.inkMuted else FarmsyColors.ink,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (!label.isNullOrEmpty()) {
                // iOS: `xmark.circle.fill`(16) inkMuted → clearOrigin.
                Icon(
                    Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
                    modifier = Modifier.size(18.dp).clickable { onClear() },
                )
            } else {
                // iOS empty state: decorative `location.circle`(20) farmGreenMap. The
                // MyLocation crosshair-circle is the closest Material glyph; no tap (the
                // whole bar opens the search sheet).
                Icon(Icons.Filled.MyLocation, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StopRow(
    index: Int, pin: FarmPin, legLabel: String, onRemove: () -> Unit, onOpen: () -> Unit,
    /// Task 4 fix round 1 #2: true for the blurred rows behind the route-preview
    /// paywall. `clickable(enabled = false)` installs no tap handling at all, so
    /// the outer unlock `Box`'s own `clickable` receives every tap on this row
    /// instead of the row swallowing it first — the same `enabled = !locked`
    /// approach `ShoppingScreen`'s `PlanView(locked:)` uses.
    locked: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !locked) { onOpen() }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(FarmsyColors.farmGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${index + 1}", style = geist(12.sp, FontWeight.Bold), color = Color.White) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pin.name, style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 1)
            Text(legLabel, style = geist(12.sp), color = FarmsyColors.inkMuted)
        }
        Icon(
            Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
            modifier = Modifier.size(16.dp).clickable(enabled = !locked) { onRemove() },
        )
    }
}

@Composable
private fun EmptyStopRow(index: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).border(1.5.dp, FarmsyColors.inkMuted.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("${index + 1}", style = geist(12.sp, FontWeight.Bold), color = FarmsyColors.inkMuted.copy(alpha = 0.6f)) }
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.pick_farm_on_map), style = geist(15.sp), color = FarmsyColors.inkMuted)
    }
}

/// Task 4's free sample: every row from the second stop on, drawn for real and
/// blurred — never a padlock on an empty screen — with one unlock row
/// underneath. The whole blurred block is also a tap target, so both paths call
/// the same `onUnlock` (mirrors ShoppingScreen's `ShoppingSample`).
@Composable
private fun LockedStopsBlock(
    rows: Int,
    stops: List<FarmPin>,
    originCoord: LatLng?,
    mode: TravelMode,
    onUnlock: () -> Unit,
) {
    // Modifier.blur() needs a RenderEffect, API 31+; older devices get a flat
    // scrim over the same content instead of no obscuring at all.
    val hide = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(7.dp)
    } else {
        Modifier.drawWithContent {
            drawContent()
            drawRect(FarmsyColors.surface.copy(alpha = 0.85f))
        }
    }
    val unlockLabel = stringResource(R.string.unlock_the_route)
    // Fix round 1 #4 put the sentence + button on top of the blur instead of
    // below it (which sat unreachable inside the stop list's own scroll area).
    // Fix round 2: at the sheet's half-open detent that scroll area only has
    // ~120dp below the first stop, and a centred, 168dp-tall overlay pushed the
    // button below the visible edge — so this is now top-aligned and compact:
    // ~8dp padding, a 2-line capped sentence, an 8dp gap, then the small pill
    // button. ~96dp total.
    // 104 + one more caption line (~14dp): the floor a 3-line sentence needs,
    // and no more — the button sits at the top of the block, so it stays on
    // screen at the sheet's half-open detent.
    Box(Modifier.fillMaxWidth().heightIn(min = 118.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = unlockLabel }
                .clickable(onClickLabel = unlockLabel, role = Role.Button, onClick = onUnlock),
        ) {
            Column(hide.clearAndSetSemantics {}) {
                for (i in 1 until rows) {
                    if (i < stops.size) {
                        // Fix round 1 #2: locked = true — no clickable is
                        // installed on the row at all, so this outer Box's own
                        // clickable receives the tap instead of the row
                        // swallowing it with a no-op callback.
                        StopRow(i, stops[i], legLabel(i, stops, originCoord, mode), onRemove = {}, onOpen = {}, locked = true)
                    } else {
                        EmptyStopRow(i)
                    }
                }
            }
        }
        // The real, readable control: a soft surface-coloured backing keeps the
        // sentence legible over the blur; only the stop rows above are hidden
        // from accessibility, so this text and button read normally.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .background(FarmsyColors.surface.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.route_stops_locked_arg, stops.size),
                style = geist(12.sp), color = FarmsyColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                // Three lines: the German sentence is ~100 characters and was
                // ellipsised at large text sizes.
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            PillButton(unlockLabel, PillVariant.PRIMARY, PillSize.SMALL, onClick = onUnlock)
        }
    }
}

@Composable
private fun ModeButton(m: TravelMode, icon: ImageVector, labelRes: Int, current: TravelMode, modifier: Modifier = Modifier, onSelect: (TravelMode) -> Unit) {
    val selected = current == m
    Surface(
        modifier.clickable { onSelect(m) },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) FarmsyColors.farmGreen else Color.White,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, FarmsyColors.hairline),
    ) {
        Row(
            Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (selected) Color.White else FarmsyColors.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(labelRes), style = geist(13.sp, FontWeight.SemiBold), color = if (selected) Color.White else FarmsyColors.ink)
        }
    }
}

@Composable
private fun OutlineAction(title: String, icon: ImageVector, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier.clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, FarmsyColors.hairline),
    ) {
        Row(
            Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = FarmsyColors.ink.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(8.dp))
            Text(title, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink.copy(alpha = if (enabled) 1f else 0.5f))
        }
    }
}

// MARK: - My trips

@Composable
private fun MyTripsTab(
    isAuthenticated: Boolean,
    draftCount: Int,
    savedTrips: List<SavedTrip>,
    armedDelete: String?,
    onArm: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onGoPlan: () -> Unit,
    onOpenFarm: (FarmPin) -> Unit,
) {
    if (!isAuthenticated) { Gate(stringResource(R.string.sign_in_to_view_trips)); return }
    // Saved trips are free for any signed-in user — parity with web, which un-gated
    // them on 29 Aug (PanelLists.tsx has no isPro; FavoritesProvider: "Saving farms
    // and trips are not Pro — they are free for anyone with an account"). Only the
    // route planner stayed Pro. Android was a week behind; the `saved_trips_pro` wall
    // was one the web does not have. Signed-out is still gated above.

    // Draft banner
    Box(
        Modifier.fillMaxWidth()
            .border(1.dp, FarmsyColors.inkMuted.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(enabled = draftCount > 0) { onGoPlan() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (draftCount == 0) stringResource(R.string.no_trip_in_progress)
            else stringResource(R.string.draft_waiting, draftCount),
            style = geist(14.sp), color = FarmsyColors.inkMuted,
        )
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.my_trips), style = geist(16.sp, FontWeight.Bold), color = FarmsyColors.ink)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.saved_count, savedTrips.size), style = geist(13.sp), color = FarmsyColors.inkMuted)
        }
        val rows = maxOf(savedTrips.size, 6)
        for (i in 0 until rows) {
            if (i < savedTrips.size) {
                SavedRow(i, savedTrips[i], armed = armedDelete == savedTrips[i].id, onArm = onArm, onDelete = onDelete, onOpen = onOpen)
            } else {
                Row(Modifier.fillMaxWidth().clickable { onGoPlan() }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).border(1.5.dp, FarmsyColors.inkMuted.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = geist(12.sp, FontWeight.Bold), color = FarmsyColors.inkMuted.copy(alpha = 0.6f)) }
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.plan_trip_to_fill), style = geist(15.sp), color = FarmsyColors.inkMuted)
                }
            }
        }
    }

    RecommendationCarousel(onOpenFarm = onOpenFarm, cardHeight = 156.dp)
}

@Composable
private fun SavedRow(index: Int, t: SavedTrip, armed: Boolean, onArm: (String) -> Unit, onDelete: (String) -> Unit, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).background(FarmsyColors.farmGreen, CircleShape), contentAlignment = Alignment.Center) {
            Text("${index + 1}", style = geist(12.sp, FontWeight.Bold), color = Color.White)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = geist(15.sp, FontWeight.Bold), color = FarmsyColors.ink, maxLines = 1)
            val farmsWord = if (t.stopCount == 1) stringResource(R.string.farm_singular) else stringResource(R.string.farms_plural)
            Text("${t.stopCount} $farmsWord", style = geist(12.sp), color = FarmsyColors.inkMuted)
        }
        Icon(
            if (armed) Icons.Filled.Delete else Icons.Filled.Close, null,
            tint = if (armed) FarmsyColors.warnRed else FarmsyColors.inkMuted,
            modifier = Modifier.size(16.dp).clickable { if (armed) onDelete(t.id) else onArm(t.id) },
        )
    }
}

@Composable
private fun Gate(text: String) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 220.dp).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(34.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// MARK: - Helpers

private fun totalsText(distanceMeters: Double?, durationSeconds: Double?, onRoads: Boolean): String {
    val km = (distanceMeters ?: 0.0) / 1000
    val mins = ((durationSeconds ?: 0.0) / 60).toInt()
    val time = if (mins >= 60) "${mins / 60} h ${mins % 60}" else "$mins min"
    return "~%.0f km · %s%s".format(km, time, if (onRoads) "" else " (est.)")
}

private fun legLabel(i: Int, stops: List<FarmPin>, originCoord: LatLng?, mode: TravelMode): String {
    val coords = buildList {
        originCoord?.let { add(it) }
        stops.forEach { add(LatLng(it.lat, it.lng)) }
    }
    val idx = if (originCoord != null) i + 1 else i
    if (idx < 1 || idx >= coords.size) return "Start"
    val km = TripGeometry.haversineKm(coords[idx - 1], coords[idx])
    return "~%.0f km · %d min".format(km, mode.minutes(km))
}

/// R8 · hand the planned drive to Google Maps.
///
/// The URL itself is built in [MapsHandoff], shared with iOS and the web, so
/// what opens here is the same route those two open for the same trip. This
/// function is only the Android half: turning stops into coordinates and firing
/// the intent.
///
/// Before R8 it built the URL inline from `[originCoord] + stops` and took the
/// last of those as the destination, so a drive planned towards somewhere
/// opened as a drive ending at the last farm.
private fun openGoogleMaps(
    context: android.content.Context,
    originCoord: LatLng?,
    stops: List<FarmPin>,
    destinationCoord: LatLng?,
    mode: TravelMode,
) {
    val url = MapsHandoff.googleMapsUrl(
        MapsHandoff.Plan(
            origin = originCoord,
            stops = stops.map { LatLng(it.lat, it.lng) },
            destination = destinationCoord,
            travelMode = mode.googleMode,
        )
    )
    if (url.isEmpty()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

