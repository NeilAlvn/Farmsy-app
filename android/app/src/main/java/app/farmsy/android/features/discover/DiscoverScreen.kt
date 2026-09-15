package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.R
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.MultiImageFarmCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.role
import kotlinx.coroutines.launch

/// Discover — inspiration, not utility. For now: the farms with a story and a
/// gallery, then the recommendation carousel. Seasons, "just arrived" and
/// "this weekend" land here in a later phase.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen() {
    val farms = LocalFarms.current
    val shell = LocalShell.current
    val galleries by farms.galleries.collectAsState()
    val galleriesLoaded by farms.galleriesLoaded.collectAsState()
    val featuredTeasers by farms.featuredTeasers.collectAsState()
    val pins by farms.pins.collectAsState()

    val locationHelper = LocalLocationHelper.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location by locationHelper.location.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val language = remember { ShoppingItems.language(context) }
    val seasonItems by Seasons.items.collectAsState()
    val seasonMonth by Seasons.month.collectAsState()
    val inSeason = remember(seasonItems, seasonMonth) { Seasons.thisMonth(seasonItems, seasonMonth) }

    LaunchedEffect(Unit) { farms.loadGalleriesIfNeeded() }
    LaunchedEffect(Unit) { Seasons.loadIfNeeded() }

    // The store's frozen once-shuffled order, resolved to pins, capped at 10.
    val featured = remember(galleriesLoaded, pins) {
        if (galleriesLoaded) farms.featuredFarms.take(10) else emptyList()
    }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()) {
        ScreenHeader(stringResource(R.string.discover))
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
            contentPadding = PaddingValues(bottom = TabBarInset.content),
        ) {
            if (inSeason.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader(stringResource(R.string.in_season_near_you), top = 0.dp)
                        Text(stringResource(R.string.in_season_sub), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                        FlowRow(
                            Modifier.padding(top = Space.s2, bottom = Space.s2),
                            horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            inSeason.forEach { item ->
                                Chip(item.label(language), emoji = item.emoji, dot = if (item.isPeak(seasonMonth)) FarmsyColors.vivid else null) {
                                    scope.launch {
                                        farms.showProduct(item.label(language), item.terms, location, radiusKm)
                                        shell.showTab(AppTab.MAP)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.farms_with_a_story), top = if (inSeason.isEmpty()) 0.dp else Space.s4) }
            if (!galleriesLoaded) {
                items(3) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(180.dp)) }
            } else {
                items(featured, key = { it.osmId }) { pin ->
                    MultiImageFarmCard(
                        pin = pin,
                        images = galleries[pin.osmId] ?: emptyList(),
                        teaser = featuredTeasers[pin.osmId],
                        onOpen = { shell.openFarm(pin) },
                    )
                }
            }
            item { RecommendationCarousel(onOpenFarm = shell.openFarm, modifier = Modifier.padding(top = Space.s4), cardHeight = 156.dp) }
        }
    }
}
