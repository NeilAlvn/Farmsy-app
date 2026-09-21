package app.farmsy.android.features.map

import android.Manifest
import com.google.android.gms.maps.model.LatLngBounds
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import android.graphics.Path
import android.graphics.Paint
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.farmsy.android.ui.theme.tapCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.core.FarmAxis
import app.farmsy.android.core.FarmCategory
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.FarmsStore
import app.farmsy.android.core.RecentReports
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.core.SmartSearchApi
import app.farmsy.android.core.SmartSearchIntent
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.geist
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import app.farmsy.android.ui.theme.FitText

/// Marker bitmaps are expensive to rasterise — build one per category ×
/// confirmed, once. MarkerComposable is far too slow for thousands of pins.
private val pinIcons = HashMap<Pair<FarmCategory, Boolean>, BitmapDescriptor>()
private val dotIcons = HashMap<Pair<FarmCategory, Boolean>, BitmapDescriptor>()
private const val VIVID_POSITIVE_ARGB = 0xFF9BE15D.toInt()

/// Teardrop pin, parameterised by the iOS FarmPinView sizes so the highlighted
/// variant uses the *literal* manifest values (drop 38 normal / **50** highlighted,
/// white circle 22/28, emoji 12/15). The SF-Symbol point sizes are mapped to bitmap
/// pixels by fixed factors (headR = drop·0.9, whiteR = circlePt, emojiPx = emojiPt·2.33)
/// so `farmPinBitmap` reproduces the previous normal pin and the highlight scales
/// off the same mapping.
private fun pinBitmap(dropPt: Int, whiteCirclePt: Int, emojiPt: Int, bodyArgb: Int, emoji: String, confirmed: Boolean = false): BitmapDescriptor {
    val headR = dropPt * 0.9f
    val w = (headR * 2f + 24f).toInt()
    val h = (w * 1.29f).toInt()
    val cx = w / 2f
    val headCy = headR + 8f
    val bmp = createBitmap(w, h)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Plus: a vivid ring around a farm a visitor confirmed open today (iOS 3pt).
    if (confirmed) {
        paint.color = VIVID_POSITIVE_ARGB
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 7f
        canvas.drawCircle(cx, headCy, headR + 3.5f, paint)
        paint.style = Paint.Style.FILL
    }

    // Drop shadow
    paint.color = 0x33000000
    canvas.drawCircle(cx, headCy + 3f, headR, paint)
    // Teardrop: circle head + triangular tail
    paint.color = bodyArgb
    canvas.drawCircle(cx, headCy, headR, paint)
    val tw = headR * 0.65f
    val tail = Path().apply {
        moveTo(cx - tw, headCy + headR * 0.7f)
        lineTo(cx, h - 6f)
        lineTo(cx + tw, headCy + headR * 0.7f)
        close()
    }
    canvas.drawPath(tail, paint)
    // White inner circle
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, headCy, whiteCirclePt.toFloat(), paint)
    // Category emoji
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = emojiPt * 2.33f
        textAlign = Paint.Align.CENTER
    }
    val fm = text.fontMetrics
    canvas.drawText(emoji, cx, headCy - (fm.ascent + fm.descent) / 2f, text)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/// The category pin — iOS FarmPinView normal (drop 38, white 22, emoji 12).
private fun farmPinBitmap(cat: FarmCategory, confirmed: Boolean): BitmapDescriptor = pinIcons.getOrPut(cat to confirmed) {
    pinBitmap(dropPt = 38, whiteCirclePt = 22, emojiPt = 12, bodyArgb = cat.color.toArgb(), emoji = cat.emoji, confirmed = confirmed)
}

/// A farm at province zoom — iOS FarmDotView: a 10dp dot in the category colour
/// with a 1.5dp white stroke, plus the 3dp vivid ring when confirmed today.
/// `scale` is px per dp; density does not change under a running map.
private fun farmDotBitmap(cat: FarmCategory, confirmed: Boolean, scale: Float): BitmapDescriptor = dotIcons.getOrPut(cat to confirmed) {
    val r = 5f * scale
    val ring = 3f * scale
    val s = ((r + ring) * 2f + 4f).toInt()
    val c = s / 2f
    val bmp = createBitmap(s, s)
    val canvas = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (confirmed) {
        p.color = VIVID_POSITIVE_ARGB; p.style = Paint.Style.STROKE; p.strokeWidth = ring
        canvas.drawCircle(c, c, r + ring / 2f, p)
        p.style = Paint.Style.FILL
    }
    p.color = cat.color.toArgb()
    canvas.drawCircle(c, c, r, p)
    p.color = android.graphics.Color.WHITE; p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * scale
    canvas.drawCircle(c, c, r - p.strokeWidth / 2f, p)
    BitmapDescriptorFactory.fromBitmap(bmp)
}

