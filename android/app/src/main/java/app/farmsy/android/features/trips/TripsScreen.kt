package app.farmsy.android.features.trips

import android.location.Geocoder
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.MapsHandoff
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SavedTrip
import app.farmsy.android.core.TravelMode
import app.farmsy.android.core.TripGeometry
import app.farmsy.android.features.discover.RecommendationCarousel
import app.farmsy.android.features.place.PlaceSearchSheet
import app.farmsy.android.ui.theme.FarmsyColors
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
    val scope = rememberCoroutineScope()

    val pins by farms.pins.collectAsState()
    val stopIds by trip.stopIds.collectAsState()
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
    val routeLine by trip.routeLine.collectAsState()
    val distanceMeters by trip.distanceMeters.collectAsState()
    val durationSeconds by trip.durationSeconds.collectAsState()
    val isRouting by trip.isRouting.collectAsState()
    val onRoads by trip.onRoads.collectAsState()
    val savedTrips by trip.savedTrips.collectAsState()
    val userSession by session.session.collectAsState()

    val uid = userSession?.user?.id
    val pinIndex = remember(pins) { pins.associateBy { it.osmId } }
    val stops = remember(stopIds, pinIndex) { stopIds.mapNotNull { pinIndex[it] } }
    val canRoute = (originCoord != null && stops.isNotEmpty()) || stops.size >= 2

    var planTab by remember { mutableStateOf(true) }
    var naming by remember { mutableStateOf(false) }
    var tripName by remember { mutableStateOf("") }
    var armedDelete by remember { mutableStateOf<String?>(null) }
    var reorderNote by remember { mutableStateOf<String?>(null) }
    // S19 origin picker (Photon). iOS OriginBar tap → showOriginSearch → PlaceSearchSheet.
    var showOriginSearch by remember { mutableStateOf(false) }

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
            trip.refreshRoute(pinIndex)
        }
    }

    // Recompute the route whenever the stops change; load saved trips once signed in.
    LaunchedEffect(stopIds, originCoord) { trip.refreshRoute(pinIndex) }
    LaunchedEffect(uid) { uid?.let { trip.loadTrips(it) } }

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
                    onClear = { trip.clearOrigin(); scope.launch { trip.refreshRoute(pinIndex) } },
                )

                Spacer(Modifier.size(14.dp))

                // R7 · the day. Under the start, because a drive is a place and
                // then a time, and that is the order it is decided in.
                DayRow(
                    date = tripDate,
                    onPick = { trip.setTripDate(it) },
                    onClear = { trip.clearTripDate() },
                )

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

                reorderNote?.let {
                    Text(it, style = geist(12.sp), color = FarmsyColors.farmGreen)
                }
                if (stops.size >= 3) {
                    Text(
                        stringResource(R.string.best_order), style = geist(14.sp, FontWeight.SemiBold),
                        color = FarmsyColors.farmGreen,
                        modifier = Modifier.clickable {
                            val saved = trip.optimise(pinIndex)
                            reorderNote = if (saved >= 0.5)
                                context.getString(R.string.reordered_km_shorter, saved.toInt())
                            else context.getString(R.string.already_shortest)
                            scope.launch { trip.refreshRoute(pinIndex) }
                        },
                    )
                }

                // Mode selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(TravelMode.CAR, Icons.Filled.DirectionsCar, R.string.mode_drive, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex) }
                    }
                    ModeButton(TravelMode.BIKE, Icons.Filled.DirectionsBike, R.string.mode_bike, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex) }
                    }
                    ModeButton(TravelMode.WALK, Icons.Filled.DirectionsWalk, R.string.mode_walk, mode, Modifier.weight(1f)) {
                        trip.setMode(it); scope.launch { trip.refreshRoute(pinIndex) }
                    }
                }

                // Totals bar
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

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineAction(
                        stringResource(R.string.save_trip), Icons.Filled.Bookmark, Modifier.weight(1f),
                        enabled = stops.isNotEmpty() && uid != null,
                    ) { tripName = ""; naming = true }
                    OutlineAction(
                        stringResource(R.string.show_route), Icons.Filled.NearMe, Modifier.weight(1f),
                        enabled = canRoute,
                    ) { trip.requestFit() }
                }
                OutlineAction(
                    stringResource(R.string.open_in_google_maps), Icons.Filled.OpenInNew, Modifier.fillMaxWidth(),
                    enabled = stops.isNotEmpty(),
                ) { openGoogleMaps(context, originCoord, stops, destinationCoord, mode) }

                // R4 · farms on the way. Shown once there's a road to measure against.
                // Fed the FILTERED pin set (the map's own list) so it never offers a
                // farm the map is hiding. Adding a farm toggles it onto the drive, which
                // re-routes (LaunchedEffect(stopIds) above), so it re-positions against
                // the new road for free.
                if (routeLine.size >= 2) {
                    Spacer(Modifier.height(4.dp))
                    RouteCorridor(
                        road = routeLine,
                        tripKm = distanceMeters?.let { it / 1000.0 },
                        // R6 · the corridor asks "open when you pass", so it needs how
                        // long the road takes, which day the drive is on and when it
                        // sets off.
                        tripMinutes = durationSeconds?.let { it / 60.0 },
                        tripDate = tripDate,
                        departMinutes = null,
                        // R5b · what each farm sells. The map already fetched it.
                        produceByOsm = farms.produceByOsm,
                        filteredFarms = farms.filtered(),
                        stopIds = stopIds.toSet(),
                        onOpenFarm = onOpenFarm,
                        onAddStop = { pin -> trip.toggle(pin.osmId); scope.launch { trip.refreshRoute(pinIndex) } },
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
                scope.launch { trip.refreshRoute(pinIndex) }
            },
            onLocate = { useMyLocation() },
            onDismiss = { showOriginSearch = false },
        )
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
private fun StopRow(index: Int, pin: FarmPin, legLabel: String, onRemove: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 14.dp, vertical = 11.dp),
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
            modifier = Modifier.size(16.dp).clickable { onRemove() },
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
