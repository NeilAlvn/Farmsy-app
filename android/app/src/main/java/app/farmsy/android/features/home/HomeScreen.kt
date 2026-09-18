package app.farmsy.android.features.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmFilters
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.FarmsStore
import app.farmsy.android.core.ProductNearby
import app.farmsy.android.core.RecentReports
import app.farmsy.android.core.ReportStatus
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.detail.minutesAgoLabel
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.AtmosphereBand
import app.farmsy.android.ui.ProductImage
import app.farmsy.android.ui.theme.Badge
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.IconButton
import app.farmsy.android.ui.theme.ListRow
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.PlusLockCard
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.SearchField
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.TileShape
import app.farmsy.android.ui.theme.Wordmark
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.rememberTapHaptic
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import app.farmsy.android.ui.theme.ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.Calendar

/// "Where am I" for a screen: asks for the permission when it is missing, takes
/// a fix when it is granted. Mirrors iOS `locationManager.request()`.
@Composable
fun rememberLocationRequest(): () -> Unit {
    val locationHelper = LocalLocationHelper.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locationHelper.request()
    }
    return {
        if (locationHelper.hasPermission()) locationHelper.request()
        else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/// Home — "what should I know today?" Personal, not a directory: what is
/// available near you, the farms you follow, and what Plus would tell you.
/// Profile lives behind the person button in the header.
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val session = LocalSession.current
    val farms = LocalFarms.current
    val favorites = LocalFavorites.current
    val locationHelper = LocalLocationHelper.current
    val shell = LocalShell.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()
    val tap = rememberTapHaptic()
    val requestLocation = rememberLocationRequest()

    val currentSession by session.session.collectAsState()
    val profile by session.profile.collectAsState()
    val isAuthenticated = currentSession != null
    val pins by farms.pins.collectAsState()
    val flagsLoaded by farms.flagsLoaded.collectAsState()
    val savedIds by favorites.osmIds.collectAsState()
    val location by locationHelper.location.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val items by ShoppingItems.items.collectAsState()
    val language = remember { ShoppingItems.language(context) }

    var query by remember { mutableStateOf("") }
    var nearby by remember { mutableStateOf<List<ProductNearby>>(emptyList()) }
    var nearbyReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { farms.loadFlagsIfNeeded() }
    LaunchedEffect(Unit) { Seasons.loadIfNeeded() }
    // Plus: the recent-reports feed, for "confirmed 18 min ago" on a tile.
    val plus = session.hasFullAccess
    LaunchedEffect(plus) { if (plus) RecentReports.refresh() }
    val recentReports by RecentReports.reports.collectAsState()
    val nearReports = remember(recentReports, plus, location, radiusKm, pins) {
        if (!plus || location == null) emptyList()
        else RecentReports.near(location, radiusKm, pins.associateBy { it.osmId })
    }
    val seasonItems by Seasons.items.collectAsState()
    val seasonMonth by Seasons.month.collectAsState()
    val seasonPicks = remember(seasonItems, seasonMonth) { Seasons.thisMonth(seasonItems, seasonMonth).take(6) }
    LaunchedEffect(Unit) { if (location == null && locationHelper.hasPermission()) locationHelper.request() }
    LaunchedEffect(location, radiusKm, items.size, flagsLoaded, pins.size) {
        val loc = location ?: return@LaunchedEffect
        if (!flagsLoaded || items.isEmpty() || pins.isEmpty()) return@LaunchedEffect
        val produce = farms.produceByOsm
        nearby = withContext(Dispatchers.Default) {
            FarmsStore.productsNearby(items, pins, produce, loc.latitude, loc.longitude, radiusKm)
        }
        nearbyReady = true
    }

    val greeting = run {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = stringResource(
            when (hour) { in 5..11 -> R.string.good_morning; in 12..17 -> R.string.good_afternoon; else -> R.string.good_evening }
        )
        val name = profile?.firstName?.trim().orEmpty()
        if (name.isEmpty()) base else "$base, $name"
    }
    val savedPins = remember(pins, savedIds, location) {
        farms.sortedByDistance(pins.filter { it.osmId in savedIds }, location)
    }

    Column(
        Modifier.fillMaxSize().background(FarmsyColors.cream).verticalScroll(rememberScrollState()),
    ) {
        // MARK: Header band
        Box(Modifier.fillMaxWidth().height(176.dp)) {
            AtmosphereBand(Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.s4, vertical = Space.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(Icons.Outlined.Person, stringResource(R.string.profile), overMedia = true) { shell.openProfile() }
                Spacer(Modifier.weight(1f))
                Wordmark(onAtmosphere = true)
                Spacer(Modifier.weight(1f))
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(Icons.Outlined.LocationOn, stringResource(R.string.search_radius), overMedia = true) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = FarmsyColors.surface) {
                        SearchRadius.choices.forEach { km ->
                            DropdownMenuItem(
                                text = { Text("${km.toInt()} km", style = ui(16.sp), color = FarmsyColors.ink) },
                                leadingIcon = { if (km == radiusKm) Icon(Icons.Filled.Check, null, tint = FarmsyColors.ink, modifier = Modifier.size(18.dp)) },
                                onClick = { SearchRadius.set(context, km); menu = false },
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = Space.s4).padding(bottom = TabBarInset.content)) {
            // MARK: Greeting
            Column(
                Modifier.offset(y = -Space.s2).fillMaxWidth().card(),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                    Text(greeting, style = role(TextRole.HEADING), color = FarmsyColors.ink)
                    Text(
                        if (location == null) stringResource(R.string.home_local_food_near)
                        else stringResource(R.string.home_local_food_within_arg, radiusKm.toInt()),
                        style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted,
                    )
                }
                SearchField(query, { query = it }, stringResource(R.string.search_farms_or_products)) {
                    val q = query.trim()
                    if (q.isNotEmpty()) {
                        farms.searchText.value = q
                        shell.showTab(AppTab.MAP)
                    }
                }
                PillButton(stringResource(R.string.open_now_near_me), PillVariant.PRIMARY, PillSize.MEDIUM, block = true, icon = Icons.Filled.Schedule) {
                    farms.clearAllFilters()
                    farms.filterOpenNow.value = true
                    shell.showTab(AppTab.MAP)
                }
            }

            // MARK: Available near you
            SectionHeader(stringResource(R.string.available_near_you), stringResource(R.string.shopping) to { shell.showTab(AppTab.SHOPPING) })
            when {
                location == null -> InfoCard(
                    Icons.Filled.LocationOff, stringResource(R.string.where_are_you), stringResource(R.string.allow_location_to_see),
                    stringResource(R.string.allow) to requestLocation,
                )
                !nearbyReady -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    repeat(3) { SkeletonBox(cornerRadius = Radius.tile, modifier = Modifier.size(172.dp, 120.dp)) }
                }
                nearby.isEmpty() -> Text(
                    stringResource(R.string.home_no_farm_lists_arg, radiusKm.toInt()),
                    style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted, modifier = Modifier.fillMaxWidth().card(),
                )
                else -> Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    nearby.take(10).forEach { p ->
                        Column(
                            Modifier.width(172.dp).background(FarmsyColors.surface, TileShape).tapCard {
                                scope.launch {
                                    farms.showProduct(p.item.label(language), p.item.terms, location, radiusKm)
                                    shell.showTab(AppTab.MAP)
                                }
                            }.padding(Space.s4),
                            verticalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            ProductImage(p.item.imageSlug, p.item.emoji, 64.dp)
                            Text(p.item.label(language), style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.home_farms_km_arg, p.count, String.format("%.1f", p.nearestKm)),
                                style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted, maxLines = 1,
                            )
                            // Plus: the newest "open" report within the radius that names
                            // this product. Free tiles say nothing about timing.
                            val hit = nearReports
                                .filter { it.status == ReportStatus.OPEN.wire && p.item.id in it.products }
                                .mapNotNull { it.report.instant }.maxOrNull()
                            if (hit != null) {
                                val mins = maxOf(0L, Duration.between(hit, Instant.now()).toMinutes()).toInt()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(6.dp).background(FarmsyColors.vividPositive, CircleShape))
                                    Text(
                                        stringResource(R.string.confirmed_arg, minutesAgoLabel(mins)),
                                        style = role(TextRole.CAPTION), color = FarmsyColors.positive, maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MARK: This week — season news. Renders nothing until the calendar has
            // loaded, so a missing endpoint costs a section, not a screen.
            if (seasonPicks.isNotEmpty()) {
                SectionHeader(stringResource(R.string.in_season_now), stringResource(R.string.see_all) to { shell.showTab(AppTab.DISCOVER) })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    seasonPicks.forEach { item ->
                        Column(
                            Modifier.width(148.dp).background(FarmsyColors.surface, CardShape).tapCard {
                                scope.launch {
                                    farms.showProduct(item.label(language), item.terms, location, radiusKm)
                                    shell.showTab(AppTab.MAP)
                                }
                            }.padding(Space.s3),
                            verticalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            Box {
                                ProductImage(item.imageSlug, item.emoji, 124.dp, corner = Radius.tile)
                                if (item.isPeak(seasonMonth)) {
                                    Badge(stringResource(R.string.peak), fill = FarmsyColors.vivid, ink = FarmsyColors.ink, modifier = Modifier.align(Alignment.TopEnd).padding(Space.s2))
                                }
                            }
                            Text(item.label(language), style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.find_it_nearby), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
                        }
                    }
                }
            }

            // MARK: Your farms
            SectionHeader(
                stringResource(R.string.your_farms),
                if (isAuthenticated && savedPins.size > 3) stringResource(R.string.see_all) to { shell.showTab(AppTab.MAP) } else null,
            )
            when {
                !isAuthenticated -> InfoCard(
                    Icons.Outlined.FavoriteBorder, stringResource(R.string.keep_your_favourites), stringResource(R.string.sign_in_to_follow_farms),
                    stringResource(R.string.sign_in) to requestAuth,
                )
                savedPins.isEmpty() -> InfoCard(
                    Icons.Outlined.FavoriteBorder, stringResource(R.string.no_farms_followed_yet), stringResource(R.string.tap_heart_opening), null,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    savedPins.take(3).forEach { pin -> FarmRow(pin, location?.let { pin.distanceMeters(it.latitude, it.longitude) / 1000 }) { tap(); shell.openFarm(pin) } }
                }
            }

            // MARK: For you
            SectionHeader(stringResource(R.string.for_you))
            if (session.hasFullAccess) {
                Box(Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape)) {
                    ListRow(Icons.Outlined.Notifications, stringResource(R.string.product_alerts), subtitle = stringResource(R.string.product_alerts_sub)) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.farmsy.app/alerts")))
                    }
                }
            } else {
                PlusLockCard(stringResource(R.string.plus_alerts_title), stringResource(R.string.plus_alerts_text), onUnlock = shell.openPlus)
            }
        }
    }
}

/// Icon, title + caption, optional small primary action — the "do this first" card.
@Composable
private fun InfoCard(icon: ImageVector, title: String, text: String, action: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth().card(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
        Icon(icon, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink)
            Text(text, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
        }
        action?.let { (label, run) -> PillButton(label, PillVariant.PRIMARY, PillSize.SMALL, onClick = run) }
    }
}

/// A followed farm: category tile, name, today's opening dot and distance.
@Composable
private fun FarmRow(pin: FarmPin, km: Double?, onClick: () -> Unit) {
    val open = FarmFilters.isOpenToday(pin.openingHours)
    Row(
        Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape).clickable(onClick = onClick).padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(Modifier.size(48.dp).background(FarmsyColors.creamFill, RoundedCornerShape(Radius.thumb)), contentAlignment = Alignment.Center) {
            Text(pin.primaryCategory.emoji, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pin.name, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(if (open) FarmsyColors.vividPositive else FarmsyColors.hairline, CircleShape))
                Text(
                    stringResource(if (open) R.string.open_today else R.string.closed_today) + (km?.let { " · ${String.format("%.1f", it)} km" } ?: ""),
                    style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
    }
}
