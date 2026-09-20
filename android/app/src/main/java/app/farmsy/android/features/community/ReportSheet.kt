package app.farmsy.android.features.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.FarmStatusApi
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.ReportStatus
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SearchField
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import kotlinx.coroutines.launch

/// Report without opening a farm card first: pick a farm near you, tap what
/// you found, tag what was on the shelf. Same row, same one-per-person-per-day
/// rule as the card's own section; this is only a shorter way in.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(onDismiss: () -> Unit, onSent: suspend () -> Unit) {
    val farms = LocalFarms.current
    val locationHelper = LocalLocationHelper.current
    val session = LocalSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pins by farms.pins.collectAsState()
    val location by locationHelper.location.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val language = remember { ShoppingItems.language(context) }

    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<FarmPin?>(null) }
    var status by remember { mutableStateOf<ReportStatus?>(null) }
    var products by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { farms.loadFlagsIfNeeded(); ShoppingItems.loadIfNeeded() }

    val nearby = remember(pins, query, location) {
        val q = ProductMatch.fold(query)
        val pool = if (q.isEmpty()) pins else pins.filter {
            ProductMatch.fold(it.name).contains(q) || ProductMatch.fold(it.city ?: "").contains(q)
        }
        farms.sortedByDistance(pool, location).take(8)
    }
    // The farm's own products, as chips. Falls back to the whole picker for a
    // farm that never said what it sells — the visitor is telling us.
    val items = remember(catalogue, chosen) {
        val sells = chosen?.let { farms.produceByOsm[it.osmId] }
        val own = if (sells.isNullOrEmpty()) emptyList() else catalogue.filter { ProductMatch.covers(sells, it.terms) }
        own.ifEmpty { catalogue.take(12) }
    }

    fun send() {
        val farm = chosen ?: return
        val s = status ?: return
        scope.launch {
            val token = session.accessToken() ?: return@launch
            sending = true
            if (FarmStatusApi.report(farm.osmId, s, products.toList(), token)) {
                sent = true
                onSent()
            }
            sending = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FarmsyColors.cream,
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.what_did_you_see), compact = true, onBack = onDismiss)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = Space.s4, end = Space.s4, bottom = Space.s8),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                val farm = chosen
                when {
                    sent -> EmptyState(
                        Icons.Filled.CheckCircle, stringResource(R.string.thank_you), stringResource(R.string.report_sent_body),
                        stringResource(R.string.done) to onDismiss,
                    )
                    farm != null -> {
                        FarmPickRow(farm, location?.let { farm.distanceMeters(it.latitude, it.longitude) / 1000 }, selected = true) {
                            chosen = null; status = null; products = emptySet()
                        }
                        Label(stringResource(R.string.status_ask))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                            StatusChip(ReportStatus.OPEN, R.string.status_open, FarmsyColors.vividPositive, status) { status = it }
                            StatusChip(ReportStatus.CLOSED, R.string.status_closed, FarmsyColors.vividCritical, status) { status = it }
                            StatusChip(ReportStatus.SOLD_OUT, R.string.status_sold_out, FarmsyColors.vividWarning, status) { status = it }
                        }
                        if (status == ReportStatus.OPEN || status == ReportStatus.SOLD_OUT) {
                            Label(stringResource(if (status == ReportStatus.SOLD_OUT) R.string.status_what_was_still_there else R.string.status_what_did_you_find))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                                items.forEach { item ->
                                    Chip(item.label(language), emoji = item.emoji, selected = item.id in products) {
                                        products = if (item.id in products) products - item.id else products + item.id
                                    }
                                }
                            }
                        }
                        PillButton(
                            stringResource(R.string.report_send), PillVariant.PRIMARY, PillSize.LARGE, block = true,
                            enabled = status != null && !sending, modifier = Modifier.padding(top = Space.s3), onClick = ::send,
                        )
                    }
                    else -> {
                        Label(stringResource(R.string.which_farm))
                        SearchField(query, { query = it }, stringResource(R.string.farms_search_placeholder))
                        if (location == null && query.isEmpty()) {
                            Text(stringResource(R.string.allow_location_or_search), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
                        }
                        nearby.forEach { pin ->
                            FarmPickRow(pin, location?.let { pin.distanceMeters(it.latitude, it.longitude) / 1000 }, selected = false) { chosen = pin }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = role(TextRole.LABEL), color = FarmsyColors.inkFaint, modifier = Modifier.padding(top = Space.s2))
}

@Composable
private fun StatusChip(s: ReportStatus, labelRes: Int, dot: androidx.compose.ui.graphics.Color, current: ReportStatus?, onPick: (ReportStatus) -> Unit) {
    Chip(stringResource(labelRes), selected = current == s, dot = if (current == s) null else dot) { onPick(s) }
}

@Composable
private fun FarmPickRow(pin: FarmPin, km: Double?, selected: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) FarmsyColors.farmGreenSoft else FarmsyColors.surface, CardShape)
            .tapCard(onTap = onTap).padding(Space.s3),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(FarmsyColors.creamFill, CircleShape), contentAlignment = Alignment.Center) {
            Text(pin.primaryCategory.emoji, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pin.name, style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(pin.city, km?.let { "%.1f km".format(it) }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
        }
        Icon(
            if (selected) Icons.Filled.Close else Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = FarmsyColors.inkMuted, modifier = Modifier.size(18.dp),
        )
    }
}
