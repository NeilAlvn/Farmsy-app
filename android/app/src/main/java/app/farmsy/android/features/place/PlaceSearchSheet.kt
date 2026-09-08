package app.farmsy.android.features.place

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.R
import app.farmsy.android.core.httpClient
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import com.google.android.gms.maps.model.LatLng
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/// S19 · PlaceSearchSheet — the trip's "starting point" picker. iOS
/// (`Features/Trips/PlaceSearch.swift`) wraps `MKLocalSearchCompleter` (free,
/// on-device, NL/BE-biased). Android has no free on-device autocomplete, so the
/// provider differs by decision: **Photon** (OSM-based, `photon.komoot.io/api`) via
/// a plain Ktor GET through the shared client + kotlinx.serialization decode — no
/// key, no dependency. Google Places Autocomplete (New) is the exact-iOS-parity
/// path and is DEFERRED (see PORT_NOTES). Layout/behaviour are 1:1 with iOS.
///
/// Data: Photon returns GeoJSON with coordinates in the SAME response, so there's
/// no completer→resolve two-step like iOS. NL/BE proximity bias (lat/lon +
/// location_bias_scale) is REQUIRED — without it a bare postcode pulls same-number
/// matches worldwide; Photon has no country param, so results are also filtered
/// client-side on `countrycode ∈ {NL, BE}`. Input is debounced (the public
/// instance throttles "extensive usage"; no published numeric limit). An
/// identifiable User-Agent + contact is sent per the usage policy.
///
/// Attribution: place data © OpenStreetMap contributors, under the ODbL — required
/// for OSM-derived data; surfaced as a footer line in the sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchSheet(
    onPick: (LatLng, String) -> Unit,
    onLocate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }

    // Debounced search: wait 300ms after the last keystroke before hitting Photon,
    // rather than firing per keystroke (usage policy throttles extensive use).
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = runCatching { photonSearch(q) }.getOrDefault(emptyList())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = FarmsyColors.cream) {
        Column(Modifier.fillMaxWidth()) {

            // 1 · Header (iOS pad h16 t16 b10): "STARTING POINT" eyebrow + xmark close
            // (13, #6B7280 in 32/#F3F4F6 circle — exact iOS values, PlaceSearch.swift:53-55).
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.starting_point).uppercase(),
                    style = geist(11.sp, FontWeight.SemiBold), letterSpacing = 1.2.sp, color = FarmsyColors.inkMuted,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close, null, tint = Color(0xFF6B7280),
                    modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), CircleShape).noRipple { onDismiss() }.padding(9.dp),
                )
            }

            // 2 · Search bar (magnifyingglass 15 + field + clear); white Capsule + hairline.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, FarmsyColors.hairline, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(15.dp))
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = geist(15.sp).copy(color = FarmsyColors.ink),
                    cursorBrush = SolidColor(FarmsyColors.farmGreen),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_town_address_postcode), style = geist(15.sp), color = FarmsyColors.inkMuted)
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    // xmark.circle.fill clear → Material Close in a filled inkMuted circle (§7a).
                    Box(
                        Modifier.size(16.dp).background(FarmsyColors.inkMuted, CircleShape).noRipple { query = "" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
                }
            }

            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp),
            ) {
                // 3 · Use my location row (location.fill 15 farmGreenMap in 34 @12% circle).
                Row(
                    Modifier.fillMaxWidth().noRipple { onLocate(); onDismiss() }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(34.dp).background(FarmsyColors.farmGreenMap.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.LocationOn, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(15.dp)) }
                    Text(stringResource(R.string.use_my_location), style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
                }
                HorizontalDivider(Modifier.padding(start = 62.dp), color = FarmsyColors.hairline)

                // 4 · Result rows (mappin.circle 16 in a 34-wide slot + title + subtitle).
                results.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().noRipple {
                            focus.clearFocus()
                            onPick(LatLng(r.lat, r.lon), r.title)
                            onDismiss()
                        }.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // mappin.circle → Material Place (§7a).
                        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Place, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(16.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(r.title, style = geist(15.sp), color = FarmsyColors.ink, maxLines = 1)
                            if (r.subtitle.isNotEmpty()) {
                                Text(r.subtitle, style = geist(12.sp), color = FarmsyColors.inkMuted, maxLines = 1)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 62.dp), color = FarmsyColors.hairline)
                }

                // Attribution — place data © OpenStreetMap contributors (ODbL). Prudent
                // for OSM-derived data; iOS's MapKit carries its own, so this row is
                // Android-only and has no iOS counterpart.
                Text(
                    stringResource(R.string.osm_attribution),
                    style = geist(11.sp), color = FarmsyColors.inkMuted.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/// A no-ripple tap (matches iOS `Button(.plain)` rows — no Material ripple).
@Composable
private fun Modifier.noRipple(onClick: () -> Unit): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }

// ── Photon client (feature-local; not core) ──────────────────────────────────

/// One suggestion: `name` → title; `city/state/postcode/country` → subtitle;
/// `geometry.coordinates` → the origin coord.
data class PlacePrediction(val title: String, val subtitle: String, val lat: Double, val lon: Double)

private val photonJson = Json { ignoreUnknownKeys = true; isLenient = true }

/// NL/BE proximity bias centre (roughly between the two) + a strong bias scale, so
/// a bare postcode resolves to the NL/BE match rather than a same-number one abroad.
private const val BIAS_LAT = 51.8
private const val BIAS_LON = 4.7

/// Query Photon for autocomplete suggestions, biased to NL/BE and filtered to those
/// two countries client-side (Photon has no country param). Coordinates arrive in the
/// same response — no second resolve call.
private suspend fun photonSearch(query: String): List<PlacePrediction> {
    val url = "https://photon.komoot.io/api" +
        "?q=${query.encodeURLQueryComponent()}" +
        "&limit=8&lang=default&lat=$BIAS_LAT&lon=$BIAS_LON&location_bias_scale=0.6"
    val resp = httpClient.get(url) {
        // Identifiable UA + contact, per the Photon/OSM usage policy.
        header(HttpHeaders.UserAgent, "FarmsyAndroid/1.0 (app.farmsy.android; hello@farmsy.app)")
    }
    val body = photonJson.decodeFromString<PhotonResponse>(resp.bodyAsText())
    return body.features.mapNotNull { f ->
        val p = f.properties
        val cc = p.countrycode?.uppercase()
        if (cc != "NL" && cc != "BE") return@mapNotNull null
        val coords = f.geometry.coordinates
        if (coords.size < 2) return@mapNotNull null
        val name = p.name ?: return@mapNotNull null
        // Subtitle: the region context, iOS `.subtitle` — city/state + postcode,
        // de-duped against the name, joined with commas.
        val parts = listOfNotNull(
            p.city?.takeIf { it != name },
            p.state?.takeIf { it != name && it != p.city },
            p.postcode,
        )
        PlacePrediction(title = name, subtitle = parts.joinToString(", "), lat = coords[1], lon = coords[0])
    }
}

@Serializable
private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
private data class PhotonFeature(val geometry: PhotonGeometry, val properties: PhotonProperties)

@Serializable
private data class PhotonGeometry(val coordinates: List<Double> = emptyList())

@Serializable
private data class PhotonProperties(
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val countrycode: String? = null,
)
