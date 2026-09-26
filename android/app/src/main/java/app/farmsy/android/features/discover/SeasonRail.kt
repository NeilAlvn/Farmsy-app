package app.farmsy.android.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.farmsy.android.LocalTrip
import app.farmsy.android.R
import app.farmsy.android.features.main.LocalShell
import app.farmsy.android.features.main.AppTab
import app.farmsy.android.core.SeasonalItem
import app.farmsy.android.core.Seasons
import app.farmsy.android.core.ShoppingItem
import app.farmsy.android.core.ShoppingItems
import app.farmsy.android.features.whatsnew.SkeletonBox
import app.farmsy.android.ui.theme.EmptyState
import app.farmsy.android.ui.ProductImage
import app.farmsy.android.ui.theme.Badge
import app.farmsy.android.ui.theme.CardShape
import app.farmsy.android.ui.theme.FarmsyColors
import app.farmsy.android.ui.theme.PillButton
import app.farmsy.android.ui.theme.PillSize
import app.farmsy.android.ui.theme.PillVariant
import app.farmsy.android.ui.theme.Radius
import app.farmsy.android.ui.theme.ScreenHeader
import app.farmsy.android.ui.theme.SectionHeader
import app.farmsy.android.ui.theme.Space
import app.farmsy.android.ui.theme.TextRole
import app.farmsy.android.ui.theme.role
import app.farmsy.android.ui.theme.tapCard
import app.farmsy.android.ui.theme.ui
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// The year as a vertical rail of twelve months, the current one large and
// green, the past dimmed, the future plain. Nothing is locked: a month is a
// page you can read any time. The Android twin of iOS SeasonRail.swift.

fun monthName(m: Int): String {
    val locale = Locale.getDefault()
    return Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

private fun monthShort(m: Int): String {
    val locale = Locale.getDefault()
    return Month.of(m).getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(3).replaceFirstChar { it.titlecase(locale) }
}

/// One month as a rail node: circle, connector, name, count, up to five thumbs.
@Composable
fun SeasonNode(m: Int, now: Int, items: List<SeasonalItem>, ideas: Int, onOpen: () -> Unit) {
    val current = m == now
    val past = m < now
    val peak = items.filter { it.isPeak(m) }
    val circle = if (current) 56.dp else 44.dp
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).tapCard(onTap = onOpen),
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Column(Modifier.width(56.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(circle)
                    .background(if (current) FarmsyColors.vivid else if (past) FarmsyColors.creamFill else FarmsyColors.surface, CircleShape)
                    .then(if (!current && !past) Modifier.border(1.dp, FarmsyColors.hairline, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    monthShort(m), style = ui(if (current) 15.sp else 12.sp, FontWeight.Bold),
                    color = if (past) FarmsyColors.inkFaint else FarmsyColors.ink,
                )
            }
            if (m < 12) Box(Modifier.width(3.dp).weight(1f).background(FarmsyColors.hairline))
        }
        Column(Modifier.weight(1f).padding(bottom = Space.s5), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    monthName(m), style = role(if (current) TextRole.HEADING else TextRole.SUBHEADING),
                    color = if (past) FarmsyColors.inkMuted else FarmsyColors.ink,
                )
                if (current) Badge(stringResource(R.string.now_badge), fill = FarmsyColors.vivid, ink = FarmsyColors.ink)
            }
            Text(
                if (items.isEmpty()) stringResource(R.string.season_stored) else stringResource(R.string.season_count_arg, items.size, ideas),
                style = role(TextRole.CAPTION), color = FarmsyColors.inkMuted,
            )
            if (items.isNotEmpty()) {
                Row(Modifier.alpha(if (past) 0.55f else 1f), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    peak.ifEmpty { items }.take(5).forEach { ProductImage(it.imageSlug, it.emoji, 40.dp, corner = 10.dp) }
                }
            }
        }
    }
}

/// One month: what is ripe (peak first), then things to make, each with one
/// button that puts its ingredients on the shopping list. Sheet content.
@Composable
fun MonthSheet(month: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var openProduct by remember { mutableStateOf<SeasonalItem?>(null) }
    val language = remember { ShoppingItems.language(context) }
    val seasonItems by Seasons.items.collectAsState()
    val seasonIdeas by Seasons.ideas.collectAsState()
    val items = remember(seasonItems, month) { Seasons.items(month, seasonItems) }
    val ideas = remember(seasonIdeas, month) { Seasons.ideas(month, seasonIdeas) }

    Column(Modifier.fillMaxSize().background(FarmsyColors.cream)) {
        ScreenHeader(monthName(month), compact = true, onBack = onDismiss)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.s4).padding(bottom = Space.s8),
            verticalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            SectionHeader(stringResource(R.string.in_season), top = 0.dp)
            if (items.isEmpty()) {
                Text(stringResource(R.string.season_empty_month), style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted)
            }
            // A plain 3-column grid: a lazy grid cannot sit inside a scroll column.
            items.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().padding(bottom = Space.s1), horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    rowItems.forEach { item ->
                        Column(
                            Modifier.weight(1f).tapCard { openProduct = item },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                ProductImage(item.imageSlug, item.emoji, 96.dp, corner = Radius.tile)
                                if (item.isPeak(month)) {
                                    Badge(stringResource(R.string.peak), fill = FarmsyColors.vivid, ink = FarmsyColors.ink, modifier = Modifier.padding(6.dp))
                                }
                            }
                            Text(item.label(language), style = role(TextRole.CAPTION), color = FarmsyColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (ideas.isNotEmpty()) {
                SectionHeader(stringResource(R.string.what_to_make))
                CardCarousel(ideas) { idea ->
                    IdeaCard(
                        kicker = null, title = idea.title.text(language), text = idea.body.text(language),
                        image = idea.image, fallback = "🍽️", ingredients = idea.ingredients,
                    )
                }
            }
        }
    }
    openProduct?.let { item ->
        ProductBottomSheet(item.slug, fallbackLabel = item.label(language), fallbackEmoji = item.emoji) { openProduct = null }
    }
}

