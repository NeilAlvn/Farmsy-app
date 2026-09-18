package app.farmsy.android.core

import com.google.android.gms.maps.model.LatLng
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

// Turn a shopping list into trip stops — the Android twin of iOS ShoppingList.swift.
//
// "Where can I buy this?" is the free map. "I need eggs, milk and potatoes —
// where do I actually drive?" is the thing a map cannot answer, and it is the
// reason someone plans a trip at all.
//
// Nothing here needs the network per farm: the pins are already in memory with
// their `produce` text, the geometry is TripGeometry, and the result is
// ordinary trip stops that the existing route drawing and Google Maps hand-off
// pick up unchanged.
//
// The one thing it fetches is the picker itself — GET /api/shopping/items —
// because the vocabulary is the part that keeps being wrong. `expandQuery`
// would answer "lamb" with beef, pork and chicken: right for a search, and
// wrong for a list in the way that matters. The web's `termsFor` narrows
// exactly those groups, so the terms arrive already expanded.

// ── Matching ─────────────────────────────────────────────────────────────────

/// How a word is compared with a farm's `produce` text.
///
/// Two rules, for two jobs, both ported from the web (src/lib/searchTerms.ts
/// and src/lib/shoppingList.ts) and both live bugs there before they were fixed:
///
///   `matches` — the SEARCH rule. A long term may sit inside a longer word, so
///   "kaas" finds "boerenkaas". Short ones may not, or `ui` finds `fruit`,
///   `tuin` and `uit`, and a search for onions returns a third of the map.
///
///   `covers`  — the LIST rule. Every term must be a whole word, because the
///   served terms already carry their own plurals.
object ProductMatch {

    /// Terms this short must match a whole word even under the search rule.
    const val SHORT_TERM = 3

    /// Accents off, case off, hyphens and underscores to spaces.
    ///
    /// The product data is slugs — "pommes-de-terre", "hard-cheese" — while a
    /// farm writing prose says "pommes de terre", and "légumes" arrives spelled
    /// both ways. One folded form on both sides settles it in one place.
    fun fold(s: String): String {
        val lower = s.lowercase().replace('-', ' ').replace('_', ' ')
        // ASCII fast path: almost every Dutch farm name is plain ASCII, and this
        // runs over thousands of them.
        if (lower.all { it.code <= 127 }) return lower
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit()

    /// Is `t` in `hay` as a word, rather than buried inside a longer one?
    fun hasWord(hay: String, t: String): Boolean {
        if (t.isEmpty()) return false
        var i = hay.indexOf(t)
        while (i != -1) {
            val beforeOk = i == 0 || !isWordChar(hay[i - 1])
            val end = i + t.length
            val afterOk = end >= hay.length || !isWordChar(hay[end])
            if (beforeOk && afterOk) return true
            i = hay.indexOf(t, i + 1)
        }
        return false
    }

    /// The SEARCH rule — does this farm's text answer any of these terms?
    fun matches(haystack: String, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val h = fold(haystack)
        return terms.any { term ->
            val f = fold(term)
            when {
                f.isEmpty() -> false
                f.length <= SHORT_TERM -> hasWord(h, f)
                else -> h.contains(f)
            }
        }
    }

    /// The LIST rule — does this farm actually sell the thing, whole word only?
    ///
    /// Port of the web's `coverageOf`. The served terms already include the
    /// plural forms the data is inconsistent about, so nothing is gained by
    /// letting a term match inside a longer word, and a great deal is lost.
    fun covers(sells: String, terms: List<String>): Boolean {
        if (terms.isEmpty() || sells.isEmpty()) return false
        val h = fold(sells)
        return terms.any { term ->
            val f = fold(term)
            f.length >= 2 && hasWord(h, f)
        }
    }
}

// ── The picker ───────────────────────────────────────────────────────────────

/// One thing a person can put on a list, as GET /api/shopping/items serves it.
@Serializable
data class ShoppingItem(
    val id: String,
    val nl: String,
    val en: String,
    /// Every word that means this, already narrowed and pluralised by the web.
    val terms: List<String> = emptyList(),
    /// The picker group (dairy, eggs, vegetables, …). Older servers omit it.
    val category: String? = null,
    /// Slug of the bundled tile photograph. Older servers omit it; the id
    /// itself is the file name for every shopping item, so it falls back to that.
    val image: String? = null,
) {
    val imageSlug: String get() = image ?: id

    /// Dutch or English, the same rule the website applies. fr and de fall back
    /// to English rather than showing an id — the labels only exist in two.
    fun label(language: String): String = if (language == "nl") nl else en

    /// A glyph for tiles and chips. Ids are the web's closed list; anything new
    /// falls back to the basket.
    val emoji: String
        get() = when (id) {
            "eggs" -> "🥚"; "cheese" -> "🧀"; "milk" -> "🥛"; "potatoes" -> "🥔"
            "vegetables" -> "🥬"; "fruits" -> "🍎"; "meat" -> "🥩"; "honey" -> "🍯"
            "bread" -> "🍞"; "juices" -> "🧃"; "jams" -> "🫙"; "ice-cream" -> "🍦"
            "strawberry" -> "🍓"; "apples" -> "🍏"; "pears" -> "🍐"; "asparagus" -> "🌱"
            "pumpkin" -> "🎃"; "tomatoes" -> "🍅"; "onions" -> "🧅"; "carrot" -> "🥕"
            "mushrooms" -> "🍄"; "nuts" -> "🌰"; "butter" -> "🧈"; "yoghurt" -> "🥣"
            "herbs" -> "🌿"; "flowers" -> "🌷"; "wine" -> "🍷"; "beer" -> "🍺"
            "fish" -> "🐟"
            else -> "🧺"
        }
}

/// How many farms near here sell a thing, and how close the nearest is.
data class ProductNearby(val item: ShoppingItem, val count: Int, val nearestKm: Double)

/// A picker group, as the server orders them.
@Serializable
data class ShoppingCategory(val id: String, val nl: String, val en: String, val image: String) {
    fun label(language: String): String = if (language == "nl") nl else en
}

@Serializable
private data class ItemsPayload(
    val items: List<ShoppingItem> = emptyList(),
    val categories: List<ShoppingCategory> = emptyList(),
)

/// The picker list, fetched once.
object ShoppingItems {

