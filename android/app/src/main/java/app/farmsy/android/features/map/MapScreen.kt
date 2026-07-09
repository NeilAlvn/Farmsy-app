package app.farmsy.android.features.map

import android.Manifest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
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
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
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

/// Map-first discovery — mirrors iOS MapScreen: full-bleed map with a floating
/// search row, farms-count badge, and bottom controls (list toggle + category
/// menu). Tapping a pin opens the farm (auth-gated one level up).
@Composable
fun MapScreen(onOpenFarm: (FarmPin) -> Unit) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current

    val pins by farms.pins.collectAsState()
    val isLoading by farms.isLoading.collectAsState()
    val loadError by farms.loadError.collectAsState()
    val searchText by farms.searchText.collectAsState()
    val selectedCategory by farms.selectedCategory.collectAsState()
    val userLocation by locationHelper.location.collectAsState()

    var showList by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) locationHelper.request() }

    // Centered between NL and BE to start (same as iOS).
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(51.8, 4.7), 6.5f)
    }

    // SwiftUI/Compose maps slow down past a few hundred markers — cap what we
    // draw to the pins near the current viewport (same annotationCap as iOS).
    val filtered = remember(pins, searchText, selectedCategory) { farms.filtered() }

    // Only recompute the drawn markers once the camera has settled, and read
    // the position *outside* composition — reading it during composition makes
    // every recomposition observe the camera and drift the map on its own.
    var mapCenter by remember { mutableStateOf(LatLng(51.8, 4.7)) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            mapCenter = cameraPositionState.position.target
        }
    }
    val visiblePins = remember(filtered, mapCenter) {
        filtered.sortedBy { it.distanceMeters(mapCenter.latitude, mapCenter.longitude) }.take(130)
    }

    Box(Modifier.fillMaxSize()) {
        if (showList) {
            LazyColumn(
                Modifier.fillMaxSize().background(FarmsyColors.cream)
                    .padding(horizontal = 14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 80.dp, bottom = 150.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(farms.sortedByDistance(filtered, userLocation)) { pin ->
                    FarmRow(pin) { onOpenFarm(pin) }
                }
            }
        } else {
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
                    Marker(
                        state = MarkerState(LatLng(pin.lat, pin.lng)),
                        title = pin.name,
                        snippet = pin.city,
                        // Category-tinted pins, mirroring the iOS teardrops.
                        icon = BitmapDescriptorFactory.defaultMarker(pin.primaryCategory.markerHue),
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
                        onValueChange = { farms.searchText.value = it },
                        placeholder = { Text(stringResource(R.string.search_by_farm_city_or_postcode), style = geist(15.sp)) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = FarmsyColors.inkMuted) },
                        singleLine = true,
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
            if (!showList) {
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

        // Bottom controls, lifted above the floating tab bar
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = 66.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(48.dp).clickable { showList = !showList },
                shape = CircleShape, color = Color.White, shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = FarmsyColors.farmGreen)
                }
            }
            CategoryMenu(
                selected = selectedCategory,
                onSelect = { farms.selectedCategory.value = it },
                modifier = Modifier.weight(1f)
            )
        }
    }
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
            Modifier.fillMaxWidth().clickable { expanded = true },
            shape = CircleShape, color = Color.White, shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(vertical = 14.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selected?.let { "${it.emoji} ${stringResource(it.labelRes)}" }
                        ?: stringResource(R.string.all_categories_2),
                    style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen, maxLines = 1
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

@Composable
private fun FarmRow(pin: FarmPin, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
            .clickable { onOpen() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(pin.name, style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink)
            (pin.city ?: pin.address)?.let {
                Text(it, style = geist(14.sp), color = FarmsyColors.inkMuted, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pin.categories.take(4).forEach { Text(it.emoji, fontSize = 16.sp) }
            }
        }
    }
}
