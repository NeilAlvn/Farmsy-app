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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.R
import app.farmsy.android.core.FarmCategory
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SmartSearchApi
import app.farmsy.android.core.SmartSearchIntent
import app.farmsy.android.ui.theme.FarmsyColors
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
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import app.farmsy.android.ui.theme.FitText

/// Compose maps slow down past a few hundred markers (same cap as iOS).
private const val ANNOTATION_CAP = 130

/// Cap the drawn markers without skewing the visible category mix. Naively
/// taking the first N draws them in database order, which clusters one or two
/// colours; instead spread the budget evenly across the pins in view.
private fun capRepresentative(pins: List<FarmPin>, cap: Int): List<FarmPin> {
    if (pins.size <= cap) return pins
    val stride = pins.size.toDouble() / cap
    return (0 until cap).map { pins[(it * stride).toInt()] }
}

/// Marker bitmaps are expensive to rasterise — build one per category, once.
private val pinIcons = HashMap<FarmCategory, BitmapDescriptor>()

/// Teardrop pin in the category's colour with its emoji, matching the iOS
/// FarmPinView and the web map's markers.
private fun farmPinBitmap(cat: FarmCategory): BitmapDescriptor {
    val w = 84; val h = 108
    val bmp = createBitmap(w, h)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val cx = w / 2f
    val headR = 34f
    val headCy = 38f

    // Drop shadow
    paint.color = 0x33000000
    canvas.drawCircle(cx, headCy + 3f, headR, paint)

    // Teardrop: circle head + triangular tail
    paint.color = cat.color.toArgb()
    canvas.drawCircle(cx, headCy, headR, paint)
    val tail = Path().apply {
        moveTo(cx - 22f, headCy + 24f)
        lineTo(cx, h - 6f)
        lineTo(cx + 22f, headCy + 24f)
        close()
    }
    canvas.drawPath(tail, paint)

    // White inner circle
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, headCy, 22f, paint)

    // Category emoji
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    val fm = text.fontMetrics
    canvas.drawText(cat.emoji, cx, headCy - (fm.ascent + fm.descent) / 2f, text)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/// Map-first discovery — mirrors iOS MapScreen: full-bleed map with a floating
