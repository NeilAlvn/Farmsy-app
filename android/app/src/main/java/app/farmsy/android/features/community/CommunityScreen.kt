package app.farmsy.android.features.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmContentApi
import app.farmsy.android.core.FarmStatus
import app.farmsy.android.core.FarmStatusApi
import app.farmsy.android.core.Ping
import app.farmsy.android.core.RecentReports
import app.farmsy.android.core.ReportStatus
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.detail.ImageLightbox
import app.farmsy.android.features.detail.LightboxSource
import app.farmsy.android.features.detail.peopleLabel
import app.farmsy.android.features.detail.statusWord
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.PingCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.role
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/// One card per farm + status + Amsterdam day.
data class ReportGroup(
    val farmOsmId: String,
    val status: ReportStatus,
    val day: String,
    val newest: Instant,
    val count: Int,
    val products: List<String>,
) {
    val id: String get() = "$farmOsmId|${status.wire}|$day"
}

/// A row of the feed: a post or a report group, sorted together by time.
private sealed class Entry(val id: String, val at: Instant) {
    class Post(val ping: Ping) : Entry("p" + ping.id, FarmStatus.parseTimestamp(ping.createdAt) ?: Instant.MIN)
    class Report(val group: ReportGroup) : Entry("r" + group.id, group.newest)
}

/// Community — "Near you": what people reported and posted at farms around
/// you, newest first. A report card is one farm, one status, one day, with
/// how many people said so and what they found; "Confirm" adds your voice
/// with one tap. Plus sees minutes; free sees the day.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen() {
    val farms = LocalFarms.current
    val session = LocalSession.current
    val locationHelper = LocalLocationHelper.current
    val shell = LocalShell.current
    val requestAuth = LocalRequestAuth.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pings by remember { mutableStateOf<List<Ping>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var lightbox by remember { mutableStateOf<LightboxSource?>(null) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val fromAPost = stringResource(R.string.lightbox_from_a_post)

    val pins by farms.pins.collectAsState()
    val location by locationHelper.location.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val recentReports by RecentReports.reports.collectAsState()
    val items by ShoppingItems.items.collectAsState()
    val language = remember { ShoppingItems.language(context) }
    session.profile.collectAsState().value
    val plus = session.hasFullAccess

    suspend fun load(force: Boolean = false) {
        RecentReports.refresh(force)
        ShoppingItems.loadIfNeeded()
        pings = FarmContentApi.feedPosts(limit = 30)
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    // Everywhere when the phone has no location; the radius, widened to at
    // least 25 km, when it has — a report feed with nothing in it teaches
    // nobody to report.
    val feed = remember(pings, recentReports, location, radiusKm, pins) {
        val near = RecentReports.near(location, maxOf(radiusKm, 25.0), pins.associateBy { it.osmId })
        val groups = near.mapNotNull { r ->
            val at = r.report.instant ?: return@mapNotNull null
            val status = r.report.reportStatus ?: return@mapNotNull null
            Triple(r, at, status)
        }.groupBy { (r, at, status) -> "${r.farmOsmId}|${status.wire}|${FarmStatus.amsterdamDay(at)}" }
            .values.map { rs ->
                val (r, at, status) = rs[0]
                ReportGroup(
                    farmOsmId = r.farmOsmId, status = status, day = FarmStatus.amsterdamDay(at),
                    newest = rs.maxOf { it.second }, count = rs.size,
                    products = rs.sortedByDescending { it.second }.flatMap { it.first.products }.distinct(),
                )
            }
        (pings.map { Entry.Post(it) } + groups.map { Entry.Report(it) }).sortedByDescending { it.at }
    }

    // One tap agrees: the same status, on the same farm, from you. The server's
    // one-per-person-per-day rule makes a second tap a correction, not spam.
    fun confirm(g: ReportGroup) {
        scope.launch {
            val token = session.accessToken()
            if (token == null) { requestAuth(); return@launch }
            if (FarmStatusApi.report(g.farmOsmId, g.status, token = token)) {
                confirming = g.id
                RecentReports.refresh(force = true)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.community))
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(force = true); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Space.s4),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
                contentPadding = PaddingValues(bottom = TabBarInset.content),
            ) {
                item { SectionHeader(stringResource(R.string.community_near_you), top = 0.dp) }
                when {
                    loading -> items(3) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(150.dp)) }
                    feed.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.Forum, stringResource(R.string.nothing_posted_yet), stringResource(R.string.visit_farm_tell),
                            stringResource(R.string.open_the_map) to { shell.showTab(AppTab.MAP) },
                        )
                    }
                    else -> items(feed, key = { it.id }) { entry ->
                        when (entry) {
                            is Entry.Post -> {
                                val ping = entry.ping
                                val farm = farms.pinForOsmId(ping.farmOsmId)
                                PingCard(
                                    ping = ping,
                                    farmName = farm?.name,
                                    onOpenFarm = { farm?.let { shell.openFarm(it) } },
                                    onOpenImage = { idx ->
                                        lightbox = LightboxSource(
                                            images = ping.images, startIndex = idx, eyebrow = fromAPost,
                                            title = ping.authorName, subtitle = farm?.name, postText = ping.body,
                                        )
                                    },
                                )
                            }
                            is Entry.Report -> {
                                val g = entry.group
                                val farm = farms.pinForOsmId(g.farmOsmId)
                                ReportCard(
                                    g, farmName = farm?.name, plus = plus,
                                    found = g.products.mapNotNull { id -> items.firstOrNull { it.id == id }?.label(language) },
                                    confirmed = confirming == g.id,
                                    onOpenFarm = { farm?.let { shell.openFarm(it) } },
                                    onConfirm = { confirm(g) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    lightbox?.let { src -> ImageLightbox(source = src, onClose = { lightbox = null }) }
}

@Composable
private fun ReportCard(
    g: ReportGroup,
    farmName: String?,
    plus: Boolean,
    found: List<String>,
    confirmed: Boolean,
    onOpenFarm: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dot = when (g.status) {
        ReportStatus.OPEN -> FarmsyColors.vividPositive
        ReportStatus.SOLD_OUT -> FarmsyColors.vividWarning
        ReportStatus.CLOSED -> FarmsyColors.vividCritical
    }
    // Plus: minutes and hours. Free: the day, which the summary already gives.
    val mins = maxOf(0L, Duration.between(g.newest, Instant.now()).toMinutes()).toInt()
    val days = mins / 1440
    val whenText = when {
        plus && mins < 60 -> stringResource(R.string.min_ago_arg, mins)
        plus && mins < 60 * 36 -> stringResource(R.string.h_ago_arg, mins / 60)
        days == 0 -> stringResource(R.string.report_today)
        days == 1 -> stringResource(R.string.report_yesterday)
        else -> stringResource(R.string.report_days_ago_arg, days)
    }
    Column(Modifier.fillMaxWidth().card(), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Text(statusWord(g.status), style = role(TextRole.SUBHEADING), color = FarmsyColors.ink)
            Text("·", style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
            Text(whenText, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(peopleLabel(g.count), style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
        }
        Text(
            farmName ?: g.farmOsmId, style = role(TextRole.BODY), color = FarmsyColors.farmGreen,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onOpenFarm),
        )
        if (found.isNotEmpty()) {
            Text(stringResource(R.string.on_the_shelf_arg, found.joinToString(", ")), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.visitor_report), style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
            Spacer(Modifier.weight(1f))
            PillButton(
                stringResource(if (confirmed) R.string.confirmed else R.string.confirm),
                PillVariant.SOFT, PillSize.SMALL, enabled = !confirmed, onClick = onConfirm,
            )
        }
    }
}
