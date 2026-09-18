package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import app.farmsy.android.core.MonthState
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.ProductProfile
import app.farmsy.android.core.Products
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.Tips
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.MultiImageFarmCard
import app.farmsy.android.features.whatsnew.PingCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.ProductImage
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SearchField
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.TileShape
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.ChronoUnit
import kotlin.math.max

/// Discover — inspiration, not utility. Three tabs under one title, the Nime
/// pattern: Season (the year as a rail of months, each with what is ripe and
/// what to make), Discover (what just arrived, grandmother's tips, pick your
/// own, the community), Farms (search, four chips, farms with a story).
enum class DiscoverTab(val labelRes: Int) {
    SEASON(R.string.discover_tab_season), DISCOVER(R.string.discover), FARMS(R.string.discover_tab_farms)
}

enum class FarmChip(val labelRes: Int) {
    PHOTOS(R.string.with_photos), VERIFIED(R.string.verified), OPEN_TODAY(R.string.open_today), PICK_YOUR_OWN(R.string.filter_zelfpluk);

    fun matches(p: FarmPin): Boolean = when (this) {
        PHOTOS -> p.image != null
        VERIFIED -> p.isVerified
        OPEN_TODAY -> FarmFilters.isOpenToday(p.openingHours)
        PICK_YOUR_OWN -> FarmFilters.looksLikeZelfpluk(p.name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val seasonIdeas by Seasons.ideas.collectAsState()
    val seasonMonth by Seasons.month.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val tips by Tips.tips.collectAsState()
    val products by Products.all.collectAsState()
    var recent by remember { mutableStateOf<List<Ping>>(emptyList()) }
    var recentLoaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(DiscoverTab.SEASON) }
    var openMonth by remember { mutableStateOf<Int?>(null) }
    var farmQuery by remember { mutableStateOf("") }
    var farmChips by remember { mutableStateOf(setOf<FarmChip>()) }

    /// Posts from the last thirty days, newest first. A week was the intent,
    /// but with today's posting volume a week is often empty.
    suspend fun loadRecent() {
        val since = Instant.now().minus(30, ChronoUnit.DAYS).toString()
        recent = FarmContentApi.feedPosts(limit = 60, sinceIso = since)
        recentLoaded = true
    }

    LaunchedEffect(Unit) { farms.loadGalleriesIfNeeded() }
    LaunchedEffect(Unit) { Seasons.loadIfNeeded() }
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { Tips.loadIfNeeded() }
    LaunchedEffect(Unit) { Products.loadIfNeeded(Products.lang(context)) }
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

    // Name or city, folded, plus every selected chip. Nearest first.
    val farmResults: List<FarmPin> = remember(pins, farmQuery, farmChips, location) {
        val q = ProductMatch.fold(farmQuery)
        val hits = pins.filter { pin ->
            (q.isEmpty() || ProductMatch.fold(pin.name).contains(q) || ProductMatch.fold(pin.city ?: "").contains(q)) &&
                farmChips.all { it.matches(pin) }
        }
        farms.sortedByDistance(hits, location)
    }

    /// One idea from a product at its peak this month, fixed for the ISO week
    /// so everyone sees the same one and it changes on Monday (iOS recipeOfWeek).
    val recipeOfWeek: Pair<ProductProfile, ProductProfile.Idea>? = remember(products) {
        val today = LocalDate.now()
        val pool = products.filter { it.state(today.monthValue) == MonthState.PEAK }.flatMap { p -> p.ideas.map { p to it } }
        if (pool.isEmpty()) null else pool[today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % pool.size]
    }

    fun LazyListScope.discoverTab() {
        if (!recentLoaded && tips.isEmpty()) {
            items(3) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(150.dp)) }
            return
        }
        if (justArrived.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.just_arrived), top = 0.dp)
                    Text(stringResource(R.string.just_arrived_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                    Row(
                        Modifier.padding(top = Space.s2).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        justArrived.forEach { (item, count) ->
                            Column(
                                Modifier.width(124.dp).background(FarmsyColors.surface, TileShape)
                                    .tapCard { shell.openProduct(item.id) }
                                    .padding(Space.s4),
                                verticalArrangement = Arrangement.spacedBy(Space.s2),
                            ) {
                                ProductImage(item.imageSlug, item.emoji, 64.dp)
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
        recipeOfWeek?.let { (p, idea) ->
            item {
                Column {
                    SectionHeader(stringResource(R.string.recipe_of_the_week), p.name to { shell.openProduct(p.slug) })
                    CardCarousel(listOf(idea)) {
                        IdeaCard(
                            kicker = null, title = it.title, text = it.body, image = it.image,
                            fallbackImage = p.image, fallback = "🍽️", ingredients = it.ingredients,
                        )
                    }
                }
            }
        }
        if (tips.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.grandmothers_tips))
                    Text(
                        stringResource(R.string.grandmothers_tips_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted,
                        modifier = Modifier.padding(bottom = Space.s2),
                    )
                    CardCarousel(tips) { tip ->
                        IdeaCard(
                            kicker = tip.kicker.text(language), title = tip.title.text(language), text = tip.body.text(language),
                            image = tip.image, fallback = "🧺", ingredients = listOfNotNull(tip.ingredient),
                        )
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
                    ProductImage("strawberry", "🍓", 48.dp)
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
        if (recent.isEmpty() && tips.isEmpty() && pickYourOwn.isEmpty()) {
            item { EmptyState(Icons.Filled.Eco, stringResource(R.string.nothing_new_yet), stringResource(R.string.nothing_new_yet_sub)) }
        }
    }

    fun LazyListScope.farmsTab() {
        item {
            Column {
                SearchField(farmQuery, { farmQuery = it }, stringResource(R.string.farms_search_placeholder))
                Row(
                    Modifier.padding(vertical = Space.s3).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    FarmChip.entries.forEach { chip ->
                        Chip(stringResource(chip.labelRes), selected = chip in farmChips) {
                            farmChips = if (chip in farmChips) farmChips - chip else farmChips + chip
                        }
                    }
                }
            }
        }
        if (farmQuery.isNotEmpty() || farmChips.isNotEmpty()) {
            item {
                Text(
                    (if (farmResults.size == 1) stringResource(R.string.one_farm) else stringResource(R.string.n_farms_arg, farmResults.size)).uppercase(),
                    style = role(TextRole.LABEL), color = FarmsyColors.inkFaint,
                )
            }
            if (farmResults.isEmpty()) {
                item { EmptyState(Icons.Filled.Search, stringResource(R.string.no_farm_matches), stringResource(R.string.try_fewer_words)) }
            }
            items(farmResults.take(40), key = { "farm-" + it.osmId }) { pin ->
                FarmRow(pin, location?.let { pin.distanceMeters(it.latitude, it.longitude) / 1000 }) { shell.openFarm(pin) }
            }
        } else {
            item { SectionHeader(stringResource(R.string.farms_with_a_story), top = 0.dp) }
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

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.discover))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = Space.s4, end = Space.s4, bottom = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            DiscoverTab.entries.forEach { t -> Chip(stringResource(t.labelRes), selected = tab == t) { tab = t } }
        }
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
                when (tab) {
                    DiscoverTab.SEASON -> {
                        if (seasonItems.isEmpty()) {
                            item { SeasonLoading() }
                        } else {
                            item {
                                Text(
                                    stringResource(R.string.season_rail_intro), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted,
                                    modifier = Modifier.padding(bottom = Space.s2),
                                )
                            }
                            items((1..12).toList(), key = { "month-$it" }) { m ->
                                SeasonNode(m, seasonMonth, Seasons.items(m, seasonItems), Seasons.ideas(m, seasonIdeas).size) { openMonth = m }
                            }
                        }
                    }
                    DiscoverTab.DISCOVER -> discoverTab()
                    DiscoverTab.FARMS -> farmsTab()
                }
            }
        }
    }
    openMonth?.let { m ->
        ModalBottomSheet(
            onDismissRequest = { openMonth = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
        ) {
            MonthSheet(m) { openMonth = null }
        }
    }
}


/// One farm as a row: cover or category glyph, name, city, distance.
@Composable
fun FarmRow(pin: FarmPin, km: Double?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape).tapCard(onTap = onOpen).padding(Space.s3),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(Radius.thumb)).background(FarmsyColors.creamFill), contentAlignment = Alignment.Center) {
            Text(pin.primaryCategory.emoji, fontSize = 20.sp)
            if (pin.image != null) AsyncImage(pin.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pin.name, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pin.city?.let { Text(it, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted) }
                if (km != null) Text("· " + String.format("%.1f", km) + " km", style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
    }
}