/// search row, farms-count badge, and bottom controls (list toggle + category
/// menu). Tapping a pin opens the farm (auth-gated one level up).
@Composable
fun MapScreen(onOpenFarm: (FarmPin) -> Unit, bottomInset: Dp = 96.dp) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current

    val pins by farms.pins.collectAsState()
    val isLoading by farms.isLoading.collectAsState()
    val loadError by farms.loadError.collectAsState()
    val searchText by farms.searchText.collectAsState()
    val selectedCategory by farms.selectedCategory.collectAsState()
    val userLocation by locationHelper.location.collectAsState()
    val aiIntent by farms.aiIntent.collectAsState()
    val aiPlaceToken by farms.aiPlaceToken.collectAsState()
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

    // Maps slow down past a few hundred markers, so draw only the pins inside
    // the current viewport, capped — same rule as iOS MapScreen.visiblePins.
    // Zooming in therefore reveals the farms in that area.
    val filtered = remember(pins, searchText, selectedCategory, aiIntent) { farms.filtered() }

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

    // Snapshot the viewport only once the camera settles. Reading the camera
    // during composition makes every recomposition observe it, which nudges
    // the camera and walks the map away on its own.
    var viewport by remember { mutableStateOf<LatLngBounds?>(null) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            viewport = cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
    }
    val visiblePins = remember(filtered, viewport) {
        val inView = viewport?.let { bounds ->
            // Pad the box slightly so pins don't pop in right at the edge.
            val latPad = (bounds.northeast.latitude - bounds.southwest.latitude) * 0.075
            val lngPad = (bounds.northeast.longitude - bounds.southwest.longitude) * 0.075
            filtered.filter {
                it.lat > bounds.southwest.latitude - latPad &&
                    it.lat < bounds.northeast.latitude + latPad &&
                    it.lng > bounds.southwest.longitude - lngPad &&
                    it.lng < bounds.northeast.longitude + lngPad
            }
        } ?: filtered
        capRepresentative(inView, ANNOTATION_CAP)
    }

    Box(Modifier.fillMaxSize()) {
        run {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
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
                visiblePins.forEach { pin ->
                    val cat = pin.primaryCategory
                    val icon = remember(cat) { pinIcons[cat] ?: farmPinBitmap(cat).also { pinIcons[cat] = it } }
                    Marker(
                        state = MarkerState(LatLng(pin.lat, pin.lng)),
                        title = pin.name,
                        snippet = pin.city,
                        icon = icon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1f),
                        onClick = { onOpenFarm(pin); true },
                        onInfoWindowClick = { onOpenFarm(pin) },
                    )
                }
            }
        }

        // Floating search row + farms-count badge
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    Modifier.weight(1f), shape = RoundedCornerShape(15.dp),
                    color = Color.White, shadowElevation = 6.dp
                ) {
                    TextField(
                        value = searchText,
                        onValueChange = {
                            farms.searchText.value = it
                            // Emptying the field drops the AI intent.
                            if (it.isBlank() && aiIntent != null) farms.clearAISearch()
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.search_or_ask),
                                style = geist(15.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (aiSearching) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FarmsyColors.farmGreen)
                            } else {
                                Icon(
                                    if (aiIntent != null) Icons.Filled.AutoAwesome else Icons.Filled.Search,
                                    null,
                                    tint = if (aiIntent != null) FarmsyColors.farmGreen else FarmsyColors.inkMuted,
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSmartSearch() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                }
                Surface(
                    Modifier.size(48.dp).clickable {
                        if (locationHelper.hasPermission()) locationHelper.request()
                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    shape = RoundedCornerShape(15.dp), color = Color.White, shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MyLocation, null, tint = FarmsyColors.farmGreen)
                    }
                }
            }

            // AI summary bar — the parsed summary + values as chips, an × to clear.
            aiIntent?.let { ai ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
                    Column(Modifier.padding(vertical = 11.dp, horizontal = 14.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(16.dp))
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
                                    Surface(shape = CircleShape, color = FarmsyColors.farmGreen.copy(alpha = 0.12f)) {
                                        Text(
                                            chip, style = geist(11.sp, FontWeight.SemiBold),
                                            color = FarmsyColors.farmGreen,
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.95f), shadowElevation = 4.dp) {
                Row(
                    Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = FarmsyColors.farmGreen)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.loading_farms), style = geist(13.sp, FontWeight.Medium))
                    } else {
                        Text(
                            stringResource(R.string.arg_farms, filtered.size.toString()),
                            style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted
                        )
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
                    style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                    modifier = Modifier.clickable { farms.loadIfNeeded() }
                )
            }
        }

        // Category filter, bottom-left and compact. The list toggle that used to sit
        // beside it is gone — it duplicated the Discover tab, which already offers a
        // browsable list of farms — so the map is just the map now. The pill sizes to
        // its own label rather than stretching across, so it reads as a control, not
        // a banner.
        Box(
            Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                .padding(bottom = bottomInset, start = 12.dp)
        ) {
            CategoryMenu(
                selected = selectedCategory,
                onSelect = { farms.selectedCategory.value = it },
            )
        }
    }
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

@Composable
private fun CategoryMenu(
    selected: FarmCategory?,
    onSelect: (FarmCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            Modifier.clickable { expanded = true },
            shape = CircleShape, color = Color.White, shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(vertical = 11.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // When a category is picked, show its emoji + name. With none, keep
                // it short — "Categories", not "All Categories" — so the pill stays
                // a compact control rather than a banner across the map.
                Text(
                    selected?.let { "${it.emoji} ${stringResource(it.labelRes)}" }
                        ?: "🍽️ ${stringResource(R.string.categories_short)}",
                    style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen, maxLines = 1
                )
                Spacer(Modifier.size(6.dp))
                Icon(Icons.Filled.KeyboardArrowUp, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_categories)) },
                onClick = { onSelect(null); expanded = false }
            )
            FarmCategory.entries.forEach { cat ->
                DropdownMenuItem(
                    text = { Text("${cat.emoji} ${stringResource(cat.labelRes)}") },
                    onClick = { onSelect(cat); expanded = false }
                )
            }
        }
    }
}