/// Full-width cards that snap one per page, with dots. The Nime "For you"
/// carousel.
@Composable
fun <T> CardCarousel(items: List<T>, content: @Composable (T) -> Unit) {
    val pager = rememberPagerState { items.size }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 32.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        ) { i ->
            content(items[i])
        }
        if (items.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                items.indices.forEach { i ->
                    Box(Modifier.size(6.dp).background(if (i == pager.currentPage) FarmsyColors.ink else FarmsyColors.hairline, CircleShape))
                }
            }
        }
    }
}

/// Image, kicker, title, body, and one button that puts the ingredients on
/// the shopping list. Shared by the season ideas and the tips.
@Composable
fun IdeaCard(
    kicker: String?, title: String, text: String, image: String, fallback: String, ingredients: List<String>,
    /// Shown when `image` is not bundled (a recipe picture not generated yet).
    fallbackImage: String? = null,
) {
    val context = LocalContext.current
    val trip = LocalTrip.current
    val shell = LocalShell.current
    val language = remember { ShoppingItems.language(context) }
    val wanted by trip.wantedProducts.collectAsState()
    val catalogue by ShoppingItems.items.collectAsState()
    val seasonItems by Seasons.items.collectAsState()
    var added by remember { mutableStateOf(false) }

    // Shopping ids the ingredients resolve to: a shopping id as itself, a
    // seasonal slug through the shopping item that shares its photograph,
    // else a custom item named after it.
    val listIds = remember(ingredients, catalogue, seasonItems) {
        ingredients.map { ing ->
            if (ShoppingItems.item(ing) != null) return@map ing
            val season = seasonItems.firstOrNull { it.slug == ing } ?: return@map ShoppingItem.custom(ing).id
            catalogue.firstOrNull { it.imageSlug == season.imageSlug }?.id ?: ShoppingItem.custom(season.label(language)).id
        }
    }
    val onList = added || (listIds.isNotEmpty() && listIds.all { it in wanted })

    Column(
        Modifier.fillMaxWidth().shadow(8.dp, CardShape).background(FarmsyColors.surface, CardShape).padding(Space.s5),
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProductImage(image, fallback, 160.dp, corner = Radius.tile, fallbackSlug = fallbackImage) }
        if (kicker != null) Text(kicker.uppercase(), style = role(TextRole.LABEL), color = FarmsyColors.farmGreen)
        // Lines reserved, not just capped: HorizontalPager is lazy and measures only the
        // realised page(s), so a shorter title/body on another card would otherwise shrink
        // this page and the row would resize while swiping.
        Text(title, style = role(TextRole.HEADING), color = FarmsyColors.ink, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(text, style = role(TextRole.BODY_SM), color = FarmsyColors.inkMuted, minLines = 4, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (ingredients.isNotEmpty()) {
            PillButton(
                // Once the ingredients are on the list, the useful next step is
                // seeing the list. This used to say "On your list" on a disabled
                // button, which is a dead end at exactly the moment of intent.
                stringResource(if (onList) R.string.view_my_list else R.string.put_on_my_list),
                if (onList) PillVariant.SOFT else PillVariant.PRIMARY, PillSize.SMALL,
                icon = if (onList) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.ShoppingBasket,
            ) {
                if (onList) {
                    // showTab closes whatever sheet this card is inside, so the
                    // shopping list is actually visible rather than behind it.
                    shell.showTab(AppTab.SHOPPING)
                } else {
                    listIds.filter { it !in wanted }.forEach { trip.toggleProduct(it) }
                    added = true
                }
            }
        }
    }
}

/// Four skeleton rows while the calendar loads; a retry once the fetch failed.
@Composable
fun SeasonLoading() {
    val failed by Seasons.loadFailed.collectAsState()
    val scope = rememberCoroutineScope()
    if (failed) {
        EmptyState(
            Icons.Filled.WifiOff, stringResource(R.string.calendar_failed), stringResource(R.string.check_connection),
            action = stringResource(R.string.try_again) to { scope.launch { Seasons.loadIfNeeded() } },
        )
    } else {
        repeat(4) { SkeletonBox(cornerRadius = Radius.card, modifier = Modifier.fillMaxWidth().height(84.dp)) }
    }
}
