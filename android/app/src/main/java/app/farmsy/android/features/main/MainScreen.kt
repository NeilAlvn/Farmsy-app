package app.farmsy.android.features.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.BuildConfig
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.SurveyApi
import app.farmsy.android.features.detail.FarmDetailScreen7
import app.farmsy.android.features.map.MapScreen
import app.farmsy.android.features.survey.SurveyScreen
import app.farmsy.android.features.whatsnew.WhatsNewSheet
import app.farmsy.android.features.saved.SavedScreen
import app.farmsy.android.features.settings.SettingsScreen
import app.farmsy.android.features.trips.TripsScreen
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist

/// Map-first shell — a 1:1 port of iOS MainView: "the map is the app". There is
/// one shared live map (owned here, rendered by MapScreen — the only GoogleMap in
/// the app). The farm card and the secondary surfaces (Discover / Saved / Trips /
/// Settings) present as detented bottom sheets OVER that live map; the map stays
/// interactive behind them. Selecting a farm flies the shared map and drops a
/// highlighted pin (focusPin).
///
/// PORT NOTE (detents): iOS uses `.presentationDetents([.fraction(0.55), .large])`
/// with `.presentationBackgroundInteraction` so the map behind stays usable.
/// Compose has no detent API; `BottomSheetScaffold` + a `StandardBottomSheetState`
/// (Hidden / PartiallyExpanded / Expanded) is the closest primitive — its body (the
/// map) is interactive by default (no scrim), the peek height gives the partial
/// detent (0.55, or 0.5 for Trips), and the expanded state is capped at 0.92 of the
/// screen so a sliver of map stays visible. The iOS farm-card's third mini-detent
/// (180px) is not reproduced — Compose offers only peek + expanded.
private enum class SheetRoute { FARM, DISCOVER, SAVED, TRIPS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current

    var route by remember { mutableStateOf<SheetRoute?>(null) }
    var selectedPin by remember { mutableStateOf<FarmPin?>(null) }
    var focusPin by remember { mutableStateOf<FarmPin?>(null) }

    var showSurvey by remember { mutableStateOf(false) }
    // An admin gets NO survey entry point at all (Aviah's spec: an answer from staff is
    // >0.5% of the data, indistinguishable from a real one later). Decided here at the
    // map level via the gate so the button never appears, rather than appearing and the
    // sheet closing on open. DEBUG keeps the button for everyone so the survey stays
    // testable on a dev build (the SurveyScreen debug bypass then renders it); Release
    // hides it for admins. Best-effort/fail-open: a failed gate leaves the button shown.
    var hideSurveyForAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(session.isAuthenticated) {
        hideSurveyForAdmin = !BuildConfig.DEBUG && SurveyApi.gate(session.accessToken()).isAdmin
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    /// Open a farm: fly the shared map + highlight the pin, and present the card.
    fun openFarm(pin: FarmPin) {
        selectedPin = pin
        focusPin = pin
        route = SheetRoute.FARM
    }

    fun requireAuth(then: SheetRoute) {
        if (session.isAuthenticated) route = then else requestAuth()
    }

    // Partial-detent height per route (fraction of the screen), matching iOS.
    val partialFraction = when (route) {
        SheetRoute.TRIPS -> 0.5f
        SheetRoute.FARM, SheetRoute.DISCOVER, SheetRoute.SAVED, SheetRoute.SETTINGS -> 0.55f
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
    BackHandler(enabled = route != null) {
        route = null; selectedPin = null; focusPin = null
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
                        FarmDetailScreen7(pin = pin, onBack = { route = null })
                    }
                    // iOS routes the Discover pill to WhatsNewSheet (S6), not the feed.
                    SheetRoute.DISCOVER -> WhatsNewSheet(onOpenFarm = { openFarm(it) }, onClose = { route = null })
                    SheetRoute.SAVED -> SavedScreen(onOpenFarm = { openFarm(it) }, onClose = { route = null })
                    SheetRoute.TRIPS -> TripsScreen(collapsed = collapsed, onOpenFarm = { openFarm(it) })
                    SheetRoute.SETTINGS -> SettingsScreen(onClose = { route = null })
                    null -> Box(Modifier.size(1.dp))
                }
            }
        },
    ) {
        // The single shared map (base layer) + the floating pill over it.
        Box(Modifier.fillMaxSize()) {
            MapScreen(onOpenFarm = { openFarm(it) }, focusPin = focusPin, bottomInset = 104.dp)

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

            // Floating bottom pill: Discover / Saved / Trips / Settings.
            // iOS shadow is `black.opacity(0.14), radius 12, y 3`. Drawn exactly via
            // `capsuleShadow` (a BlurMaskFilter carries colour + blur radius + a real
            // downward y-offset — all three, which Material `shadowElevation` can't),
            // so `shadowElevation` is dropped.
            Surface(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                    .capsuleShadow(Color.Black.copy(alpha = 0.14f), blurRadius = 12.dp, offsetY = 3.dp),
                shape = CircleShape, color = Color.White,
            ) {
                Row(Modifier.padding(6.dp)) {
                    PanelItem(Icons.Filled.Newspaper, stringResource(R.string.discover), Modifier.weight(1f)) { route = SheetRoute.DISCOVER }
                    PanelItem(Icons.Filled.Favorite, stringResource(R.string.saved), Modifier.weight(1f)) { requireAuth(SheetRoute.SAVED) }
                    PanelItem(Icons.Filled.Map, stringResource(R.string.trips), Modifier.weight(1f)) { requireAuth(SheetRoute.TRIPS) }
                    PanelItem(Icons.Filled.Settings, stringResource(R.string.settings), Modifier.weight(1f)) { requireAuth(SheetRoute.SETTINGS) }
                }
            }

            // Floating survey entry button — bottom-trailing, above the pill (which
            // spans ~safe-bottom+6 to +67), so it clears it. Hidden entirely for admins
            // (see hideSurveyForAdmin). iOS SF `text.bubble.fill` → Material Chat.
            if (!hideSurveyForAdmin) {
                Box(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 120.dp)
                        .capsuleShadow(Color.Black.copy(alpha = 0.22f), blurRadius = 10.dp, offsetY = 3.dp)
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.94f), CircleShape)
                        .clickable { showSurvey = true },
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

    // The survey presents as a modal over the map (iOS `.sheet` at 0.92), like the
    // other secondary surfaces. The gate hides its button for admins; the screen also
    // re-checks on open (belt and suspenders).
    if (showSurvey) {
        ModalBottomSheet(
            onDismissRequest = { showSurvey = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FarmsyColors.cream,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding()) {
                SurveyScreen(onClose = { showSurvey = false })
            }
        }
    }
}

@Composable
private fun PanelItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // iOS pill icon is SF Symbol `system(17,.semibold)`. Compose icon size is a
        // freely settable Dp, so the iOS point value is carried across as 17.dp
        // (Compose default would be 24.dp). SF optical sizing/weight still isn't 1:1,
        // but the size itself is now exact, not a 20.dp guess.
        Icon(icon, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.size(17.dp))
        Text(label, style = geist(10.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreenMap)
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
