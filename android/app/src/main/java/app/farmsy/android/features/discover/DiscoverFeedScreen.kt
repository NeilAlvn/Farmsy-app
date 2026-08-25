package app.farmsy.android.features.discover

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalRequestAuth
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.submit.AddFarmSheet
import app.farmsy.android.ui.theme.DisplayTitle
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.Kicker
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import app.farmsy.android.ui.theme.FitText
import androidx.compose.ui.text.style.TextOverflow

/// Discover tab — mirrors iOS DiscoverFeedView: a scrolling feed of randomly
/// picked farms that all have a photo. Save-heart and add-farm require login.
@Composable
fun DiscoverFeedScreen(onOpenFarm: (FarmPin) -> Unit) {
    val farms = LocalFarms.current
    val location = LocalLocationHelper.current
    val pins by farms.pins.collectAsState()
    val loc by location.location.collectAsState()
    var showAddFarm by remember { mutableStateOf(false) }

    var feed by remember { mutableStateOf<List<FarmPin>>(emptyList()) }
    LaunchedEffect(pins.size) {
        if (feed.isEmpty() && pins.isNotEmpty()) feed = farms.feedPicks(loc)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(FarmsyColors.cream).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(top = 6.dp)) {
                Kicker(stringResource(R.string.discover))
                DisplayTitle(
                    leading = stringResource(R.string.ob_farms_worth_a) + " ",
                    emphasis = stringResource(R.string.ob_detour),
                    size = 30.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
            }
        }
        item { RecommendationCarousel(onOpenFarm = onOpenFarm) }
        item { AddFarmBanner { showAddFarm = true } }
        items(feed) { pin -> DiscoverCard(pin = pin, onOpen = { onOpenFarm(pin) }) }
    }

    if (showAddFarm) AddFarmSheet(onDismiss = { showAddFarm = false })
}

@Composable
private fun AddFarmBanner(onClick: () -> Unit) {
    val session = LocalSession.current
    val requestAuth = LocalRequestAuth.current
    Row(
        Modifier.fillMaxWidth()
            .background(FarmsyColors.farmGreenSoft, RoundedCornerShape(18.dp))
            .clickable { if (session.isAuthenticated) onClick() else requestAuth() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AddCircle, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            // Banner copy squeezed between an icon and the card edge — shrink, don't wrap.
            FitText(
                stringResource(R.string.know_a_farm_shop_we_re_missing),
                style = geist(15.sp, FontWeight.Bold), color = FarmsyColors.ink
            )
            FitText(
                stringResource(R.string.add_it_to_the_map_for_everyone),
                style = geist(13.sp), color = FarmsyColors.inkMuted
            )
        }
    }
}

@Composable
private fun DiscoverCard(pin: FarmPin, onOpen: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val location = LocalLocationHelper.current
    val requestAuth = LocalRequestAuth.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()
    val loc by location.location.collectAsState()

    val distance = loc?.let { l ->
        val m = pin.distanceMeters(l.latitude, l.longitude)
        if (m < 1000) "${m.toInt()} m" else String.format("%.1f km", m / 1000)
    }

    Column(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable { onOpen() }
    ) {
        Box {
            if (pin.image != null) {
                AsyncImage(
                    model = pin.image, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(195.dp)
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(195.dp)
                        .background(pin.primaryCategory.color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Text(pin.primaryCategory.emoji, fontSize = 48.sp) }
            }
            // Save heart (guest → login)
            val isSaved = savedIds.contains(pin.osmId)
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(38.dp)
                    .background(Color.White.copy(alpha = 0.95f), CircleShape)
                    .clickable {
                        val uid = session.session.value?.user?.id
                        if (uid == null) requestAuth() else scope.launch { favorites.toggle(pin.osmId, uid) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                    tint = if (isSaved) FarmsyColors.warnRed else FarmsyColors.ink,
                    modifier = Modifier.size(18.dp)
                )
            }
            // Category chips overlaid on the image. Two, not three, and pinned to a
            // single line each: three full labels ("🥬 Farm Produce" etc.) could run
            // off the right edge of the card and get clipped mid-word by the rounded
            // corner. Kept to what reliably fits.
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                pin.categories.take(2).forEach { cat ->
                    Text(
                        "${cat.emoji} ${stringResource(cat.labelRes)}",
                        style = geist(11.sp, FontWeight.SemiBold), color = FarmsyColors.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.94f), CircleShape)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }

        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pin.name, style = display(22.sp, FontWeight.SemiBold), color = FarmsyColors.ink, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pin.city?.let { Text(it, style = geist(14.sp), color = FarmsyColors.inkMuted) }
                if (distance != null) {
                    Text("·", color = FarmsyColors.inkMuted)
                    Text(distance, style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen)
                }
                pin.avgRating?.let { r ->
                    Text("·", color = FarmsyColors.inkMuted)
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFEAB308), modifier = Modifier.size(11.dp))
                    Text(String.format("%.1f (%d)", r, pin.reviewCount), style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Directions (outline)
                Row(
                    Modifier.weight(1f)
                        .background(Color.White, CircleShape)
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${pin.name})"))
                            )
                        }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.directions), style = geist(14.sp, FontWeight.Bold), color = FarmsyColors.farmGreen)
                }
                // View farm (filled)
                Row(
                    Modifier.weight(1f)
                        .background(FarmsyColors.farmGreen, CircleShape)
                        .clickable { onOpen() }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.view_farm), style = geist(14.sp, FontWeight.Bold), color = Color.White)
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
