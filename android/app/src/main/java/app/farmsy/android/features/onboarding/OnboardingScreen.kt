package app.farmsy.android.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsEvent
import app.farmsy.android.core.AnalyticsProp
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.Observability
import app.farmsy.android.core.FarmCategory
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.FarmDetailApi
import app.farmsy.android.features.auth.AuthSheet
import app.farmsy.android.features.place.PlaceSearchSheet
import app.farmsy.android.features.whatsnew.MultiImageFarmCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import com.google.android.gms.maps.model.LatLng
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.ui
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.delay

/// Seven-screen onboarding — a 1:1 rebuild of iOS OnboardingView: a full-bleed
/// welcome, a personalization pass (multi-select), a location pick with a radar
/// pulse, optional preferences, a "farms near you" shelf, a notifications ask with
/// a ringing bell, and a wrap-up. welcome and done sit outside the progress bar.
private enum class Step { WELCOME, PERSONALIZE, LOCATION, DETAILS, NEARBY, NOTIFY, DONE }

private data class QuickPrefs(
    var openToday: Boolean = false,
    var pickYourOwn: Boolean = false,
    var verified: Boolean = false,
    var hasPhotos: Boolean = false,
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current

    var step by remember { mutableStateOf(Step.WELCOME) }
    var selectedCats by remember { mutableStateOf(setOf<FarmCategory>()) }
    var prefs by remember { mutableStateOf(QuickPrefs()) }
    var applyPrefs by remember { mutableStateOf(false) }
    var chosenLabel by remember { mutableStateOf<String?>(null) }
    // The point "farms near you" is measured from: an explicit town pick wins over
    // GPS (iOS focusCoord = chosenCoord ?? loc; OnboardingView.swift:36-38).
    var chosenCoord by remember { mutableStateOf<LatLng?>(null) }
    // S2.3: the real place-search sheet (S19, Photon) replaces the TownPicker chips.
    var showPlaceSearch by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }

    val loc by locationHelper.location.collectAsState()

    val steps = Step.entries
    val index = steps.indexOf(step)
    val showsHeader = step != Step.WELCOME && step != Step.DONE
    val canGoBack = showsHeader

    /// Leaving a step forwards. Fires for the step being left, skipped or not — a
    /// skipped welcome still reaches personalize, and the funnel should say so.
    fun advance() {
        Observability.capture(AnalyticsEvent.ONBOARDING_STEP_COMPLETED, mapOf(AnalyticsProp.STEP to step.name.lowercase()))
        if (index < steps.lastIndex) step = steps[index + 1]
    }
    /// The two skip paths, welcome and details: recorded as a skip, then advanced
    /// like any other step.
    fun skip() {
        Observability.capture(AnalyticsEvent.ONBOARDING_SKIPPED, mapOf(AnalyticsProp.STEP to step.name.lowercase()))
        advance()
    }
    fun goBack() { if (index > 0) step = steps[index - 1] }

    fun finish() {
        // Leaving `done`, the seventh step, so the funnel reaches its last bar.
        Observability.capture(AnalyticsEvent.ONBOARDING_STEP_COMPLETED, mapOf(AnalyticsProp.STEP to step.name.lowercase()))
        // Categories are multi-select now (iOS parity) — carry the whole chosen set.
        // Details prefs map onto the quick filters.
        farms.selectedCategories.value = selectedCats
        if (applyPrefs) {
            farms.filterVerified.value = prefs.verified
            farms.filterOpenToday.value = prefs.openToday
            farms.filterHasPhotos.value = prefs.hasPhotos
            farms.filterZelfpluk.value = prefs.pickYourOwn
        }
        onComplete()
    }

    BackHandler(enabled = canGoBack) { goBack() }

    Box(Modifier.fillMaxSize()) {
        // Full-bleed background: the welcome farm photograph (dark gradient overlay
        // for legibility) on welcome, cream elsewhere — 1:1 with iOS.
        if (step == Step.WELCOME) {
            Image(
                painterResource(R.drawable.welcome_farm_shop), null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.72f)))
                )
            )
        } else {
            Box(Modifier.fillMaxSize().background(FarmsyColors.cream))
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: back chevron in a cream circle + progress bar. Hidden (but
            // space reserved) on welcome and done.
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = if (showsHeader) 1f else 0f },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(44.dp).background(FarmsyColors.creamCard, CircleShape)
                        .clickable(enabled = canGoBack) { goBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.KeyboardArrowLeft, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(22.dp))
                }
                // fraction across the middle steps: personalize…notify over (count-2).
                val fraction = index.toFloat() / (steps.size - 2).toFloat()
                ProgressBar(fraction.coerceIn(0f, 1f), Modifier.weight(1f))
            }

            // Sliding track — one step at a time, sliding horizontally like the iOS
            // HStack track (forward slides in from the right, back from the left).
            androidx.compose.animation.AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = steps.indexOf(targetState) >= steps.indexOf(initialState)
                    val dir = if (forward) 1 else -1
                    (androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 320f)) { it * dir } +
                        androidx.compose.animation.fadeIn(tween(200))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 320f)) { -it * dir } +
                            androidx.compose.animation.fadeOut(tween(200)))
                },
                label = "onboarding-slide",
            ) { s ->
                Box(Modifier.fillMaxSize()) {
                    when (s) {
                        Step.WELCOME -> WelcomeStep(onLogin = { showLogin = true }, onSkip = { skip() })
                        Step.PERSONALIZE -> PersonalizeStep(selectedCats, { selectedCats = it }) { advance() }
                        Step.LOCATION -> LocationStep(
                            resolvedLabel = chosenLabel ?: loc?.let { stringResource(R.string.your_current_location) },
                            onUseLocation = { if (locationHelper.hasPermission()) locationHelper.request() },
                            onSearch = { showPlaceSearch = true },
                            onContinue = { advance() },
                        )
                        Step.DETAILS -> DetailsStep(
                            prefs = prefs, onChange = { prefs = it },
                            onShowFarms = { applyPrefs = true; advance() },
                            onSkip = { applyPrefs = false; skip() },
                        )
                        // focusCoord = chosenCoord ?? loc (iOS precedence: a picked town
                        // wins over GPS). Passed to NearbyStep so it centres on it.
                        Step.NEARBY -> NearbyStep(
                            coord = chosenCoord ?: loc?.let { LatLng(it.latitude, it.longitude) },
                            label = chosenLabel,
                            onContinue = { advance() },
                        )
                        Step.NOTIFY -> NotifyStep(onContinue = { advance() })
                        Step.DONE -> DoneStep(onStart = { finish() })
                    }
                }
            }
        }
    }

    if (showLogin) AuthSheet(onDone = { showLogin = false })

    // S2.3 place-search (S19, reused as-is). iOS onboarding: onPick sets chosenCoord +
    // chosenLabel; onLocate requests GPS. The picked town flows to NearbyStep as the
    // focus coord, so "farms near you" measures from where they chose, not just GPS.
    if (showPlaceSearch) {
        PlaceSearchSheet(
            onPick = { coord, label -> chosenCoord = coord; chosenLabel = label },
            onLocate = { locationHelper.request() },
            onDismiss = { showPlaceSearch = false },
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(5.dp).background(FarmsyColors.farmGreen.copy(alpha = 0.22f), CircleShape)) {
        Box(Modifier.fillMaxWidth(fraction.coerceAtLeast(0.03f)).height(5.dp).background(FarmsyColors.farmGreen, CircleShape))
    }
}

