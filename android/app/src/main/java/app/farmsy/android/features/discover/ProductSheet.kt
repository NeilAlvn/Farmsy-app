package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalFarms
import app.farmsy.android.LocalLocationHelper
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.core.MonthState
import app.farmsy.android.core.ProductMatch
import app.farmsy.android.core.ProductProfile
import app.farmsy.android.core.Products
import app.farmsy.android.core.SearchRadius
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.ProductImage
import app.farmsy.android.ui.theme.Chip
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.card
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.ui
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

// One product, as a page: its photograph, the year bar (Nime's score bar,
// turned into twelve months), how to choose one at the farm, how to keep and
// preserve it, five things to make with it, grandmother's tips, and the two
// actions that lead back into the app: find it nearby, put it on the list.
// The Android twin of iOS ProductSheet.swift.
//
// Opened from anywhere a product is named: Home tiles, the month page,
// shopping rows, Discover. Takes a shopping id or a seasonal slug; the
// profile store resolves either.

/// The sheet as a full-height modal (iOS `.presentationDetents([.large])`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductBottomSheet(slug: String, fallbackLabel: String = "", fallbackEmoji: String = "🌱", onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FarmsyColors.cream,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.96f).navigationBarsPadding()) {
            ProductSheet(slug, fallbackLabel, fallbackEmoji, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductSheet(slug: String, fallbackLabel: String, fallbackEmoji: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shell = LocalShell.current
    val farms = LocalFarms.current
    val trip = LocalTrip.current
    val scope = rememberCoroutineScope()
    val location by LocalLocationHelper.current.location.collectAsState()
    val radiusKm by SearchRadius.km.collectAsState()
    val language = remember { ShoppingItems.language(context) }
    val all by Products.all.collectAsState()
    val loaded by Products.loaded.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val seasonItems by Seasons.items.collectAsState()
    val wanted by trip.wantedProducts.collectAsState()
    // The slug on screen; a "goes with" chip swaps it in place rather than
    // stacking a second sheet.
    var current by remember(slug) { mutableStateOf(slug) }
    val scroll = rememberScrollState()
    val month = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 }

    LaunchedEffect(Unit) { Products.loadIfNeeded(Products.lang(context)) }
    LaunchedEffect(Unit) { ShoppingItems.loadIfNeeded() }
    LaunchedEffect(Unit) { Seasons.loadIfNeeded() }
    LaunchedEffect(current) { scroll.animateScrollTo(0) }

    val profile = remember(all, current) { Products.profile(current) }
    val name = profile?.name ?: fallbackLabel.ifEmpty {
        ShoppingItems.item(current)?.label(language) ?: seasonItems.firstOrNull { it.slug == current }?.label(language) ?: ""
    }

    // The shopping id this product goes on the list as.
    val listId: String? = when {
        profile != null -> profile.shopping ?: ShoppingItem.custom(profile.name).id
        ShoppingItems.item(current) != null -> current
        else -> null
    }
    val onList = listId != null && listId in wanted

    val terms: List<String> = remember(profile, current, catalogue, seasonItems) {
        val shopId = profile?.shopping ?: current.takeIf { ShoppingItems.item(it) != null }
        shopId?.let { ShoppingItems.item(it) }?.terms
            ?: seasonItems.firstOrNull { it.slug == (profile?.seasonal ?: current) }?.terms
            ?: listOf(ProductMatch.fold(profile?.name ?: fallbackLabel))
    }

    @Composable
    fun Actions() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
            PillButton(
                stringResource(R.string.find_nearby), PillVariant.PRIMARY, PillSize.MEDIUM, block = true,
                icon = Icons.Outlined.LocationOn, modifier = Modifier.weight(1f),
            ) {
                scope.launch {
                    farms.showProduct(name, terms, location, radiusKm)
                    onDismiss()
                    shell.showTab(AppTab.MAP)
                }
            }
            if (listId != null) {
                PillButton(
                    stringResource(if (onList) R.string.on_your_list else R.string.add_to_list),
                    if (onList) PillVariant.SOFT else PillVariant.SECONDARY, PillSize.MEDIUM,
                    icon = if (onList) Icons.Filled.Check else Icons.Filled.ShoppingBasket, enabled = !onList,
                ) { if (!onList) trip.toggleProduct(listId) }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        ScreenHeader(name, compact = true, onBack = onDismiss)
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = Space.s4).padding(bottom = Space.s8),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProductImage(profile?.image ?: current, fallbackEmoji, 200.dp, corner = Radius.card)
            }
            val p = profile
            if (p != null) {
                YearBar(p, month)
                if (p.regionNote.isNotEmpty()) Note(p.regionNote, Icons.Outlined.Map)
                if (p.greenhouseNote.isNotEmpty()) Note(p.greenhouseNote, Icons.Outlined.Eco)
                Actions()
                Section(stringResource(R.string.how_to_choose)) { p.choose.forEach { Bullet(it) } }
                Section(stringResource(R.string.keeping)) {
                    Text(p.store.place, style = role(TextRole.BODY), color = FarmsyColors.ink)
                    Text(p.store.how, style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                    if (p.store.days > 0) {
                        Text(stringResource(R.string.about_days_arg, p.store.days), style = role(TextRole.CAPTION), color = FarmsyColors.inkFaint)
                    }
                }
                if (p.preserve.isNotEmpty()) {
                    Section(stringResource(R.string.preserving)) {
                        p.preserve.forEach { x ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.Top) {
                                Text(
                                    x.method.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                                    style = role(TextRole.SUBHEADING), color = FarmsyColors.ink, modifier = Modifier.width(96.dp),
                                )
                                Text(x.how, style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
                            }
                        }
                    }
                }
                if (p.ideas.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.what_to_make), top = Space.s2)
                    CardCarousel(p.ideas) { idea ->
                        IdeaCard(
                            kicker = null, title = idea.title, text = idea.body, image = idea.image,
                            fallback = "🍽️", ingredients = idea.ingredients, fallbackImage = p.image,
                        )
                    }
                }
                if (p.tips.isNotEmpty()) {
                    Section(stringResource(R.string.grandmothers_tip)) { p.tips.forEach { Bullet(it) } }
                }
                if (p.pairs.isNotEmpty()) {
                    Section(stringResource(R.string.goes_with)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                            p.pairs.forEach { pr ->
                                val item = ShoppingItems.item(pr)
                                Chip(Products.profile(pr)?.name ?: item?.label(language) ?: pr, emoji = item?.emoji) { current = pr }
                            }
                        }
                    }
                }
                if (p.funFact.isNotEmpty()) Note(p.funFact, Icons.Outlined.AutoAwesome, Modifier.padding(top = Space.s2))
            } else if (loaded) {
                Actions()
                Text(stringResource(R.string.no_profile_yet), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
            } else {
                repeat(3) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
    }
}

