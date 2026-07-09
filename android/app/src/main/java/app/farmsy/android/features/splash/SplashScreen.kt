package app.farmsy.android.features.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.R
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.displayItalic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// Opening animation — mirrors iOS SplashView: barn mark pops in, "Farmsy"
/// letters cascade up into place, then a green underline sweeps beneath.
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val letters = remember { "Farmsy".toList() }

    val markScale = remember { Animatable(0.55f) }
    val markAlpha = remember { Animatable(0f) }
    val letterProgress = remember { letters.map { Animatable(0f) } }
    val underlineWidth = remember { Animatable(0f) }
    val settle = remember { Animatable(1.06f) }

    LaunchedEffect(Unit) {
        launch { markScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
        launch { markAlpha.animateTo(1f, tween(350)) }
        letterProgress.forEachIndexed { i, anim ->
            launch {
                delay(i * 75L)
                anim.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
            }
        }
        launch {
            delay(850)
            underlineWidth.animateTo(132f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            delay(200)
            settle.animateTo(1f, tween(700))
        }
        delay(2300)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(FarmsyColors.cream),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Optical centering: pull the group up slightly (iOS offset -14).
            modifier = Modifier.offset(y = (-14).dp).scale(settle.value)
        ) {
            Image(
                painter = painterResource(R.drawable.farmsy_logo),
                contentDescription = null,
                modifier = Modifier
                    .height(116.dp)
                    .scale(markScale.value)
                    .alpha(markAlpha.value)
            )
            Spacer(Modifier.height(16.dp))
            Row {
                letters.forEachIndexed { i, ch ->
                    val p = letterProgress[i].value
                    Text(
                        ch.toString(),
                        style = displayItalic(60.sp, FontWeight.Medium),
                        color = FarmsyColors.ink,
                        modifier = Modifier
                            .offset(y = ((1f - p) * 38).dp)
                            .alpha(p)
                            .blur(((1f - p) * 7).dp)
                            .rotate((1f - p) * 7f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .width(underlineWidth.value.dp)
                    .height(4.dp)
                    .background(FarmsyColors.farmGreen, CircleShape)
            )
        }
    }
}