    private val _items = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val items: StateFlow<List<ShoppingItem>> = _items.asStateFlow()

    private val _categories = MutableStateFlow<List<ShoppingCategory>>(emptyList())
    val categories: StateFlow<List<ShoppingCategory>> = _categories.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    suspend fun loadIfNeeded() {
        if (_items.value.isNotEmpty()) return
        val payload = runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/shopping/items")
            if (resp.status.value != 200) null
            else lenientJson.decodeFromString<ItemsPayload>(resp.bodyAsText())
        }.getOrNull()
        if (payload == null) { _loadFailed.value = true; return }
        _items.value = payload.items
        _categories.value = payload.categories
        _loadFailed.value = false
    }

    fun item(id: String): ShoppingItem? = _items.value.firstOrNull { it.id == id }

    /// Dutch or English, the same rule the website applies; fr and de fall back
    /// to English because the labels only exist in two.
    fun language(context: android.content.Context): String =
        LanguageStore.current(context).code.ifEmpty { java.util.Locale.getDefault().language }
}

// ── Planner ──────────────────────────────────────────────────────────────────

/// Which farms to visit for a shopping list, and what each one is for.
object ShoppingPlanner {

    /// A farm as the planner needs it — deliberately not `FarmPin`, so this is
    /// a pure function a test can call without building a pin.
    data class Candidate(val osmId: String, val coord: LatLng, val sells: String)

    /// One stop, and the list items it answers (item ids, in the picked order).
    data class Pick(val osmId: String, val covers: List<String>)

    data class Plan(val picks: List<Pick>, val missing: List<String>) {
        val isEmpty: Boolean get() = picks.isEmpty()
    }

    /// Greedy: repeatedly take the farm that answers the most still-unanswered
    /// items for the least extra driving.
    ///
    /// ponytail: greedy set cover, not optimal — a perfect answer is NP-hard and
    /// this runs over thousands of farms while someone waits. With five stops and
    /// a dozen items the gap to optimal is small enough not to be visible. The
    /// website goes further and offers three alternatives (most items / fewest
    /// stops / shortest drive); if that turns out to be the better screen here
    /// too, the honest move is to call its endpoint rather than port it.
    fun plan(
        wanted: List<ShoppingItem>,
        farms: List<Candidate>,
        origin: LatLng,
        maxStops: Int = 5,
        radiusKm: Double = 25.0,
    ): Plan {
        if (wanted.isEmpty()) return Plan(emptyList(), emptyList())
        val order = wanted.map { it.id }

        data class Entry(val candidate: Candidate, val items: Set<String>)
        val covers = ArrayList<Entry>()
        for (farm in farms) {
            if (farm.sells.isEmpty()) continue
            if (TripGeometry.haversineKm(origin, farm.coord) > radiusKm) continue
            val hit = wanted.filter { ProductMatch.covers(farm.sells, it.terms) }.map { it.id }
            if (hit.isNotEmpty()) covers.add(Entry(farm, hit.toSet()))
        }

        val remaining = order.toMutableSet()
        val picks = ArrayList<Pick>()
        val used = HashSet<String>()
        var cursor = origin

        while (remaining.isNotEmpty() && picks.size < maxStops) {
            var bestEntry: Entry? = null
            var bestGained: Set<String> = emptySet()
            var bestScore = 0.0
            for (entry in covers) {
                if (entry.candidate.osmId in used) continue
                val gained = entry.items.intersect(remaining)
                if (gained.isEmpty()) continue
                // Items answered per kilometre of extra driving. The +1 keeps a
                // farm on the doorstep from scoring infinitely better than a
                // farm one street further that answers twice as much.
                val km = TripGeometry.haversineKm(cursor, entry.candidate.coord)
                val score = gained.size / (1 + km)
                // Ties broken on osm_id so the same list always plans the same
                // trip — a route that reshuffles on every tap reads as broken.
                val better = bestEntry == null || score > bestScore + 1e-9 ||
                    (Math.abs(score - bestScore) <= 1e-9 && entry.candidate.osmId < bestEntry!!.candidate.osmId)
                if (better) { bestEntry = entry; bestGained = gained; bestScore = score }
            }
            val choice = bestEntry ?: break
            picks.add(Pick(choice.candidate.osmId, order.filter { it in bestGained }))
            remaining.removeAll(bestGained)
            used.add(choice.candidate.osmId)
            cursor = choice.candidate.coord
        }

        return Plan(picks, order.filter { it in remaining })
    }
}
