package app.farmsy.android.features.shopping

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.role
import androidx.compose.material3.Text

/// The free sample, wherever Farmsy's paid answer is withheld: the real
/// coverage sentence, and the real result blurred underneath it. Looking is
/// free, Farmsy doing the work is Plus — and there is never a padlock on an
/// empty screen, because what sits behind the blur is the honest answer.
///
/// The Shopping tab and the trip planner's "Shop from a list" sheet both draw
/// their own rows through this (final review #1: the sheet had no membership
/// check at all and handed the named farms out for free), so the blur, the tap
/// target and the semantics are decided once. Twin of iOS `LockedSample`.
///
/// The content clears its own semantics and installs no click of its own, so
/// TalkBack reads one named button instead of the rows behind the blur, and the
/// tap lands on this container.
@Composable
fun LockedSample(
    coverage: String,
    label: String,
    onUnlock: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Modifier.blur() needs a RenderEffect, API 31+; older devices get a flat
    // scrim over the same content instead of no obscuring at all.
    val hide = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(7.dp)
    } else {
        Modifier.drawWithContent {
            drawContent()
            drawRect(FarmsyColors.surface.copy(alpha = 0.85f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        // Outside the tap target, so the real number is still read out rather
        // than swallowed by the button's name.
        Text(coverage, style = role(TextRole.HEADING), color = FarmsyColors.ink)
        Box(
            Modifier
                .fillMaxWidth()
                // The wrapped content clears its own semantics (it is the
                // child, so that clear doesn't reach up here) — without an
                // explicit name this node would reach TalkBack as an unnamed
                // button; `onClickLabel` alone is only the action hint.
                .semantics { contentDescription = label }
                .clickable(onClickLabel = label, role = Role.Button, onClick = onUnlock),
        ) {
            Box(hide.clearAndSetSemantics {}) { content() }
        }
    }
}