// MARK: - Step 1: welcome

@Composable
private fun WelcomeStep(onLogin: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(106.dp).background(FarmsyColors.cream, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(R.drawable.farmsy_logo), null,
                        modifier = Modifier.size(74.dp), contentScale = ContentScale.Fit,
                    )
                }
                Text("Farmsy", style = ui(52.sp, FontWeight.Bold), color = Color.White)
            }
            Text(
                stringResource(R.string.welcome_tagline),
                style = display(26.sp, FontWeight.Medium), color = Color.White, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.welcome_body),
                style = geist(16.sp), color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // "Log in / Sign up" — white filled on the dark hero.
            Box(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp))
                    .clickable { onLogin() }.padding(vertical = 17.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.welcome_login), style = geist(18.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
            }
            // "Skip for now" — translucent outline.
            Box(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable { onSkip() }.padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.welcome_skip), style = geist(17.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}

// MARK: - Step 2: personalize (multi-select)

@Composable
private fun PersonalizeStep(selected: Set<FarmCategory>, onChange: (Set<FarmCategory>) -> Unit, onContinue: () -> Unit) {
    val options = listOf(
        FarmCategory.PRODUCE, FarmCategory.DAIRY, FarmCategory.CHEESE, FarmCategory.EGGS,
        FarmCategory.HONEY, FarmCategory.MEAT, FarmCategory.FISH, FarmCategory.WINE,
    )
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(R.string.ob_personalize))
            DisplayTitle(stringResource(R.string.ob_what_are_you), stringResource(R.string.ob_looking), stringResource(R.string.ob_for_q), 32.sp, Modifier.fillMaxWidth())
            Text(stringResource(R.string.personalize_sub), style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(options) { cat ->
                val on = cat in selected
                CategoryTile(cat, on) {
                    onChange(if (on) selected - cat else selected + cat)
                }
            }
        }
        PrimaryButton(
            if (selected.isEmpty()) stringResource(R.string.skip) else stringResource(R.string.continue_),
            Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 12.dp),
        ) { onContinue() }
    }
}

