package app.farmsy.android.features.shopping

import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.ShoppingPlanner
import app.farmsy.android.core.lenientJson
import app.farmsy.android.features.home.rememberLocationRequest
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.ProductImage
import app.farmsy.android.ui.theme.Badge
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillShape
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SearchField
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.TileShape
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.rememberTapHaptic
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import app.farmsy.android.ui.theme.ui
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
/// map cannot do, and the reason to pay — so it sits in one bar above the tab
/// pill the moment the list has an item.
///
/// The list itself is `TripStore.wantedProducts` (already persisted, already
/// what the trip planner's chips read), so nothing is stored twice.
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val categories by ShoppingItems.categories.collectAsState()
    val loadFailed by ShoppingItems.loadFailed.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val language = remember { ShoppingItems.language(context) }

    var plan by remember { mutableStateOf<ShoppingPlanner.Plan?>(null) }
    var matchingFarms by remember { mutableStateOf<Int?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(ShoppingHistory.read(context)) }
    // The picker's search field. Typing filters the chips; Enter with no
    // matching chip adds what was typed as a custom item.
    var query by remember { mutableStateOf("") }
    // The stop a "Change farm" sheet is open for.
    var swapping by remember { mutableStateOf<ShoppingPlanner.Pick?>(null) }

    val picked = remember(wanted, catalogue) { wanted.mapNotNull { ShoppingItems.item(it) } }
    val origin: LatLng? = tripOrigin ?: location?.let { LatLng(it.latitude, it.longitude) }
    fun labels(ids: List<String>) = ids.map { id -> ShoppingItems.item(id)?.label(language) ?: id }
    fun candidates() = pins.map {
        ShoppingPlanner.Candidate(it.osmId, LatLng(it.lat, it.lng), farms.produceFor(it.osmId) ?: "")
    }

    fun findFarms() {
        val o = origin ?: return
        isPlanning = true
        scope.launch {
            farms.loadFlagsIfNeeded()
            val candidates = candidates()
            plan = withContext(Dispatchers.Default) {
                ShoppingPlanner.plan(wanted = picked, farms = candidates, origin = o, radiusKm = radiusKm)
            }
            isPlanning = false
        }
    }

    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { farms.loadFlagsIfNeeded() }
    LaunchedEffect(wanted) { plan = null }
    // The free half of the answer: how many farms in the radius sell any of
    // it — and, same key, same effect (the Android mirror of iOS's
    // `.task(id: matchKey)`), the planner itself, for everyone. Setting
    // `plan` never touches these keys, so this cannot re-trigger itself;
    // `isPlanning` just keeps two runs from overlapping if the key changes
    // again before the first finishes.
    LaunchedEffect(wanted, origin, radiusKm, flagsLoaded, pins.size, picked.size) {
        val o = origin
        if (o == null || !flagsLoaded || picked.isEmpty()) { matchingFarms = null; return@LaunchedEffect }
        val terms = picked.flatMap { it.terms }
        val produce = farms.produceByOsm
        val count = withContext(Dispatchers.Default) {
            pins.count { pin ->
                val sells = produce[pin.osmId] ?: return@count false
                pin.distanceMeters(o.latitude, o.longitude) / 1000 <= radiusKm && ProductMatch.covers(sells, terms)
            }
        }
        matchingFarms = count
        if (count > 0) {
            if (!isPlanning) findFarms()
        } else {
            plan = null
        }
    }

    /// The picks become trip stops; the planner orders them and draws the road.
    fun buildRoute(p: ShoppingPlanner.Plan) {
        history = ShoppingHistory.remember(context, wanted)
        trip.addStops(p.picks.map { it.osmId })
        trip.requestFit()
        shell.openTrips()
    }

    /// The one place free users open Plus from Shopping — the pinned action
    /// bar and the blurred sample block both call this, so `paywall_viewed`
    /// only ever fires from one spot.
    fun openPlusFromSample() {
        tap()
        Observability.capture(
            AnalyticsEvent.PAYWALL_VIEWED,
            mapOf(AnalyticsProp.TRIGGER to AnalyticsValue.Trigger.SHOPPING_SAMPLE.key),
        )
        shell.openPlus()
    }

    /// Enter in the search field: the one chip that matches, else a custom item.
    fun addTyped() {
        val text = query.trim()
        if (text.isEmpty()) return
        val q = ProductMatch.fold(text)
        val exact = catalogue.firstOrNull { ProductMatch.fold(it.label(language)) == q && it.id !in wanted }
        tap()
        trip.toggleProduct(exact?.id ?: ShoppingItem.custom(text).id)
        query = ""
    }

    fun km(osmId: String): Double? = location?.let { l -> farms.pinForOsmId(osmId)?.distanceMeters(l.latitude, l.longitude)?.div(1000) }

    Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(stringResource(R.string.shopping))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.s4).padding(bottom = TabBarInset.content + if (picked.isEmpty()) 0.dp else 72.dp),
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
                                Row(
                                    Modifier.weight(1f).then(if (item.isCustom) Modifier else Modifier.tapCard { shell.openProduct(item.id) }),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                                ) {
                                    ProductImage(item.imageSlug, item.emoji, 36.dp, corner = 8.dp)
                                    Text(item.label(language), style = role(TextRole.BODY), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                                }
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
                        n != null -> Column(Modifier.fillMaxWidth().card(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                            Text(
                                if (n == 0) stringResource(R.string.shopping_none_within_arg, radiusKm.toInt())
                                else stringResource(R.string.shopping_match_arg, n, radiusKm.toInt()),
                                style = role(TextRole.BODY), color = FarmsyColors.ink,
                            )
                            if (n > 0) {
                                val p = plan
                                if (session.hasFullAccess) {
                                    PlanView(plan, picked.size, ::labels, onOpen = { osmId ->
                                        farms.pinForOsmId(osmId)?.let { shell.openFarm(it) }
                                    }, onSwap = { swapping = it }, km = ::km)
                                } else if (p != null && !p.isEmpty) {
                                    ShoppingSample(p, picked.size, radiusKm, ::labels, ::km, onUnlock = ::openPlusFromSample)
                                } else if (isPlanning) {
                                    // The planner runs for everyone the moment `n` is known, so this
                                    // is just the gap before it lands — never shown once planning
                                    // ends, even if the plan comes back empty (the two matchers,
                                    // `ShoppingList.matches` for `n` and `ProductMatch.covers` for the
                                    // plan, can disagree). The effect's keys don't include `plan`, so
                                    // an empty result here can't retrigger a new plan and get stuck.
                                    SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(88.dp))
                                }
                                // Else: planning finished with nothing to show — the count
                                // sentence above already said so.
                            }
                        }
                        else -> SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(88.dp))
                    }
                }

                // MARK: Add products
                SectionHeader(stringResource(R.string.add_products))
                SearchField(
                    query, { query = it }, stringResource(R.string.shopping_search_placeholder),
                    modifier = Modifier.padding(bottom = Space.s3), onSubmit = ::addTyped,
                )
                if (catalogue.isEmpty()) {
                    if (loadFailed) {
                        Text(stringResource(R.string.shopping_list_catalogue_failed), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                    } else {
                        SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                } else {
                    val q = ProductMatch.fold(query)
                    val available = catalogue.filter { it.id !in wanted }
                    val shown = if (q.isEmpty()) available
                    else available.filter { ProductMatch.fold(it.label(language)).contains(q) || it.terms.any { t -> t.contains(q) } }
                    if (q.isNotEmpty() && shown.none { ProductMatch.fold(it.label(language)) == q }) {
                        // What was typed is not a chip: offer it as its own item.
                        Box(Modifier.padding(bottom = Space.s3)) {
                            Chip(stringResource(R.string.shopping_add_typed_arg, query.trim()), icon = Icons.Filled.Add, selected = true) { addTyped() }
                        }
                    }
                    if (q.isEmpty() && categories.isNotEmpty()) {
                        categories.forEach { cat ->
                            val group = shown.filter { (it.category ?: "other") == cat.id }
                            if (group.isNotEmpty()) {
                                Text(
                                    cat.label(language).uppercase(), style = role(TextRole.LABEL), color = FarmsyColors.inkFaint,
                                    modifier = Modifier.padding(top = Space.s3, bottom = Space.s2),
                                )
                                Chips(group, language) { trip.toggleProduct(it) }
                            }
                        }
                    } else {
                        Chips(shown, language) { trip.toggleProduct(it) }
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

        // MARK: The Plus bar — one bar, one job at a time, pinned above the tab
        // pill: first "Find farms", then "Build my route". Free users get the
        // Plus sheet from either.
        if (picked.isNotEmpty()) {
            val stops = plan?.picks?.size ?: 0
            val enabled = !isPlanning && origin != null
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp).padding(bottom = TabBarInset.height + 24.dp)
                    .shadow(16.dp, PillShape, ambientColor = FarmsyColors.ink.copy(alpha = 0.14f), spotColor = FarmsyColors.ink.copy(alpha = 0.14f))
                    .fillMaxWidth().heightIn(min = PillSize.LARGE.height)
                    .alpha(if (enabled) 1f else 0.55f)
                    .background(FarmsyColors.ink, PillShape).clip(PillShape)
                    .clickable(enabled = enabled) {
                        val p = plan
                        when {
                            !session.hasFullAccess -> openPlusFromSample()
                            p != null && !p.isEmpty -> { tap(); buildRoute(p) }
                            else -> { tap(); findFarms() }
                        }
                    }
                    .padding(horizontal = Space.s5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                if (isPlanning) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (!session.hasFullAccess) Icons.Filled.Lock else if (stops > 0) Icons.Filled.DirectionsCar else Icons.Filled.AutoAwesome,
                        null, tint = Color.White, modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    if (!session.hasFullAccess) stringResource(R.string.see_which_farms)
                    else if (stops > 0) stringResource(R.string.shopping_build_route_stops_arg, stops)
                    else stringResource(R.string.shopping_find_farms_bar),
                    style = ui(17.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Badge("PLUS", fill = FarmsyColors.vivid, ink = FarmsyColors.ink)
            }
        }
    }

    // MARK: Change farm — up to five other farms that could take this stop's place.
    swapping?.let { pick ->
        val current = plan ?: ShoppingPlanner.Plan(emptyList(), emptyList())
        val options = remember(pick, current, picked, pins, origin, radiusKm) {
            origin?.let { ShoppingPlanner.alternatives(pick, current, picked, candidates(), it, radiusKm) } ?: emptyList()
        }
        ModalBottomSheet(
            onDismissRequest = { swapping = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s5).padding(bottom = Space.s8),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Text(stringResource(R.string.change_farm), style = role(TextRole.HEADING), color = FarmsyColors.ink)
                Text(stringResource(R.string.change_farm_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                if (options.isEmpty()) {
                    Text(
                        stringResource(R.string.change_farm_none_arg, radiusKm.toInt()), style = role(TextRole.BODY), color = FarmsyColors.inkMuted,
                        modifier = Modifier.padding(top = Space.s4),
                    )
                }
                options.forEach { alt ->
                    val pin = farms.pinForOsmId(alt.osmId)
                    Row(
                        Modifier.fillMaxWidth().background(FarmsyColors.surface, CardShape).clip(CardShape).clickable {
                            tap()
                            plan = ShoppingPlanner.replacing(pick, alt, current)
                            swapping = null
                        }.padding(Space.s4),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(pin?.name ?: alt.osmId, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(labels(alt.covers).joinToString(" · "), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        km(alt.osmId)?.let { Text("${String.format("%.1f", it)} km", style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint) }
                        Icon(Icons.Filled.ChevronRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<ShoppingItem>, language: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        items.forEach { item -> Chip(item.label(language), emoji = item.emoji) { onPick(item.id) } }
    }
}

/// The Plus half: the planner's picks and what is missing. The buttons that
/// drive it live in the bar above the tab pill.
@Composable
private fun PlanView(
    plan: ShoppingPlanner.Plan?,
    total: Int,
    labels: (List<String>) -> List<String>,
    onOpen: (String) -> Unit,
    onSwap: (ShoppingPlanner.Pick) -> Unit,
    km: (String) -> Double?,
    /// True for the free sample: no row opens a farm or a swap sheet, and the
    /// whole thing is cleared from the accessibility tree rather than read
    /// out from behind the blur.
    locked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val farms = LocalFarms.current
    if (plan == null) {
        Text(stringResource(R.string.shopping_plus_hint_short), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
        return
    }
    if (plan.isEmpty) {
        Text(stringResource(R.string.shopping_nothing_covers), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
        return
    }
    Column(
        modifier.then(if (locked) Modifier.clearAndSetSemantics {} else Modifier),
        verticalArrangement = Arrangement.spacedBy(Space.s2),
    ) {
        plan.picks.forEachIndexed { i, pick ->
            val pin = farms.pinForOsmId(pick.osmId)
            Column(Modifier.fillMaxWidth().background(FarmsyColors.creamFill, TileShape).padding(Space.s3), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { onOpen(pick.osmId) }, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    Box(Modifier.size(26.dp).background(FarmsyColors.vivid, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = ui(13.sp, FontWeight.Bold), color = FarmsyColors.ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(pin?.name ?: pick.osmId, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(labels(pick.covers).joinToString(" · "), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            Text(stringResource(R.string.shopping_coverage_arg, pick.covers.size, total), style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
                            km(pick.osmId)?.let { Text("· ${String.format("%.1f", it)} km", style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint) }
                        }
                    }
                }
                PillButton(stringResource(R.string.change_farm), PillVariant.TEXT, PillSize.SMALL, enabled = !locked) { onSwap(pick) }
            }
        }
        if (plan.missing.isNotEmpty()) {
            Text(
                stringResource(R.string.shopping_list_missing_arg, labels(plan.missing).joinToString(", ")),
                style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
            )
        }
    }
}

/// The free sample: the real coverage sentence, plus the real stop rows
/// blurred — looking is free, the farms are Plus. No button drawn on top
/// (that collided with the pinned action bar's identical CTA); the whole
/// blurred block is itself the tap target, and the pinned bar is the visible
/// CTA. Never a padlock on an empty screen.
@Composable
private fun ShoppingSample(
    plan: ShoppingPlanner.Plan,
    total: Int,
    radiusKm: Double,
    labels: (List<String>) -> List<String>,
    km: (String) -> Double?,
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
    val seeWhichFarms = stringResource(R.string.see_which_farms)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Text(
            stringResource(R.string.shopping_sample_coverage_arg, plan.coveredCount, total, plan.picks.size, radiusKm.toInt()),
            style = role(TextRole.HEADING), color = FarmsyColors.ink,
        )
        Box(Modifier.clickable(onClickLabel = seeWhichFarms, role = Role.Button, onClick = onUnlock)) {
            PlanView(plan, total, labels, onOpen = {}, onSwap = {}, km = km, locked = true, modifier = hide)
        }
    }
}
