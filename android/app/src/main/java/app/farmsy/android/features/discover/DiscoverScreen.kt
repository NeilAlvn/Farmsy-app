package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.R
import app.farmsy.android.core.FarmContentApi
import app.farmsy.android.core.FarmFilters
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.Ping
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.MultiImageFarmCard
import app.farmsy.android.features.whatsnew.PingCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.TileShape
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.max

/// Discover — inspiration, not utility. What is in season, what people just
/// found at farms, where to pick your own this weekend, what the community is
/// saying, then the farms with a story. Every item is one tap from the map.
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    val farms = LocalFarms.current
    val shell = LocalShell.current
    val galleries by farms.galleries.collectAsState()
    val galleriesLoaded by farms.galleriesLoaded.collectAsState()
    val featuredTeasers by farms.featuredTeasers.collectAsState()
    val pins by farms.pins.collectAsState()

    val locationHelper = LocalLocationHelper.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location by locationHelper.location.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val language = remember { ShoppingItems.language(context) }
    val seasonItems by Seasons.items.collectAsState()
    val seasonMonth by Seasons.month.collectAsState()
    val inSeason = remember(seasonItems, seasonMonth) { Seasons.thisMonth(seasonItems, seasonMonth) }
    val catalogue by ShoppingItems.items.collectAsState()
    var recent by remember { mutableStateOf<List<Ping>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }

    /// Posts from the last thirty days, newest first. A week was the intent,
    /// but with today's posting volume a week is often empty.
    suspend fun loadRecent() {
        val since = Instant.now().minus(30, ChronoUnit.DAYS).toString()
        recent = FarmContentApi.feedPosts(limit = 60, sinceIso = since)
    }

    LaunchedEffect(Unit) { farms.loadGalleriesIfNeeded() }
    LaunchedEffect(Unit) { Seasons.loadIfNeeded() }
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { loadRecent() }

    // "Just arrived": products people mentioned at farms in the last month,
    // most farms first. A post saying "verse aardbeien vandaag" is the signal —
    // the same one the product-alert cron uses.
    val justArrived: List<Pair<ShoppingItem, Int>> = remember(recent, catalogue) {
        if (recent.isEmpty()) emptyList() else catalogue.mapNotNull { item ->
            val n = recent.filter { ProductMatch.matches(it.body, item.terms) }.map { it.farmOsmId }.toSet().size
            if (n == 0) null else item to n
        }.sortedByDescending { it.second }.take(8)
    }

    // Pick-your-own farms within the radius (at least 25 km) that open on
    // Saturday or Sunday, nearest first.
    val pickYourOwn: List<FarmPin> = remember(pins, location, radiusKm) {
        val loc = location ?: return@remember emptyList()
        val near = pins.filter {
            FarmFilters.looksLikeZelfpluk(it.name) &&
                it.distanceMeters(loc.latitude, loc.longitude) / 1000 <= max(radiusKm, 25.0) &&
                (FarmFilters.isOpenOnDay(it.openingHours, 5) || FarmFilters.isOpenOnDay(it.openingHours, 6))
        }
        farms.sortedByDistance(near, loc).take(3)
    }

    // The store's frozen once-shuffled order, resolved to pins, capped at 10.
    val featured = remember(galleriesLoaded, pins) {
        if (galleriesLoaded) farms.featuredFarms.take(10) else emptyList()
    }

    fun show(label: String, terms: List<String>) {
        scope.launch {
            farms.showProduct(label, terms, location, radiusKm)
            shell.showTab(AppTab.MAP)
        }
    }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.discover))
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; loadRecent(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
                contentPadding = PaddingValues(bottom = TabBarInset.content),
            ) {
                if (inSeason.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(stringResource(R.string.in_season_near_you), top = 0.dp)
                            Text(stringResource(R.string.in_season_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                            FlowRow(
                                Modifier.padding(top = Space.s2, bottom = Space.s2),
                                horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2),
                            ) {
                                inSeason.forEach { item ->
                                    Chip(item.label(language), emoji = item.emoji, dot = if (item.isPeak(seasonMonth)) FarmsyColors.vivid else null) {
                                        show(item.label(language), item.terms)
                                    }
                                }
                            }
                        }
                    }
                }
                if (justArrived.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader(stringResource(R.string.just_arrived))
                            Text(stringResource(R.string.just_arrived_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                            Row(
                                Modifier.padding(top = Space.s2).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Space.s3),
                            ) {
                                justArrived.forEach { (item, count) ->
                                    Column(
                                        Modifier.width(124.dp).background(FarmsyColors.surface, TileShape)
                                            .tapCard { show(item.label(language), item.terms) }
                                            .padding(Space.s4),
                                        verticalArrangement = Arrangement.spacedBy(Space.s2),
                                    ) {
                                        Text(item.emoji, fontSize = 28.sp)
                                        Text(item.label(language), style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            if (count == 1) stringResource(R.string.one_farm) else stringResource(R.string.n_farms_arg, count),
                                            style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (pickYourOwn.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(R.string.pick_your_own_weekend),
                            stringResource(R.string.map) to {
                                farms.clearAllFilters()
                                farms.filterZelfpluk.value = true
                                shell.showTab(AppTab.MAP)
                            },
                        )
                    }
                    items(pickYourOwn, key = { "pyo-" + it.osmId }) { pin ->
                        val today = FarmFilters.isOpenToday(pin.openingHours)
                        val km = location?.let { pin.distanceMeters(it.latitude, it.longitude) / 1000 }
                        Row(
                            Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape)
                                .tapCard { shell.openFarm(pin) }.padding(Space.s3),
                            horizontalArrangement = Arrangement.spacedBy(Space.s3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp).background(FarmsyColors.creamFill, RoundedCornerShape(Radius.thumb)), contentAlignment = Alignment.Center) {
                                Text("🍓", fontSize = 22.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(pin.name, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).background(if (today) FarmsyColors.vividPositive else FarmsyColors.hairline, CircleShape))
                                    Text(
                                        stringResource(if (today) R.string.open_today else R.string.open_this_weekend),
                                        style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
                                    )
                                    if (km != null) Text("· " + String.format("%.1f", km) + " km", style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
                                }
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.from_the_community), stringResource(R.string.see_all) to { shell.showTab(AppTab.COMMUNITY) })
                    }
                    items(recent.take(2), key = { "ping-" + it.id }) { ping ->
                        val farm = farms.pinForOsmId(ping.farmOsmId)
                        PingCard(ping = ping, farmName = farm?.name, onOpenFarm = { farm?.let { shell.openFarm(it) } })
                    }
                }
                item { SectionHeader(stringResource(R.string.farms_with_a_story)) }
                if (!galleriesLoaded) {
                    items(3) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(180.dp)) }
                } else {
                    items(featured, key = { it.osmId }) { pin ->
                        MultiImageFarmCard(
                            pin = pin,
                            images = galleries[pin.osmId] ?: emptyList(),
                            teaser = featuredTeasers[pin.osmId],
                            onOpen = { shell.openFarm(pin) },
                        )
                    }
                }
                item { RecommendationCarousel(onOpenFarm = shell.openFarm, modifier = Modifier.padding(top = Space.s4), cardHeight = 156.dp) }
            }
        }
    }
}
