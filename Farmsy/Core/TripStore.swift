import Foundation
import SwiftUI
import CoreLocation
import Observation

// MARK: - Route API (POST /api/route)

/// Road routing through the web's proxy (no key needed on the app). Returns the
/// road line plus per-leg distance/duration. 501/502 → fall back to straight
/// lines, which is what the web does.
enum RouteAPI {
    struct Response: Decodable {
        let coordinates: [[Double]]?          // [[lng,lat], …] — the road line
        let distance: Double?                 // metres, whole route
        let duration: Double?                 // seconds, whole route
        let segments: [Segment]?
        struct Segment: Decodable { let distance: Double?; let duration: Double? }
    }

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

// MARK: - Trip store

/// A single itinerary held on the device (the web keeps it in localStorage; we
/// keep it in UserDefaults by osm_id). Pro-gated at the UI, but the store itself
/// is dumb — it just holds ordered stops and the road line.
@MainActor
@Observable
final class TripStore {
    /// Ordered stops, as osm_ids. Resolved to pins by the views via FarmsStore.
    private(set) var stopIds: [String] = []
    /// The drawn road line, as coordinates — nil until a route is fetched.
    private(set) var routeLine: [CLLocationCoordinate2D] = []
    private(set) var distanceMeters: Double?
    private(set) var durationSeconds: Double?
    private(set) var isRouting = false

    private let key = "trip.stopIds"

    init() {
        stopIds = UserDefaults.standard.stringArray(forKey: key) ?? []
    }

    func contains(_ osmId: String) -> Bool { stopIds.contains(osmId) }

    func toggle(_ osmId: String) {
        if let i = stopIds.firstIndex(of: osmId) { stopIds.remove(at: i) }
        else { stopIds.append(osmId) }
        persist()
    }

    func remove(_ osmId: String) {
        stopIds.removeAll { $0 == osmId }
        persist()
    }

    func move(from: IndexSet, to: Int) {
        stopIds.move(fromOffsets: from, toOffset: to)
        persist()
    }

    func clear() {
        stopIds = []; routeLine = []; distanceMeters = nil; durationSeconds = nil
        persist()
    }

    /// Order the stops by nearest-neighbour on straight-line distance (the web's
    /// "best order" — comparing by road would be a request per permutation).
    func optimize(pins: [String: FarmPin], from start: CLLocation?) {
        var remaining = stopIds.compactMap { pins[$0] }
        guard remaining.count > 2 else { return }
        var ordered: [FarmPin] = []
        var current = start ?? remaining.first.map { CLLocation(latitude: $0.lat, longitude: $0.lng) }
        while !remaining.isEmpty {
            guard let cur = current else { break }
            let idx = remaining.enumerated().min {
                CLLocation(latitude: $0.1.lat, longitude: $0.1.lng).distance(from: cur)
                    < CLLocation(latitude: $1.1.lat, longitude: $1.1.lng).distance(from: cur)
            }?.offset ?? 0
            let next = remaining.remove(at: idx)
            ordered.append(next)
            current = CLLocation(latitude: next.lat, longitude: next.lng)
        }
        stopIds = ordered.map(\.osmId)
        persist()
    }

    /// Fetch the road route for the current stops. Falls back to straight lines.
    func refreshRoute(pins: [String: FarmPin]) async {
        let coords = stopIds.compactMap { pins[$0]?.coordinate }
        guard coords.count >= 2 else { routeLine = []; distanceMeters = nil; durationSeconds = nil; return }
        isRouting = true; defer { isRouting = false }
        if let r = await RouteAPI.route(coords), let line = r.coordinates {
            routeLine = line.map { CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0]) }
            distanceMeters = r.distance
            durationSeconds = r.duration
        } else {
            routeLine = coords   // straight-line fallback
            distanceMeters = nil
            durationSeconds = nil
        }
    }

    private func persist() {
        UserDefaults.standard.set(stopIds, forKey: key)
    }
}
