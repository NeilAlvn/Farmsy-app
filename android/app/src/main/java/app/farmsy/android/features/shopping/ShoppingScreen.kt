package app.farmsy.android.features.shopping

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.ShoppingPlanner
import app.farmsy.android.core.lenientJson
import app.farmsy.android.features.home.rememberLocationRequest
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.PlusLockCard
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.TileShape
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.rememberTapHaptic
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.ui
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/// Previous lists, newest first, as JSON in prefs — small, local, and only ever
/// written when a route is built from a list. Same key as iOS (`shoppingHistory`).
private object ShoppingHistory {
    private const val KEY = "shoppingHistory"
    fun read(context: Context): List<List<String>> = runCatching {
        lenientJson.decodeFromString<List<List<String>>>(
            context.getSharedPreferences("farmsy", Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        )
    }.getOrDefault(emptyList())

    fun remember(context: Context, list: List<String>): List<List<String>> {
        val all = (listOf(list) + read(context).filter { it != list }).take(5)
        context.getSharedPreferences("farmsy", Context.MODE_PRIVATE).edit()
            .putString(KEY, lenientJson.encodeToString(all)).apply()
        return all
    }
}

/// Shopping — "help me get my local groceries". The list is free. Which farms
/// answer it, and the route between them, is Farmsy Plus: that is the thing a
/// map cannot do, and the reason to pay.
///
/// The list itself is `TripStore.wantedProducts` (already persisted, already
/// what the trip planner's chips read), so nothing is stored twice.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen() {
    val context = LocalContext.current
    val session = LocalSession.current
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current
    val trip = LocalTrip.current
    val shell = LocalShell.current
    val scope = rememberCoroutineScope()
    val tap = rememberTapHaptic()
    val requestLocation = rememberLocationRequest()

    val wanted by trip.wantedProducts.collectAsState()
    val tripOrigin by trip.originCoord.collectAsState()
    val location by locationHelper.location.collectAsState()
    val pins by farms.pins.collectAsState()
    val flagsLoaded by farms.flagsLoaded.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val loadFailed by ShoppingItems.loadFailed.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val language = remember { ShoppingItems.language(context) }

    var plan by remember { mutableStateOf<ShoppingPlanner.Plan?>(null) }
    var matchingFarms by remember { mutableStateOf<Int?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(ShoppingHistory.read(context)) }

    val picked = remember(wanted, catalogue) { wanted.mapNotNull { id -> catalogue.firstOrNull { it.id == id } } }
    val origin: LatLng? = tripOrigin ?: location?.let { LatLng(it.latitude, it.longitude) }
    fun labels(ids: List<String>) = ids.map { id -> catalogue.firstOrNull { it.id == id }?.label(language) ?: id }

    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { farms.loadFlagsIfNeeded() }
    LaunchedEffect(wanted) { plan = null }
    // The free half of the answer: how many farms in the radius sell any of it.
    LaunchedEffect(wanted, origin, radiusKm, flagsLoaded, pins.size, picked.size) {
        val o = origin
        if (o == null || !flagsLoaded || picked.isEmpty()) { matchingFarms = null; return@LaunchedEffect }
        val terms = picked.flatMap { it.terms }
        val produce = farms.produceByOsm
        matchingFarms = withContext(Dispatchers.Default) {
            pins.count { pin ->
                val sells = produce[pin.osmId] ?: return@count false
                pin.distanceMeters(o.latitude, o.longitude) / 1000 <= radiusKm && ProductMatch.covers(sells, terms)
            }
        }
    }

    fun findFarms() {
        val o = origin ?: return
        isPlanning = true
        scope.launch {
            farms.loadFlagsIfNeeded()
            val candidates = pins.map {
                ShoppingPlanner.Candidate(it.osmId, LatLng(it.lat, it.lng), farms.produceFor(it.osmId) ?: "")
            }
            plan = withContext(Dispatchers.Default) {
                ShoppingPlanner.plan(wanted = picked, farms = candidates, origin = o, radiusKm = radiusKm)
            }
            isPlanning = false
        }
    }

