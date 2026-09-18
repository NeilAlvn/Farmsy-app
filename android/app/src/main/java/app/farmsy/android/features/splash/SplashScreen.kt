package app.farmsy.android.features.splash

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
    val view = LocalView.current

    // iOS drives the mark's scale + opacity + blur together under one
    // `withAnimation(.spring(duration:0.65, bounce:0.3)) { markIn = true }`, so all
    // three share the same spring here (bounce 0.3 ≈ dampingRatio 0.7).
    val markScale = remember { Animatable(0.55f) }
    val markAlpha = remember { Animatable(0f) }
    val markBlur = remember { Animatable(5f) }        // iOS .blur(markIn ? 0 : 5)
    val letterProgress = remember { letters.map { Animatable(0f) } }
    val underlineWidth = remember { Animatable(0f) }
    val settle = remember { Animatable(1.06f) }

    LaunchedEffect(Unit) {
        val markSpring = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
        launch { markScale.animateTo(1f, markSpring) }
        launch { markAlpha.animateTo(1f, markSpring) }
        launch { markBlur.animateTo(0f, markSpring) }
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
            // iOS settle: easeOut(0.7) delay 0.2.
            delay(200)
            settle.animateTo(1f, tween(700, easing = EaseOut))
        }
        delay(2300)
        // PORT NOTE: iOS Haptics.tap() = UIImpactFeedbackGenerator(.light); the
        // closest Android light-tap constant is VIRTUAL_KEY via View.performHapticFeedback.
        if (app.farmsy.android.ui.theme.Haptics.enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                    .blur(markBlur.value.dp)
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
                            // iOS rotates each letter with anchor .bottom (pivots at the
                            // baseline), so use graphicsLayer with a bottom transform origin
                            // rather than .rotate(), which pivots at the center.
                            .graphicsLayer {
                                rotationZ = (1f - p) * 7f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
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