@Composable
private fun CategoryTile(cat: FarmCategory, isOn: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (isOn) FarmsyColors.farmGreenSoft else FarmsyColors.creamCard, RoundedCornerShape(16.dp))
            .border(if (isOn) 1.5.dp else 1.dp, if (isOn) FarmsyColors.farmGreen else FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .clickable { onTap() }.padding(vertical = 16.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(cat.emoji, fontSize = 24.sp)
        Text(
            stringResource(cat.labelRes), style = geist(16.sp, FontWeight.SemiBold),
            color = if (isOn) FarmsyColors.farmGreen else FarmsyColors.ink, maxLines = 1, modifier = Modifier.weight(1f),
        )
        if (isOn) Icon(Icons.Filled.CheckCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
    }
}

// MARK: - RadarPulse

/// A location "radar": static range rings, staggered expanding pulses, a centre
/// pin. 1:1 with iOS RadarPulse.
@Composable
private fun RadarPulse() {
    val t = rememberInfiniteTransition(label = "radar")
    Box(Modifier.height(170.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Fixed range rings.
        listOf(0, 1, 2).forEach { i ->
            Box(
                Modifier.size((58 + i * 44).dp)
                    .border(1.5.dp, FarmsyColors.farmGreen.copy(alpha = 0.22f), CircleShape)
            )
        }
        // Two staggered pulses rippling outward.
        listOf(0, 1).forEach { i ->
            val p by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(2400, delayMillis = i * 1200), RepeatMode.Restart),
                label = "pulse$i",
            )
            val sizeDp = (50 + p * 100).dp
            Box(
                Modifier.size(sizeDp)
                    .border(2.dp, FarmsyColors.farmGreenMap.copy(alpha = (1f - p) * 0.55f), CircleShape)
            )
        }
        // Centre pin.
        Box(
            Modifier.size(48.dp).background(FarmsyColors.farmGreenMap, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LocationOn, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// MARK: - Step 3: location

@Composable
private fun LocationStep(resolvedLabel: String?, onUseLocation: () -> Unit, onSearch: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(R.string.ob_location))
            DisplayTitle(stringResource(R.string.ob_where_are_you), stringResource(R.string.ob_exploring), stringResource(R.string.ob_today_q), 30.sp)
        }
        Spacer(Modifier.height(20.dp))
        RadarPulse()
        Spacer(Modifier.height(28.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LocationRow(Icons.Filled.LocationOn, stringResource(R.string.use_my_location), filled = true) { onUseLocation() }
            // iOS: "Search a town instead" → onSearch → PlaceSearchSheet (S19). Was an
            // inline TownPicker (8 hardcoded chips), now the real Photon-backed search.
            LocationRow(Icons.Filled.Search, stringResource(R.string.search_a_town), filled = false) { onSearch() }
        }
        resolvedLabel?.let {
            Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
                Text(it, style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.continue_), Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) { onContinue() }
    }
}

@Composable
private fun LocationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, filled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(if (filled) FarmsyColors.farmGreenMap else FarmsyColors.farmGreenSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (filled) Color.White else FarmsyColors.farmGreen, modifier = Modifier.size(18.dp))
        }
        Text(title, style = geist(17.sp, FontWeight.SemiBold), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Step 4: details

@Composable
private fun DetailsStep(prefs: QuickPrefs, onChange: (QuickPrefs) -> Unit, onShowFarms: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(R.string.ob_optional))
            DisplayTitle(stringResource(R.string.ob_anything_else), stringResource(R.string.ob_know), stringResource(R.string.ob_q_mark), 30.sp)
            Text(stringResource(R.string.details_sub), style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrefRow("🕒", stringResource(R.string.pref_open_today), stringResource(R.string.pref_open_today_sub), prefs.openToday) { onChange(prefs.copy(openToday = it)) }
            PrefRow("🧺", stringResource(R.string.pref_pyo), stringResource(R.string.pref_pyo_sub), prefs.pickYourOwn) { onChange(prefs.copy(pickYourOwn = it)) }
            PrefRow("✅", stringResource(R.string.pref_verified), stringResource(R.string.pref_verified_sub), prefs.verified) { onChange(prefs.copy(verified = it)) }
            PrefRow("📷", stringResource(R.string.pref_photos), stringResource(R.string.pref_photos_sub), prefs.hasPhotos) { onChange(prefs.copy(hasPhotos = it)) }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(stringResource(R.string.show_me_farms)) { onShowFarms() }
            Text(stringResource(R.string.explore_on_my_own), style = geist(16.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted, modifier = Modifier.clickable { onSkip() })
        }
    }
}

private fun QuickPrefs.copy(openToday: Boolean = this.openToday, pickYourOwn: Boolean = this.pickYourOwn, verified: Boolean = this.verified, hasPhotos: Boolean = this.hasPhotos) =
    QuickPrefs(openToday, pickYourOwn, verified, hasPhotos)

@Composable
private fun PrefRow(emoji: String, title: String, subtitle: String, isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .clickable { onToggle(!isOn) }.padding(vertical = 14.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(title, style = geist(16.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            Text(subtitle, style = geist(13.sp), color = FarmsyColors.inkMuted)
        }
        // Toggle track + knob.
        Box(
            Modifier.size(width = 46.dp, height = 28.dp)
                .background(if (isOn) FarmsyColors.farmGreenMap else Color(0xFFE5E4DF), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            val knobOffset by animateDpAsState(if (isOn) 21.dp else 3.dp, spring(), label = "knob")
            Box(Modifier.offset(x = knobOffset).size(22.dp).background(Color.White, CircleShape))
        }
    }
}

// MARK: - Step 5: nearby (farm shelf)

@Composable
private fun NearbyStep(coord: LatLng?, label: String?, onContinue: () -> Unit) {
    val farms = LocalFarms.current
    val pins by farms.pins.collectAsState()
    val galleries by farms.galleries.collectAsState()
    val galleriesLoaded by farms.galleriesLoaded.collectAsState()
    val featuredTeasers by farms.featuredTeasers.collectAsState()

    var shown by remember { mutableStateOf<List<FarmPin>>(emptyList()) }
    var built by remember { mutableStateOf(false) }
    var nearbyCount by remember { mutableStateOf(0) }
    var extraTeasers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // iOS build() + fetchTeasers, keyed like iOS buildKey (pins / galleriesLoaded /
    // coord). Pool = photo'd farms within 100 km (nearest first) of the focus coord
    // (a picked town or GPS), else 30 popular picks. Split described (has a gallery) /
    // plain, take 6 + 8, shuffle so described cards don't cluster by distance, show ≤10.
    LaunchedEffect(pins.size, galleriesLoaded, coord?.latitude, coord?.longitude) {
        if (pins.isEmpty()) return@LaunchedEffect
        farms.loadGalleriesIfNeeded()
        val base = coord?.let { farms.nearbyWithImages(it.latitude, it.longitude, 100.0) }
            ?: farms.feedPicks(null, 30)
        nearbyCount = base.size
        val g = farms.galleries.value
        val described = base.filter { g[it.osmId]?.isNotEmpty() == true }
        val plain = base.filter { g[it.osmId].isNullOrEmpty() }
        shown = (described.take(6) + plain.take(8)).shuffled()
        built = true
        // Batch-fetch teasers for the cover-only cards that don't already carry one.
        val targets = shown.take(10).map { it.osmId }
            .filter { farms.featuredTeasers.value[it] == null && extraTeasers[it] == null }
        if (targets.isNotEmpty()) {
            val result = FarmDetailApi.teasers(targets)
            if (result.isNotEmpty()) extraTeasers = extraTeasers + result
        }
    }

    // Photos for a card: the featured gallery when we have it, else the cover.
    fun imagesFor(pin: FarmPin): List<String> {
        galleries[pin.osmId]?.takeIf { it.isNotEmpty() }?.let { return it }
        pin.image?.let { return listOf(it) }
        return emptyList()
    }
    fun teaserFor(pin: FarmPin): String? = featuredTeasers[pin.osmId] ?: extraTeasers[pin.osmId]

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker(stringResource(R.string.ob_great_choice))
            DisplayTitle(stringResource(R.string.ob_here_are_farms), stringResource(R.string.ob_near), stringResource(R.string.ob_you), 30.sp)
            // Count is the POOL size (iOS nearbyCount = base.count), not the ≤10 shown.
            val headline = when {
                !built -> stringResource(R.string.finding_farms_near_you)
                coord == null -> stringResource(R.string.popular_farm_shops)
                label != null -> stringResource(R.string.farms_within_100km_of_arg, nearbyCount, label)
                else -> stringResource(R.string.farms_within_100km_of_you, nearbyCount)
            }
            Text(headline, style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!built) {
                // Same skeleton the What's New "Featured farms" shelf uses (iOS SkeletonBox).
                repeat(3) { SkeletonBox(16.dp, Modifier.fillMaxWidth().height(180.dp)) }
            } else {
                // C4 MultiImageFarmCard (reused as-is), like iOS. onOpen → advance.
                shown.take(10).forEach { pin ->
                    MultiImageFarmCard(pin = pin, images = imagesFor(pin), teaser = teaserFor(pin), onOpen = onContinue)
                }
            }
        }
        PrimaryButton(stringResource(R.string.see_all_on_map), Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 12.dp)) { onContinue() }
    }
}

