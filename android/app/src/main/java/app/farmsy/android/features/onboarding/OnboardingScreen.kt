package app.farmsy.android.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.R
import app.farmsy.android.core.FarmCategory
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.SecondaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import app.farmsy.android.ui.theme.FitText

/// Question-per-screen onboarding — full port of iOS OnboardingView:
/// category → location → finding → counts → value → notify → referral.
private enum class Step { CATEGORY, LOCATION, FINDING, COUNTS, VALUE, NOTIFY, REFERRAL }

private data class Place(val name: String, val lat: Double, val lng: Double)

private val presets = listOf(
    Place("Amsterdam", 52.3676, 4.9041), Place("Rotterdam", 51.9244, 4.4777),
    Place("Utrecht", 52.0907, 5.1214), Place("Den Haag", 52.0705, 4.3007),
    Place("Eindhoven", 51.4416, 5.4697), Place("Antwerpen", 51.2194, 4.4025),
    Place("Gent", 51.0543, 3.7174), Place("Brussel", 50.8503, 4.3517),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = Step.entries[stepIndex]
    var chosenCategory by remember { mutableStateOf<FarmCategory?>(null) }
    var chosenPlace by remember { mutableStateOf<Place?>(null) }

    val progress by animateFloatAsState(
        (stepIndex + 1f) / (Step.entries.size + 1f), tween(500), label = "progress"
    )

    fun advance() { if (stepIndex < Step.entries.lastIndex) stepIndex++ else onComplete() }

    Column(
        Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding().padding(20.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .background(FarmsyColors.farmGreen.copy(alpha = 0.22f), CircleShape)
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(5.dp)
                    .background(FarmsyColors.farmGreen, CircleShape)
            )
        }
        Spacer(Modifier.height(20.dp))

        Box(Modifier.weight(1f)) {
            when (step) {
                Step.CATEGORY -> CategoryStep(chosenCategory) { chosenCategory = it }
                Step.LOCATION -> LocationStep(chosenPlace) { chosenPlace = it }
                Step.FINDING -> FindingStep { advance() }
                Step.COUNTS -> CountsStep(chosenPlace)
                Step.VALUE -> ValueStep()
                Step.NOTIFY -> NotifyStep()
                Step.REFERRAL -> ReferralStep()
            }
        }

        Spacer(Modifier.height(12.dp))
        when (step) {
            Step.FINDING -> Unit // auto-advances
            Step.NOTIFY -> {
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { advance() }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.no)) { advance() } }
                    Box(Modifier.weight(1f)) {
                        PrimaryButton(stringResource(R.string.notify_me)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else advance()
                        }
                    }
                }
            }
            Step.LOCATION -> PrimaryButton(
                stringResource(R.string.continue_), enabled = chosenPlace != null
            ) { advance() }
            Step.REFERRAL -> {
                PrimaryButton(stringResource(R.string.continue_)) { advance() }
                Text(
                    stringResource(R.string.skip),
                    style = geist(17.sp), color = FarmsyColors.inkMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 14.dp).clickable { onComplete() }
                )
            }
            else -> PrimaryButton(stringResource(R.string.continue_)) { advance() }
        }
    }
}

@Composable
private fun CategoryStep(selected: FarmCategory?, onSelect: (FarmCategory?) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Kicker(stringResource(R.string.personalization), Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_what_are_you) + " ",
            emphasis = stringResource(R.string.ob_looking),
            trailing = " " + stringResource(R.string.ob_for_q),
            size = 32.sp, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(26.dp))
        RadioRow("🍽️", stringResource(R.string.everything_local), selected == null) { onSelect(null) }
        listOf(
            FarmCategory.PRODUCE, FarmCategory.DAIRY, FarmCategory.CHEESE,
            FarmCategory.EGGS, FarmCategory.HONEY, FarmCategory.MEAT,
        ).forEach { cat ->
            Spacer(Modifier.height(12.dp))
            RadioRow(cat.emoji, stringResource(cat.labelRes), selected == cat) { onSelect(cat) }
        }
    }
}

@Composable
private fun RadioRow(emoji: String, label: String, isSelected: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                if (isSelected) FarmsyColors.farmGreenSoft else FarmsyColors.creamCard,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onTap).padding(vertical = 17.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(26.dp).background(
                if (isSelected) FarmsyColors.farmGreen else Color.Transparent, CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Text("✓", color = Color.White, fontSize = 13.sp)
        }
        Text(emoji, fontSize = 18.sp)
        Text(
            label, style = geist(18.sp, FontWeight.SemiBold),
            color = if (isSelected) FarmsyColors.farmGreen else FarmsyColors.ink
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationStep(chosen: Place?, onChoose: (Place) -> Unit) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current
    val pins by farms.pins.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locationHelper.request()
    }
    val loc by locationHelper.location.collectAsState()
    LaunchedEffect(loc) {
        loc?.let { onChoose(Place("your location", it.latitude, it.longitude)) }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(
            if (pins.isEmpty()) stringResource(R.string.farm_shops_across_nl_be)
            else stringResource(R.string.arg_farm_shops_nl_be, pins.size.toString())
        )
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_lets_find_your) + " ",
            emphasis = stringResource(R.string.ob_local),
            trailing = " " + stringResource(R.string.ob_farms).lowercase(),
            size = 32.sp
        )
        Spacer(Modifier.height(26.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.forEach { place ->
                val on = chosen?.name == place.name
                Text(
                    place.name, style = geist(15.sp, FontWeight.Medium),
                    color = if (on) Color.White else FarmsyColors.ink,
                    modifier = Modifier.padding(vertical = 5.dp)
                        .background(if (on) FarmsyColors.farmGreen else FarmsyColors.creamCard, CircleShape)
                        .clickable { onChoose(place) }
                        .padding(vertical = 11.dp, horizontal = 16.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.use_my_location),
            style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
            modifier = Modifier.clickable {
                if (locationHelper.hasPermission()) locationHelper.request()
                else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        )
    }
}

@Composable
private fun FindingStep(onDone: () -> Unit) {
    val farms = LocalFarms.current
    val isLoading by farms.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        delay(1400)
        while (isLoading) delay(200)
        onDone()
    }

    Column(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Kicker(stringResource(R.string.farms_within_50_km_of_you))
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_farms) + " ",
            emphasis = stringResource(R.string.ob_near),
            trailing = " " + stringResource(R.string.ob_you),
            size = 34.sp
        )
        Spacer(Modifier.height(60.dp))
        CircularProgressIndicator(color = FarmsyColors.farmGreen)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.finding_farms_near_you), style = geist(18.sp), color = FarmsyColors.inkMuted)
    }
}

