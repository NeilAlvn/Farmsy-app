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
    val selectedCategory = MutableStateFlow<FarmCategory?>(null)

    // Quick filters + the two new axes (Aviah's taxonomy) — mirror iOS. They
    // combine with the category selection rather than replacing it.
    val filterVerified = MutableStateFlow(false)
    val filterOpenToday = MutableStateFlow(false)
    val filterAutomaat = MutableStateFlow(false)
    val filterZelfpluk = MutableStateFlow(false)
    val filterHasPhotos = MutableStateFlow(false)
    val selectedPlaceTypes = MutableStateFlow<Set<String>>(emptySet())
    val selectedMethods = MutableStateFlow<Set<String>>(emptySet())

    fun anyFilterOn(): Boolean =
        selectedCategory.value != null ||
            filterVerified.value || filterOpenToday.value || filterAutomaat.value ||
            filterZelfpluk.value || filterHasPhotos.value ||
            selectedPlaceTypes.value.isNotEmpty() || selectedMethods.value.isNotEmpty()

    fun clearAllFilters() {
        selectedCategory.value = null
        filterVerified.value = false; filterOpenToday.value = false
        filterAutomaat.value = false; filterZelfpluk.value = false; filterHasPhotos.value = false
        selectedPlaceTypes.value = emptySet(); selectedMethods.value = emptySet()
    }

    // AI search — the parsed intent in effect. When set it drives filtering +
    // ranking; the summary bar shows what was understood. Mirrors iOS.
    val aiIntent = MutableStateFlow<SmartSearchIntent?>(null)
    private val _aiCenter = MutableStateFlow<Pair<Double, Double>?>(null)
    val aiCenter: StateFlow<Pair<Double, Double>?> = _aiCenter.asStateFlow()
    private val _aiPlaceToken = MutableStateFlow(0)
    val aiPlaceToken: StateFlow<Int> = _aiPlaceToken.asStateFlow()

    // Flags maps (loaded once from /api/farms/flags).
    private var flagsLoaded = false
    private var produceByOsm: Map<String, String> = emptyMap()
    private var locationTypesByOsm: Map<String, List<String>> = emptyMap()
    private var methodsByOsm: Map<String, List<String>> = emptyMap()

    private val radiusNearMe = 15.0
    private val radiusNamedPlace = 25.0

    private val pageSize = 1000

    /// Fetch the flags endpoint once → produce/place-type/method lookups for
    /// AI-search matching (and, later, the filter groups).
    suspend fun loadFlagsIfNeeded() {
        if (flagsLoaded) return
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
            flagsLoaded = true
        } catch (e: Exception) { /* leave maps empty; AI still filters on categories */ }
    }

    /// Apply a parsed AI intent — takes over filtering and resolves the centre
    /// (server `center`, or the user's own location for a nearMe query).
    suspend fun applyAISearch(intent: SmartSearchIntent, userLocation: Location?) {
        loadFlagsIfNeeded()
        selectedCategory.value = null
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
        selectedCategory.value?.let { cat ->
            result = result.filter { it.categories.contains(cat) }
        }
        if (filterVerified.value) result = result.filter { it.isVerified }
        if (filterOpenToday.value) result = result.filter { FarmFilters.isOpenToday(it.openingHours) }
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
            result = result.filter {
                it.name.lowercase().contains(query) ||
                    (it.city?.lowercase()?.contains(query) ?: false) ||
                    (it.postalCode?.lowercase()?.contains(query) ?: false)
            }
        }
        return result
    }

    private fun filteredForAI(ai: SmartSearchIntent): List<FarmPin> {
        var result = _pins.value
        val cats = ai.categories.mapNotNull { FarmCategory.from(it) }.toSet()
        val prods = ai.products.map { it.lowercase() }
        // What they sell: category OR produce text (OR'd — thin produce coverage
        // must not drop farms the category already accounts for).
        if (cats.isNotEmpty() || prods.isNotEmpty()) {
            result = result.filter { pin ->
                val catMatch = cats.isNotEmpty() && pin.categories.any { it in cats }
                val prodMatch = prods.isNotEmpty() &&
                    (produceByOsm[pin.osmId]?.let { txt -> prods.any { txt.contains(it) } } ?: false)
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
}
