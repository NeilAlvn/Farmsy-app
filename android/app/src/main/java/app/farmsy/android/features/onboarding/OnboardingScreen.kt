package app.farmsy.android.features.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.R
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlin.math.roundToInt

/// Onboarding — condensed port of iOS OnboardingView. Keeps the value story,
/// the real category-count grid with the count-up animation, and hands off to
/// the main app as a guest. (Category/location personalization is optional and
/// omitted for the first Android cut; the flow still ends the same way.)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val lastStep = 2

    val progress by animateFloatAsState(
        targetValue = (step + 1f) / (lastStep + 2f),
        animationSpec = tween(500), label = "progress"
    )

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).padding(20.dp)) {
        // Progress bar
        Box(
            Modifier.fillMaxWidth().height(5.dp)
                .background(FarmsyColors.farmGreen.copy(alpha = 0.22f), RoundedCornerShape(50))
        ) {
            Box(
                Modifier.fillMaxWidth(progress).height(5.dp)
                    .background(FarmsyColors.farmGreen, RoundedCornerShape(50))
            )
        }
        Spacer(Modifier.height(24.dp))

        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> ValueStep()
                1 -> CountsStep()
                else -> ReferralStep()
            }
        }

        PrimaryButton(
            stringResource(if (step < lastStep) R.string.continue_ else R.string.skip)
        ) {
            if (step < lastStep) step++ else onComplete()
        }
    }
}

@Composable
private fun ValueStep() {
    val farms = LocalFarms.current
    val pins by farms.pins.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Kicker(stringResource(R.string.why_farmsy))
        DisplayTitle(
            leading = stringResource(R.string.ob_real_food) + " ",
            emphasis = stringResource(R.string.ob_farm),
            size = 32.sp
        )
        val count = pins.size
        val animated by animateFloatAsState(
            targetValue = if (revealed) count.toFloat() else 0f,
            animationSpec = tween(1100), label = "count"
        )
        Text(
            if (count == 0) stringResource(R.string.ob_thousands)
            else "${animated.roundToInt()}+",
            style = display(40.sp, FontWeight.Bold), color = FarmsyColors.farmGreen
        )
        Text(stringResource(R.string.farm_shops), style = geist(15.sp), color = FarmsyColors.inkMuted)
    }
}

@Composable
private fun CountsStep() {
    val farms = LocalFarms.current
    val pins by farms.pins.collectAsState()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val counts = remember(pins) {
        val totals = LinkedHashMap<app.farmsy.android.core.FarmCategory, Int>()
        for (p in pins) for (c in p.categories) totals[c] = (totals[c] ?: 0) + 1
        totals.entries.sortedByDescending { it.value }.take(9)
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Kicker(stringResource(R.string.farms_across_nl_be))
        DisplayTitle(
            leading = stringResource(R.string.ob_farms) + " ",
            emphasis = stringResource(R.string.ob_near),
            trailing = " " + stringResource(R.string.ob_you),
            size = 34.sp
        )
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth()) {
            items(counts) { (cat, n) ->
                val animated by animateFloatAsState(
                    targetValue = if (revealed) n.toFloat() else 0f,
                    animationSpec = tween(800), label = "c"
                )
                Column(
                    Modifier.padding(6.dp)
                        .background(FarmsyColors.creamCard, RoundedCornerShape(18.dp))
                        .fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(cat.emoji, style = geist(30.sp))
                    Text(
                        "${animated.roundToInt()}",
                        style = geist(24.sp, FontWeight.Bold), color = FarmsyColors.farmGreen
                    )
                    Text(stringResource(cat.labelRes), style = geist(13.sp), color = FarmsyColors.ink)
                }
            }
        }
    }
}

@Composable
private fun ReferralStep() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
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
    }
}
