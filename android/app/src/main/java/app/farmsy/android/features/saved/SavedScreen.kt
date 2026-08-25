package app.farmsy.android.features.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.discover.RecommendationCarousel
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import kotlinx.coroutines.launch

/// Saved farms — 1:1 with iOS SavedScreen: an eyebrow header, then a numbered
/// slot list (filled green circle + name + city + X-remove for a saved farm,
/// dashed circle + "Tap a heart to save a farm" for the empty slots up to eight),
/// with the recommendation carousel below. Guest state invites sign-in.
@Composable
fun SavedScreen(onOpenFarm: (FarmPin) -> Unit) {
    val session = LocalSession.current
    val farms = LocalFarms.current
    val favorites = LocalFavorites.current
    val location = LocalLocationHelper.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()

    val currentSession by session.session.collectAsState()
    val pins by farms.pins.collectAsState()
    val savedIds by favorites.osmIds.collectAsState()
    val loc by location.location.collectAsState()

    val saved = farms.sortedByDistance(pins.filter { savedIds.contains(it.osmId) }, loc)

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(stringResource(R.string.saved_farms))
        }

        if (currentSession == null) {
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🤍", fontSize = 54.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.keep_your_favourites), style = display(24.sp), color = FarmsyColors.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.sign_in_to_save_farms_and_find_them_here_on_every_device),
                    style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(stringResource(R.string.sign_in), Modifier.padding(horizontal = 20.dp), onClick = requestAuth)
            }
        } else {
            val slots = maxOf(8, saved.size)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        .background(FarmsyColors.creamCard, RoundedCornerShape(16.dp))
                        .border(1.dp, FarmsyColors.hairline, RoundedCornerShape(16.dp)),
                ) {
                    for (i in 0 until slots) {
                        if (i < saved.size) {
                            SavedRow(i, saved[i], onOpen = { onOpenFarm(saved[i]) }, onRemove = {
                                val uid = currentSession?.user?.id ?: return@SavedRow
                                scope.launch { favorites.toggle(saved[i].osmId, uid) }
                            })
                        } else {
                            EmptyRow(i)
                        }
                        if (i < slots - 1) HorizontalDivider(Modifier.padding(start = 62.dp), color = FarmsyColors.hairline)
                    }
                }
                RecommendationCarousel(
                    onOpenFarm = onOpenFarm,
                    modifier = Modifier.padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SavedRow(index: Int, pin: FarmPin, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp).background(FarmsyColors.farmGreenMap, CircleShape), contentAlignment = Alignment.Center) {
            Text("${index + 1}", style = geist(13.sp, FontWeight.Bold), color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(pin.name, style = geist(15.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 1)
            pin.city?.let { Text(it, style = geist(12.sp), color = FarmsyColors.inkMuted, maxLines = 1) }
        }
        Icon(
            Icons.Filled.Close, null, tint = FarmsyColors.inkMuted,
            modifier = Modifier.size(32.dp).clickable { onRemove() }.padding(9.dp),
        )
    }
}

@Composable
private fun EmptyRow(index: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(30.dp).border(1.5.dp, FarmsyColors.inkMuted.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = geist(13.sp, FontWeight.Bold), color = FarmsyColors.inkMuted.copy(alpha = 0.6f))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tap_a_heart_to_save_a_farm), style = geist(15.sp), color = FarmsyColors.inkMuted)
            Text("—", style = geist(12.sp), color = FarmsyColors.inkMuted.copy(alpha = 0.5f))
        }
    }
}
