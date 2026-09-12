import Foundation
import SwiftUI
import CoreLocation
import Observation

// MARK: - Route API (POST /api/route)

/// Road routing through the web's proxy (no key in the app). Returns the road
/// line plus per-leg distance/duration. 501/502 → fall back to straight lines.
enum RouteAPI {
    struct Response: Decodable {
        let coordinates: [[Double]]?
        let distance: Double?
        let duration: Double?
        let segments: [Segment]?
        struct Segment: Decodable { let distance: Double?; let duration: Double? }
    }

    /// Stops are [lat, lng] on our side; the API wants [lng, lat] — swap here.
    static func route(_ stops: [CLLocationCoordinate2D]) async -> Response? {
        guard stops.count >= 2, stops.count <= 50 else { return nil }
        var request = URLRequest(url: Backend.webAPI.appending(path: "route"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let coords = stops.map { [$0.longitude, $0.latitude] }
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["coordinates": coords])
        guard let (data, resp) = try? await URLSession.shared.data(for: request),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let decoded = try? JSONDecoder().decode(Response.self, from: data)
        else { return nil }
        return decoded
    }
}

// MARK: - Travel mode

/// How the trip is travelled. The web's road proxy only routes `driving-car`, so
/// the drawn road line is the same for every mode for now — but the time
/// estimate and the Google Maps hand-off both honour the choice, and the drive
/// distance is a fair stand-in for the bike/walk path over the same roads.
enum TravelMode: String, CaseIterable {
    case car, bike, walk

    var label: String {
        switch self {
        case .car:  return String(localized: "Drive")
        case .bike: return String(localized: "Bike")
        case .walk: return String(localized: "Walk")
        }
    }
    var icon: String {
        switch self {
        case .car:  return "car.fill"
        case .bike: return "bicycle"
        case .walk: return "figure.walk"
        }
    }
    /// Google Maps deep-link `travelmode`.
    var googleMode: String {
        switch self {
        case .car:  return "driving"
        case .bike: return "bicycling"
        case .walk: return "walking"
        }
    }
    /// Rough average speed for the time estimate. Deliberately conservative;
    /// presented as "about", never as an arrival time.
    var kmh: Double {
        switch self {
        case .car:  return 55
        case .bike: return 15
        case .walk: return 4.8
        }
    }
    func minutes(km: Double) -> Int { Int((km / kmh * 60).rounded()) }
}

// MARK: - Geometry (straight-line, no network)

enum TripGeometry {
    static func haversineKm(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        let r = 6371.0
        let dLat = (b.latitude - a.latitude) * .pi / 180
        let dLng = (b.longitude - a.longitude) * .pi / 180
        let la1 = a.latitude * .pi / 180, la2 = b.latitude * .pi / 180
        let h = sin(dLat / 2) * sin(dLat / 2) + sin(dLng / 2) * sin(dLng / 2) * cos(la1) * cos(la2)
        return 2 * r * asin(min(1, sqrt(h)))
    }

    static func lengthKm(_ stops: [CLLocationCoordinate2D]) -> Double {
        guard stops.count > 1 else { return 0 }
        return zip(stops, stops.dropFirst()).reduce(0) { $0 + haversineKm($1.0, $1.1) }
    }

    /// 55 km/h under-estimates motorways and over-estimates lanes; straight-line
    /// is already shorter than road, so the two errors cancel. Present as "about".
    static func roughDriveMinutes(_ km: Double) -> Int { Int((km / 55 * 60).rounded()) }

    /// Nearest-neighbour + 2-opt on straight-line distance. Returns the stops
    /// reordered; index 0 is pinned (people start from the farm they care about).
    static func optimise(_ stops: [CLLocationCoordinate2D]) -> [Int] {
        let n = stops.count
        guard n > 2 else { return Array(0..<n) }
        var d = Array(repeating: Array(repeating: 0.0, count: n), count: n)
        for i in 0..<n { for j in 0..<n { d[i][j] = haversineKm(stops[i], stops[j]) } }

        // Nearest neighbour from 0.
        var order = [0]; var used = Set([0])
        while order.count < n {
            let last = order.last!
            let next = (0..<n).filter { !used.contains($0) }.min { d[last][$0] < d[last][$1] }!
            order.append(next); used.insert(next)
        }
        // 2-opt, first stop pinned.
        var improved = true, passes = 0
        while improved && passes < 50 {
            improved = false; passes += 1
            for i in 1..<(n - 1) {
                for k in (i + 1)..<n {
                    let a = order[i - 1], b = order[i], c = order[k]
                    let e = k + 1 < n ? order[k + 1] : -1
                    let before = d[a][b] + (e >= 0 ? d[c][e] : 0)
                    let after = d[a][c] + (e >= 0 ? d[b][e] : 0)
                    if after + 1e-9 < before {
                        order[i...k].reverse(); improved = true
                    }
                }
            }
        }
        return order
    }
}

// MARK: - Saved trip (DB: trips + trip_farms)

struct SavedTrip: Decodable, Identifiable {
    let id: String
    let name: String
    let updatedAt: String?
    let stopCount: Int

