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
    /// Which trip is being edited (so save updates instead of duplicating).
    private(set) var editingTripId: String?

    // Route.
    private(set) var routeLine: [CLLocationCoordinate2D] = []
    private(set) var distanceMeters: Double?
    private(set) var durationSeconds: Double?
    private(set) var isRouting = false
    private(set) var onRoads = false
    /// The line as it draws itself, sliced by the trace animation (0→1). The map
    /// renders this, not `routeLine`, so the route traces along the road.
    private(set) var traceProgress: Double = 1
    private var traceTask: Task<Void, Never>?

    /// The visible portion of the route while it traces in.
    var tracedLine: [CLLocationCoordinate2D] {
        guard traceProgress < 1, routeLine.count > 2 else { return routeLine }
        let n = max(2, Int((Double(routeLine.count) * traceProgress).rounded(.up)))
        return Array(routeLine.prefix(n))
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
    /// Bumped whenever the map should refit to the whole trip (opening a saved
    /// trip, setting the origin) — the map watches this.
    private(set) var fitToken = 0
    func requestFit() { fitToken += 1 }

    private let stopsKey = "dlb_pending_trip"
    private let originKey = "dlb_trip_origin"
    private let ownerKey = "dlb_trip_owner"
    /// Route answers keyed by the stops they belong to (failures cached too).
    private var routeCache: [String: RouteAPI.Response?] = [:]

    init() {
        stopIds = UserDefaults.standard.stringArray(forKey: stopsKey) ?? []
        if let o = UserDefaults.standard.array(forKey: originKey) as? [Double], o.count >= 2 {
            originCoord = CLLocationCoordinate2D(latitude: o[0], longitude: o[1])
            originLabel = o.count >= 2 ? UserDefaults.standard.string(forKey: originKey + ".label") : nil
        }
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
        routeLine = []; distanceMeters = nil; durationSeconds = nil; onRoads = false
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

    /// Wipe the draft if the account changed (a shared device must not carry the
    /// previous person's stops). Signed-out counts as owner "anon".
    func reconcileOwner(_ userId: String?) {
        let owner = userId ?? "anon"
        let stored = UserDefaults.standard.string(forKey: ownerKey)
        if let stored, stored != owner {
            stopIds = []; editingTripId = nil
            routeLine = []; distanceMeters = nil; durationSeconds = nil
            persist()
        }
        UserDefaults.standard.set(owner, forKey: ownerKey)
    }

    // MARK: Ordering

    /// The full leg list: origin then farms (or farms alone).
    private func legs(pins: [String: FarmPin]) -> [CLLocationCoordinate2D] {
        var out: [CLLocationCoordinate2D] = []
        if let originCoord { out.append(originCoord) }
        out.append(contentsOf: stopIds.compactMap { pins[$0]?.coordinate })
        return out
    }

    /// "Best order" — reorders the farms (origin pinned as leg zero). Returns km saved.
    @discardableResult
    func optimise(pins: [String: FarmPin]) -> Double {
        let farms = stopIds.compactMap { pins[$0] }
        guard farms.count > 2 else { return 0 }
        let coords = legs(pins: pins)
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
        let after = TripGeometry.lengthKm(legs(pins: pins))
        return before - after
    }

    // MARK: Route

    private func keyOf(_ coords: [CLLocationCoordinate2D]) -> String {
        coords.map { String(format: "%.5f,%.5f", $0.latitude, $0.longitude) }.joined(separator: ";")
    }

    func refreshRoute(pins: [String: FarmPin]) async {
        let coords = legs(pins: pins)
        guard coords.count >= 2 else {
            routeLine = []; distanceMeters = nil; durationSeconds = nil; onRoads = false; return
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
            routeLine = line.map { CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0]) }
            distanceMeters = r.distance
            durationSeconds = r.duration
            onRoads = true
            startTrace()
        } else {
            routeLine = straight
            distanceMeters = TripGeometry.lengthKm(straight) * 1000
            durationSeconds = Double(TripGeometry.roughDriveMinutes(TripGeometry.lengthKm(straight))) * 60
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
    }

    /// Save the current draft as a trip (insert, or update when editing). Caches
    /// the farm name/coords/city/image on each stop row so a share link survives.
    func save(name: String, userId: String, pins: [String: FarmPin]) async {
        struct TripRow: Encodable { let user_id: String; let name: String }
        struct StopRow: Encodable {
            let trip_id: String; let farm_osm_id: String; let farm_name: String
            let farm_lat: Double; let farm_lng: Double
            let farm_city: String?; let farm_image: String?; let sort_order: Int
        }
        struct Inserted: Decodable { let id: String }

        let tripId: String
        if let editingTripId {
            _ = try? await supabase.from("trips")
                .update(["name": name, "updated_at": ISO8601DateFormatter().string(from: Date())])
                .eq("id", value: editingTripId).execute()
            _ = try? await supabase.from("trip_farms").delete().eq("trip_id", value: editingTripId).execute()
            tripId = editingTripId
        } else {
            guard let rows: [Inserted] = try? await supabase.from("trips")
                .insert(TripRow(user_id: userId, name: name)).select("id").execute().value,
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
