import Foundation
import CoreLocation

/// Where a saved drive starts and ends (R1), and the one place that shape is
/// enforced on its way to and from `trips`.
///
/// The bug this closes: a trip is a name and a list of farms, so `save()` never
/// carried the ends. `setOrigin` writes the start to UserDefaults under
/// `dlb_trip_origin` and no further — `TripRow` is `(user_id, name)`. Save a
/// drive from Utrecht to Groningen, reopen it tomorrow, and it starts at the
/// first farm instead of at your house. Nothing errors; the trip just quietly
/// means something else than what was saved.
///
/// The web has the same rules in `src/lib/tripEndpoints.ts`, and migration 058
/// has them again as CHECK constraints. Three implementations of one truth, the
/// same shape as the access rule in T-1 — if a rule changes here it changes
/// there, in the same commit.
///
/// Three rules that are easy to get wrong:
///  - **A coordinate is a pair or it is nothing.** Half a point survives a write
///    and then fails at the far end of a route calculation, where the cause is
///    no longer visible.
///  - **Null is legal, everywhere.** Every trip saved before R1 has no ends, and
///    a trip that is only a list of farms stays a valid trip. Reading one back
///    must produce a usable plan, not an error.
///  - **The radius travels with the pair.** A corridor only means something at
///    the width it was chosen at: five kilometres around a city drive and five
///    around a cross-country one are different offers. Reopening at the default
///    width would quietly show a different set of farms than the one saved.

/// Somewhere someone chose in the place search. Kept separate from
/// `CLLocationCoordinate2D` so it can carry the label it was chosen by, and so
/// this file is testable without a map.
struct TripPlace: Equatable, Codable {
    let lat: Double
    let lng: Double
    /// The place name as it was picked. Stored rather than re-geocoded: Photon
    /// and MKLocalSearch will not always give back the same words for the same
    /// point, and a saved trip that renames its own start is unsettling.
    let label: String

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    /// A pair that is really on the planet, or nil. Rejects the half-pair, NaN
    /// and infinity — all three survive a write and fail somewhere else.
    static func make(lat: Double?, lng: Double?, label: String?) -> TripPlace? {
        guard let lat, let lng, lat.isFinite, lng.isFinite else { return nil }
        guard (-90...90).contains(lat), (-180...180).contains(lng) else { return nil }

        // A point with no label is still a point. Falling back to the
        // coordinates is uglier than a place name and better than dropping a
        // start someone chose.
        let text = label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return TripPlace(
            lat: lat,
            lng: lng,
            label: text.isEmpty ? String(format: "%.4f, %.4f", lat, lng) : text
        )
    }

    static func make(_ coordinate: CLLocationCoordinate2D, label: String?) -> TripPlace? {
        make(lat: coordinate.latitude, lng: coordinate.longitude, label: label)
    }
}

struct TripEndpoints: Equatable {
    var origin: TripPlace?
    var destination: TripPlace?
    /// Corridor width in km. Nil means "never chosen" — the panel suggests one.
    var radiusKm: Double?

    static let none = TripEndpoints(origin: nil, destination: nil, radiusKm: nil)

    /// The widest corridor the clients offer. Anything past it is a bad write
    /// rather than a preference, so it is dropped instead of clamped: clamping
    /// stores a number nobody chose.
    static let maxRadiusKm: Double = 100

    static func validRadius(_ km: Double?) -> Double? {
        guard let km, km.isFinite, km > 0, km <= maxRadiusKm else { return nil }
        return km
    }
}

/// The `trips` columns from migration 058, exactly as they are stored.
///
/// Encodable for the insert, Decodable for reading a saved trip back. All
/// optional: a trip from before R1 decodes to `TripEndpoints.none`, which is a
/// working plan and not a failure.
struct TripEndpointRow: Codable, Equatable {
    var origin_lat: Double?
    var origin_lng: Double?
    var origin_label: String?
    var destination_lat: Double?
    var destination_lng: Double?
    var destination_label: String?
    var radius_km: Double?

    /// Always all seven, so clearing an end writes NULL rather than leaving
    /// yesterday's value behind. The failure mode of a partial update is a trip
    /// that keeps a start the user deliberately removed.
    init(_ endpoints: TripEndpoints) {
        origin_lat        = endpoints.origin?.lat
        origin_lng        = endpoints.origin?.lng
        origin_label      = endpoints.origin?.label
        destination_lat   = endpoints.destination?.lat
        destination_lng   = endpoints.destination?.lng
        destination_label = endpoints.destination?.label
        radius_km         = TripEndpoints.validRadius(endpoints.radiusKm)
    }

    var endpoints: TripEndpoints {
        TripEndpoints(
            origin: TripPlace.make(lat: origin_lat, lng: origin_lng, label: origin_label),
            destination: TripPlace.make(lat: destination_lat, lng: destination_lng, label: destination_label),
            radiusKm: TripEndpoints.validRadius(radius_km)
        )
    }

    /// The columns to select when opening a trip. Here so a new column cannot be
    /// added to the row without the query that reads it.
    static let columns =
        "origin_lat, origin_lng, origin_label, destination_lat, destination_lng, destination_label, radius_km"
}
