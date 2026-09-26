package app.farmsy.android.core

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// MARK: - Route API (POST /api/route)

/// Road routing through the web's proxy (no key in the app). Returns the road
/// line plus per-leg distance/duration. Non-200 → caller falls back to straight
/// lines. Mirrors iOS RouteAPI.
object RouteAPI {
    @Serializable
    data class Response(
        val coordinates: List<List<Double>>? = null,
        val distance: Double? = null,
        val duration: Double? = null,
        val segments: List<Segment>? = null,
    ) {
        @Serializable
        data class Segment(val distance: Double? = null, val duration: Double? = null)
    }

    @Serializable
    private data class Request(val coordinates: List<List<Double>>)

    /// Stops are (lat, lng) on our side; the API wants [lng, lat] — swap here.
    suspend fun route(stops: List<LatLng>): Response? {
        if (stops.size < 2 || stops.size > 50) return null
        return try {
            val coords = stops.map { listOf(it.longitude, it.latitude) }
            // A third stop is Plus; the server decides from the session. Two
            // stops route for everyone, token or not.
            val token = supabase.auth.currentAccessTokenOrNull()
            val resp = httpClient.post("${Backend.WEB_API}/route") {
                header(HttpHeaders.ContentType, "application/json")
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                setBody(lenientJson.encodeToString(Request(coords)))
            }
            if (resp.status.value != 200) return null
            lenientJson.decodeFromString<Response>(resp.bodyAsText())
        } catch (e: Exception) {
            null
        }
    }
}

// MARK: - Travel mode

/// How the trip is travelled. The web's road proxy only routes `driving-car`, so
/// the drawn road line is the same for every mode — but the time estimate and the
/// Google Maps hand-off honour the choice. Mirrors iOS TravelMode.
enum class TravelMode(val kmh: Double, val googleMode: String) {
    CAR(55.0, "driving"),
    BIKE(15.0, "bicycling"),
    WALK(4.8, "walking");

    fun minutes(km: Double): Int = Math.round(km / kmh * 60).toInt()

    companion object {
        fun fromRaw(s: String?): TravelMode? = entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
    }
}

// MARK: - Geometry (straight-line, no network)

object TripGeometry {
    fun haversineKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0
        val dLat = (b.latitude - a.latitude) * Math.PI / 180
        val dLng = (b.longitude - a.longitude) * Math.PI / 180
        val la1 = a.latitude * Math.PI / 180
        val la2 = b.latitude * Math.PI / 180
        val h = sin(dLat / 2) * sin(dLat / 2) + sin(dLng / 2) * sin(dLng / 2) * cos(la1) * cos(la2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun lengthKm(stops: List<LatLng>): Double {
        if (stops.size < 2) return 0.0
        return stops.zipWithNext().sumOf { haversineKm(it.first, it.second) }
    }

    /// Nearest-neighbour + 2-opt on straight-line distance. Returns the stop
    /// indices reordered; index 0 is pinned (people start from the farm they care
    /// about). Mirrors iOS TripGeometry.optimise.
    fun optimise(stops: List<LatLng>): List<Int> {
        val n = stops.size
        if (n <= 2) return (0 until n).toList()
        val d = Array(n) { i -> DoubleArray(n) { j -> haversineKm(stops[i], stops[j]) } }

        val order = mutableListOf(0)
        val used = mutableSetOf(0)
        while (order.size < n) {
            val last = order.last()
            val next = (0 until n).filter { it !in used }.minByOrNull { d[last][it] }!!
            order.add(next); used.add(next)
        }
        var improved = true
        var passes = 0
        while (improved && passes < 50) {
            improved = false; passes++
            for (i in 1 until n - 1) {
                for (k in i + 1 until n) {
                    val a = order[i - 1]; val b = order[i]; val c = order[k]
                    val e = if (k + 1 < n) order[k + 1] else -1
                    val before = d[a][b] + (if (e >= 0) d[c][e] else 0.0)
                    val after = d[a][c] + (if (e >= 0) d[b][e] else 0.0)
                    if (after + 1e-9 < before) {
                        order.subList(i, k + 1).reverse(); improved = true
                    }
                }
            }
        }
        return order
    }
}

// MARK: - Saved trip (DB: trips + trip_farms)

data class SavedTrip(val id: String, val name: String, val updatedAt: String?, val stopCount: Int)

@Serializable
private data class SavedTripRow(
    val id: String,
    val name: String = "Trip",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("trip_farms") val tripFarms: List<CountRow> = emptyList(),
) {
    @Serializable data class CountRow(val count: Int = 0)
}

// MARK: - Trip store

/// The trip planner's engine — the Android twin of iOS TripStore. The draft
/// (stops, origin, mode) is local (SharedPreferences); named trips persist to the
/// same `trips` / `trip_farms` tables the web uses. The route line comes from
/// POST /api/route and traces itself in.
class TripStore(context: Context, private val scope: CoroutineScope) {

