package app.farmsy.android.core

import android.location.Location
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/// One row of `/api/farms/flags`: galleries (g), produce text (p), place-types
/// (l) and methods (m). Null when empty.
@Serializable
private data class FarmFlag(
    val o: String,
    val g: List<String>? = null,
    val p: String? = null,
    val l: List<String>? = null,
    val m: List<String>? = null,
)

/// All public farm pins, loaded once via the get_farms_pins RPC (paginated
/// the same way the web map does) and filtered in memory. Mirrors
/// iOS FarmsStore.swift.
class FarmsStore(private val scope: CoroutineScope) {

    private val _pins = MutableStateFlow<List<FarmPin>>(emptyList())
    val pins: StateFlow<List<FarmPin>> = _pins.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    val searchText = MutableStateFlow("")
    /// Categories are multi-select, like the web filter — a farm matches if it is in
    /// ANY selected category. Empty means "all". Mirrors iOS `selectedCategories`.
    val selectedCategories = MutableStateFlow<Set<FarmCategory>>(emptySet())

    // Quick filters + the two new axes (Aviah's taxonomy) — mirror iOS. They
    // combine with the category selection rather than replacing it.
    val filterVerified = MutableStateFlow(false)
    val filterOpenToday = MutableStateFlow(false)
    val filterAutomaat = MutableStateFlow(false)
    val filterZelfpluk = MutableStateFlow(false)
    val filterHasPhotos = MutableStateFlow(false)
    val selectedPlaceTypes = MutableStateFlow<Set<String>>(emptySet())
    val selectedMethods = MutableStateFlow<Set<String>>(emptySet())

    // Pro time filters (Aviah's closed five-group set). Distinct from the FREE
    // `filterOpenToday` (day-based): these use the Amsterdam-clock parser —
    // `filterOpenNow` is open at this exact minute, the day filters ask about a
    // specific weekday (Sat = Mon-based 5, Sun = 6). Gated on membership in the UI;
    // the predicates here are unconditional (only *set* when unlocked).
    val filterOpenNow = MutableStateFlow(false)
    val filterOpenSaturday = MutableStateFlow(false)
    val filterOpenSunday = MutableStateFlow(false)
    /// Plus: only farms a visitor reported open today. The set comes from
    /// RecentReports; the map hands it over so this store stays network-free.
    val filterConfirmedToday = MutableStateFlow(false)
    val confirmedTodayIds = MutableStateFlow<Set<String>>(emptySet())

    /// Any of the quick-filter toggles on (Verified / Open today / Automaat /
    /// Zelfpluk / Has photos) — excludes categories and the two axes. Mirrors iOS.
    fun anyQuickFilterOn(): Boolean =
        filterVerified.value || filterOpenToday.value || filterAutomaat.value ||
            filterZelfpluk.value || filterHasPhotos.value

    /// Any of the five Pro groups active (three time filters + the two axis groups,
    /// which moved into Pro per Aviah's later-3). Mirrors iOS anyProFilterOn.
    fun anyProFilterOn(): Boolean =
        filterOpenNow.value || filterOpenSaturday.value || filterOpenSunday.value || filterConfirmedToday.value ||
            selectedPlaceTypes.value.isNotEmpty() || selectedMethods.value.isNotEmpty()

    fun anyFilterOn(): Boolean =
        anyQuickFilterOn() || anyProFilterOn() || selectedCategories.value.isNotEmpty()

    // ── Preferences (P0-3) — mirrors iOS FarmsStore.preferences / apply(_:) ──

    /// The five persisted fields as the server stores them.
    fun snapshotPreferences(): Preferences = Preferences(
        categories = FarmCategory.entries.filter { it in selectedCategories.value }.map { it.raw },
        openToday = filterOpenToday.value, pickYourOwn = filterZelfpluk.value,
        verified = filterVerified.value, hasPhotos = filterHasPhotos.value,
    )