/// Every farm is its own pin: a count bubble hides the one farm somebody is
/// looking for. What keeps it fast: zoomed out (span >= DOT_SPAN) the pins are
/// thinned to one per screen cell of about 7dp and drawn as dots. Two farms on
/// the same pixels draw as one dot at that farm's own coordinate; zooming in
/// separates them. Mirrors iOS MapScreen.visiblePins.
private const val DOT_SPAN = 0.06
/// Screen cells across the width at province zoom; 7pt dots on a 402pt phone.
private const val CELLS_ACROSS = 56.0

/// Viewport cull at 1.2× the span, then thin to one pin per screen cell when
/// zoomed out. First pin wins so the choice is stable while panning.
internal fun visiblePins(pins: List<FarmPin>, centerLat: Double, centerLng: Double, latSpan: Double, lngSpan: Double): List<FarmPin> {
    val latHalf = latSpan / 2 * 1.2
    val lngHalf = lngSpan / 2 * 1.2
    val inView = pins.filter {
        kotlin.math.abs(it.lat - centerLat) < latHalf && kotlin.math.abs(it.lng - centerLng) < lngHalf
    }
    if (latSpan < DOT_SPAN) return inView
    val cellLng = lngSpan / CELLS_ACROSS
    val cellLat = cellLng * 0.62   // dots are round; latitude degrees are longer
    val seen = HashSet<Long>()
    val out = ArrayList<FarmPin>(minOf(inView.size, 2000))
    for (pin in inView) {
        val key = kotlin.math.floor(pin.lat / cellLat).toLong() * 1_000_003L + kotlin.math.floor(pin.lng / cellLng).toLong()
        if (seen.add(key)) out.add(pin)
    }
    return out
}

/// The selected pin — iOS FarmPinView(isHighlighted:) with the literal manifest
/// values: drop **50**, white circle **28**, emoji **15**, in farmGreenDeep.
private val highlightIcons = HashMap<Pair<FarmCategory, Boolean>, BitmapDescriptor>()
private fun highlightedPinBitmap(cat: FarmCategory, confirmed: Boolean): BitmapDescriptor = highlightIcons.getOrPut(cat to confirmed) {
    pinBitmap(dropPt = 50, whiteCirclePt = 28, emojiPt = 15, bodyArgb = 0xFF18321A.toInt(), emoji = cat.emoji, confirmed = confirmed)
}

/// A numbered trip-stop marker — a green circle carrying the visiting order.
/// Matches iOS TripStopMarker (farmGreen circle, white number, white ring).
private val tripStopIcons = HashMap<Int, BitmapDescriptor>()
private fun tripStopBitmap(n: Int): BitmapDescriptor = tripStopIcons.getOrPut(n) {
    val s = 76
    val bmp = createBitmap(s, s)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0x33000000
    c.drawCircle(s / 2f, s / 2f + 2f, s / 2f - 6f, p)
    p.color = 0xFF234725.toInt()        // farmGreen (deep brand green — trip stops)
    c.drawCircle(s / 2f, s / 2f, s / 2f - 6f, p)
    p.color = android.graphics.Color.WHITE
    p.style = Paint.Style.STROKE; p.strokeWidth = 4f
    c.drawCircle(s / 2f, s / 2f, s / 2f - 8f, p)
    p.style = Paint.Style.FILL
    p.textSize = 34f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
    val fm = p.fontMetrics
    c.drawText(n.toString(), s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, p)
    BitmapDescriptorFactory.fromBitmap(bmp)
}

