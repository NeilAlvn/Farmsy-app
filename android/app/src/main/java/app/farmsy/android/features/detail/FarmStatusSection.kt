package app.farmsy.android.features.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.AnalyticsValue
import app.farmsy.android.core.FarmStatus
import app.farmsy.android.core.FarmStatusApi
import app.farmsy.android.core.FarmStatusLoaded
import app.farmsy.android.core.Freshness
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.ReportStatus
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.StatusLead
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch
import java.time.Instant

/// "Was it open?" — the one question a visitor can answer that nobody else can.
/// Mirrors iOS FarmStatusSection.
///
/// Three buttons and a sentence. Tapping what you already said takes it back: a
/// button that only ever adds is one nobody can correct after a mis-tap.
///
/// The sentence says who is speaking. Every report here is another visitor's,
/// because there are no farmers on the platform yet, and "sold out" from the
/// shop itself is a different claim from "somebody found it sold out".
///
/// Once you have said "open" or "sold out", a row of product chips asks what
/// you found on the shelf — the same ids the shopping list matches on. Free
/// sees how many and how many days ago; Plus sees minutes, how many agree
/// today, and what they found.
///
/// `sells` is what this farm sells, so the chips are its products and not the
/// whole picker.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarmStatusSection(osmId: String, sells: String? = null, onNeedsSignIn: () -> Unit) {
    val session = LocalSession.current
    val shell = LocalShell.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by ShoppingItems.items.collectAsState()
    val language = remember { ShoppingItems.language(context) }
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }

    var loaded by remember { mutableStateOf<FarmStatusLoaded?>(null) }
    // The farm the loaded reports belong to, so one farm's answers can never
    // appear on another's card for a frame when the card swaps in place.
    var loadedFor by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val currentSession by session.session.collectAsState()
    // Derived from the COLLECTED profile: `session.hasFullAccess` alone is a
    // plain field read that invalidates nothing, so a membership bought
    // mid-session would leave this row locked.
    val profile by session.profile.collectAsState()
    val plus = profile?.hasFullAccess == true

    suspend fun reload() {
        loaded = FarmStatusApi.load(osmId, session.accessToken())
        loadedFor = osmId
    }

    LaunchedEffect(osmId, currentSession?.user?.id) { reload() }

    val reports = if (loadedFor == osmId) loaded?.reports.orEmpty() else emptyList()
    val mineList = if (loadedFor == osmId) loaded?.mine.orEmpty() else emptyList()
    val summary = remember(reports) { FarmStatus.summarise(reports) }
    val freshness = remember(reports) { FarmStatus.freshness(reports) }
    val myReport = remember(mineList) {
        val today = FarmStatus.amsterdamDay(Instant.now())
        mineList.firstOrNull { r -> r.instant?.let { FarmStatus.amsterdamDay(it) == today } == true }
    }
    val mine = myReport?.reportStatus
    // The farm's own products, as chips. Falls back to the whole picker for a
    // farm that never said what it sells — the visitor is telling us.
    val chips = remember(items, sells) {
        val own = if (sells.isNullOrEmpty()) emptyList() else items.filter { ProductMatch.covers(sells, it.terms) }
        own.ifEmpty { items.take(12) }
    }

    fun send(status: ReportStatus) {
        scope.launch {
            val token = session.accessToken()
            if (token == null) { onNeedsSignIn(); return@launch }
            isSending = true
            // Tapping what you already said takes it back.
            val ok = if (mine == status) FarmStatusApi.clear(osmId, token)
                     else FarmStatusApi.report(osmId, status, token = token)
            if (ok) reload()
            isSending = false
        }
    }

    // The products ride on the same row as the status, so a toggle re-sends
    // today's report with the new list. One per person per day still holds.
    fun toggleProduct(id: String) {
        val my = myReport ?: return
        val status = my.reportStatus ?: return
        scope.launch {
            val token = session.accessToken() ?: return@launch
            isSending = true
            val products = if (id in my.products) my.products - id else my.products + id
            if (FarmStatusApi.report(osmId, status, products, token)) reload()
            isSending = false
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Counts and how long ago, never a percentage. Two reports rendered as
        // "50% found it open" is a lie with a decimal point in it.
        if (plus && freshness != null) {
            FreshLine(freshness, items, language)
        } else {
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
                        color = if (summary.lead == StatusLead.TROUBLE) FarmsyColors.critical else FarmsyColors.inkMuted,
                    )
                }
            }
            if (!plus && freshness != null) {
                Row(
                    Modifier.clickable { shell.openPlus(AnalyticsValue.Trigger.FARM_DETAIL) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.status_confirmed_today_plus), style = geist(12.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
                }
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

        if ((mine == ReportStatus.OPEN || mine == ReportStatus.SOLD_OUT) && chips.isNotEmpty()) {
            Text(
                stringResource(if (mine == ReportStatus.SOLD_OUT) R.string.status_what_was_still_there else R.string.status_what_did_you_find).uppercase(),
                style = geist(11.sp, FontWeight.SemiBold),
                color = FarmsyColors.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { item ->
                    Chip(item.label(language), emoji = item.emoji, selected = item.id in myReport!!.products) {
                        if (!isSending) toggleProduct(item.id)
                    }
                }
            }
        }
    }
}

/// "N min ago" / "N h ago" / "N d ago" — the Plus wording for a report's age.
@Composable
fun minutesAgoLabel(mins: Int): String = when {
    mins < 60 -> stringResource(R.string.min_ago_arg, mins)
    mins < 60 * 36 -> stringResource(R.string.h_ago_arg, mins / 60)
    else -> stringResource(R.string.d_ago_arg, mins / 1440)
}

/// "1 person" / "N people".
@Composable
fun peopleLabel(n: Int): String =
    if (n == 1) stringResource(R.string.one_person) else stringResource(R.string.n_people_arg, n)

@Composable
fun statusWord(status: ReportStatus): String = stringResource(
    when (status) {
        ReportStatus.OPEN -> R.string.status_open
        ReportStatus.CLOSED -> R.string.status_closed
        ReportStatus.SOLD_OUT -> R.string.status_sold_out
    }
)

/// Plus: "Open · confirmed 18 min ago by 3 people", and what they found.
@Composable
private fun FreshLine(f: Freshness, items: List<ShoppingItem>, language: String) {
    // The bare-duration strings so German can say "vor 18 Min. bestätigt".
    val whenText = if (f.minutesAgo < 60) stringResource(R.string.fresh_when_min_arg, f.minutesAgo)
                   else stringResource(R.string.fresh_when_h_arg, f.minutesAgo / 60)
    val open = f.status == ReportStatus.OPEN
    val found = f.products.mapNotNull { id -> items.firstOrNull { it.id == id }?.label(language) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(if (open) FarmsyColors.vividPositive else FarmsyColors.vividCritical, CircleShape))
            Text(
                stringResource(R.string.status_fresh_line_arg, statusWord(f.status), whenText, peopleLabel(f.confirmations)),
                style = geist(12.sp, FontWeight.SemiBold),
                color = if (open) FarmsyColors.positive else FarmsyColors.critical,
            )
        }
        if (found.isNotEmpty()) {
            Text(stringResource(R.string.on_the_shelf_arg, found.joinToString(", ")), style = geist(12.sp), color = FarmsyColors.inkMuted)
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