    companion object {
        /// Free users with 2+ stops: ordering the stops and drawing the road
        /// between them is the Plus work (Task 4). `stopCount` is the trip's
        /// farm stops, not the leg count — a single farm is always free to
        /// route to.
        fun isRouteLocked(hasFullAccess: Boolean, stopCount: Int): Boolean = !hasFullAccess && stopCount >= 2
    }

    private val prefs = context.getSharedPreferences("farmsy_trip", Context.MODE_PRIVATE)
    private val stopsKey = "dlb_pending_trip"
    private val originKey = "dlb_trip_origin"
    private val originLabelKey = "dlb_trip_origin_label"
    private val destinationKey = "dlb_trip_destination"
    private val destinationLabelKey = "dlb_trip_destination_label"
    private val ownerKey = "dlb_trip_owner"
    private val modeKey = "dlb_trip_mode"
    private val tripDateKey = "dlb_trip_date"
    private val wantedKey = "dlb_shopping_list"

    // Draft (local).
    private val _stopIds = MutableStateFlow<List<String>>(emptyList())
    val stopIds: StateFlow<List<String>> = _stopIds.asStateFlow()

    private val _originCoord = MutableStateFlow<LatLng?>(null)
    val originCoord: StateFlow<LatLng?> = _originCoord.asStateFlow()
    private val _originLabel = MutableStateFlow<String?>(null)
    val originLabel: StateFlow<String?> = _originLabel.asStateFlow()

    /// Where the drive ends (R1). Null is the pre-R1 shape and stays legal: a
    /// trip that is only a list of farms is still a trip.
    private val _destinationCoord = MutableStateFlow<LatLng?>(null)
    val destinationCoord: StateFlow<LatLng?> = _destinationCoord.asStateFlow()
    private val _destinationLabel = MutableStateFlow<String?>(null)
    val destinationLabel: StateFlow<String?> = _destinationLabel.asStateFlow()

    /// The day this drive is for (R7), `yyyy-mm-dd`. Null until somebody picks
    /// one: a trip that never had a day must reopen against the planner's
    /// default rather than claim it was planned for a date in the past.
    private val _tripDate = MutableStateFlow<String?>(null)
    val tripDate: StateFlow<String?> = _tripDate.asStateFlow()

    private val _mode = MutableStateFlow(TravelMode.CAR)
    val mode: StateFlow<TravelMode> = _mode.asStateFlow()

    // R5 — the product chips picked for the corridor (agreed-term keys, e.g.
    // "strawberries"). On the TRIP, not the corridor view, so editing the route does
    // not silently clear the picks. Empty = no product filter. Several picked means
    // ANY of them, never all. Mirrors iOS TripStore.selectedProducts.
    private val _selectedProducts = MutableStateFlow<Set<String>>(emptySet())
    val selectedProducts: StateFlow<Set<String>> = _selectedProducts.asStateFlow()

    /// The shopping list: `ShoppingItem` ids, in the order they were picked.
    /// Ids rather than words, because the label is presentation and the terms
    /// that match it are served. Lives with the draft because it is how the
    /// draft gets filled.
    private val _wantedProducts = MutableStateFlow<List<String>>(emptyList())
    val wantedProducts: StateFlow<List<String>> = _wantedProducts.asStateFlow()

    var editingTripId: String? = null
        private set