/// Map-first discovery — mirrors iOS MapScreen: full-bleed map with a floating
/// search row, farms-count badge, and bottom controls (list toggle + category
/// menu). Tapping a pin opens the farm (auth-gated one level up).
@Composable
fun MapScreen(onOpenFarm: (FarmPin) -> Unit, focusPin: FarmPin? = null, bottomInset: Dp = 96.dp) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current
    val trip = LocalTrip.current
    val session = LocalSession.current
    val shell = LocalShell.current
    val density = LocalDensity.current
    // iOS route widths are SwiftUI points (casing 8, line 5). Google Maps Compose
    // Polyline width is in *pixels*, so convert 8.dp / 5.dp → px at the current
    // density (points→dp is 1:1; the dp→px factor is the device density). Recorded
    // in PORT_NOTES.md.
    val routeCasingPx = with(density) { 8.dp.toPx() }
    val routeLinePx = with(density) { 5.dp.toPx() }

    val pins by farms.pins.collectAsState()
    val isLoading by farms.isLoading.collectAsState()
    val loadError by farms.loadError.collectAsState()
    val searchText by farms.searchText.collectAsState()
    // Multi-select categories (used as a recompose key for `filtered`; the map has no
    // category UI now — S5's FilterSheet will bind to this set).
    val selectedCategories by farms.selectedCategories.collectAsState()
    val userLocation by locationHelper.location.collectAsState()
    // Trip route lives on this single shared map (TripsScreen no longer has its own).
    val tripStopIds by trip.stopIds.collectAsState()
    val tripRouteLine by trip.routeLine.collectAsState()
    val tripTraceProgress by trip.traceProgress.collectAsState()
    val tripOnRoads by trip.onRoads.collectAsState()
    val tripOrigin by trip.originCoord.collectAsState()
    val tripFitToken by trip.fitToken.collectAsState()
    val aiIntent by farms.aiIntent.collectAsState()
    val aiPlaceToken by farms.aiPlaceToken.collectAsState()
    // The manual filters (quick toggles + the two axes) so the map recomposes as
    // they change, and so the count badge / filter dot stay in sync.
    val fVerified by farms.filterVerified.collectAsState()
    val fOpen by farms.filterOpenToday.collectAsState()
    val fAutomaat by farms.filterAutomaat.collectAsState()
    val fZelfpluk by farms.filterZelfpluk.collectAsState()
    val fPhotos by farms.filterHasPhotos.collectAsState()
    val placeTypes by farms.selectedPlaceTypes.collectAsState()
    val methods by farms.selectedMethods.collectAsState()
    val fConfirmed by farms.filterConfirmedToday.collectAsState()
    // Visitor reports, for the confirmed-today chip and ring.
    val reports by RecentReports.reports.collectAsState()
    val confirmedToday = remember(reports) { RecentReports.confirmedOpenToday(reports) }
    LaunchedEffect(Unit) { RecentReports.refresh() }
    var showFilters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var aiSearching by remember { mutableStateOf(false) }

    fun runSmartSearch() {
        val q = searchText.trim()
        if (q.length < 2) return
        keyboard?.hide()
        aiSearching = true
        scope.launch {
            val intent = SmartSearchApi.parse(q)
            aiSearching = false
            if (intent != null && !intent.isEmpty) {
                farms.applyAISearch(intent, userLocation)
            }
        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) locationHelper.request() }

    // Centered between NL and BE to start (same as iOS).
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(51.8, 4.7), 6.5f)
    }

    val filtered = remember(
        pins, searchText, selectedCategories, aiIntent,
        fVerified, fOpen, fAutomaat, fZelfpluk, fPhotos, placeTypes, methods, fConfirmed,
    ) { farms.filtered() }

    // The two axes filter against the flags feed (location_types / methods); pull
    // it lazily the first time the sheet opens, same as the AI path does.
    LaunchedEffect(showFilters) { if (showFilters) farms.loadFlagsIfNeeded() }

    // An AI search that resolved a centre flies the map there (server `center`, or
    // the user's location for a nearMe query).
    LaunchedEffect(aiPlaceToken) {
        val c = farms.aiCenter.value ?: return@LaunchedEffect
        val km = aiIntent?.radiusKm ?: (if (aiIntent?.nearMe == true) 15.0 else 25.0)
        val zoom = (11.5 - kotlin.math.log2(km / 5.0)).coerceIn(7.0, 13.0).toFloat()
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(c.first, c.second), zoom)
        )
    }

    // First fix: a search or product tap from another tab may already be waiting
    // (aiPlaceToken handles that); otherwise open on the user rather than on the
    // whole country. Once only, so a later fix never yanks the camera back.
    var didCenterOnUser by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        if (didCenterOnUser || farms.aiIntent.value != null) return@LaunchedEffect
        didCenterOnUser = true
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 9.5f))
    }

    // Snapshot the viewport only once the camera settles. Reading the camera
    // during composition makes every recomposition observe it, which nudges
    // the camera and walks the map away on its own.
    var viewport by remember { mutableStateOf<LatLngBounds?>(null) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            viewport = cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
    }
    // Trip stops are drawn as their own always-visible numbered markers.
    val tripStopSet = remember(tripStopIds) { tripStopIds.toSet() }
    val span = viewport?.let { it.northeast.latitude - it.southwest.latitude } ?: 3.4
    val drawsDots = span >= DOT_SPAN
    val visible = remember(filtered, viewport, tripStopSet) {
        val b = viewport
        val all = filtered.filter { it.osmId !in tripStopSet }
        if (b != null) {
            visiblePins(all, b.center.latitude, b.center.longitude,
                b.northeast.latitude - b.southwest.latitude, b.northeast.longitude - b.southwest.longitude)
        } else {
            visiblePins(all, 51.8, 4.7, 3.4, 3.4)
        }
    }

    // Trip route pieces for the shared map (TripsScreen no longer draws its own).
    val pinIndex = remember(pins) { pins.associateBy { it.osmId } }
    val tripStops = remember(tripStopIds, pinIndex) { tripStopIds.mapNotNull { pinIndex[it] } }
    val tracedRoute = remember(tripRouteLine, tripTraceProgress) { trip.tracedLine() }

    // Selecting a farm flies the shared map, keeping the pin in the upper part of the
    // screen (the detail sheet covers the lower ~55%). This ports iOS `flyToFocus`
    // (MapScreen.swift:327) exactly, as a single code path: delta = min(currentSpan,
    // 0.15) — keep the user's zoom when they're already in past 0.15, else zoom in to
    // 0.15; the region centre is shifted south by delta*0.28 so the pin sits in the
    // upper strip; the shown span is delta. iOS sets `MKCoordinateRegion(center, span)`
    // and lets MapKit fit it; the exact Google-Maps analog is `newLatLngBounds` of a
    // delta-sized box (both fit-a-region with the same aspect adjustment), which also
    // retires the old `zoom < 11 → 12` heuristic. Fallback span 0.15 mirrors iOS's
    // `visibleRegion ?? cap`.
    LaunchedEffect(focusPin?.osmId) {
        val p = focusPin ?: return@LaunchedEffect
        val current = viewport?.let { it.northeast.latitude - it.southwest.latitude } ?: 0.15
        val delta = minOf(current, 0.15)
        val centerLat = p.lat - delta * 0.28
        val half = delta / 2.0
        val bounds = LatLngBounds(
            LatLng(centerLat - half, p.lng - half),
            LatLng(centerLat + half, p.lng + half),
        )
        // newLatLngBounds needs a laid-out map (throws otherwise); we're post-interaction
        // so it's fine, but guard + fall back to a plain recenter that keeps zoom.
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 0)) }
            .onFailure { cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(centerLat, p.lng))) }
    }

    // An explicit trip fit (Show route / open saved trip / set origin) frames the
    // whole trip on the shared map.
    LaunchedEffect(tripFitToken) {
        if (tripFitToken == 0) return@LaunchedEffect
        val pts = buildList {
            tripOrigin?.let { add(it) }
            tripStops.forEach { add(LatLng(it.lat, it.lng)) }
            addAll(tracedRoute)
        }
        if (pts.size == 1) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pts.first(), 12f))
        } else if (pts.size >= 2) {
            val b = LatLngBounds.builder().apply { pts.forEach { include(it) } }.build()
            runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(b, 140)) }
        }
    }

    // Map content padding = the system bars + the floating-pill clearance
    // (`bottomInset`). Google Maps positions its logo and the required "Terms"
    // legal attribution *inside* this padding, so with edge-to-edge (mandatory at
    // API 36) the attribution clears the status/nav bars and the bottom pill
    // instead of drawing under them — a Maps ToS requirement. Static, so it holds
    // in every sheet detent. (Previously `bottomInset` was never applied.)
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val mapContentPadding = PaddingValues(
        top = systemBars.calculateTopPadding(),
        bottom = systemBars.calculateBottomPadding() + bottomInset,
    )

    Box(Modifier.fillMaxSize()) {
        run {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = mapContentPadding,
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = locationHelper.hasPermission(),
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                ),
            ) {
                visible.forEach { pin ->
                    // The focused pin is drawn highlighted below — skip it here.
                    if (pin.osmId == focusPin?.osmId) return@forEach
                    val confirmed = session.hasFullAccess && pin.osmId in confirmedToday
                    key(pin.osmId) {
                        Marker(
                            state = MarkerState(LatLng(pin.lat, pin.lng)),
                            title = pin.name,
                            snippet = pin.city,
                            icon = if (drawsDots) farmDotBitmap(pin.primaryCategory, confirmed, density.density)
                                   else farmPinBitmap(pin.primaryCategory, confirmed),
                            anchor = if (drawsDots) androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                                     else androidx.compose.ui.geometry.Offset(0.5f, 1f),
                            onClick = { onOpenFarm(pin); true },
                            onInfoWindowClick = { onOpenFarm(pin) },
                        )
                    }
                }

                // The active trip's road line — white casing (8dp) under a blue line
                // (5dp, dashed for the straight-line fallback). The origin is the START
                // of this line, not a separate marker (matches iOS — no origin pin).
                // PORT NOTE (aboveLabels): iOS draws the route `.mapOverlayLevel(.aboveLabels)`;
                // Google Maps Compose has no equivalent — polylines always render beneath
                // the base map's place/road labels, and zIndex only orders overlays among
                // themselves. Not expressible; see PORT_NOTES.md.
                if (tracedRoute.size >= 2) {
                    Polyline(points = tracedRoute, color = Color.White, width = routeCasingPx, zIndex = 1f)
                    Polyline(
                        points = tracedRoute, color = Color(0xFF2563EB), width = routeLinePx, zIndex = 2f,
                        pattern = if (tripOnRoads) null else listOf(Dash(22f), Gap(18f)),
                    )
                }
                // Numbered stop markers, above the route.
                tripStops.forEachIndexed { i, pin ->
                    Marker(
                        state = MarkerState(LatLng(pin.lat, pin.lng)),
                        title = pin.name, snippet = pin.city,
                        icon = tripStopBitmap(i + 1),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        zIndex = 3f,
                        onClick = { onOpenFarm(pin); true },
                    )
                }
                // The focused (selected) pin, highlighted (drop 50, farmGreenDeep), on
                // top — but NOT when the focused farm is a trip stop (it already shows as
                // a numbered marker), so exactly one marker renders at that coordinate.
                focusPin?.let { fp ->
                    if (fp.osmId !in tripStopSet) {
                        Marker(
                            state = MarkerState(LatLng(fp.lat, fp.lng)),
                            title = fp.name, snippet = fp.city,
                            icon = highlightedPinBitmap(fp.primaryCategory, session.hasFullAccess && fp.osmId in confirmedToday),
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
                            zIndex = 4f,
                            onClick = { onOpenFarm(fp); true },
                        )
                    }
                }
            }
        }

        // Floating search row — 1:1 with iOS: a white Capsule holding the search
        // icon, the field, and the filter control INSIDE it, with only the circular
        // locate button beside. No category pill, no farms-count chip (both gone on
        // iOS — the map is just the map).
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val filtersOn = farms.anyFilterOn() || aiIntent != null
                Surface(
                    Modifier.weight(1f), shape = CircleShape,
                    color = Color.White, shadowElevation = 8.dp
                ) {
                    Row(
                        Modifier.padding(vertical = 13.dp, horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (aiSearching) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FarmsyColors.farmGreenMap)
                        } else {
                            Icon(
                                if (aiIntent != null) Icons.Filled.AutoAwesome else Icons.Filled.Search, null,
                                tint = if (aiIntent != null) FarmsyColors.farmGreenMap else FarmsyColors.inkMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        BasicTextField(
                            value = searchText,
                            onValueChange = {
                                farms.searchText.value = it
                                if (it.isBlank() && aiIntent != null) farms.clearAISearch()
                            },
                            singleLine = true,
                            textStyle = geist(15.sp).copy(color = FarmsyColors.ink),
                            cursorBrush = SolidColor(FarmsyColors.farmGreenMap),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSmartSearch() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchText.isEmpty()) {
                                    Text(
                                        stringResource(R.string.search_or_ask), style = geist(15.sp),
                                        color = FarmsyColors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            },
                        )
                        // Filter lives on the search bar, web-style — tap slides the sheet up.
                        Icon(
                            Icons.Filled.FilterList, null, tint = FarmsyColors.farmGreenMap,
                            modifier = Modifier.size(22.dp).clickable { Observability.capture(AnalyticsEvent.FILTERS_OPENED); showFilters = true },
                        )
                        if (filtersOn) {
                            Box(Modifier.size(7.dp).background(FarmsyColors.farmGreenMap, CircleShape))
                        }
                    }
                }
                // Route planner entry, sharing the row with the locate button — its
                // only other entrance is Shopping → "Build my route", easy to miss.
                val planARoute = stringResource(R.string.plan_a_route)
                Surface(
                    Modifier.size(44.dp)
                        .semantics { contentDescription = planARoute }
                        .clickable { shell.openTrips() },
                    shape = CircleShape, color = Color.White, shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NearMe, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(20.dp))
                    }
                }
                Surface(
                    Modifier.size(44.dp).clickable {
                        if (locationHelper.hasPermission()) locationHelper.request()
                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    shape = CircleShape, color = Color.White, shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MyLocation, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Quick chips — the four filters people reach for most, one tap under
            // the search bar (iOS quickChips). "Open now" leads: farm hours are
            // irregular and seasonal, and a wasted drive is the thing this map
            // exists to prevent. Hidden while an AI intent owns the bar.
            if (aiIntent == null) {
                val fOpenNow by farms.filterOpenNow.collectAsState()
                val fOpenToday by farms.filterOpenToday.collectAsState()
                val fZelfpluk by farms.filterZelfpluk.collectAsState()
                val fAutomaat by farms.filterAutomaat.collectAsState()
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    Chip(stringResource(R.string.open_now), selected = fOpenNow, dot = if (fOpenNow) null else FarmsyColors.vividPositive) { farms.filterOpenNow.value = !fOpenNow }
                    Chip(stringResource(R.string.open_today), selected = fOpenToday) { farms.filterOpenToday.value = !fOpenToday }
                    // Plus: what visitors confirmed today. Free sees the number and
                    // the lock; the number is what makes the lock worth tapping.
                    val n = confirmedToday.size
                    if (n > 0) {
                        Chip(
                            stringResource(R.string.confirmed_open_today_arg, n),
                            icon = if (session.hasFullAccess) null else Icons.Filled.Lock,
                            selected = fConfirmed,
                            dot = if (fConfirmed) null else FarmsyColors.vividPositive,
                        ) {
                            if (session.hasFullAccess) {
                                farms.confirmedTodayIds.value = confirmedToday
                                farms.filterConfirmedToday.value = !fConfirmed
                            } else {
                                Observability.capture(AnalyticsEvent.PRO_FILTER_TAPPED, mapOf(AnalyticsProp.FILTER to "confirmed_today"))
                                shell.openPlus()
                            }
                        }
                    }
                    Chip(stringResource(R.string.filter_zelfpluk), emoji = "🍓", selected = fZelfpluk) { farms.filterZelfpluk.value = !fZelfpluk }
                    Chip(stringResource(R.string.axis_vending_machine), emoji = "🥚", selected = fAutomaat) { farms.filterAutomaat.value = !fAutomaat }
                }
            }

            // AI summary bar — the parsed summary + values as chips, an × to clear.
            aiIntent?.let { ai ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
                    Column(Modifier.padding(vertical = 11.dp, horizontal = 14.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(16.dp))
                            Text(ai.summary ?: "", style = geist(13.sp), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
                                modifier = Modifier.size(18.dp).clickable { farms.clearAISearch() }
                            )
                        }
                        val chips = aiChips(ai)
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chips.forEach { chip ->
                                    Surface(shape = CircleShape, color = FarmsyColors.farmGreenMap.copy(alpha = 0.12f)) {
                                        Text(
                                            chip, style = geist(11.sp, FontWeight.SemiBold),
                                            color = FarmsyColors.farmGreenMap,
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error state
        loadError?.let {
            Column(
                Modifier.align(Alignment.Center).background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.couldn_t_load_farms_check_your_connection_and_try_again), style = geist(14.sp, FontWeight.Medium))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.retry),
                    style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreenMap,
                    modifier = Modifier.clickable { farms.loadIfNeeded() }
                )
            }
        }

        // No category pill and no farms-count chip on the map — both are gone on
        // iOS (the map is just the map; category filtering lives in the filter sheet).

        if (showFilters) {
            FilterSheet(farms = farms, onDismiss = { showFilters = false })
        }
    }
}