@Composable
private fun CountsStep(place: Place?) {
    val farms = LocalFarms.current
    val pins by farms.pins.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val counts = remember(pins, place) {
        if (place != null) {
            farms.categoryCounts(place.lat, place.lng, 50.0).take(9)
        } else {
            val totals = LinkedHashMap<FarmCategory, Int>()
            for (p in pins) for (c in p.categories) totals[c] = (totals[c] ?: 0) + 1
            totals.entries.sortedByDescending { it.value }.take(9).map { it.key to it.value }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(
            place?.let { stringResource(R.string.farms_within_50_km_of_arg, it.name) }
                ?: stringResource(R.string.farms_across_nl_be)
        )
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_farms) + " ",
            emphasis = stringResource(R.string.ob_near),
            trailing = " " + stringResource(R.string.ob_you),
            size = 34.sp
        )
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
            items(counts) { (cat, n) ->
                // Numbers roll up from 0 as the tiles pop in (iOS CountUpText).
                val animated by animateFloatAsState(
                    if (revealed) n.toFloat() else 0f, tween(800), label = "count"
                )
                val scale by animateFloatAsState(if (revealed) 1f else 0.9f, tween(500), label = "scale")
                Column(
                    Modifier.padding(6.dp).scale(scale).alpha(if (revealed) 1f else 0f)
                        .background(FarmsyColors.creamCard, RoundedCornerShape(18.dp))
                        .fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(cat.emoji, fontSize = 30.sp)
                    Text(
                        "${animated.roundToInt()}",
                        style = geist(24.sp, FontWeight.Bold), color = FarmsyColors.farmGreen
                    )
                    FitText(
                        stringResource(cat.labelRes), style = geist(13.sp),
                        color = FarmsyColors.ink
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueStep() {
    val farms = LocalFarms.current
    val pins by farms.pins.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val emojiGrid = listOf(
        "🥬", "🥛", "🧀", "🥚", "🥩", "🐟", "🍯", "🍷", "🧺",
        "🌱", "🍎", "🥔", "🍓", "🌷", "🍞", "🫐", "🥕", "🌽",
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Sit in the middle of the step rather than crammed against the top with a
        // void beneath. The content is short; anchoring it high left most of the
        // screen empty and made the emoji grid look like it had run out.
        verticalArrangement = Arrangement.Center
    ) {
        Kicker(stringResource(R.string.why_farmsy))
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_real_food),
            emphasis = stringResource(R.string.ob_farm),
            size = 32.sp
        )
        Spacer(Modifier.height(24.dp))

        Row(
            Modifier.fillMaxWidth().background(FarmsyColors.creamCard, RoundedCornerShape(18.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val count = pins.size
            val animated by animateFloatAsState(
                if (revealed) count.toFloat() else 0f, tween(1100), label = "stat"
            )
            StatTile(
                // Grouped, so 12667 reads as a number of farms and not a serial code.
                if (count == 0) stringResource(R.string.ob_thousands)
                else "%,d+".format(animated.roundToInt()),
                stringResource(R.string.farm_shops)
            )
            StatTile("10", stringResource(R.string.ob_categories))
            StatTile("NL + BE", stringResource(R.string.ob_and_growing))
        }
        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            items(emojiGrid) { e ->
                val scale by animateFloatAsState(if (revealed) 1f else 0.4f, tween(500), label = "e")
                Text(
                    e, fontSize = 28.sp,
                    modifier = Modifier.padding(8.dp).scale(scale).alpha(if (revealed) 1f else 0f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(value: String, caption: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value, style = geist(22.sp, FontWeight.Bold), color = FarmsyColors.farmGreen)
        FitText(caption, style = geist(13.sp), color = FarmsyColors.inkMuted)
    }
}

@Composable
private fun NotifyStep() {
    Column(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Kicker(stringResource(R.string.stay_in_the_loop))
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.know_when_new_farms_appear),
            emphasis = stringResource(R.string.near_you),
            size = 30.sp
        )
        // The bell used to sit in 50dp of air above and 30dp below at 76sp, which
        // left it stranded in the middle of the screen rather than reading as part of
        // the message.
        Spacer(Modifier.height(28.dp))
        Text("🔔", fontSize = 58.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.new_farm_shops_join_farmsy_every_week),
            style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReferralStep() {
    Column(
        Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Kicker(stringResource(R.string.one_last_thing))
        Spacer(Modifier.height(10.dp))
        DisplayTitle(
            leading = stringResource(R.string.ob_have_a) + " ",
            emphasis = stringResource(R.string.ob_referral),
            trailing = " " + stringResource(R.string.ob_code_q),
            size = 32.sp
        )
        Spacer(Modifier.height(40.dp))
        Text("🎟️", fontSize = 44.sp)
    }
}