    // Route.
    private val _routeLine = MutableStateFlow<List<LatLng>>(emptyList())
    val routeLine: StateFlow<List<LatLng>> = _routeLine.asStateFlow()
    private val _distanceMeters = MutableStateFlow<Double?>(null)
    val distanceMeters: StateFlow<Double?> = _distanceMeters.asStateFlow()
    private val _durationSeconds = MutableStateFlow<Double?>(null)
    val durationSeconds: StateFlow<Double?> = _durationSeconds.asStateFlow()
    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()
    private val _onRoads = MutableStateFlow(false)
    val onRoads: StateFlow<Boolean> = _onRoads.asStateFlow()
    private val _traceProgress = MutableStateFlow(1.0)
    val traceProgress: StateFlow<Double> = _traceProgress.asStateFlow()

    private var traceJob: Job? = null
    private var cumLen: List<Double> = emptyList()
    private var totalLen: Double = 0.0

    // Saved trips.
    private val _savedTrips = MutableStateFlow<List<SavedTrip>>(emptyList())
    val savedTrips: StateFlow<List<SavedTrip>> = _savedTrips.asStateFlow()
    private val _plannedFarmIds = MutableStateFlow<Set<String>>(emptySet())
    val plannedFarmIds: StateFlow<Set<String>> = _plannedFarmIds.asStateFlow()

    /// Bumped whenever the map should refit to the whole trip (opening a saved
    /// trip, setting the origin).
    private val _fitToken = MutableStateFlow(0)
    val fitToken: StateFlow<Int> = _fitToken.asStateFlow()
    /// True when the caller also leaves the map full-screen — "Show route"
    /// dismisses the planner, so the route should sit in the middle. The default
    /// fit biases it upward to clear the trips sheet, which is right when the
    /// sheet is still up (opening a saved trip, setting the origin) and wrong
    /// when it is not: the route ends up crammed into the top of an empty map.
    var fitCentered: Boolean = false
        private set

    fun requestFit(centered: Boolean = false) { fitCentered = centered; _fitToken.value += 1 }

    /// Route answers keyed by the stops they belong to (failures cached as null too).
    private val routeCache = HashMap<String, RouteAPI.Response?>()

    init {
        _stopIds.value = readStops()
        val o = prefs.getString(originKey, null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }
        if (o != null && o.size >= 2) {
            _originCoord.value = LatLng(o[0], o[1])
            _originLabel.value = prefs.getString(originLabelKey, null)
        }
        val d = prefs.getString(destinationKey, null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }
        if (d != null && d.size >= 2) {
            _destinationCoord.value = LatLng(d[0], d[1])
            _destinationLabel.value = prefs.getString(destinationLabelKey, null)
        }
        // Validated on the way in as well as on the way out: a day written by
        // an older build, or edited by hand, is not a day this build trusts.
        _tripDate.value = TripEndpoints.validDate(prefs.getString(tripDateKey, null))
        TravelMode.fromRaw(prefs.getString(modeKey, null))?.let { _mode.value = it }
        _wantedProducts.value = readWanted()
    }

    // MARK: Shopping list

    /// On or off. Picking keeps the order things were chosen in, which is the
    /// order the answer lists them back.
    fun toggleProduct(id: String) {
        val cur = _wantedProducts.value
        _wantedProducts.value = if (id in cur) cur - id else cur + id
        persistWanted()
    }

    fun removeProduct(id: String) {
        _wantedProducts.value = _wantedProducts.value.filterNot { it == id }
        persistWanted()
    }

    fun clearProducts() {
        _wantedProducts.value = emptyList()
        persistWanted()
    }

    /// Add planned stops to the draft, keeping the planner's order and skipping
    /// farms already on the trip — filling a list twice must not duplicate stops.
    fun addStops(osmIds: List<String>) {
        _stopIds.value = _stopIds.value + osmIds.filterNot { it in _stopIds.value }
        persist()
    }

    private fun persistWanted() {
        prefs.edit().putString(wantedKey, lenientJson.encodeToString(_wantedProducts.value)).apply()
    }

