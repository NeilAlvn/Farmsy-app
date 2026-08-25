package app.farmsy.android.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.discover.DiscoverFeedScreen
import app.farmsy.android.features.map.MapScreen
import app.farmsy.android.features.saved.SavedScreen
import app.farmsy.android.features.settings.SettingsScreen
import app.farmsy.android.features.trips.TripsScreen
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.geist

/// Map-first shell — a 1:1 port of iOS MainView: "the map is the app". There is no
/// tab bar; the map is always the base, and Discover / Saved / Trips / Settings
/// open as bottom sheets over it from a floating white pill. Saved / Trips /
/// Settings ask for an account first; Discover and farm cards are open to everyone.
private enum class PanelRoute { DISCOVER, SAVED, TRIPS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onOpenFarm: (FarmPin) -> Unit) {
    val session = LocalSession.current
    var route by remember { mutableStateOf<PanelRoute?>(null) }
    var showAuthForRoute by remember { mutableStateOf(false) }

    fun requireAuth(then: PanelRoute) {
        if (session.isAuthenticated) route = then else showAuthForRoute = true
    }

    Box(Modifier.fillMaxSize()) {
        MapScreen(onOpenFarm = onOpenFarm, bottomInset = 104.dp)

        // Floating bottom pill: Discover / Saved / Trips / Settings.
        Surface(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = CircleShape, color = Color.White, shadowElevation = 12.dp,
        ) {
            Row(Modifier.padding(6.dp)) {
                PanelItem(Icons.Filled.Newspaper, stringResource(R.string.discover), Modifier.weight(1f)) { route = PanelRoute.DISCOVER }
                PanelItem(Icons.Filled.Favorite, stringResource(R.string.saved), Modifier.weight(1f)) { requireAuth(PanelRoute.SAVED) }
                PanelItem(Icons.Filled.Map, stringResource(R.string.trips), Modifier.weight(1f)) { requireAuth(PanelRoute.TRIPS) }
                PanelItem(Icons.Filled.Settings, stringResource(R.string.settings), Modifier.weight(1f)) { requireAuth(PanelRoute.SETTINGS) }
            }
        }
    }

    // The secondary surfaces, as bottom sheets over the map — matching iOS's
    // sheet presentation (partial + full detents).
    route?.let { r ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { route = null },
            sheetState = sheetState,
            containerColor = FarmsyColors.cream,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                when (r) {
                    PanelRoute.DISCOVER -> DiscoverFeedScreen(onOpenFarm = { route = null; onOpenFarm(it) })
                    PanelRoute.SAVED -> SavedScreen(onOpenFarm = { route = null; onOpenFarm(it) })
                    PanelRoute.TRIPS -> TripsScreen(onOpenFarm = { route = null; onOpenFarm(it) })
                    PanelRoute.SETTINGS -> SettingsScreen()
                }
            }
        }
    }

    if (showAuthForRoute) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAuthForRoute = false },
            sheetState = sheetState,
            containerColor = FarmsyColors.cream,
        ) {
            app.farmsy.android.features.auth.AuthSheet(onDone = { showAuthForRoute = false })
        }
    }
}

@Composable
private fun PanelItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = FarmsyColors.farmGreenMap, modifier = Modifier.padding(0.dp))
        Text(label, style = geist(10.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreenMap)
    }
}
