package app.farmsy.android.features.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.farmsy.android.features.community.CommunityScreen
import app.farmsy.android.features.discover.DiscoverScreen
import app.farmsy.android.features.discover.ProductBottomSheet
import app.farmsy.android.features.home.HomeScreen
import app.farmsy.android.features.map.ProUpsellSheet
import app.farmsy.android.features.profile.ProfileScreen
import app.farmsy.android.features.shopping.ShoppingScreen
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.rememberTapHaptic
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.graphics.BlurMaskFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.PushRegistrar
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SurveyApi
import app.farmsy.android.core.SurveyGate
import app.farmsy.android.features.detail.FarmDetailScreen
import app.farmsy.android.features.map.MapScreen
import app.farmsy.android.features.survey.SurveyMode
import app.farmsy.android.features.survey.SurveyScreen
import app.farmsy.android.features.trips.TripsScreen
import app.farmsy.android.ui.theme.FarmsyColors
import kotlinx.coroutines.launch

/// Five tabs, one floating pill: Home · Shopping · Map · Discover · Community
/// (iOS AppShell). Profile opens from the Home header. Sheets that any tab can
/// raise — the farm card, sign-in, the trip planner, the membership sheet — live
/// here once, and screens reach them through `LocalShell`.
///
/// PORT NOTE (detents): the farm card and the trip planner stay detented sheets
/// over the live content (`BottomSheetScaffold`, peek = the partial detent, the
/// expanded state capped at 0.92 so a strip of the tab stays visible), exactly as
/// before the redesign; Profile and Plus are modal sheets.
private enum class SheetRoute { FARM, TRIPS }

/// Farmsy's own glyphs (res/drawable/ic_tab_*): a farmhouse, a basket, a
/// folded map, a seedling, two people. Outline at rest, solid when selected.
enum class AppTab(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int, @DrawableRes val fillRes: Int) {
    HOME(R.string.home, R.drawable.ic_tab_home, R.drawable.ic_tab_home_fill),
    SHOPPING(R.string.shopping, R.drawable.ic_tab_shopping, R.drawable.ic_tab_shopping_fill),
    MAP(R.string.map, R.drawable.ic_tab_map, R.drawable.ic_tab_map_fill),
    DISCOVER(R.string.discover, R.drawable.ic_tab_discover, R.drawable.ic_tab_discover_fill),
    COMMUNITY(R.string.community, R.drawable.ic_tab_community, R.drawable.ic_tab_community_fill),
}

/// What a screen can ask the shell to do.
class ShellActions(
    val openFarm: (FarmPin) -> Unit = {},
    val showTab: (AppTab) -> Unit = {},
    val openTrips: () -> Unit = {},
    val openProfile: () -> Unit = {},
    val openPlus: () -> Unit = {},
    /// A product page, by shopping id or seasonal slug.
    val openProduct: (String) -> Unit = {},
)

val LocalShell = staticCompositionLocalOf { ShellActions() }