    private fun readWanted(): List<String> =
        prefs.getString(wantedKey, null)?.let {
            runCatching { lenientJson.decodeFromString<List<String>>(it) }.getOrNull()
        } ?: emptyList()

    /// The visible portion of the route while it traces in — cut at the exact
    /// distance the progress represents, with an interpolated tip. Read reactively
    /// via routeLine + traceProgress. Mirrors iOS tracedLine.
    fun tracedLine(): List<LatLng> {
        val line = _routeLine.value
        val progress = _traceProgress.value
        if (progress >= 1 || line.size <= 2 || totalLen <= 0) return line
        val target = totalLen * progress
        var i = 0
        while (i + 1 < cumLen.size && cumLen[i + 1] < target) i++
        val out = line.take(i + 1).toMutableList()
        if (i + 1 < line.size) {
            val seg = cumLen[i + 1] - cumLen[i]
            val frac = if (seg > 0) (target - cumLen[i]) / seg else 0.0
            val a = line[i]; val b = line[i + 1]
            out.add(LatLng(a.latitude + (b.latitude - a.latitude) * frac, a.longitude + (b.longitude - a.longitude) * frac))
        }
        return if (out.size >= 2) out else line.take(2)
    }

    private fun setRouteLine(line: List<LatLng>) {
        _routeLine.value = line
        if (line.size < 2) { cumLen = emptyList(); totalLen = 0.0; return }
        val cum = ArrayList<Double>(line.size)
        cum.add(0.0)
        var acc = 0.0
        for (i in 1 until line.size) {
            acc += TripGeometry.haversineKm(line[i - 1], line[i])
            cum.add(acc)
        }
        cumLen = cum; totalLen = acc
    }

    /// Draw the road from the start over ~2.2s with a cubic ease-out, restarted
    /// from zero on every new route.
    private fun startTrace() {
        traceJob?.cancel()
        if (_routeLine.value.size <= 2) { _traceProgress.value = 1.0; return }
        _traceProgress.value = 0.0
        traceJob = scope.launch {
            val duration = 2.2
            val start = System.currentTimeMillis()
            while (isActive) {
                val t = min((System.currentTimeMillis() - start) / 1000.0 / duration, 1.0)
                _traceProgress.value = 1 - (1 - t).pow(3)
                if (t >= 1) break
                delay(16)
            }
        }
    }

    fun setMode(m: TravelMode) {
        if (m == _mode.value) return
        _mode.value = m
        prefs.edit().putString(modeKey, m.name).apply()
    }

    // R5 — corridor product filter. Named distinctly from the shopping list's
    // toggleProduct/clearProducts (which act on wantedProducts): these act on
    // selectedProducts, the corridor chips. Kept on the trip so a re-route leaves
    // the picks in place.
    fun toggleCorridorProduct(key: String) {
        _selectedProducts.value = _selectedProducts.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }
    }
    fun clearCorridorProducts() { _selectedProducts.value = emptySet() }

    // R6 — corridor day/departure bridge. Luuk's R7 stores the chosen day as an ISO
    // string (TripEndpoints, persisted to DB + device); the R4 corridor needs it as a
    // Mon-indexed weekday plus a departure minute to place each farm's arrival. Derive
    // both from his model so the date has one source of truth, not two. Android has no
    // departure-time picker (his endpoints leave departMinutes null), so departure is
    // the 10:00 default.
    val resolvedDepartMinutes: Int get() = 600
    val resolvedDayMon: Int get() {
        val day = TripEndpoints.day(_tripDate.value) ?: TripEndpoints.day(TripEndpoints.nextSaturday())
        return (day?.dayOfWeek?.value ?: 6) - 1   // java DayOfWeek 1=Mon…7=Sun → 0=Mon…6=Sun
    }

    // MARK: Draft

    fun contains(osmId: String): Boolean = _stopIds.value.contains(osmId)

    fun toggle(osmId: String) {
        _stopIds.value = if (osmId in _stopIds.value) _stopIds.value - osmId else _stopIds.value + osmId
        persist()
    }

    fun remove(osmId: String) { _stopIds.value = _stopIds.value - osmId; persist() }

    fun move(from: Int, to: Int) {
        val list = _stopIds.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _stopIds.value = list; persist()
    }

