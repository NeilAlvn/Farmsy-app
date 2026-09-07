package app.farmsy.android.features.detail

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// What to show in the photo viewer — 1:1 with iOS `LightboxSource`
/// (ImageLightbox.swift:4-16). Co-located with the view, as on iOS.
data class LightboxSource(
    val images: List<String>,
    val startIndex: Int = 0,
    /// "Farm photo" or "From a post".
    val eyebrow: String,
    /// The farm's name, or the post author's name.
    val title: String,
    /// The farm's name under a post author, when the viewer names both.
    val subtitle: String? = null,
    /// The post's words, shown above the picture, clamped.
    val postText: String? = null,
)

/// S10 · ImageLightbox — the photo viewer. iOS presents it as a `.fullScreenCover`
/// with a clear background, popping from centre (scale + fade), over a pale blurred
/// veil (ImageLightbox.swift:21). The Compose equivalent of a clear-background
/// full-screen cover that floats above everything WITHOUT going through a sheet is a
/// `Dialog(usePlatformDefaultWidth = false)`: it owns its own window on top of the
/// activity, so it needs no MainScreen scaffold wiring. The window's default dim is
/// cleared so only our white veil shows.
///
/// PORT NOTE (backdrop blur): iOS's veil is `white@55% + .ultraThinMaterial` — a blur
/// of the content behind. From a Dialog window that is DEFERRED (API:
/// `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` + `blurBehindRadius`, API 31+, and it
/// also depends on the OS "allow blur" setting; minSdk 26 has no equivalent). The
/// white@55% veil is exact; the blur is not applied yet. See PORT_NOTES.
@Composable
fun ImageLightbox(source: LightboxSource, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Clear the dialog window's default scrim so only our white veil shows, and
        // make its background transparent (fullScreenCover clear bg).
        val view = LocalView.current
        LaunchedEffect(Unit) {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                w.setBackgroundDrawableResource(android.R.color.transparent)
                @Suppress("DEPRECATION")
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        LightboxContent(source, onClose)
    }
}

@Composable
private fun LightboxContent(source: LightboxSource, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var shown by remember { mutableStateOf(false) }
    var index by remember {
        mutableIntStateOf(source.startIndex.coerceIn(0, (source.images.size - 1).coerceAtLeast(0)))
    }
    val hasMany = source.images.size > 1

    // appear: easeOut 0.22; close: easeIn 0.15 (ImageLightbox.swift:67,71). Opacity for
    // veil + panel; scale 0.94→1 for the panel only.
    val enter = shown
    val alpha by animateFloatAsState(
        targetValue = if (enter) 1f else 0f,
        animationSpec = tween(if (enter) 220 else 150, easing = if (enter) EaseOut else EaseIn),
        label = "lightboxAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (enter) 1f else 0.94f,
        animationSpec = tween(if (enter) 220 else 150, easing = if (enter) EaseOut else EaseIn),
        label = "lightboxScale",
    )

    // close(): animate out (easeIn 0.15) THEN onClose (ImageLightbox.swift:70-73).
    fun close() {
        scope.launch {
            shown = false
            delay(150)
            onClose()
        }
    }
    // step(±1): wraps at both ends, easeOut 0.24 (ImageLightbox.swift:199-205). The
    // crossfade below carries the 0.24 easeOut.
    fun step(delta: Int) {
        val n = source.images.size
        if (n == 0) return
        index = (index + delta + n) % n
    }

    LaunchedEffect(Unit) { shown = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelW = minOf(maxWidth - 32.dp, 440.dp)
        val panelH = minOf(maxHeight - 64.dp, 620.dp)

        // Veil — white@55%, fades in place, tap closes (no haptic on the veil, matching
        // iOS .onTapGesture). detectTapGestures gives a no-ripple tap like onTapGesture.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Color.White.copy(alpha = 0.55f))
                .pointerInputClose { close() },
        )

        // Panel — fixed frame, does NOT resize to the photo. Pops from centre.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(panelW, panelH)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .zIndex(1f),
        ) {
            Panel(
                source = source,
                index = index,
                hasMany = hasMany,
                onCloseTapped = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    close()
                },
                onStep = { delta ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    step(delta)
                },
            )
        }
    }
}