/// Detached 64dp pill above the gesture bar, icons only (labels are for
/// accessibility). Active = ink + filled symbol, inactive = muted + outline.
@Composable
fun FloatingTabBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberTapHaptic()
    Row(
        modifier
            .capsuleShadow(FarmsyColors.ink.copy(alpha = 0.10f), blurRadius = 12.dp, offsetY = 8.dp)
            .height(TabBarInset.height)
            .background(FarmsyColors.surface, CircleShape)
            .border(1.dp, FarmsyColors.hairline, CircleShape)
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val on = tab == selected
            val label = stringResource(tab.titleRes)
            Box(
                Modifier.size(56.dp, 44.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!on) tap()
                        onSelect(tab)
                    }
                    .semantics { contentDescription = label; this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (on) tab.fillRes else tab.iconRes), null,
                    tint = if (on) FarmsyColors.ink else FarmsyColors.inkMuted, modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var route by remember { mutableStateOf<SheetRoute?>(null) }
    var selectedPin by remember { mutableStateOf<FarmPin?>(null) }
    var focusPin by remember { mutableStateOf<FarmPin?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showPlus by remember { mutableStateOf(false) }
    var productSlug by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSurvey by remember { mutableStateOf(false) }
    var surveyMode by remember { mutableStateOf(SurveyMode.QUESTIONS) }
    // The survey gate drives the whole entry point: admin → no button; answered → the
    // button opens feedback and the arrow is gone; not-answered → questions + arrow.
    // Best-effort/fail-open: while null (loading) or on a gate failure (all-false) the
    // button shows — better to offer the survey than wrongly withhold it. Refreshed on
    // sign-in/out and after the sheet closes (they may have just answered).
    // The survey entry (button + arrow + auto-open) is shown for EVERY role — signed
    // out, signed in and admin alike (Neil: "the survey appears whatever the role").
    // Only `answered` differs it: answered → the button opens feedback, no arrow.
    // Admin data integrity is kept server-side — POST /api/survey/respond rejects an
    // admin submit with `is_admin` — so showing the UI to staff pollutes nothing.
    var surveyGate by remember { mutableStateOf<SurveyGate?>(null) }
    LaunchedEffect(session.isAuthenticated) {
        surveyGate = SurveyApi.gate(session.accessToken())
        PushRegistrar.sync(session.session.value?.user?.id, session.accessToken())
    }
    // The arrow points only while there is an unanswered survey to point at — role no
    // longer matters, only `answered`.
    val showSurveyArrow = surveyGate?.let { !it.answered } == true

    // Cold-launch auto-open. LaunchedEffect(Unit) runs once when MainScreen first
    // enters composition — i.e. on cold launch (RootNav builds Main fresh), NOT on
    // resume (the retained composition is not rebuilt when the app returns from the
    // background). 1.4s after the map appears lets it settle first so the survey reads
    // as a question, not part of the loading. Fires for any role; only `answered` and
    // the frequency cap stop it (once ever per signed-in account, once a day signed-out).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        val g = SurveyApi.gate(session.accessToken())
        surveyGate = g
        if (g.answered) return@LaunchedEffect
        val account = session.email.takeIf { it.isNotEmpty() }
        if (!SurveyAutoOpen.canAutoOpen(context, account)) return@LaunchedEffect
        SurveyAutoOpen.recordAutoOpen(context, account)
        surveyMode = SurveyMode.QUESTIONS
        showSurvey = true
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    /// Farm cards open for everyone, signed out included. Opening a farm from
    /// another tab switches to the map and flies to it, so "where is it" is
    /// always one tap from "what is it".
    fun openFarm(pin: FarmPin, source: AnalyticsValue.Source) {
        tab = AppTab.MAP
        selectedPin = pin
        focusPin = pin
        route = SheetRoute.FARM
        // Fired here, where the pin is set, not in a composable that recomposes.
        // `source` is what tells whether the map or the feed sells.
        Observability.capture(
            AnalyticsEvent.FARM_OPENED,
            mapOf(AnalyticsProp.OSM_ID to pin.osmId, AnalyticsProp.SOURCE to source.key),
        )
    }

    fun requireAuth(then: () -> Unit) {
        if (session.isAuthenticated) then() else requestAuth()
    }

    val shell = remember {
        ShellActions(
            openFarm = { openFarm(it, AnalyticsValue.Source.WHATS_NEW) },
            showTab = { tab = it },
            openTrips = { requireAuth { route = SheetRoute.TRIPS } },
            openProfile = { showProfile = true },
            openPlus = { requireAuth { showPlus = true } },
            openProduct = { productSlug = it },
        )
    }

    // A notification tap lands here: open the farm it was about.
    val farmsForPush = LocalFarms.current
    val pinCount by farmsForPush.pins.collectAsState()
    val pendingPush by PushRegistrar.pendingOsmId.collectAsState()
    LaunchedEffect(pendingPush, pinCount.size) {
        val id = pendingPush ?: return@LaunchedEffect
        val pin = farmsForPush.pinForOsmId(id) ?: return@LaunchedEffect
        PushRegistrar.consumePending()
        openFarm(pin, AnalyticsValue.Source.WHATS_NEW)
    }

    // Partial-detent height per route (fraction of the screen), matching iOS.
    val partialFraction = when (route) {
        SheetRoute.TRIPS -> 0.5f
        SheetRoute.FARM -> 0.55f
        null -> 0f
    }
    val peek = screenHeight * partialFraction

    // Live "is the sheet dragged above the partial detent?" — read the sheet's current
    // drag OFFSET (px from the top of the container to the sheet's top edge), not its
    // settled anchor. Visible sheet height = container − offset; block once it exceeds
    // the partial-detent height, so blocking engages mid-drag the instant the sheet
    // passes the partial detent (iOS `.enabled(upThrough: partial)`), not only once it
    // settles at Expanded. `requireOffset()` is a snapshot state, so `derivedStateOf`
    // recomputes as the sheet is dragged and flips the boolean at the threshold.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val peekPx = with(density) { peek.toPx() }

    // Expanded-detent height per route. iOS uses `.large` for the FARM card ONLY
    // (MainView.swift:49) and `.fraction(0.92)` for Discover/Saved/Settings/Trips.
    // `.large` = the sheet top rests just below the status bar, so the expressed
    // height is (screen − statusBar): inset-driven, not a magic number. Clamped to
    // [0.92, 0.96] so FARM is never shorter than the 0.92 group nor full-screen
    // (a strip of map + the rounded corners stay visible). Device-gated for the exact
    // top-gap. The other routes keep 0.92.
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val expandedFraction = if (route == SheetRoute.FARM)
        ((screenHeightPx - statusBarPx) / screenHeightPx).coerceIn(0.92f, 0.96f)
    else 0.92f
    val blockBackground by remember(peekPx, screenHeightPx) {
        derivedStateOf {
            val offset = runCatching { sheetState.requireOffset() }.getOrNull() ?: return@derivedStateOf false
            (screenHeightPx - offset) > peekPx + 1f
        }
    }

    // Drive the sheet from the active route: none → hide; a route → partial-expand.
    LaunchedEffect(route) {
        if (route == null) sheetState.hide() else sheetState.partialExpand()
    }
    // If the user drags the sheet fully closed, clear the route + focus (returns the
    // map to all-farms and reveals the pill).
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Hidden && route != null) {
            route = null; selectedPin = null; focusPin = null
        }
    }
    val collapsed = sheetState.currentValue == SheetValue.PartiallyExpanded

    // System back closes an open sheet (same clear as dragging it to Hidden) instead
    // of leaving the app. Only enabled while a sheet is up, so on the bare map back
    // still exits. At API 36 predictive back is on by default, so this also drives the
    // predictive dismiss animation for the sheet rather than the app-exit animation.
    BackHandler(enabled = route != null || tab != AppTab.HOME) {
        if (route != null) { route = null; selectedPin = null; focusPin = null } else tab = AppTab.HOME
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peek,
        sheetContainerColor = FarmsyColors.cream,
        sheetContent = {
            // Declared expanded detent: the sheet content height is `expandedFraction`
            // of the screen (0.92 for the pill routes; ~.large for FARM — see above),
            // so BottomSheetScaffold's Expanded state settles at a declared fraction
            // (not an emergent content height) — a strip of the live map stays visible.
            // The peek height gives the partial detent.
            // navigationBarsPadding: edge-to-edge (mandatory at API 36) draws the
            // sheet behind the gesture bar; without this the last row of a scrollable
            // route (feed / detail) sits under it. BottomSheetScaffold does not inset
            // sheet content for the nav bar, so we do it here for every route.
            Box(Modifier.fillMaxWidth().fillMaxHeight(expandedFraction).navigationBarsPadding()) {
                when (route) {
                    SheetRoute.FARM -> selectedPin?.let { pin ->
                        FarmDetailScreen(pin = pin, onBack = { route = null })
                    }
                    SheetRoute.TRIPS -> TripsScreen(collapsed = collapsed, onOpenFarm = { openFarm(it, AnalyticsValue.Source.TRIPS) })
                    null -> Box(Modifier.size(1.dp))
                }
            }
        },
    ) {
        // The five tabs + the floating pill. A pager with scrolling off keeps every
        // tab composed (iOS TabView): each keeps its scroll position, camera and
        // loaded state across switches, which a `when` would drop.
        val pager = rememberPagerState(initialPage = tab.ordinal) { AppTab.entries.size }
        LaunchedEffect(tab) { pager.scrollToPage(tab.ordinal) }
        CompositionLocalProvider(LocalShell provides shell) {
        Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
            HorizontalPager(
                state = pager, userScrollEnabled = false, beyondViewportPageCount = AppTab.entries.size - 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (AppTab.entries[page]) {
                    AppTab.HOME -> HomeScreen()
                    AppTab.SHOPPING -> ShoppingScreen()
                    AppTab.MAP -> MapScreen(onOpenFarm = { openFarm(it, AnalyticsValue.Source.MAP_PIN) }, focusPin = focusPin, bottomInset = TabBarInset.content)
                    AppTab.DISCOVER -> DiscoverScreen()
                    AppTab.COMMUNITY -> CommunityScreen()
                }
            }

            // Background-interaction scope: iOS enables it `upThrough` the partial
            // detent (0.55 / 0.5) for every pill route, and blocks continuously above
            // that. Compose's BottomSheetScaffold has no scrim at all, so we add a
            // touch-consuming blocker over the whole body (map + pill) whenever the
            // sheet is dragged above the partial detent — driven by the live drag
            // offset (`blockBackground`), so it engages mid-drag, not only once the
            // sheet settles at Expanded. (Auth, the one modal surface, is a
            // ModalBottomSheet in RootNav — its own scrim disables background
            // interaction there.)
            if (blockBackground) {
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                        }
                    }
                )
            }

            FloatingTabBar(
                selected = tab, onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Space.s3),
            )

            // Floating survey entry button — bottom-trailing, above the pill (which
            // spans ~safe-bottom+6 to +67), so it clears it. Shown for every role
            // (Neil). A down-arrow points at it while the survey is unanswered. iOS SF
            // `text.bubble.fill` → Material Chat. The button never goes away: it opens
            // the questions while unanswered, the feedback box once answered.
            // Only over the map: on scrolling tabs it would cover content.
            if (tab == AppTab.MAP) Column(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                    .padding(end = 14.dp, bottom = TabBarInset.content + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showSurveyArrow) SurveyArrow()
                Box(
                    Modifier
                        .capsuleShadow(Color.Black.copy(alpha = 0.22f), blurRadius = 10.dp, offsetY = 3.dp)
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.94f), CircleShape)
                        .clickable {
                            surveyMode = if (surveyGate?.answered == true) SurveyMode.FEEDBACK else SurveyMode.QUESTIONS
                            showSurvey = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat, null,
                        tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        }
    }

    if (showProfile) {
        ModalBottomSheet(
            onDismissRequest = { showProfile = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.96f).navigationBarsPadding()) {
                ProfileScreen(onClose = { showProfile = false }, onOpenPlus = { showProfile = false; showPlus = true })
            }
        }
    }
    if (showPlus) ProUpsellSheet(onDismiss = { showPlus = false })
    productSlug?.let { s -> ProductBottomSheet(s, onDismiss = { productSlug = null }) }

    // The survey presents as a modal over the map (iOS `.sheet` at 0.92), like the
    // other secondary surfaces. The gate hides its button for admins; the screen also
    // re-checks on open (belt and suspenders). On dismiss, refresh the gate so the
    // arrow disappears and the button switches to feedback after answering.
    if (showSurvey) {
        ModalBottomSheet(
            onDismissRequest = {
                showSurvey = false
                scope.launch { surveyGate = SurveyApi.gate(session.accessToken()) }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
                SurveyScreen(mode = surveyMode, onClose = { showSurvey = false })
            }
        }
    }
}

