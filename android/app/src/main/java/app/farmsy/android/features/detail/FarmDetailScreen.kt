package app.farmsy.android.features.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFavorites
import app.farmsy.android.LocalSession
import app.farmsy.android.R
import app.farmsy.android.core.FarmDetail
import app.farmsy.android.core.FarmDetailApi
import app.farmsy.android.core.FarmDetailException
import app.farmsy.android.core.FarmPin
import app.farmsy.android.features.claim.ClaimSheet
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PrimaryButton
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.display
import app.farmsy.android.ui.theme.geist
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/// Farm detail — mirrors iOS FarmDetailView. The full payload only exists
/// behind the farmsy.app API's subscription check; without access we show the
/// locked state. No purchase button by design — membership is on the web.
@Composable
fun FarmDetailScreen(pin: FarmPin, onBack: () -> Unit) {
    val context = LocalContext.current
    val session = LocalSession.current
    val favorites = LocalFavorites.current
    val scope = rememberCoroutineScope()
    val savedIds by favorites.osmIds.collectAsState()

    var detail by remember { mutableStateOf<FarmDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showClaim by remember { mutableStateOf(false) }

    suspend fun reload() {
        isLoading = true; isLocked = false
        val token = session.accessToken()
        if (token == null) { isLocked = true; isLoading = false; return }
        try {
            detail = FarmDetailApi.fetch(pin.osmId, token)
        } catch (e: FarmDetailException.Locked) {
            isLocked = true
        } catch (e: Exception) {
            if (!session.hasFullAccess) isLocked = true
        }
        isLoading = false
    }

    LaunchedEffect(pin.osmId) { session.refreshProfile(); reload() }

    Box(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        if (isLocked) {
            LockedAccessView(pin = pin, onClaim = { showClaim = true }) { reload() }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Gallery
                val urls = (detail?.images?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(detail?.image ?: pin.image))
                if (urls.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(210.dp).background(FarmsyColors.farmGreen),
                        contentAlignment = Alignment.Center
                    ) { Text(pin.primaryCategory.emoji, fontSize = 64.sp) }
                } else {
                    AsyncImage(
                        model = urls.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }

                Column(Modifier.padding(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pin.categories.take(4).forEach { cat ->
                            Text(
                                "${cat.emoji} ${stringResource(cat.labelRes)}",
                                style = geist(12.sp, FontWeight.SemiBold), color = FarmsyColors.ink,
                                modifier = Modifier.background(cat.color.copy(alpha = 0.14f), CircleShape)
                                    .padding(vertical = 5.dp, horizontal = 9.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(pin.name, style = display(30.sp), color = FarmsyColors.ink)
                    pin.city?.let {
                        Text(it, style = geist(15.sp), color = FarmsyColors.inkMuted)
                    }
                    Spacer(Modifier.height(16.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val phone = detail?.phone ?: pin.phone
                        if (phone != null) ActionButton(stringResource(R.string.call), Color(0xFF2563EB), Modifier.weight(1f)) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }
                        val site = detail?.website ?: pin.website
                        if (site != null) ActionButton(stringResource(R.string.web), Color(0xFFF97316), Modifier.weight(1f)) {
                            val u = if (site.startsWith("http")) site else "https://$site"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
                        }
                        ActionButton(stringResource(R.string.directions), FarmsyColors.farmGreen, Modifier.weight(1f)) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}(${pin.name})"))
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FarmsyColors.farmGreen)
                        }
                    } else {
                        detail?.description?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = geist(16.sp), color = FarmsyColors.ink)
                            Spacer(Modifier.height(16.dp))
                        }
                        Column(Modifier.fillMaxWidth().card(6)) {
                            (detail?.openingHours ?: pin.openingHours)?.let {
                                InfoRow(stringResource(R.string.opening_hours), it)
                            }
                            (detail?.address ?: pin.address)?.let {
                                InfoRow(
                                    stringResource(R.string.address),
                                    listOfNotNull(it, detail?.postalCode ?: pin.postalCode, pin.city).joinToString(", ")
                                )
                            }
                            detail?.email?.let { InfoRow(stringResource(R.string.email), it) }
                            detail?.operatorName?.let { InfoRow(stringResource(R.string.run_by), it) }
                            detail?.produce?.takeIf { it.isNotEmpty() }?.let {
                                InfoRow(stringResource(R.string.produce), it)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Claim link
                        Row(
                            Modifier.fillMaxWidth()
                                .background(FarmsyColors.farmGreenSoft, RoundedCornerShape(14.dp))
                                .clickable { showClaim = true }.padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.is_this_your_farm_claim_it),
                                style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen
                            )
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }

        // Top bar: back + favorite
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
            val isSaved = savedIds.contains(pin.osmId)
            CircleIconButton(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isSaved) FarmsyColors.warnRed else FarmsyColors.ink
            ) {
                val uid = session.session.value?.user?.id ?: return@CircleIconButton
                scope.launch { favorites.toggle(pin.osmId, uid) }
            }
        }
    }

    if (showClaim) ClaimSheet(pin = pin, onDismiss = { showClaim = false })
}

@Composable
private fun ActionButton(label: String, fill: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.background(fill, RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(label, style = geist(15.sp, FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(12.dp)) {
        Text(label, style = geist(13.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted)
        Text(value, style = geist(15.sp), color = FarmsyColors.ink)
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = FarmsyColors.ink,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(38.dp).background(Color.White.copy(alpha = 0.95f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

/// Members-only state — mirrors iOS LockedAccessView. No purchase CTA, no
/// external links; owners can still claim.
@Composable
private fun LockedAccessView(pin: FarmPin, onClaim: () -> Unit, onRecheck: suspend () -> Unit) {
    val farms = app.farmsy.android.LocalFarms.current
    val pins by farms.pins.collectAsState()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        app.farmsy.android.ui.theme.Kicker(stringResource(R.string.members_only))
        app.farmsy.android.ui.theme.DisplayTitle(
            leading = stringResource(R.string.unlock_every_farm_s),
            emphasis = stringResource(R.string.full_story),
            size = 32.sp
        )
        Column(Modifier.fillMaxWidth().card(22), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = FarmsyColors.farmGreen, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.your_account_doesn_t_have_full_access_yet),
                style = geist(19.sp, FontWeight.Bold), color = FarmsyColors.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.full_access_opening_hours_contact_details_photos_and_more_fo, pin.name),
                style = geist(15.sp), color = FarmsyColors.inkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (checking) {
            CircularProgressIndicator(color = FarmsyColors.farmGreen)
        } else {
            Text(
                stringResource(R.string.i_ve_upgraded_check_again),
                style = geist(16.sp, FontWeight.SemiBold), color = FarmsyColors.farmGreen,
                modifier = Modifier.clickable {
                    checking = true
                    scope.launch { onRecheck(); checking = false }
                }
            )
        }
        Text(
            stringResource(R.string.is_arg_yours_claim_it, pin.name),
            style = geist(14.sp, FontWeight.SemiBold), color = FarmsyColors.inkMuted,
            modifier = Modifier.clickable { onClaim() }
        )
    }
}