    enum CodingKeys: String, CodingKey {
        case id, name, updatedAt = "updated_at", tripFarms = "trip_farms"
    }
    private struct Count: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = (try? c.decode(String.self, forKey: .name)) ?? "Trip"
        updatedAt = try? c.decodeIfPresent(String.self, forKey: .updatedAt)
        let counts = (try? c.decodeIfPresent([Count].self, forKey: .tripFarms)) ?? []
        stopCount = counts.first?.count ?? 0
    }
}

// MARK: - Trip store

@MainActor
@Observable
final class TripStore {
    // Draft (local).
    private(set) var stopIds: [String] = []
    /// Starting point — leg zero. Survives clear().
    private(set) var originCoord: CLLocationCoordinate2D?
    private(set) var originLabel: String?
    /// Where the drive ends (R1). Nil is the pre-R1 shape and stays legal: a
    /// trip that is only a list of farms is still a trip.
    private(set) var destinationCoord: CLLocationCoordinate2D?
    private(set) var destinationLabel: String?
    /// Which trip is being edited (so save updates instead of duplicating).
    private(set) var editingTripId: String?

    // Route.
    private(set) var routeLine: [CLLocationCoordinate2D] = []
    private(set) var distanceMeters: Double?
    private(set) var durationSeconds: Double?
    private(set) var isRouting = false
    private(set) var onRoads = false
    /// Chosen travel mode — affects the time estimate and the Google Maps link.
    private(set) var mode: TravelMode = .car

    /// R6 — the day and departure the corridor answers "open when you pass" about.
    /// Both nullable, and deliberately NOT persisted: nil means "resolve the default
    /// at render" (never a frozen date), so a trip planned today and reopened next
    /// week answers about the *next* Saturday rather than a stale past one. The web's
    /// contract is the same.
    private(set) var tripDate: Date?
    /// Minutes past midnight to set out. nil → the 10:00 default.
    private(set) var departMinutes: Int?
    /// The line as it draws itself, sliced by the trace animation (0→1). The map
    /// renders this, not `routeLine`, so the route traces along the road.
    private(set) var traceProgress: Double = 1
    private var traceTask: Task<Void, Never>?
    /// Cumulative along-route distance to each vertex, and the total — so the
    /// trace advances at a constant speed by *distance* rather than by vertex
    /// count (vertices bunch up on bends, which made the tip lurch).
    private var cumLen: [Double] = []
    private var totalLen: Double = 0

    /// The visible portion of the route while it traces in — cut at the exact
    /// distance the progress represents, with an interpolated tip so the line
    /// grows smoothly along a segment instead of snapping vertex to vertex.
    var tracedLine: [CLLocationCoordinate2D] {
        guard traceProgress < 1, routeLine.count > 2, totalLen > 0 else { return routeLine }
        let target = totalLen * traceProgress
        var i = 0
        while i + 1 < cumLen.count && cumLen[i + 1] < target { i += 1 }
        var out = Array(routeLine.prefix(i + 1))
        if i + 1 < routeLine.count {
            let seg = cumLen[i + 1] - cumLen[i]
            let frac = seg > 0 ? (target - cumLen[i]) / seg : 0
            let a = routeLine[i], b = routeLine[i + 1]
            out.append(CLLocationCoordinate2D(
                latitude: a.latitude + (b.latitude - a.latitude) * frac,
                longitude: a.longitude + (b.longitude - a.longitude) * frac))
        }
        return out.count >= 2 ? out : Array(routeLine.prefix(2))
    }