    /// Clears the stops and editing state — but keeps the origin.
    fun clear() {
        _stopIds.value = emptyList(); editingTripId = null
        setRouteLine(emptyList()); _distanceMeters.value = null; _durationSeconds.value = null; _onRoads.value = false
        persist()
    }

    fun setOrigin(coord: LatLng, label: String) {
        _originCoord.value = coord; _originLabel.value = label
        prefs.edit()
            .putString(originKey, "${coord.latitude},${coord.longitude}")
            .putString(originLabelKey, label)
            .apply()
        requestFit()
    }

    fun clearOrigin() {
        _originCoord.value = null; _originLabel.value = null
        prefs.edit().remove(originKey).remove(originLabelKey).apply()
    }

    fun setDestination(coord: LatLng, label: String) {
        _destinationCoord.value = coord; _destinationLabel.value = label
        prefs.edit()
            .putString(destinationKey, "${coord.latitude},${coord.longitude}")
            .putString(destinationLabelKey, label)
            .apply()
        requestFit()
    }

    /// Choose the day. A value that is not a day clears it rather than storing
    /// it — the picker cannot produce one, but a restored preference can.
    fun setTripDate(date: String?) {
        val valid = TripEndpoints.validDate(date)
        _tripDate.value = valid
        if (valid == null) prefs.edit().remove(tripDateKey).apply()
        else prefs.edit().putString(tripDateKey, valid).apply()
    }

    fun clearTripDate() = setTripDate(null)

    fun clearDestination() {
        _destinationCoord.value = null; _destinationLabel.value = null
        prefs.edit().remove(destinationKey).remove(destinationLabelKey).apply()
    }

    /// The ends as they are stored (R1). One place builds them, so the insert,
    /// the update and the Maps hand-off cannot disagree about what the trip is.
    val endpoints: TripEndpoints
        get() = TripEndpoints(
            origin = TripPlace.make(_originCoord.value, _originLabel.value),
            destination = TripPlace.make(_destinationCoord.value, _destinationLabel.value),
            radiusKm = null,
            date = _tripDate.value,
            // Not offered anywhere yet. The column exists (059) and the rules
            // are shared, so the day can ship without waiting for a time.
            departMinutes = null,
        )

    /// Wipe the draft if the account changed. Signed-out counts as owner "anon".
    fun reconcileOwner(userId: String?) {
        val owner = userId ?: "anon"
        val stored = prefs.getString(ownerKey, null)
        if (stored != null && stored != owner) {
            _stopIds.value = emptyList(); editingTripId = null
            _wantedProducts.value = emptyList()
            setRouteLine(emptyList()); _distanceMeters.value = null; _durationSeconds.value = null
            persist(); persistWanted()
        }
        prefs.edit().putString(ownerKey, owner).apply()
    }

    // MARK: Ordering

    /// The full leg list: origin, then farms, then the destination (R1). Any of
    /// the three may be absent — farms alone is still a drive.
    ///
    /// `includingDestination = false` is for the reordering below, and it is not
    /// a convenience. TripGeometry.optimise pins only the first leg, so a
    /// destination on the end would be shuffled in among the farms: the order
    /// would be optimised for a road that ends somewhere it does not, and the
    /// index mapping underneath would then drop it, leaving a saved order that
    /// belongs to a route nobody planned. Pinning the last leg as well is a
    /// change to TripGeometry, and it belongs in its own commit.
    private fun legs(pins: Map<String, FarmPin>, includingDestination: Boolean = true): List<LatLng> {
        val out = ArrayList<LatLng>()
        _originCoord.value?.let { out.add(it) }
        out.addAll(_stopIds.value.mapNotNull { pins[it]?.let { p -> LatLng(p.lat, p.lng) } })
        if (includingDestination) _destinationCoord.value?.let { out.add(it) }
        return out
    }