/// A regular dark-green down arrow, above the survey button, pointing at it — shown
/// only while the survey is unanswered. Dark green = `farmGreen` (the button is the
/// primary green; "dark green" per the brief distinguishes it from the lighter
/// on-map `farmGreenMap`). Bounces ~9px, ~0.9s, eased (Aviah's thread settles this),
/// and holds still under the system "remove animations" setting. Hidden from
/// accessibility — it says nothing the button's own label doesn't.
/// SF: iOS build 23 hand-drew a curved Path; corrected here to a standard Material
/// arrow (§7a). DISPUTED vs Aviah's thread (hand-drawn) — see PORT_NOTES.
@Composable
private fun SurveyArrow() {
    val context = LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
    val dy = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "surveyArrow")
        transition.animateFloat(
            initialValue = 0f, targetValue = 9f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "surveyArrowDrop",
        ).value
    }
    Icon(
        Icons.Filled.ArrowDownward, null,
        tint = FarmsyColors.farmGreen,
        modifier = Modifier.size(22.dp)
            .offset { IntOffset(0, dy.toInt()) }
            .clearAndSetSemantics { },
    )
}

/// Whether the survey may auto-open, and the record that it did. Signed in it opens
/// once ever per account; signed out, once per calendar day (a phone cold-launches
/// often, so an uncapped "every launch" would harass). SharedPreferences — per-device
/// is acceptable signed-out (no identity to key on); the signed-in per-account flag is
/// belt-and-braces over the server-side `answered` state, which is the real stop.
object SurveyAutoOpen {
    private const val PREFS = "farmsy"