// MARK: - RingingBell

/// A bell swinging on its clapper with a pulsing red badge. 1:1 with iOS RingingBell.
@Composable
private fun RingingBell(size: Int = 76) {
    val t = rememberInfiniteTransition(label = "bell")
    val swing by t.animateFloat(-14f, 14f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "swing")
    val pulse by t.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    Box(contentAlignment = Alignment.TopEnd) {
        Icon(
            Icons.Filled.Notifications, null, tint = Color(0xFFF5B301),
            modifier = Modifier.size(size.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationZ = swing
            },
        )
        Box(
            Modifier.size((size * 0.3f).dp).offset(x = (size * 0.06f).dp, y = -(size * 0.04f).dp)
                .scale(pulse).background(Color(0xFFEF4444), CircleShape),
        )
    }
}

// MARK: - Step 6: notify

@Composable
private fun NotifyStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onContinue() }
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker(stringResource(R.string.stay_in_the_loop))
            DisplayTitle(stringResource(R.string.know_when_new_farms_appear), stringResource(R.string.near_you), size = 28.sp)
        }
        Spacer(Modifier.height(60.dp))
        RingingBell(76)
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrimaryButton(stringResource(R.string.turn_on_notifications)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else onContinue()
            }
            Text(stringResource(R.string.maybe_later), style = geist(16.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted, modifier = Modifier.clickable { onContinue() })
        }
        Text(
            stringResource(R.string.new_farm_shops_join_farmsy_every_week),
            style = geist(15.sp).copy(fontStyle = FontStyle.Italic), color = FarmsyColors.inkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
        )
    }
}

// MARK: - Step 7: done

@Composable
private fun DoneStep(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Verified, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(68.dp).padding(bottom = 22.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker(stringResource(R.string.ob_ready))
            DisplayTitle(stringResource(R.string.ob_youre_all), stringResource(R.string.ob_set), size = 34.sp)
        }
        Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DoneBullet("🗺️", stringResource(R.string.done_bullet_map))
            DoneBullet("❤️", stringResource(R.string.done_bullet_save))
            DoneBullet("🧭", stringResource(R.string.done_bullet_trip))
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.start_exploring), Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) { onStart() }
    }
}

@Composable
private fun DoneBullet(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).background(FarmsyColors.farmGreenSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 22.sp)
        }
        Text(text, style = geist(16.sp, FontWeight.Medium), color = FarmsyColors.ink)
    }
}