/// The filter groups Aviah split out — quick toggles plus "Type of place" and
/// "How it's grown" (location_types / methods). Mirrors the iOS FilterSheet: the
/// selections combine (AND) with each other and with the category pill, and
/// "organic" deliberately stays a category, not a method. Values write straight
/// to the store, so the map behind the sheet updates live as you tap.
/// S5 · FilterSheet — iOS `MapScreen.swift:690` (a unified `.tapCard` ROW list, not
/// chips): header + "All categories" row + 10 category rows + 5 quick-filter rows +
/// two axis sections (Type of place / How it's grown). Rebuilt this session from a
/// chip layout that had NO category selection. Category rows bind to
/// `selectedCategories` (multi-select; add/remove per iOS). SF→Material subs in §7a.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(farms: FarmsStore, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current
    val fVerified by farms.filterVerified.collectAsState()
    val fOpen by farms.filterOpenToday.collectAsState()
    val fAutomaat by farms.filterAutomaat.collectAsState()
    val fZelfpluk by farms.filterZelfpluk.collectAsState()
    val fPhotos by farms.filterHasPhotos.collectAsState()
    val fOpenNow by farms.filterOpenNow.collectAsState()
    val fOpenSat by farms.filterOpenSaturday.collectAsState()
    val fOpenSun by farms.filterOpenSunday.collectAsState()
    val categories by farms.selectedCategories.collectAsState()
    val placeTypes by farms.selectedPlaceTypes.collectAsState()
    val methods by farms.selectedMethods.collectAsState()
    val pins by farms.pins.collectAsState()

    // Time and place-type filters are free now: opening hours are the farm's own
    // information, not intelligence. Plus sells matching, routing and alerts.
    val proLocked = false
    var showPro by remember { mutableStateOf(false) }
    fun onProTap(id: String, toggle: () -> Unit) {
        if (!proLocked) { toggle(); return }
        if (session.isAuthenticated) {
            // A signed-in non-member on a locked row: the tap, then the sheet it
            // opens. A signed-out tap goes to sign-in, not to a paywall.
            Observability.capture(AnalyticsEvent.PRO_FILTER_TAPPED, mapOf(AnalyticsProp.FILTER to id))
            Observability.capture(AnalyticsEvent.PAYWALL_VIEWED, mapOf(AnalyticsProp.TRIGGER to AnalyticsValue.Trigger.FILTER_ROW.key))
            showPro = true
        } else {
            requestAuth()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FarmsyColors.cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // Header (iOS pad h16 t12 b6): "Filters" + xmark close.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.filters_title), style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close, null, tint = Color(0xFF6B7280),
                    modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), CircleShape).tapCard { onDismiss() }.padding(9.dp),
                )
            }

            // "All categories" — clears every filter; on when nothing is selected.
            FilterRow(emoji = "🍽️", tint = FarmsyColors.inkMuted, label = stringResource(R.string.all_categories),
                trailing = "${pins.size}", isOn = !farms.anyFilterOn()) { farms.clearAllFilters() }
            FilterDivider()

            // 10 category rows — emoji in cat.color circle; toggle selectedCategories
            // (iOS: contains → remove, else insert).
            FarmCategory.entries.forEach { cat ->
                FilterRow(emoji = cat.emoji, tint = cat.color, label = stringResource(cat.labelRes),
                    isOn = categories.contains(cat)) {
                    farms.selectedCategories.value =
                        categories.toMutableSet().apply { if (contains(cat)) remove(cat) else add(cat) }
                }
            }
            FilterDivider()

            // 5 quick-filter rows (icon in #F3F4F6 circle).
            FilterRow(icon = Icons.Filled.Verified, label = stringResource(R.string.filter_verified), isOn = fVerified) { farms.filterVerified.value = !fVerified }
            FilterRow(icon = Icons.Filled.Bolt, label = stringResource(R.string.filter_automaat), isOn = fAutomaat) { farms.filterAutomaat.value = !fAutomaat }
            FilterRow(icon = Icons.Filled.Schedule, label = stringResource(R.string.filter_open_today), isOn = fOpen) { farms.filterOpenToday.value = !fOpen }
            FilterRow(icon = Icons.Filled.Eco, label = stringResource(R.string.filter_zelfpluk), isOn = fZelfpluk) { farms.filterZelfpluk.value = !fZelfpluk }
            FilterRow(icon = Icons.Filled.PhotoCamera, label = stringResource(R.string.filter_has_photos), isOn = fPhotos) { farms.filterHasPhotos.value = !fPhotos }

            // When and what kind — the three time filters and the two axis groups.
            FilterDivider()
            FilterSectionHeader(stringResource(R.string.filter_when_and_what))
            FilterRow(icon = Icons.Filled.Schedule, label = stringResource(R.string.pro_open_now),
                isOn = fOpenNow, lockedTrailing = proLocked, dimmed = proLocked) {
                onProTap(AnalyticsValue.Filter.OPEN_NOW) { farms.filterOpenNow.value = !fOpenNow }
            }
            FilterRow(icon = Icons.Filled.CalendarMonth, label = stringResource(R.string.pro_open_saturday),
                isOn = fOpenSat, lockedTrailing = proLocked, dimmed = proLocked) {
                onProTap(AnalyticsValue.Filter.OPEN_SATURDAY) { farms.filterOpenSaturday.value = !fOpenSat }
            }
            FilterRow(icon = Icons.Filled.CalendarMonth, label = stringResource(R.string.pro_open_sunday),
                isOn = fOpenSun, lockedTrailing = proLocked, dimmed = proLocked) {
                onProTap(AnalyticsValue.Filter.OPEN_SUNDAY) { farms.filterOpenSunday.value = !fOpenSun }
            }

            // Type of place — an axis group, now Pro. Combines with categories.
            FilterDivider()
            FilterSectionHeader(stringResource(R.string.filter_type_of_place))
            FarmAxis.placeTypes.forEach { v ->
                val on = v.id in placeTypes
                FilterRow(icon = axisIcon(v.id), label = stringResource(v.labelRes),
                    isOn = on, lockedTrailing = proLocked, dimmed = proLocked) {
                    onProTap(v.id) { farms.selectedPlaceTypes.value = placeTypes.toMutableSet().apply { if (on) remove(v.id) else add(v.id) } }
                }
            }

            FilterDivider()
            FilterSectionHeader(stringResource(R.string.filter_how_grown))
            FarmAxis.methods.forEach { v ->
                val on = v.id in methods
                FilterRow(icon = axisIcon(v.id), label = stringResource(v.labelRes),
                    isOn = on, lockedTrailing = proLocked, dimmed = proLocked) {
                    onProTap(v.id) { farms.selectedMethods.value = methods.toMutableSet().apply { if (on) remove(v.id) else add(v.id) } }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPro) {
        ProUpsellSheet(onDismiss = { showPro = false })
    }
}

