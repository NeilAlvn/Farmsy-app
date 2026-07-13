package app.farmsy.android.features.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import app.farmsy.android.ui.theme.FitText

/// Saved farms — mirrors iOS SavedScreen (guest state, empty state, list).
@Composable
fun SavedScreen(onOpenFarm: (FarmPin) -> Unit) {
    val session = LocalSession.current
    val farms = LocalFarms.current
    val favorites = LocalFavorites.current
    val location = LocalLocationHelper.current
    val requestAuth = LocalRequestAuth.current

    val currentSession by session.session.collectAsState()
    val pins by farms.pins.collectAsState()
    val savedIds by favorites.osmIds.collectAsState()
    val loc by location.location.collectAsState()

    val saved = farms.sortedByDistance(pins.filter { savedIds.contains(it.osmId) }, loc)

    Column(
        Modifier.fillMaxSize().background(FarmsyColors.cream),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            currentSession == null -> CenteredMessage(
                emoji = "🤍",
                title = stringResource(R.string.keep_your_favourites),
                body = stringResource(R.string.sign_in_to_save_farms_and_find_them_here_on_every_device),
                buttonText = stringResource(R.string.sign_in),
                onButton = requestAuth
            )
            saved.isEmpty() -> CenteredMessage(
                emoji = "🤍",
                title = stringResource(R.string.no_saved_farms_yet),
                body = stringResource(R.string.tap_the_heart_on_any_farm_to_keep_it_here),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(saved) { pin -> SavedCard(pin) { onOpenFarm(pin) } }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    emoji: String, title: String, body: String,
    buttonText: String? = null, onButton: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 54.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = display(24.sp), color = FarmsyColors.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = geist(15.sp), color = FarmsyColors.inkMuted, textAlign = TextAlign.Center)
        if (buttonText != null && onButton != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(buttonText, onClick = onButton)
        }
    }
}

@Composable
private fun SavedCard(pin: FarmPin, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(18.dp))
            .clickable { onOpen() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // Farm names/addresses are arbitrary length and sit beside a chevron:
            // shrink to fit rather than wrap and reflow the whole row.
            FitText(pin.name, style = geist(18.sp, FontWeight.Bold), color = FarmsyColors.ink)
            (pin.city ?: pin.address)?.let {
                FitText(it, style = geist(14.sp), color = FarmsyColors.inkMuted)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FarmsyColors.inkMuted)
    }
}