// MARK: - Pieces

/// Twelve cells. Peak vivid, available soft green, none tile; the current
/// month ringed so "now" reads at a glance.
@Composable
private fun YearBar(p: ProductProfile, month: Int) {
    val letters = remember {
        (1..12).map { Month.of(it).getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()) }
    }
    val cell = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().card(padding = 16), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (m in 1..12) {
                val s = p.state(m)
                val fill = when (s) {
                    MonthState.PEAK -> FarmsyColors.vivid
                    MonthState.AVAILABLE -> FarmsyColors.farmGreenSoft
                    MonthState.NONE -> FarmsyColors.creamFill
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 28.dp).background(fill, cell)
                        .then(if (m == month) Modifier.border(2.dp, FarmsyColors.ink, cell) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letters[m - 1], style = ui(11.sp, if (s == MonthState.PEAK) FontWeight.Bold else FontWeight.Medium),
                        color = if (s == MonthState.NONE) FarmsyColors.inkFaint else FarmsyColors.ink,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            Legend(FarmsyColors.vivid, stringResource(R.string.peak_legend))
            Legend(FarmsyColors.farmGreenSoft, stringResource(R.string.in_season))
            Spacer(Modifier.weight(1f))
            val now = p.state(month)
            Text(
                stringResource(
                    when (now) {
                        MonthState.PEAK -> R.string.at_its_best_now
                        MonthState.AVAILABLE -> R.string.in_season_now
                        MonthState.NONE -> R.string.not_in_season_now
                    }
                ),
                style = role(TextRole.CAPTION), color = if (now == MonthState.NONE) FarmsyColors.inkMuted else FarmsyColors.positive,
            )
        }
    }
}

@Composable
private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(c, RoundedCornerShape(3.dp)))
        Text(t, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card(padding = 16), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text(title.uppercase(), style = role(TextRole.LABEL), color = FarmsyColors.inkFaint)
        content()
    }
}

@Composable
private fun Bullet(line: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(FarmsyColors.farmGreen, CircleShape))
        Text(line, style = role(TextRole.BODY_SM), color = FarmsyColors.ink)
    }
}

@Composable
private fun Note(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = FarmsyColors.farmGreen, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Text(text, style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted)
    }
}