    /// "Best order" — reorders the farms (origin pinned as leg zero). Returns km saved.
    fun optimise(pins: Map<String, FarmPin>): Double {
        val farms = _stopIds.value.mapNotNull { pins[it] }
        if (farms.size <= 2) return 0.0
        val coords = legs(pins, includingDestination = false)
        val before = TripGeometry.lengthKm(coords)
        val order = TripGeometry.optimise(coords)
        val hasOrigin = _originCoord.value != null
        val farmOrder = order.mapNotNull { idx ->
            val f = if (hasOrigin) idx - 1 else idx
            if (f in farms.indices) farms[f].osmId else null
        }
        _stopIds.value = farmOrder
        persist()
        val after = TripGeometry.lengthKm(legs(pins, includingDestination = false))
        return before - after
    }

    // MARK: Route

    private fun keyOf(coords: List<LatLng>): String =
        coords.joinToString(";") { "%.5f,%.5f".format(it.latitude, it.longitude) }

    /// `locked`: the caller has already decided (via `isRouteLocked`) that this
    /// is a free user's multi-stop trip. Skip the request entirely rather than
    /// let the server 402 it — and drop any stale line/totals so the locked
    /// screen never shows the paid answer.
    suspend fun refreshRoute(pins: Map<String, FarmPin>, locked: Boolean = false) {
        if (locked) {
            setRouteLine(emptyList()); _distanceMeters.value = null; _durationSeconds.value = null; _onRoads.value = false
            return
        }
        val coords = legs(pins)
        if (coords.size < 2) {
            setRouteLine(emptyList()); _distanceMeters.value = null; _durationSeconds.value = null; _onRoads.value = false
            return
        }
        val key = keyOf(coords)
        if (routeCache.containsKey(key)) { apply(routeCache[key], coords); return }
        _isRouting.value = true
        try {
            val r = RouteAPI.route(coords)
            routeCache[key] = r
            apply(r, coords)
        } finally {
            _isRouting.value = false
        }
    }

    private fun apply(r: RouteAPI.Response?, straight: List<LatLng>) {
        val line = r?.coordinates
        if (r != null && line != null && line.size >= 2) {
            setRouteLine(line.map { LatLng(it[1], it[0]) })
            _distanceMeters.value = r.distance
            // ORS only routes driving-car, so trust its duration for driving and
            // re-derive from the road distance at bike/walk speed otherwise.
            _durationSeconds.value = when {
                _mode.value == TravelMode.CAR -> r.duration
                r.distance != null -> _mode.value.minutes(r.distance / 1000).toDouble() * 60
                else -> r.duration
            }
            _onRoads.value = true
            startTrace()
        } else {
            setRouteLine(straight)
            val km = TripGeometry.lengthKm(straight)
            _distanceMeters.value = km * 1000
            _durationSeconds.value = _mode.value.minutes(km).toDouble() * 60
            _onRoads.value = false
            traceJob?.cancel(); _traceProgress.value = 1.0   // straight lines draw instantly
        }
    }

    // MARK: Saved trips (DB)

