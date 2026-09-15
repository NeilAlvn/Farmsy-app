package app.farmsy.android.features.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmStatus
import app.farmsy.android.core.FarmStatusApi
import app.farmsy.android.core.FarmStatusLoaded
import app.farmsy.android.core.ReportStatus
import app.farmsy.android.core.StatusLead
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// "Was it open?" — the one question a visitor can answer that nobody else can.
/// Mirrors iOS FarmStatusSection.
///
/// Three buttons and a sentence. Tapping what you already said takes it back: a
/// button that only ever adds is one nobody can correct after a mis-tap.
///
/// The sentence says who is speaking. Every report here is another visitor's,
/// because there are no farmers on the platform yet, and "sold out" from the
/// shop itself is a different claim from "somebody found it sold out".
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarmStatusSection(osmId: String, onNeedsSignIn: () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf<FarmStatusLoaded?>(null) }
    // The farm the loaded reports belong to, so one farm's answers can never
    // appear on another's card for a frame when the card swaps in place.
    var loadedFor by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val currentSession by session.session.collectAsState()

    suspend fun reload() {
        loaded = FarmStatusApi.load(osmId, session.accessToken())
        loadedFor = osmId
    }

    LaunchedEffect(osmId, currentSession?.user?.id) { reload() }

    val reports = if (loadedFor == osmId) loaded?.reports.orEmpty() else emptyList()
    val mineList = if (loadedFor == osmId) loaded?.mine.orEmpty() else emptyList()
    val summary = remember(reports) { FarmStatus.summarise(reports) }
    val mine = remember(mineList) { FarmStatus.myReportToday(mineList) }

    fun send(status: ReportStatus) {
        scope.launch {
            val token = session.accessToken()
            if (token == null) { onNeedsSignIn(); return@launch }
            isSending = true
            // Tapping what you already said takes it back.
            val ok = if (mine == status) FarmStatusApi.clear(osmId, token)
                     else FarmStatusApi.report(osmId, status, token)
            if (ok) reload()
            isSending = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Counts and how long ago, never a percentage. Two reports rendered as
        // "50% found it open" is a lie with a decimal point in it.
        if (summary.total > 0) {
            val days = summary.daysAgo ?: 0
            val text = when (summary.lead) {
                StatusLead.OPEN -> stringResource(R.string.status_recent_open_arg, summary.open, days)
                StatusLead.TROUBLE -> stringResource(R.string.status_recent_trouble_arg, summary.trouble, days)
                StatusLead.MIXED -> stringResource(R.string.status_recent_mixed_arg, summary.open, summary.trouble)
                StatusLead.NONE -> ""
            }
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = geist(12.sp),
                    color = if (summary.lead == StatusLead.TROUBLE) FarmsyColors.warnRed else FarmsyColors.inkMuted,
                )
            }
        }

        Text(
            stringResource(R.string.status_ask).uppercase(),
            style = geist(11.sp, FontWeight.SemiBold),
            color = FarmsyColors.inkMuted,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(ReportStatus.OPEN, Icons.Filled.MeetingRoom, R.string.status_open, mine, isSending, ::send)
            StatusChip(ReportStatus.CLOSED, Icons.Filled.DoorFront, R.string.status_closed, mine, isSending, ::send)
            StatusChip(ReportStatus.SOLD_OUT, Icons.Filled.Inventory2, R.string.status_sold_out, mine, isSending, ::send)
        }
    }
}

@Composable
private fun StatusChip(
    status: ReportStatus,
    icon: ImageVector,
    labelRes: Int,
    mine: ReportStatus?,
    isSending: Boolean,
    onTap: (ReportStatus) -> Unit,
) {
    val on = mine == status
    val border = BorderStroke(1.dp, if (on) FarmsyColors.farmGreen else FarmsyColors.hairline)
    Row(
        Modifier
            .background(if (on) FarmsyColors.farmGreenSoft else Color.Transparent, RoundedCornerShape(20.dp))
            .border(border, RoundedCornerShape(20.dp))
            .clickable(enabled = !isSending) { onTap(status) }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            if (on) Icons.Filled.Check else icon, null,
            tint = if (on) FarmsyColors.farmGreen else FarmsyColors.inkMuted,
            modifier = Modifier.size(13.dp),
        )
        Text(
            stringResource(labelRes),
            style = geist(12.sp, FontWeight.SemiBold),
            color = if (on) FarmsyColors.farmGreen else FarmsyColors.inkMuted,
        )
    }
}
