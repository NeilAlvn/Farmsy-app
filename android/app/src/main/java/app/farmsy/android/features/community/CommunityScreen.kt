package app.farmsy.android.features.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farmsy.android.LocalFarms
import app.farmsy.android.R
import app.farmsy.android.core.FarmContentApi
import app.farmsy.android.core.Ping
import app.farmsy.android.features.detail.ImageLightbox
import app.farmsy.android.features.detail.LightboxSource
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.PingCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import kotlinx.coroutines.launch

/// Community — "Near you": what people posted at farms, newest first. Reports
/// and confirmations join this feed in a later phase.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen() {
    val farms = LocalFarms.current
    val shell = LocalShell.current
    val scope = rememberCoroutineScope()

    var pings by remember { mutableStateOf<List<Ping>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var lightbox by remember { mutableStateOf<LightboxSource?>(null) }
    val fromAPost = stringResource(R.string.lightbox_from_a_post)

    suspend fun load() {
        pings = FarmContentApi.feedPosts(limit = 30)
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.community))
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
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
                    pings.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.Forum, stringResource(R.string.nothing_posted_yet), stringResource(R.string.visit_farm_tell),
                            stringResource(R.string.open_the_map) to { shell.showTab(AppTab.MAP) },
                        )
                    }
                    else -> items(pings, key = { it.id }) { ping ->
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
                }
            }
        }
    }

    lightbox?.let { src -> ImageLightbox(source = src, onClose = { lightbox = null }) }
}