    suspend fun loadTrips(userId: String) {
        val rows = runCatching {
            supabase.from("trips")
                .select(Columns.raw("id, name, updated_at, trip_farms(count)")) {
                    filter { eq("user_id", userId) }
                    order("updated_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<SavedTripRow>()
        }.getOrDefault(emptyList())
        _savedTrips.value = rows.map { SavedTrip(it.id, it.name, it.updatedAt, it.tripFarms.firstOrNull()?.count ?: 0) }
        loadPlannedFarmIds()
    }

    private suspend fun loadPlannedFarmIds() {
        val ids = _savedTrips.value.map { it.id }
        if (ids.isEmpty()) { _plannedFarmIds.value = emptySet(); return }
        val rows = runCatching {
            supabase.from("trip_farms")
                .select(Columns.list("farm_osm_id")) {
                    filter { isIn("trip_id", ids) }
                }
                .decodeList<PlannedRow>()
        }.getOrDefault(emptyList())
        _plannedFarmIds.value = rows.map { it.farmOsmId }.toSet()
    }

    @Serializable
    private data class PlannedRow(@SerialName("farm_osm_id") val farmOsmId: String)

    /// Save the current draft as a trip (insert, or update when editing). Caches
    /// the farm details on each stop row so a share link survives.
    suspend fun save(name: String, userId: String, pins: Map<String, FarmPin>) {
        // R1: the ends travel with the row. Built once so the insert and the
        // update cannot disagree about what the trip is.
        val ends = TripEndpointRow.from(endpoints)

        val tripId: String = if (editingTripId != null) {
            val id = editingTripId!!
            runCatching {
                supabase.from("trips").update(
                    buildJsonObject {
                        put("name", JsonPrimitive(name))
                        put("updated_at", JsonPrimitive(nowIso()))
                        putEndpoints(ends)
                    }
                ) { filter { eq("id", id) } }
                supabase.from("trip_farms").delete { filter { eq("trip_id", id) } }
            }
            id
        } else {
            val inserted = runCatching {
                supabase.from("trips").insert(
                    buildJsonObject {
                        put("user_id", JsonPrimitive(userId))
                        put("name", JsonPrimitive(name))
                        putEndpoints(ends)
                    }
                ) { select() }.decodeList<InsertedId>()
            }.getOrNull()
            inserted?.firstOrNull()?.id ?: return
        }

        val stopRows = _stopIds.value.mapIndexedNotNull { i, osmId ->
            val p = pins[osmId] ?: return@mapIndexedNotNull null
            buildJsonObject {
                put("trip_id", JsonPrimitive(tripId))
                put("farm_osm_id", JsonPrimitive(osmId))
                put("farm_name", JsonPrimitive(p.name))
                put("farm_lat", JsonPrimitive(p.lat))
                put("farm_lng", JsonPrimitive(p.lng))
                put("farm_city", p.city?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?))
                put("farm_image", p.image?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?))
                put("sort_order", JsonPrimitive(i))
            }
        }
        runCatching {
            supabase.from("trip_farms").insert(stopRows)
            editingTripId = tripId
            loadTrips(userId)
        }.onFailure {
            // A trip without its farms is worse than none — roll a fresh trip back.
            if (editingTripId == null) runCatching { supabase.from("trips").delete { filter { eq("id", tripId) } } }
        }
    }

    suspend fun openTrip(id: String) {
        val stops = runCatching {
            supabase.from("trip_farms")
                .select(Columns.list("farm_osm_id, sort_order")) {
                    filter { eq("trip_id", id) }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<StopRow>()
        }.getOrDefault(emptyList())
        _stopIds.value = stops.map { it.farmOsmId }

        // R1: put the drive back the way it was saved. Before this the ends were
        // never written, so an opened trip started at its first farm rather than
        // where the person actually set out from. A trip from before R1 decodes
        // to no ends at all, which is a working plan and not a failure.
        val ends = runCatching {
            supabase.from("trips")
                .select(Columns.list(TripEndpointRow.COLUMNS)) { filter { eq("id", id) } }
                .decodeList<TripEndpointRow>()
                .firstOrNull()
        }.getOrNull()?.endpoints ?: TripEndpoints.NONE

        val o = ends.origin
        if (o != null) setOrigin(o.latLng, o.label) else clearOrigin()
        val dest = ends.destination
        if (dest != null) setDestination(dest.latLng, dest.label) else clearDestination()
        // Unconditional, including the null: reopening a trip saved before R7
        // must clear whatever day the last trip left behind, not inherit it.
        setTripDate(ends.date)

        editingTripId = id
        persist()
        requestFit()
    }

    suspend fun deleteTrip(id: String) {
        _savedTrips.value = _savedTrips.value.filterNot { it.id == id }   // optimistic
        runCatching { supabase.from("trips").delete { filter { eq("id", id) } } }
    }

    @Serializable
    private data class StopRow(
        @SerialName("farm_osm_id") val farmOsmId: String,
        @SerialName("sort_order") val sortOrder: Int = 0,
    )

    @Serializable
    private data class InsertedId(val id: String)

    // MARK: Persistence

    private fun persist() {
        prefs.edit().putString(stopsKey, lenientJson.encodeToString(_stopIds.value)).apply()
    }

    private fun readStops(): List<String> =
        prefs.getString(stopsKey, null)?.let {
            runCatching { lenientJson.decodeFromString<List<String>>(it) }.getOrNull()
        } ?: emptyList()

    private fun nowIso(): String =
        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