    /// Applies stored preferences. An empty category list means no category
    /// filter (every farm), and an unknown category is dropped, never an error.
    fun applyPreferences(p: Preferences) {
        selectedCategories.value = p.knownCategories
        filterOpenToday.value = p.openToday
        filterZelfpluk.value = p.pickYourOwn
        filterVerified.value = p.verified
        filterHasPhotos.value = p.hasPhotos
    }

    fun clearAllFilters() {
        selectedCategories.value = emptySet()
        filterVerified.value = false; filterOpenToday.value = false
        filterAutomaat.value = false; filterZelfpluk.value = false; filterHasPhotos.value = false
        filterOpenNow.value = false; filterOpenSaturday.value = false; filterOpenSunday.value = false
        filterConfirmedToday.value = false
        selectedPlaceTypes.value = emptySet(); selectedMethods.value = emptySet()
    }

    // AI search — the parsed intent in effect. When set it drives filtering +
    // ranking; the summary bar shows what was understood. Mirrors iOS.
    val aiIntent = MutableStateFlow<SmartSearchIntent?>(null)
    private val _aiCenter = MutableStateFlow<Pair<Double, Double>?>(null)
    val aiCenter: StateFlow<Pair<Double, Double>?> = _aiCenter.asStateFlow()
    private val _aiPlaceToken = MutableStateFlow(0)
    val aiPlaceToken: StateFlow<Int> = _aiPlaceToken.asStateFlow()

    // Flags maps (loaded once from /api/farms/flags). Observable so Home and
    // Shopping can recompute once the produce text is in.
    private val _flagsLoaded = MutableStateFlow(false)
    val flagsLoaded: StateFlow<Boolean> = _flagsLoaded.asStateFlow()
    // Merged product text per farm from the flags `p` (produce folded to
    // produce_inferred server-side). Read by the smart-search product match, the
    // R5 corridor product chips, and the shopping-list planner (via produceFor).
    var produceByOsm: Map<String, String> = emptyMap()
        private set

    /// Lowercase `produce` text for one farm, or null when it never said what it
    /// sells. Read by the shopping-list planner, which matches against it.
    fun produceFor(osmId: String): String? = produceByOsm[osmId]

    // `name + city + postalCode`, lowercased once per pin, for the search filter.
    // That filter used to call `lowercase()` on all three fields per pin on every
    // pass — up to ~25,000 String allocations over 8,400 farms. iOS had it worse
    // (no `remember`, so once per keystroke); this is the same fix on both sides.
    //
    // Rebuilt when the pin list instance changes, and only on the first pass that
    // actually searches, so a user who never types pays nothing. iOS builds it
    // eagerly in `pins.didSet` instead — Kotlin has no property observer here, and
    // the visible behaviour is identical either way.
    private var haystackSource: List<FarmPin>? = null
    private var haystackCache: Map<String, String> = emptyMap()
    private val searchHaystacks: Map<String, String>
        get() {
            val current = _pins.value
            if (current !== haystackSource) {
                haystackSource = current
                haystackCache = current.associate { pin ->
                    pin.osmId to buildString {
                        append(pin.name.lowercase())
                        pin.city?.takeIf { it.isNotEmpty() }?.let { append('\n').append(it.lowercase()) }
                        pin.postalCode?.takeIf { it.isNotEmpty() }?.let { append('\n').append(it.lowercase()) }
                    }
                }
            }
            return haystackCache
        }
    private var locationTypesByOsm: Map<String, List<String>> = emptyMap()
    private var methodsByOsm: Map<String, List<String>> = emptyMap()