/// One filter row (iOS `row()`): 34 circle (emoji→tint fill / icon→#F3F4F6), label
/// `geist(15)` lineLimit1, optional trailing count Capsule, `checkmark`(14) farmGreenMap
/// when on; pad h16 v11, tapCard. `checkmark`→Material `Check` (§7a).
@Composable
private fun FilterRow(
    label: String,
    isOn: Boolean,
    emoji: String? = null,
    icon: ImageVector? = null,
    tint: Color = FarmsyColors.inkMuted,
    trailing: String? = null,
    lockedTrailing: Boolean = false,
    dimmed: Boolean = false,
    onTap: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().tapCard { onTap() }.padding(horizontal = 16.dp, vertical = 11.dp)
            .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(if (emoji != null) tint else Color(0xFFF3F4F6), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                emoji != null -> Text(emoji, style = geist(15.sp))
                icon != null -> Icon(icon, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(15.dp))
            }
        }
        Text(label, style = geist(15.sp), color = FarmsyColors.ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
                modifier = Modifier.background(Color(0xFFF3F4F6), CircleShape).padding(vertical = 3.dp, horizontal = 8.dp),
            )
        }
        if (lockedTrailing) {
            // A member-only row for a non-member: a lock instead of a checkmark.
            Icon(Icons.Filled.Lock, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(13.dp))
        } else if (isOn) {
            Icon(Icons.Filled.Check, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FilterDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = FarmsyColors.hairline)
}

