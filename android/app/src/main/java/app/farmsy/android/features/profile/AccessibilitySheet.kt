package app.farmsy.android.features.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.farmsy.android.R
import app.farmsy.android.core.BadgeKind
import app.farmsy.android.ui.theme.Badge
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Haptics
import app.farmsy.android.ui.theme.ListRow
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.RowGroup
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.rememberTapHaptic
import app.farmsy.android.ui.theme.role

/// Haptics is the one switch Farmsy owns. Reduce motion and text size are the
/// phone's, shown read-only so it is clear where to change them.
/// The Android twin of iOS Profile/AccessibilitySheet.swift.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tap = rememberTapHaptic()
    var haptics by remember { mutableStateOf(Haptics.enabled) }
    val reduceMotion = remember {
        listOf(Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE)
            .any { Settings.Global.getFloat(context.contentResolver, it, 1f) == 0f }
    }
    val fontScale = context.resources.configuration.fontScale
    val textSize = if (fontScale == 1f) stringResource(R.string.default_size) else "%d%%".format((fontScale * 100).toInt())

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FarmsyColors.cream) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.s8)) {
            ScreenHeader(stringResource(R.string.accessibility), compact = true, onBack = onDismiss)
            Column(Modifier.padding(horizontal = Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                Row(
                    Modifier.fillMaxWidth().background(FarmsyColors.surface, RoundedCornerShape(Radius.card))
                        .padding(horizontal = Space.s4, vertical = Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    Icon(Icons.Outlined.Vibration, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.haptics), style = role(TextRole.BODY), color = FarmsyColors.ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = haptics,
                        onCheckedChange = { on -> Haptics.enabled = on; haptics = on; if (on) tap() },
                        colors = SwitchDefaults.colors(checkedTrackColor = FarmsyColors.vivid, checkedThumbColor = FarmsyColors.ink),
                    )
                }
                Text(stringResource(R.string.haptics_caption), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)

                Text(stringResource(R.string.android_settings), style = role(TextRole.HEADING), color = FarmsyColors.ink, modifier = Modifier.padding(top = Space.s4))
                RowGroup {
                    ListRow(Icons.Outlined.DirectionsWalk, stringResource(R.string.reduce_motion),
                        value = stringResource(if (reduceMotion) R.string.on else R.string.off), chevron = false) {}
                    ListRow(Icons.Outlined.FormatSize, stringResource(R.string.text_size), value = textSize, chevron = false) {}
                    ListRow(Icons.Outlined.Settings, stringResource(R.string.open_settings)) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
                Text(stringResource(R.string.accessibility_caption), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
            }
        }
    }
}

/// One badge: the big circle, earned or locked, and the rule in words.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeSheet(kind: BadgeKind, earned: Boolean, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FarmsyColors.cream) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.s6),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Box(
                Modifier.padding(top = Space.s4).size(96.dp).background(if (earned) FarmsyColors.vivid else FarmsyColors.creamFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kind.icon, null, tint = if (earned) FarmsyColors.ink else FarmsyColors.inkFaint, modifier = Modifier.size(40.dp))
            }
            Text(stringResource(kind.titleRes), style = role(TextRole.HEADING), color = FarmsyColors.ink)
            Badge(stringResource(if (earned) R.string.earned else R.string.locked), fill = if (earned) FarmsyColors.vivid else FarmsyColors.creamFill, ink = FarmsyColors.ink)
            Text(
                stringResource(kind.ruleRes), style = role(TextRole.BODY), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.s6),
            )
            Spacer(Modifier.height(Space.s4))
            PillButton(stringResource(R.string.done), PillVariant.PRIMARY, PillSize.MEDIUM, block = true, modifier = Modifier.padding(horizontal = Space.s4), onClick = onDismiss)
        }
    }
}