    // Featured farms (What's New shelf): a farm's gallery photos from the public
    // flags endpoint (kept only when it has 2+ photos), plus a *cached, once-
    // shuffled* order so the shelf doesn't reshuffle every time the sheet opens.
    // Both persist for the session. Mirrors iOS FarmsStore.
    private val _galleries = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val galleries: StateFlow<Map<String, List<String>>> = _galleries.asStateFlow()
    private val _galleriesLoaded = MutableStateFlow(false)
    val galleriesLoaded: StateFlow<Boolean> = _galleriesLoaded.asStateFlow()
    private val _featuredOrder = MutableStateFlow<List<String>>(emptyList())
    val featuredOrder: StateFlow<List<String>> = _featuredOrder.asStateFlow()
    /// Prefetched description teasers for the featured farms, so the cards render
    /// instantly and the shelf can put farms *with* a description first.
    private val _featuredTeasers = MutableStateFlow<Map<String, String>>(emptyMap())
    val featuredTeasers: StateFlow<Map<String, String>> = _featuredTeasers.asStateFlow()

    private val radiusNearMe = 15.0
    private val radiusNamedPlace = 25.0

    private val pageSize = 1000

    /// Fetch the flags endpoint once → produce/place-type/method lookups for
    /// AI-search matching (and, later, the filter groups).
    suspend fun loadFlagsIfNeeded() {
        if (_flagsLoaded.value) return
        try {
            val rows = withContext(Dispatchers.IO) {
                val resp = httpClient.get("${Backend.WEB_API}/farms/flags")
                if (resp.status.value != 200) return@withContext emptyList()
                lenientJson.decodeFromString<List<FarmFlag>>(resp.bodyAsText())
            }
            if (rows.isEmpty()) return
            produceByOsm = rows.mapNotNull { r -> r.p?.takeIf { it.isNotEmpty() }?.let { r.o to it.lowercase() } }.toMap()
            locationTypesByOsm = rows.mapNotNull { r -> r.l?.takeIf { it.isNotEmpty() }?.let { r.o to it } }.toMap()
            methodsByOsm = rows.mapNotNull { r -> r.m?.takeIf { it.isNotEmpty() }?.let { r.o to it } }.toMap()
            // Gallery photos, kept only for farms with 2+ photos — same rule as iOS
            // (`if (r.g?.count ?? 0) >= 2 { map[r.o] = r.g }`), for the featured shelf.
            _galleries.value = rows.mapNotNull { r ->
                r.g?.takeIf { it.size >= 2 }?.let { r.o to it }
            }.toMap()
            _flagsLoaded.value = true
        } catch (e: Exception) { /* leave maps empty; AI still filters on categories */ }
    }

    /// Fetch the galleries once, prefetch their description teasers, and freeze an
    /// order — farms that have a description first (so the top of the shelf always
    /// has one), shuffled within each group. Mirrors iOS loadGalleriesIfNeeded: the
    /// order freezes on first successful load (guarded by galleriesLoaded) and
    /// nothing invalidates it for the rest of the session.
    ///
    /// PORT NOTE (structural): iOS prefetches per-id via `FarmDetailAPI.teaser` in a
    /// concurrent TaskGroup; here we use the batch `FarmDetailApi.teasers` (server
    /// caps 60/call, so ids are chunked into 60s) — fewer round trips, same result.
    suspend fun loadGalleriesIfNeeded() {
        loadFlagsIfNeeded()
        if (_galleriesLoaded.value) return
        val ids = _galleries.value.keys.toList()

        val teasers = HashMap<String, String>()
        withContext(Dispatchers.IO) {
            ids.chunked(60).forEach { batch ->
                teasers.putAll(FarmDetailApi.teasers(batch))
            }
        }
        _featuredTeasers.value = teasers

        val described = ids.filter { teasers[it] != null }.shuffled()
        val rest = ids.filter { teasers[it] == null }.shuffled()
        _featuredOrder.value = described + rest
        _galleriesLoaded.value = true
    }

    /// The featured farms, in the frozen random order, resolved to pins. Mirrors iOS.
    val featuredFarms: List<FarmPin>
        get() {
            val byId = _pins.value.associateBy { it.osmId }
            return _featuredOrder.value.mapNotNull { byId[it] }
        }

