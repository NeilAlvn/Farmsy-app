package app.farmsy.android.features.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmContentApi
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.Review
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/// C8 · the two write surfaces embedded in S9 FarmMemberSections (iOS
/// `FarmMemberSections.swift` — ReviewComposer :378, PostComposer :439). Built here
/// standalone so S9 can consume them; **S9 is NOT_STARTED, so nothing references
/// these yet** — they are the components, not their wiring.
///
/// iOS makes these `private struct`s inside FarmMemberSections; on Android they are
/// public composables in this file, to be called from S9 when it lands.

/// ReviewComposer (iOS :378): a star rating + optional text, upserted as a review.
@Composable
fun ReviewComposer(pin: FarmPin, existing: Review?, onPosted: suspend () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    var rating by remember { mutableStateOf(0) }
    var reviewText by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    // Prefill from an existing review (iOS onAppear).
    LaunchedEffect(existing) {
        existing?.let { rating = it.rating; reviewText = it.body ?: "" }
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF7F6F2))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.review_leave), style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.ink)

        // 5 tappable stars (iOS :391) — star.fill/star → Material Star/StarBorder.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { i ->
                Icon(
                    if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null, tint = FarmsyColors.star,
                    modifier = Modifier.size(22.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { haptics.tapTick(); rating = i },
                )
            }
        }

        // TextField 2..4 lines, white RR14 + hairline (iOS :398).
        ComposerField(
            value = reviewText, onValueChange = { reviewText = it },
            placeholder = stringResource(R.string.review_placeholder),
            minLines = 2, maxLines = 4,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(14.dp))
                .padding(12.dp),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SubmitPill(posting = posting, active = rating > 0, enabled = rating > 0 && !posting, onClick = {
                val uid = session.session.value?.user?.id ?: return@SubmitPill
                scope.launch {
                    posting = true
                    // iOS sends body = reviewText.isEmpty ? nil : reviewText (no trim).
                    runCatching {
                        FarmContentApi.submitReview(
                            osmId = pin.osmId, userId = uid, reviewerName = session.displayName,
                            rating = rating, body = reviewText.ifEmpty { null },
                        )
                    }.onSuccess { view.successTick() }
                    posting = false
                    onPosted()
                }
            }) {
                Text(stringResource(R.string.review_submit), style = geist(14.sp, FontWeight.SemiBold), color = Color.White)
            }
        }
    }
}

/// PostComposer (iOS :439): a farm's "what's new" post — text + up to 3 photos.
@Composable
fun PostComposer(pin: FarmPin, onPosted: suspend () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    var text by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var posting by remember { mutableStateOf(false) }
    val limit = 280
    val canPost = text.trim().isNotEmpty() && text.length <= limit

    // Android Photo Picker — the 1:1 for iOS PhotosPicker(maxSelectionCount:3,
    // matching:.images). Needs NO permission and NO new dependency (androidx.activity,
    // already present); same construct AddFarmSheet uses.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        scope.launch {
            photos = withContext(Dispatchers.IO) {
                val out = mutableListOf<ByteArray>()
                for (uri in uris.take(3)) {
                    // Transcode to JPEG q0.8 on device (the bucket rejects HEIC). iOS
                    // caps at 4.5MB and — if a photo is still larger AFTER the single
                    // q0.8 transcode — silently DROPS it (no downscale, no retry). Match
                    // that exactly: skip the photo, keep the rest.
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) {
                            val bos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            val jpeg = bos.toByteArray()
                            if (jpeg.size <= 4_500_000) out.add(jpeg)
                        }
                    }
                }
                out
            }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Plain TextField (sits on the white card), 2..5 lines (iOS :453).
        ComposerField(
            value = text, onValueChange = { text = it },
            placeholder = stringResource(R.string.composer_whats_new_at, pin.name),
            minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
        )

        // Photo previews (56, xmark to remove) — iOS :455.
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                photos.forEachIndexed { i, data ->
                    val img = remember(data) {
                        BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
                    }
                    Box(Modifier.size(56.dp)) {
                        img?.let {
                            Image(
                                it, null,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        // xmark.circle.fill (white x on black@50%) → Close in a
                        // black@50% circle (SF substitution).
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                ) { photos = photos.filterIndexed { idx, _ -> idx != i } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Photo picker button — photo.badge.plus → Material AddPhotoAlternate.
            Row(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, null, tint = FarmsyColors.ink, modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.composer_photo), style = geist(14.sp, FontWeight.Medium), color = FarmsyColors.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text("${limit - text.length}", style = geist(13.sp), color = FarmsyColors.inkMuted)
            Spacer(Modifier.weight(1f))
            // paperplane.fill → Material AutoMirrored Send (SF substitution).
            SubmitPill(posting = posting, active = canPost, enabled = canPost && !posting, onClick = {
                val uid = session.session.value?.user?.id ?: return@SubmitPill
                scope.launch {
                    posting = true
                    runCatching {
                        FarmContentApi.createPost(
                            osmId = pin.osmId, userId = uid, authorName = session.displayName,
                            body = text.trim(), photos = photos,
                        )
                    }.onSuccess {
                        text = ""; photos = emptyList()
                        view.successTick()
                        posting = false
                        onPosted()
                    }.onFailure {
                        view.warnTick(haptics)
                        posting = false
                    }
                }
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.composer_post), style = geist(14.sp, FontWeight.SemiBold), color = Color.White)
                }
            }
        }

        Text(stringResource(R.string.composer_posted_note), style = geist(11.sp), color = FarmsyColors.inkMuted)
    }
}

/// The shared submit pill: fixed 80-wide, farmGreenMap (0.4 when inactive), RR14,
/// vpad11; a white spinner while posting (iOS Submit/Post buttons, :405 / :479).
@Composable
private fun SubmitPill(
    posting: Boolean,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) FarmsyColors.farmGreenMap else FarmsyColors.farmGreenMap.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onClick() }
            .width(80.dp)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (posting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        else content()
    }
}

/// A BasicTextField carrying its own placeholder — iOS uses a plain styled
/// `TextField` (no Material outline/label chrome), so BasicTextField is the exact
/// match; the caller supplies any background/border via `modifier`.
@Composable
private fun ComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = geist(15.sp).copy(color = FarmsyColors.ink),
        minLines = minLines,
        maxLines = maxLines,
        cursorBrush = SolidColor(FarmsyColors.farmGreen),
        modifier = modifier,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = geist(15.sp), color = FarmsyColors.inkMuted)
            inner()
        },
    )
}

// Haptics — iOS Haptics.tap() / .success() / .warning(). Compose HapticFeedbackType
// has no success/warning constant; the platform View does (CONFIRM/REJECT, API 30+;
// light tick below), the route S8 established.
private fun androidx.compose.ui.hapticfeedback.HapticFeedback.tapTick() =
    performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)

private fun android.view.View.successTick() {
    if (Build.VERSION.SDK_INT >= 30) performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

private fun android.view.View.warnTick(haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    if (Build.VERSION.SDK_INT >= 30) performHapticFeedback(HapticFeedbackConstants.REJECT)
    else haptics.tapTick()
}