    fun canAutoOpen(context: android.content.Context, account: String?): Boolean {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return if (!account.isNullOrEmpty()) {
            !p.getBoolean("survey_autoopen_acct_$account", false)
        } else {
            p.getString("survey_autoopen_day", null) != today()
        }
    }

    fun recordAutoOpen(context: android.content.Context, account: String?) {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (!account.isNullOrEmpty()) {
            p.edit().putBoolean("survey_autoopen_acct_$account", true).apply()
        } else {
            p.edit().putString("survey_autoopen_day", today()).apply()
        }
    }

    private fun today(): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return f.format(java.util.Date())
    }
}

/// A soft drop-shadow for a fully-rounded (capsule/circle) surface that reproduces
/// iOS's `shadow(color:radius:y:)` exactly: the BlurMaskFilter carries the shadow
/// **colour** and **blur radius**, and the shape is drawn at a **downward y-offset** —
/// the three parameters Material `shadowElevation` cannot set (its blur is a fixed
/// elevation curve and its direction comes from the system light source, not a value).
/// Drawn behind the surface, so the white pill on top leaves only the halo showing.
private fun Modifier.capsuleShadow(color: Color, blurRadius: Dp, offsetY: Dp): Modifier =
    drawBehind {
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val dy = offsetY.toPx()
        val r = size.height / 2f   // fully-rounded (capsule) — radius = half the height
        drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, dy, size.width, size.height + dy, r, r, paint.asFrameworkPaint()) }
    }