    /// The picks become trip stops; the planner orders them and draws the road.
    fun buildRoute(p: ShoppingPlanner.Plan) {
        history = ShoppingHistory.remember(context, wanted)
        trip.addStops(p.picks.map { it.osmId })
        trip.requestFit()
        shell.openTrips()
    }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.shopping))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s4).padding(bottom = TabBarInset.content),
        ) {
            // MARK: The list
            if (picked.isEmpty()) {
                Text(stringResource(R.string.shopping_what_need), style = role(TextRole.HEADING), color = FarmsyColors.ink)
                Text(
                    stringResource(R.string.shopping_intro), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted,
                    modifier = Modifier.padding(top = Space.s1),
                )
            } else {
                SectionHeader(stringResource(R.string.this_weeks_list), stringResource(R.string.clear) to { trip.clearProducts() }, top = 0.dp)
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    picked.forEach { item ->
                        val remove = stringResource(R.string.remove_arg, item.label(language))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(FarmsyColors.surface, CardShape)
                                .padding(horizontal = Space.s4, vertical = Space.s2),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                        ) {
                            Text(item.emoji, fontSize = 22.sp, modifier = Modifier.width(32.dp))
                            Text(item.label(language), style = role(TextRole.BODY), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.size(32.dp).clickable { tap(); trip.toggleProduct(item.id) }.semantics { contentDescription = remove },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            // MARK: Farms for the list
            if (picked.isNotEmpty()) {
                SectionHeader(stringResource(R.string.farms_for_your_list))
                val n = matchingFarms
                when {
                    origin == null -> Row(
                        Modifier.fillMaxWidth().card(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Icon(Icons.Filled.LocationOff, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.allow_location_look_around), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted, modifier = Modifier.weight(1f))
                        PillButton(stringResource(R.string.allow), PillVariant.PRIMARY, PillSize.SMALL, onClick = requestLocation)
                    }
                    // The lock card bleeds to the card's edges (iOS negative padding),
                    // so the card's own padding sits on the text, not the container.
                    n != null -> Column(Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                        val locked = n > 0 && !session.hasFullAccess
                        Text(
                            if (n == 0) stringResource(R.string.shopping_none_within_arg, radiusKm.toInt())
                            else stringResource(R.string.shopping_match_arg, n, radiusKm.toInt()),
                            style = role(TextRole.BODY), color = FarmsyColors.ink,
                            modifier = Modifier.padding(start = Space.s5, end = Space.s5, top = Space.s5, bottom = if (n > 0) 0.dp else Space.s5),
                        )
                        if (n > 0) {
                            if (!locked) {
                                Column(Modifier.padding(start = Space.s5, end = Space.s5, bottom = Space.s5), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                                    PlanView(plan, isPlanning, ::labels, onFind = ::findFarms, onBuild = ::buildRoute, onOpen = { osmId ->
                                        farms.pinForOsmId(osmId)?.let { shell.openFarm(it) }
                                    }, km = { osmId -> location?.let { l -> farms.pinForOsmId(osmId)?.distanceMeters(l.latitude, l.longitude)?.div(1000) } })
                                }
                            } else {
                                PlusLockCard(stringResource(R.string.plus_route_title), stringResource(R.string.plus_route_text), onUnlock = shell.openPlus)
                            }
                        }
                    }
                    else -> SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(88.dp))
                }
            }

            // MARK: Add products
            SectionHeader(stringResource(R.string.add_products))
            if (catalogue.isEmpty()) {
                if (loadFailed) {
                    Text(stringResource(R.string.shopping_list_catalogue_failed), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                } else {
                    SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(120.dp))
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    catalogue.filter { it.id !in wanted }.forEach { item ->
                        Chip(item.label(language), emoji = item.emoji) { trip.toggleProduct(item.id) }
                    }
                }
            }

            // MARK: History
            val previous = history.filter { it != wanted }
            if (previous.isNotEmpty()) {
                SectionHeader(stringResource(R.string.previous_lists))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                    previous.forEach { list ->
                        Row(
                            Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape).padding(Space.s4),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                        ) {
                            Text(
                                labels(list).joinToString(", "), style = role(TextRole.BODY_SM), color = FarmsyColors.ink,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            PillButton(stringResource(R.string.repeat), PillVariant.SOFT, PillSize.SMALL) {
                                trip.clearProducts()
                                list.forEach { trip.toggleProduct(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/// The Plus half: the planner's picks, what is missing, and the route button.
@Composable
private fun PlanView(
    plan: ShoppingPlanner.Plan?,
    isPlanning: Boolean,
    labels: (List<String>) -> List<String>,
    onFind: () -> Unit,
    onBuild: (ShoppingPlanner.Plan) -> Unit,
    onOpen: (String) -> Unit,
    km: (String) -> Double?,
) {
    val farms = LocalFarms.current
    if (plan == null) {
        Box(Modifier.fillMaxWidth()) {
            if (isPlanning) {
                Box(Modifier.fillMaxWidth().height(48.dp).background(FarmsyColors.ink, CircleShape), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                PillButton(stringResource(R.string.find_best_farms), PillVariant.PRIMARY, PillSize.MEDIUM, block = true, icon = Icons.Filled.AutoAwesome, onClick = onFind)
            }
        }
        return
    }
    if (plan.isEmpty) {
        Text(stringResource(R.string.shopping_nothing_covers), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        plan.picks.forEachIndexed { i, pick ->
            val pin = farms.pinForOsmId(pick.osmId)
            Row(
                Modifier.fillMaxWidth().background(FarmsyColors.creamFill, TileShape).clickable { onOpen(pick.osmId) }.padding(Space.s3),
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Box(Modifier.size(26.dp).background(FarmsyColors.vivid, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = ui(13.sp, FontWeight.Bold), color = FarmsyColors.ink)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(pin?.name ?: pick.osmId, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(labels(pick.covers).joinToString(" · "), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    km(pick.osmId)?.let { Text("${String.format("%.1f", it)} km", style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint) }
                }
            }
        }
    }
    if (plan.missing.isNotEmpty()) {
        Text(
            stringResource(R.string.shopping_list_missing_arg, labels(plan.missing).joinToString(", ")),
            style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
        )
    }
    PillButton(stringResource(R.string.build_my_route), PillVariant.PRIMARY, PillSize.MEDIUM, block = true, icon = Icons.Filled.DirectionsCar) { onBuild(plan) }
}