    /// Show the map filtered on one product near the user — the same path an
    /// AI search takes, so ranking, radius and the summary bar come for free.
    suspend fun showProduct(label: String, terms: List<String>, userLocation: Location?, radiusKm: Double) {
        val intent = SmartSearchIntent(products = terms, nearMe = userLocation != null, radiusKm = radiusKm, summary = label)
        applyAISearch(intent, userLocation)
    }

    /// Apply a parsed AI intent — takes over filtering and resolves the centre
    /// (server `center`, or the user's own location for a nearMe query).
    suspend fun applyAISearch(intent: SmartSearchIntent, userLocation: Location?) {
        loadFlagsIfNeeded()
        clearAllFilters()
        aiIntent.value = intent
        when {
            intent.center != null -> {
                _aiCenter.value = intent.center.lat to intent.center.lng
                _aiPlaceToken.value += 1
            }
            intent.nearMe && userLocation != null -> {
                _aiCenter.value = userLocation.latitude to userLocation.longitude
                _aiPlaceToken.value += 1
            }
            else -> _aiCenter.value = null
        }
    }

    fun clearAISearch() {
        aiIntent.value = null
        _aiCenter.value = null
        searchText.value = ""
    }

    private fun aiRadiusMeters(intent: SmartSearchIntent): Double? {
        if (_aiCenter.value == null) return null
        val km = intent.radiusKm ?: if (intent.nearMe) radiusNearMe else radiusNamedPlace
        return km * 1000
    }

