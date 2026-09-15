package app.farmsy.android.features.discover

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farmsy.android.LocalFarms
import app.farmsy.android.R
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.MultiImageFarmCard
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TabBarInset

/// Discover — inspiration, not utility. For now: the farms with a story and a
/// gallery, then the recommendation carousel. Seasons, "just arrived" and
/// "this weekend" land here in a later phase.
@Composable
fun DiscoverScreen() {
    val farms = LocalFarms.current
    val shell = LocalShell.current
    val galleries by farms.galleries.collectAsState()
    val galleriesLoaded by farms.galleriesLoaded.collectAsState()
    val featuredTeasers by farms.featuredTeasers.collectAsState()
    val pins by farms.pins.collectAsState()

    LaunchedEffect(Unit) { farms.loadGalleriesIfNeeded() }

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
            item { SectionHeader(stringResource(R.string.farms_with_a_story), top = 0.dp) }
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
