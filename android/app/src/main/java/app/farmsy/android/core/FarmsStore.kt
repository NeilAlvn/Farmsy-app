package app.farmsy.android.core

import android.location.Location
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val pageSize = 1000

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

    /// Pins matching the current search + category filter.
    fun filtered(): List<FarmPin> {
        var result = _pins.value
        selectedCategory.value?.let { cat ->
            result = result.filter { it.categories.contains(cat) }
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
}
