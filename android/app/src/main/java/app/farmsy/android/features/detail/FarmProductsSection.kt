package app.farmsy.android.features.detail

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalSession
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.Backend
import app.farmsy.android.core.FarmDetail
import app.farmsy.android.core.FarmPin
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.core.httpClient
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.IconButton
import app.farmsy.android.ui.theme.ListRow
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.RowGroup
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.ui
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/// What this farm sells, as tappable chips, and the way into the Shopping tab.
/// Mirrors iOS FarmProductsSection.
///
/// The chips are the list items whose served terms appear in the farm's produce
/// text (curated first, inferred as a fallback, same rule as the web). Tapping
/// one adds it to — or removes it from — the shopping list. Inferred produce is
/// labelled, because a language model read it out of the description and nobody
/// confirmed it.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FarmProductsSection(pin: FarmPin, detail: FarmDetail?, onClose: () -> Unit) {
    val farms = LocalFarms.current
    val trip = LocalTrip.current
    val shell = LocalShell.current
    val context = LocalContext.current
    val catalogue by ShoppingItems.items.collectAsState()
    val wanted by trip.wantedProducts.collectAsState()
    // produceByOsm is a plain map; this flag is what tells us it has arrived.
    val flagsLoaded by farms.flagsLoaded.collectAsState()
    val language = remember { ShoppingItems.language(context) }

    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded(); farms.loadFlagsIfNeeded() }

    val sells = detail?.displayProduce ?: if (flagsLoaded) farms.produceByOsm[pin.osmId] else null
    val inferred = detail?.produce.isNullOrBlank() && detail?.produceInferred != null
    val items = remember(sells, catalogue) {
        if (sells == null) emptyList() else catalogue.filter { ProductMatch.covers(sells, it.terms) }
    }
    if (items.isEmpty()) return
    val allOnList = items.all { it.id in wanted }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Text(stringResource(R.string.products), style = ui(17.sp, FontWeight.Bold), color = FarmsyColors.ink)
            if (inferred) Text(stringResource(R.string.products_likely), style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
            Spacer(Modifier.weight(1f))
            PillButton(
                stringResource(if (allOnList) R.string.on_your_list else R.string.add_all_to_shopping),
                PillVariant.TEXT, PillSize.SMALL,
            ) {
                // Close the card before switching tabs: it is a sheet over the
                // map, so switching underneath it left the Shopping tab hidden
                // behind an open farm card (final review #7, iOS twin).
                if (allOnList) { onClose(); shell.showTab(AppTab.SHOPPING) }
                else items.filter { it.id !in wanted }.forEach { trip.toggleProduct(it.id) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            items.forEach { item ->
                Chip(item.label(language), emoji = item.emoji, selected = item.id in wanted) { trip.toggleProduct(item.id) }
            }
        }
        Text(stringResource(R.string.products_tap_hint), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
    }
}

/// "Report incorrect information": six reasons and an optional note, sent as a
/// contact message so it lands where Neil already reads (contact_submissions,
/// the activity log, the admin inbox email). No new table until the volume
/// justifies one. Mirrors iOS ReportInfoSheet.
private enum class ReportReason(val id: String, val labelRes: Int) {
    CLOSED("closed", R.string.report_reason_closed),
    HOURS("hours", R.string.report_reason_hours),
    PRODUCTS("products", R.string.report_reason_products),
    LOCATION("location", R.string.report_reason_location),
    DUPLICATE("duplicate", R.string.report_reason_duplicate),
    OTHER("other", R.string.report_reason_other),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportInfoSheet(pin: FarmPin, onDismiss: () -> Unit) {
    val session = LocalSession.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    fun send() {
        val r = reason ?: return
        scope.launch {
            sending = true; failed = false
            val body = buildJsonObject {
                put("name", session.displayName)
                put("email", session.email.ifEmpty { "anonymous@farmsy.app" })
                put("topic", "Farm correction: ${r.id}")
                put("message", "${pin.name} (osm ${pin.osmId}, ${pin.city ?: "-"})\nReason: ${context.getString(r.labelRes)}\n$note")
                put("source", "android_app")
            }
            val ok = runCatching {
                httpClient.post("${Backend.WEB_API}/contact") {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }.status.value in 200..299
            }.getOrDefault(false)
            sending = false
            if (ok) {
                if (app.farmsy.android.ui.theme.Haptics.enabled) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
                sent = true
            } else failed = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FarmsyColors.cream,
    ) {
        ScreenHeader(stringResource(R.string.report_incorrect_info), compact = true) {
            IconButton(Icons.Filled.Close, label = stringResource(R.string.close), small = true, onClick = onDismiss)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            if (sent) {
                EmptyState(
                    Icons.Outlined.CheckCircle, stringResource(R.string.thank_you), stringResource(R.string.report_thank_you_body),
                    action = stringResource(R.string.done) to onDismiss,
                )
            } else {
                Text(pin.name, style = role(TextRole.HEADING), color = FarmsyColors.ink)
                Text(stringResource(R.string.report_what_is_wrong), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                RowGroup {
                    ReportReason.entries.forEach { r ->
                        ListRow(
                            icon = if (reason == r) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            title = stringResource(r.labelRes), chevron = false,
                        ) { reason = r }
                    }
                }
                val shape = RoundedCornerShape(Radius.input)
                BasicTextField(
                    value = note, onValueChange = { note = it }, minLines = 3, maxLines = 6,
                    textStyle = ui(16.sp).copy(color = FarmsyColors.ink),
                    modifier = Modifier.fillMaxWidth().background(FarmsyColors.surface, shape).border(1.dp, FarmsyColors.hairline, shape).padding(Space.s4),
                    decorationBox = { inner ->
                        if (note.isEmpty()) Text(stringResource(R.string.report_note_placeholder), style = ui(16.sp), color = FarmsyColors.inkMuted)
                        inner()
                    },
                )
                if (failed) Text(stringResource(R.string.report_send_failed), style = role(TextRole.CAPTION), color = FarmsyColors.critical)
                PillButton(stringResource(R.string.report_send), PillVariant.PRIMARY, PillSize.LARGE, block = true, enabled = reason != null && !sending, onClick = ::send)
            }
        }
    }
}