    /// Set the drawn route and precompute its cumulative distances for the trace.
    private func setRouteLine(_ line: [CLLocationCoordinate2D]) {
        routeLine = line
        cumLen = []; totalLen = 0
        guard line.count > 1 else { return }
        cumLen.reserveCapacity(line.count)
        cumLen.append(0)
        var acc = 0.0
        for i in 1..<line.count {
            acc += TripGeometry.haversineKm(line[i - 1], line[i])
            cumLen.append(acc)
        }
        totalLen = acc
    }

    /// Draw the road from the start over ~2.2s with a cubic ease-out — restarted
    /// from zero on every new route (add / reorder / origin change).
    private func startTrace() {
        traceTask?.cancel()
        guard routeLine.count > 2 else { traceProgress = 1; return }
        traceProgress = 0
        traceTask = Task { [weak self] in
            let duration = 2.2
            let start = Date()
            while !Task.isCancelled {
                let t = min(Date().timeIntervalSince(start) / duration, 1)
                let eased = 1 - pow(1 - t, 3)
                await MainActor.run { self?.traceProgress = eased }
                if t >= 1 { break }
                try? await Task.sleep(nanoseconds: 16_000_000)   // ~60fps
            }
        }
    }

    // Saved trips.
    private(set) var savedTrips: [SavedTrip] = []
    /// Every farm the user has already planned to visit, across all saved trips —
    /// so "recommendations near you" never suggests one of them.
    private(set) var plannedFarmIds: Set<String> = []
    /// Bumped whenever the map should refit to the whole trip (opening a saved
    /// trip, setting the origin) — the map watches this.
    private(set) var fitToken = 0
    func requestFit() { fitToken += 1 }

    private let stopsKey = "dlb_pending_trip"
    private let originKey = "dlb_trip_origin"
    private let destinationKey = "dlb_trip_destination"
    private let ownerKey = "dlb_trip_owner"
    private let modeKey = "dlb_trip_mode"
    /// Route answers keyed by the stops they belong to (failures cached too).
    private var routeCache: [String: RouteAPI.Response?] = [:]

    init() {
        stopIds = UserDefaults.standard.stringArray(forKey: stopsKey) ?? []
        if let o = UserDefaults.standard.array(forKey: originKey) as? [Double], o.count >= 2 {
            originCoord = CLLocationCoordinate2D(latitude: o[0], longitude: o[1])
            originLabel = o.count >= 2 ? UserDefaults.standard.string(forKey: originKey + ".label") : nil
        }
        if let d = UserDefaults.standard.array(forKey: destinationKey) as? [Double], d.count >= 2 {
            destinationCoord = CLLocationCoordinate2D(latitude: d[0], longitude: d[1])
            destinationLabel = UserDefaults.standard.string(forKey: destinationKey + ".label")
        }
        if let m = UserDefaults.standard.string(forKey: modeKey).flatMap(TravelMode.init) { mode = m }
    }

    /// Switch travel mode. The road geometry is cached by stops and unchanged by
    /// mode, so the caller just re-runs `refreshRoute` to re-derive the estimate.
    func setMode(_ m: TravelMode) {
        guard m != mode else { return }
        mode = m
        UserDefaults.standard.set(m.rawValue, forKey: modeKey)
    }

    // MARK: R6 — trip day + departure

    func setTripDate(_ d: Date?) { tripDate = d }
    func setDepartMinutes(_ m: Int?) { departMinutes = m }

    /// The day the corridor answers about — the coming Saturday (today if it is
    /// Saturday) until the user picks another. Amsterdam time; the drives are NL/BE.
    var resolvedTripDate: Date { tripDate ?? Self.defaultTripDate() }
    /// Minutes past midnight to set out, defaulting to 10:00.
    var resolvedDepartMinutes: Int { departMinutes ?? 600 }
    /// The chosen day as `statusOnDay`'s 0=Mon…6=Sun index.
    var resolvedDayMon: Int { Self.dayMon(for: resolvedTripDate) }

