package app.farmsy.android.features.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.discover.DiscoverFeedScreen
import app.farmsy.android.features.map.MapScreen
import app.farmsy.android.features.saved.SavedScreen
import app.farmsy.android.features.settings.SettingsScreen
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist

/// Main shell — mirrors iOS MainView: brand header up top (hidden on the
/// full-bleed Map tab), content in the middle, floating pill tab bar.
enum class Tab(val labelRes: Int, val icon: ImageVector) {
    MAP(R.string.map, Icons.Filled.Map),
    DISCOVER(R.string.discover, Icons.Filled.AutoAwesome),
    SAVED(R.string.saved, Icons.Filled.Favorite),
    SETTINGS(R.string.settings, Icons.Filled.Settings),
}

@Composable
fun MainScreen(onOpenFarm: (FarmPin) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }

    if (tab == Tab.MAP) {
        // Map is the hero: full-bleed edge to edge, tab bar floating on top.
        Box(Modifier.fillMaxSize()) {
            MapScreen(onOpenFarm = onOpenFarm)
            TabBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
    } else {
        Column(
            Modifier.fillMaxSize().background(FarmsyColors.cream).statusBarsPadding()
        ) {
            // Brand header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Image(
                    painterResource(R.drawable.farmsy_logo),
                    contentDescription = null,
                    modifier = Modifier.height(34.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Farmsy", style = display(22.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.MAP -> Unit
                    Tab.DISCOVER -> DiscoverFeedScreen(onOpenFarm = onOpenFarm)
                    Tab.SAVED -> SavedScreen(onOpenFarm = onOpenFarm)
                    Tab.SETTINGS -> SettingsScreen()
                }
            }

            TabBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(5.dp)) {
            Tab.entries.forEach { t ->
                val isOn = t == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isOn) FarmsyColors.farmGreen else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(t) }
                        .padding(vertical = 9.dp)
                ) {
                    Icon(
                        t.icon,
                        contentDescription = stringResource(t.labelRes),
                        tint = if (isOn) Color.White else FarmsyColors.inkMuted,
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        stringResource(t.labelRes),
                        style = geist(11.sp, FontWeight.SemiBold),
                        color = if (isOn) Color.White else FarmsyColors.inkMuted
                    )
                }
            }
        }
    }
}