// A no-ripple tap for the veil, matching SwiftUI .onTapGesture.
private fun Modifier.pointerInputClose(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures { onTap() }
    }

@Composable
private fun Panel(
    source: LightboxSource,
    index: Int,
    hasMany: Boolean,
    onCloseTapped: () -> Unit,
    onStep: (Int) -> Unit,
) {
    Column(
        Modifier
            // shadow black@.18 (ImageLightbox.swift:95). Colour is matched via spot/
            // ambient; exact radius 30 / y-offset 12 is not settable on Modifier.shadow
            // → DEFERRED (see PORT_NOTES), elevation approximates the blur.
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .background(Color.White, RoundedCornerShape(24.dp))
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(24.dp)),
    ) {
        Header(source, index, hasMany, onCloseTapped)

        if (!source.postText.isNullOrEmpty()) {
            Text(
                source.postText,
                // iOS geist(14).lineSpacing(2) (ImageLightbox.swift:80-83). Geist natural
                // at 14sp = 18.20sp; +2 → lineHeight 20.2sp with Trim.Both so the extra
                // sits only between lines (same treatment as C4/C7).
                style = geist(14.sp).copy(
                    lineHeight = 20.2.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                color = FarmsyColors.ink,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            )
        }

        Picture(source, index, hasMany, onStep)
    }
}

@Composable
private fun Header(
    source: LightboxSource,
    index: Int,
    hasMany: Boolean,
    onCloseTapped: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                source.eyebrow.uppercase(),
                // iOS .kerning(1.1) on the eyebrow (ImageLightbox.swift:103).
                style = geist(11.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
                letterSpacing = 1.1.sp,
            )
            Text(
                source.title, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            source.subtitle?.let {
                Text(
                    it, style = geist(12.sp), color = FarmsyColors.farmGreenMap,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasMany) {
                Text(
                    "${index + 1} / ${source.images.size}",
                    style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
                )
            }
            // Close — xmark 14 #6B7280 in a 36 #F3F4F6 circle (ImageLightbox.swift:124-134).
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3F4F6))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onCloseTapped() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.Picture(
    source: LightboxSource,
    index: Int,
    hasMany: Boolean,
    onStep: (Int) -> Unit,
) {
    // Base rect carries the size; the image fills and is clipped, so it can never
    // push past the container (ImageLightbox.swift:143-166). Compose gates hit-testing
    // by layout bounds, so the iOS `.contentShape` overflow-hit fix has no Compose
    // counterpart — a Crop image cannot steal touches outside its Box.
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF3F4F6)),
    ) {
        val url = source.images.getOrNull(index)
        // step animates the photo change: easeOut 0.24 (ImageLightbox.swift:202).
        Crossfade(
            targetState = url,
            animationSpec = tween(240, easing = EaseOut),
            label = "lightboxPicture",
            modifier = Modifier.matchParentSize(),
        ) { u ->
            if (u != null) {
                SubcomposeAsyncImage(
                    model = u, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                    // iOS: loading → ProgressView; failure → photo(40) inkMuted.
                    loading = {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    },
                    error = {
                        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Photo, null, tint = FarmsyColors.inkMuted, modifier = Modifier.size(40.dp))
                        }
                    },
                )
            }
        }

        if (hasMany) {
            Arrow(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            ) { onStep(-1) }
            Arrow(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            ) { onStep(1) }
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    // chevron 18 #374151 in a white@90 40 circle, with a soft shadow
    // (ImageLightbox.swift:184-196). Shadow y-offset not settable → same DEFERRED as
    // the panel shadow; colour matched via spot/ambient.
    Box(
        modifier
            .size(40.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            )
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color(0xFF374151), modifier = Modifier.size(18.dp))
    }
}