/// iOS sectionHeader: uppercased `geist(11,.semibold)` kerning 1.1 inkMuted, pad h16 t6 b2.
@Composable
private fun FilterSectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = geist(11.sp, FontWeight.SemiBold), letterSpacing = 1.1.sp, color = FarmsyColors.inkMuted,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/// FarmAxis id → Material icon, mirroring iOS `FarmAxisValue.icon` (SF symbols) at the
/// UI layer — Android's core `FarmAxisValue` has no icon field and is left untouched.
/// SF→Material subs recorded in §7a. Unknown id falls back to Place.
private fun axisIcon(id: String): ImageVector = when (id) {
    "shop" -> Icons.Filled.Storefront                    // storefront
    "vending-machine" -> Icons.Filled.Inventory2         // cabinet
    "stall" -> Icons.Filled.ShoppingBasket               // basket
    "milk-tap" -> Icons.Filled.WaterDrop                 // drop
    "self-picking" -> Icons.Filled.FrontHand             // hand.raised
    "self-picking-unstaffed" -> Icons.Filled.DoNotTouch  // hand.raised.slash
    "biodynamic" -> Icons.Filled.NightsStay              // moon.stars
    "regenerative" -> Icons.Filled.Recycling             // arrow.3.trianglepath
    "grass-fed" -> Icons.Filled.Eco                      // leaf
    "sustainable" -> Icons.Filled.Public                 // globe.europe.africa
    else -> Icons.Filled.Place
}

/// The parsed AI values as short chip labels — categories and axes localised the
/// same way as elsewhere, place carrying its radius.
@Composable
private fun aiChips(ai: SmartSearchIntent): List<String> {
    val out = mutableListOf<String>()
    ai.categories.mapNotNull { FarmCategory.from(it) }.forEach { out += stringResource(it.labelRes) }
    ai.products.forEach { p -> out += p.replaceFirstChar { it.uppercase() } }
    (ai.locationTypes + ai.methods).forEach { id ->
        out += id.split('-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
    if (ai.nearMe) {
        out += "📍 ${stringResource(R.string.near_you)} · ${(ai.radiusKm ?: 15.0).toInt()} km"
    } else if (!ai.place.isNullOrEmpty()) {
        out += "📍 ${ai.place} · ${(ai.radiusKm ?: 25.0).toInt()} km"
    }
    return out.distinct()
}