    fun loadIfNeeded() {
        if (_pins.value.isNotEmpty() || _isLoading.value) return
        scope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                // Network + JSON decode of thousands of pins must stay off the
                // main thread, or the UI freezes (ANR) during startup.
                val all = withContext(Dispatchers.IO) {
                    val acc = mutableListOf<FarmPin>()
                    var from = 0
                    while (true) {
                        val page = supabase.postgrest.rpc("get_farms_pins") {
                            range(from.toLong(), (from + pageSize - 1).toLong())
                        }.decodeList<FarmPin>()
                        acc += page
                        if (page.size < pageSize) break
                        from += pageSize
                    }
                    acc
                }
                _pins.value = all
            } catch (e: Exception) {
                _loadError.value = "load_failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /// Pins matching the current search + category filter, or the AI intent when
    /// one is active (which takes over and ranks the result).
    fun filtered(): List<FarmPin> {
        aiIntent.value?.let { return filteredForAI(it) }

        var result = _pins.value
        // Multi-select: a farm matches if it is in ANY selected category (iOS parity).
        val cats = selectedCategories.value
        if (cats.isNotEmpty()) {
            result = result.filter { pin -> pin.categories.any { it in cats } }
        }
        if (filterVerified.value) result = result.filter { it.isVerified }
        if (filterOpenToday.value) result = result.filter { FarmFilters.isOpenToday(it.openingHours) }
        // Pro time filters (Amsterdam clock). Saturday = Mon-based 5, Sunday = 6.
        if (filterOpenNow.value) result = result.filter { FarmFilters.isOpenNow(it.openingHours) }
        if (filterOpenSaturday.value) result = result.filter { FarmFilters.isOpenOnDay(it.openingHours, 5) }
        if (filterOpenSunday.value) result = result.filter { FarmFilters.isOpenOnDay(it.openingHours, 6) }
        if (filterConfirmedToday.value) { val ids = confirmedTodayIds.value; result = result.filter { it.osmId in ids } }
        if (filterHasPhotos.value) result = result.filter { it.image != null }
        if (filterAutomaat.value) result = result.filter { FarmFilters.looksLikeAutomaat(it.name, it.openingHours) }
        if (filterZelfpluk.value) result = result.filter { FarmFilters.looksLikeZelfpluk(it.name) }
        selectedPlaceTypes.value.takeIf { it.isNotEmpty() }?.let { want ->
            result = result.filter { (locationTypesByOsm[it.osmId] ?: emptyList()).any { v -> v in want } }
        }
        selectedMethods.value.takeIf { it.isNotEmpty() }?.let { want ->
            result = result.filter { (methodsByOsm[it.osmId] ?: emptyList()).any { v -> v in want } }
        }
        val query = searchText.value.trim().lowercase()
        if (query.isNotEmpty()) {
            // One prebuilt haystack per pin (see `searchHaystacks`), so a pass costs
            // a lookup and a `contains` rather than three `lowercase()` allocations
            // per pin. Falls back to the live fields if a pin has no entry, so a
            // missing haystack can never hide a farm. Mirrors iOS.
            val hays = searchHaystacks   // hoisted: the getter checks the cache
            result = result.filter {
                val hay = hays[it.osmId]
                if (hay != null) hay.contains(query)
                else it.name.lowercase().contains(query) ||
                    (it.city?.lowercase()?.contains(query) ?: false) ||
                    (it.postalCode?.lowercase()?.contains(query) ?: false)
            }
        }
        return result
    }

    private fun filteredForAI(ai: SmartSearchIntent): List<FarmPin> {
        var result = _pins.value
        val cats = ai.categories.mapNotNull { FarmCategory.from(it) }.toSet()
        val prods = ai.products.map { ProductMatch.fold(it) }
        // What they sell: category OR produce text (OR'd — thin produce coverage
        // must not drop farms the category already accounts for).
        if (cats.isNotEmpty() || prods.isNotEmpty()) {
            result = result.filter { pin ->
                val catMatch = cats.isNotEmpty() && pin.categories.any { it in cats }
                val prodMatch = prods.isNotEmpty() &&
                    // Same rule as the shopping list: short terms must match a
                    // whole word, or `ui` finds every farm with `fruit` in its
                    // text. The web fixed this; the apps had not.
                    (produceByOsm[pin.osmId]?.let { txt -> ProductMatch.matches(txt, prods) } ?: false)
                catMatch || prodMatch
            }
        }
        if (ai.locationTypes.isNotEmpty()) {
            val want = ai.locationTypes.toSet()
            result = result.filter { (locationTypesByOsm[it.osmId] ?: emptyList()).any { v -> v in want } }
        }
        if (ai.methods.isNotEmpty()) {
            val want = ai.methods.toSet()
            result = result.filter { (methodsByOsm[it.osmId] ?: emptyList()).any { v -> v in want } }
        }
        if (ai.openNow) result = result.filter { FarmFilters.isOpenToday(it.openingHours) }
        if (ai.verified) result = result.filter { it.isVerified }
        if (ai.automaat) result = result.filter { FarmFilters.looksLikeAutomaat(it.name, it.openingHours) }
        if (ai.zelfpluk) result = result.filter { FarmFilters.looksLikeZelfpluk(it.name) }
        // A place NARROWS: keep only farms within the radius of the centre.
        val center = _aiCenter.value
        val radius = aiRadiusMeters(ai)
        if (center != null && radius != null) {
            result = result.filter { it.distanceMeters(center.first, center.second) <= radius }
        }
        return rankForIntent(result, center, ai.ranking ?: SearchRanking.DEFAULT)
    }

    /// Rank AI results with server-supplied weights (fall back to defaults; ignore
    /// an unrecognised version). Distance never leaves the device.
    private fun rankForIntent(
        list: List<FarmPin>,
        origin: Pair<Double, Double>?,
        ranking: SearchRanking,
    ): List<FarmPin> {
        val w = if (ranking.version == SearchRanking.DEFAULT.version) ranking else SearchRanking.DEFAULT
        val zeroM = max(1.0, w.distanceZeroKm * 1000)
        fun score(p: FarmPin): Double {
            var s = 0.0
            if (origin != null) {
                val d = p.distanceMeters(origin.first, origin.second)
                s += max(0.0, 1 - d / zeroM) * w.distanceWeight
            }
            if (FarmFilters.isOpenToday(p.openingHours)) s += w.openToday
            if (p.isVerified) s += w.verified
            if (p.image != null) s += w.hasPhoto
            s += (p.avgRating ?: 0.0) * w.ratingFactor
            s += min(p.reviewCount.toDouble(), w.reviewCap) * w.reviewEach
            return s
        }
        return list.sortedByDescending { score(it) }
    }

    fun sortedByDistance(list: List<FarmPin>, location: Location?): List<FarmPin> {
        if (location == null) return list
        return list.sortedBy { it.distanceMeters(location.latitude, location.longitude) }
    }

    /// Real category counts within a radius — powers the onboarding grid.
    fun categoryCounts(lat: Double, lng: Double, radiusKm: Double): List<Pair<FarmCategory, Int>> {
        val nearby = _pins.value.filter { it.distanceMeters(lat, lng) <= radiusKm * 1000 }
        val counts = mutableMapOf<FarmCategory, Int>()
        for (pin in nearby) for (cat in pin.categories) counts[cat] = (counts[cat] ?: 0) + 1
        return FarmCategory.entries
            .mapNotNull { cat -> counts[cat]?.let { cat to it } }
            .sortedByDescending { it.second }
    }

    fun pinForOsmId(osmId: String): FarmPin? = _pins.value.firstOrNull { it.osmId == osmId }

    companion object {
        /// Home's "Available near you": every list item with at least one farm in
        /// the radius that says it sells it, most farms first. Pure over the pins
        /// and the produce text, so it runs off the main thread.
        fun productsNearby(
            items: List<ShoppingItem>, pins: List<FarmPin>, produce: Map<String, String>,
            originLat: Double, originLng: Double, radiusKm: Double,
        ): List<ProductNearby> {
            val near = pins.mapNotNull { pin ->
                val sells = produce[pin.osmId] ?: return@mapNotNull null
                val km = pin.distanceMeters(originLat, originLng) / 1000
                if (km <= radiusKm) sells to km else null
            }
            return items.mapNotNull { item ->
                var count = 0
                var nearest = Double.POSITIVE_INFINITY
                for ((sells, km) in near) if (ProductMatch.covers(sells, item.terms)) {
                    count += 1
                    nearest = minOf(nearest, km)
                }
                if (count > 0) ProductNearby(item, count, nearest) else null
            }.sortedByDescending { it.count }
        }
    }

    /// Random feed of farms that at least have a photo. When we know where
    /// the user is, farms within 75 km lead the feed (shuffled), with the
    /// rest shuffled in after.
    fun feedPicks(location: Location?, limit: Int = 40): List<FarmPin> {
        val withImage = _pins.value.filter { it.image != null }
        if (location == null) return withImage.shuffled().take(limit)
        val (nearby, rest) = withImage.partition {
            it.distanceMeters(location.latitude, location.longitude) <= 75_000
        }
        return (nearby.shuffled() + rest.shuffled()).take(limit)
    }

    /// The recommendation-shelf source — photo'd farms, nearby first, minus
    /// anything already hearted. Mirrors iOS TripRecommendations.select() without
    /// the Trips coupling (no planned/stop exclusions yet); swap this the day a
    /// real recommendation source exists, same as the iOS note says.
    fun recommendations(
        location: Location?,
        excluding: Set<String> = emptySet(),
        limit: Int = 6,
    ): List<FarmPin> =
        feedPicks(location, limit = 40).filter { it.osmId !in excluding }.take(limit)

    /// Farms within `radiusKm` that have at least one photo, nearest first. Powers
    /// the onboarding "farms near you" shelf. Mirrors iOS nearbyWithImages.
    fun nearbyWithImages(lat: Double, lng: Double, radiusKm: Double = 100.0): List<FarmPin> =
        _pins.value
            .filter { it.image != null }
            .map { it to it.distanceMeters(lat, lng) }
            .filter { it.second <= radiusKm * 1000 }
            .sortedBy { it.second }
            .map { it.first }
}