    /// The next Saturday, or today when today is already Saturday. Start-of-day in
    /// Amsterdam so the weekday is the Dutch one, not the device's.
    static func defaultTripDate(now: Date = Date()) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .current
        let today = cal.startOfDay(for: now)
        let wd = cal.component(.weekday, from: today)   // 1=Sun … 7=Sat
        let daysUntilSat = (7 - wd + 7) % 7             // 0 when today is Saturday
        return cal.date(byAdding: .day, value: daysUntilSat, to: today) ?? today
    }

    /// A date's weekday as `statusOnDay`'s index (0=Mon … 6=Sun), in Amsterdam —
    /// the same mapping the corridor used for "today", now for any chosen day.
    static func dayMon(for date: Date) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .current
        let js = cal.component(.weekday, from: date) - 1   // 0=Sun … 6=Sat
        return [6, 0, 1, 2, 3, 4, 5][js]
    }

    // MARK: Draft

    func contains(_ osmId: String) -> Bool { stopIds.contains(osmId) }

    func toggle(_ osmId: String) {
        if let i = stopIds.firstIndex(of: osmId) { stopIds.remove(at: i) } else { stopIds.append(osmId) }
        persist()
    }
    func remove(_ osmId: String) { stopIds.removeAll { $0 == osmId }; persist() }
    func move(from: IndexSet, to: Int) { stopIds.move(fromOffsets: from, toOffset: to); persist() }

    /// Clears the stops and editing state — but keeps the origin (don't make
    /// someone re-enter their front door for the next trip).
    func clear() {
        stopIds = []; editingTripId = nil
        setRouteLine([]); distanceMeters = nil; durationSeconds = nil; onRoads = false
        persist()
    }

    func setOrigin(_ coord: CLLocationCoordinate2D, label: String) {
        originCoord = coord; originLabel = label
        UserDefaults.standard.set([coord.latitude, coord.longitude], forKey: originKey)
        UserDefaults.standard.set(label, forKey: originKey + ".label")
        requestFit()
    }

    func clearOrigin() {
        originCoord = nil; originLabel = nil
        UserDefaults.standard.removeObject(forKey: originKey)
        UserDefaults.standard.removeObject(forKey: originKey + ".label")
    }

    func setDestination(_ coord: CLLocationCoordinate2D, label: String) {
        destinationCoord = coord; destinationLabel = label
        UserDefaults.standard.set([coord.latitude, coord.longitude], forKey: destinationKey)
        UserDefaults.standard.set(label, forKey: destinationKey + ".label")
        requestFit()
    }

    func clearDestination() {
        destinationCoord = nil; destinationLabel = nil
        UserDefaults.standard.removeObject(forKey: destinationKey)
        UserDefaults.standard.removeObject(forKey: destinationKey + ".label")
    }

    /// The ends as they are stored (R1). One place builds them, so the insert,
    /// the update and the Maps hand-off cannot disagree about what the trip is.
    var endpoints: TripEndpoints {
        TripEndpoints(
            origin: originCoord.flatMap { TripPlace.make($0, label: originLabel) },
            destination: destinationCoord.flatMap { TripPlace.make($0, label: destinationLabel) },
            radiusKm: nil
        )
    }

    /// Wipe the draft if the account changed (a shared device must not carry the
    /// previous person's stops). Signed-out counts as owner "anon".
    func reconcileOwner(_ userId: String?) {
        let owner = userId ?? "anon"
        let stored = UserDefaults.standard.string(forKey: ownerKey)
        if let stored, stored != owner {
            stopIds = []; editingTripId = nil
            setRouteLine([]); distanceMeters = nil; durationSeconds = nil
            persist()
        }
        UserDefaults.standard.set(owner, forKey: ownerKey)
    }

    // MARK: Ordering

    /// The full leg list: origin, then farms, then the destination (R1). Any of
    /// the three may be absent — farms alone is still a drive.
    ///
    /// `includingDestination: false` is for the reordering below, and it is not a
    /// convenience. `TripGeometry.optimise` pins only the first leg, so a
    /// destination on the end would be shuffled in among the farms: the order
    /// would be optimised for a road that ends somewhere it does not, and the
    /// index-mapping underneath would then drop it, leaving a saved order that
    /// belongs to a route nobody planned. Pinning the last leg as well is a
    /// change to TripGeometry, and it belongs in its own commit.
    private func legs(pins: [String: FarmPin], includingDestination: Bool = true) -> [CLLocationCoordinate2D] {
        var out: [CLLocationCoordinate2D] = []
        if let originCoord { out.append(originCoord) }
        out.append(contentsOf: stopIds.compactMap { pins[$0]?.coordinate })
        if includingDestination, let destinationCoord { out.append(destinationCoord) }
        return out
    }

    /// "Best order" — reorders the farms (origin pinned as leg zero). Returns km saved.
    @discardableResult
    func optimise(pins: [String: FarmPin]) -> Double {
        let farms = stopIds.compactMap { pins[$0] }
        guard farms.count > 2 else { return 0 }
        let coords = legs(pins: pins, includingDestination: false)
        let before = TripGeometry.lengthKm(coords)
        let order = TripGeometry.optimise(coords)
        // Drop the origin (index 0) from the order if present, map back to farms.
        let hasOrigin = originCoord != nil
        let farmOrder = order.compactMap { idx -> String? in
            let f = hasOrigin ? idx - 1 : idx
            guard f >= 0, f < farms.count else { return nil }
            return farms[f].osmId
        }
        stopIds = farmOrder
        persist()
        let after = TripGeometry.lengthKm(legs(pins: pins, includingDestination: false))
        return before - after
    }

    // MARK: Route

    private func keyOf(_ coords: [CLLocationCoordinate2D]) -> String {
        coords.map { String(format: "%.5f,%.5f", $0.latitude, $0.longitude) }.joined(separator: ";")
    }

    func refreshRoute(pins: [String: FarmPin]) async {
        let coords = legs(pins: pins)
        guard coords.count >= 2 else {
            setRouteLine([]); distanceMeters = nil; durationSeconds = nil; onRoads = false; return
        }
        let key = keyOf(coords)
        if let cached = routeCache[key] {
            apply(cached, straight: coords); return
        }
        isRouting = true; defer { isRouting = false }
        let r = await RouteAPI.route(coords)
        routeCache[key] = r
        apply(r, straight: coords)
    }

    private func apply(_ r: RouteAPI.Response?, straight: [CLLocationCoordinate2D]) {
        if let r, let line = r.coordinates, line.count >= 2 {
            setRouteLine(line.map { CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0]) })
            distanceMeters = r.distance
            // ORS only routes driving-car, so trust its duration for driving and
            // re-derive from the road distance at bike/walk speed otherwise.
            if mode == .car {
                durationSeconds = r.duration
            } else if let d = r.distance {
                durationSeconds = Double(mode.minutes(km: d / 1000)) * 60
            } else {
                durationSeconds = r.duration
            }
            onRoads = true
            startTrace()
        } else {
            setRouteLine(straight)
            let km = TripGeometry.lengthKm(straight)
            distanceMeters = km * 1000
            durationSeconds = Double(mode.minutes(km: km)) * 60
            onRoads = false
            traceTask?.cancel(); traceProgress = 1   // straight lines draw instantly
        }
    }

    // MARK: Saved trips (DB)

    func loadTrips(userId: String) async {
        savedTrips = (try? await supabase
            .from("trips")
            .select("id, name, updated_at, trip_farms(count)")
            .eq("user_id", value: userId)
            .order("updated_at", ascending: false)
            .limit(20)
            .execute()
            .value) ?? []
        await loadPlannedFarmIds()
    }

    /// Every farm across the user's saved trips, in one query — for excluding
    /// already-planned farms from the recommendations shelf.
    private func loadPlannedFarmIds() async {
        let ids = savedTrips.map(\.id)
        guard !ids.isEmpty else { plannedFarmIds = []; return }
        struct Row: Decodable { let farm_osm_id: String }
        let rows: [Row] = (try? await supabase
            .from("trip_farms")
            .select("farm_osm_id")
            .in("trip_id", values: ids)
            .execute()
            .value) ?? []
        plannedFarmIds = Set(rows.map(\.farm_osm_id))
    }

    /// Save the current draft as a trip (insert, or update when editing). Caches
    /// the farm name/coords/city/image on each stop row so a share link survives.
    func save(name: String, userId: String, pins: [String: FarmPin]) async {
        // R1: the ends travel with the row. Flattened rather than nested so the
        // column names are the ones migration 058 added, and so the update below
        // can carry exactly the same seven fields as the insert.
        struct TripRow: Encodable {
            let user_id: String
            let name: String
            let origin_lat: Double?
            let origin_lng: Double?
            let origin_label: String?
            let destination_lat: Double?
            let destination_lng: Double?
            let destination_label: String?
            let radius_km: Double?

            init(user_id: String, name: String, ends: TripEndpointRow) {
                self.user_id = user_id
                self.name = name
                origin_lat = ends.origin_lat
                origin_lng = ends.origin_lng
                origin_label = ends.origin_label
                destination_lat = ends.destination_lat
                destination_lng = ends.destination_lng
                destination_label = ends.destination_label
                radius_km = ends.radius_km
            }
        }

        /// Editing an existing trip. Every one of the seven ends is written, so
        /// removing a destination clears the column instead of leaving the old
        /// one behind — a trip that keeps a start the user deleted is worse than
        /// one that never had it.
        struct TripUpdate: Encodable {
            let name: String
            let updated_at: String
            let origin_lat: Double?
            let origin_lng: Double?
            let origin_label: String?
            let destination_lat: Double?
            let destination_lng: Double?
            let destination_label: String?
            let radius_km: Double?
        }
        struct StopRow: Encodable {
            let trip_id: String; let farm_osm_id: String; let farm_name: String
            let farm_lat: Double; let farm_lng: Double
            let farm_city: String?; let farm_image: String?; let sort_order: Int
        }
        struct Inserted: Decodable { let id: String }

        let ends = TripEndpointRow(endpoints)

        let tripId: String
        if let editingTripId {
            _ = try? await supabase.from("trips")
                .update(TripUpdate(
                    name: name,
                    updated_at: ISO8601DateFormatter().string(from: Date()),
                    origin_lat: ends.origin_lat, origin_lng: ends.origin_lng, origin_label: ends.origin_label,
                    destination_lat: ends.destination_lat, destination_lng: ends.destination_lng,
                    destination_label: ends.destination_label,
                    radius_km: ends.radius_km))
                .eq("id", value: editingTripId).execute()
            _ = try? await supabase.from("trip_farms").delete().eq("trip_id", value: editingTripId).execute()
            tripId = editingTripId
        } else {
            guard let rows: [Inserted] = try? await supabase.from("trips")
                .insert(TripRow(user_id: userId, name: name, ends: ends)).select("id").execute().value,
                  let id = rows.first?.id else { return }
            tripId = id
        }

        let stopRows = stopIds.enumerated().compactMap { i, osmId -> StopRow? in
            guard let p = pins[osmId] else { return nil }
            return StopRow(trip_id: tripId, farm_osm_id: osmId, farm_name: p.name,
                           farm_lat: p.lat, farm_lng: p.lng, farm_city: p.city,
                           farm_image: p.image, sort_order: i)
        }
        do {
            try await supabase.from("trip_farms").insert(stopRows).execute()
            editingTripId = tripId
            await loadTrips(userId: userId)
        } catch {
            // A trip without its farms is worse than none — roll the trip back.
            if editingTripId == nil { _ = try? await supabase.from("trips").delete().eq("id", value: tripId).execute() }
        }
    }

    func openTrip(_ id: String) async {
        struct Stop: Decodable { let farm_osm_id: String; let sort_order: Int }
        let stops: [Stop] = (try? await supabase.from("trip_farms")
            .select("farm_osm_id, sort_order").eq("trip_id", value: id)
            .order("sort_order", ascending: true).execute().value) ?? []
        stopIds = stops.map(\.farm_osm_id)

        // R1: put the drive back the way it was saved. Before this the ends were
        // never written, so an opened trip started at its first farm rather than
        // where the person actually set out from. A trip from before R1 decodes
        // to no ends at all, which is a working plan and not a failure.
        let rows: [TripEndpointRow] = (try? await supabase.from("trips")
            .select(TripEndpointRow.columns).eq("id", value: id).limit(1)
            .execute().value) ?? []
        let ends = rows.first?.endpoints ?? .none
        if let o = ends.origin { setOrigin(o.coordinate, label: o.label) } else { clearOrigin() }
        if let d = ends.destination { setDestination(d.coordinate, label: d.label) } else { clearDestination() }

        editingTripId = id
        persist()
        requestFit()
    }

    func deleteTrip(_ id: String, userId: String) async {
        savedTrips.removeAll { $0.id == id }   // optimistic
        _ = try? await supabase.from("trips").delete().eq("id", value: id).execute()
    }

    private func persist() { UserDefaults.standard.set(stopIds, forKey: stopsKey) }
}
