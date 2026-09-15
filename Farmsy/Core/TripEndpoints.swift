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
    /// The day it was planned for, `yyyy-mm-dd`. Nil for anything saved before
    /// R7, and for a trip that is only a list of farms.
    ///
    /// A date and not a timestamp. A farm shop's opening hours are a property of
    /// the weekday, not of an instant, and a `Date` would drag a time zone into
    /// a question that does not have one. Saturday in Amsterdam is Saturday.
    var date: String?
    /// When the drive sets off, minutes past midnight. Nil means never chosen.
    var departMinutes: Int?

    static let none = TripEndpoints(
        origin: nil, destination: nil, radiusKm: nil, date: nil, departMinutes: nil
    )

    /// The widest corridor the clients offer. Anything past it is a bad write
    /// rather than a preference, so it is dropped instead of clamped: clamping
    /// stores a number nobody chose.
    static let maxRadiusKm: Double = 100

    static func validRadius(_ km: Double?) -> Double? {
        guard let km, km.isFinite, km > 0, km <= maxRadiusKm else { return nil }
        return km
    }

    /// A `yyyy-mm-dd` that is actually one, or nil.
    ///
    /// Shape-checked and then round-tripped through a calendar, because
    /// `2026-02-31` matches the pattern and is not a day. A trip that claims to
    /// be planned for a date that does not exist would compare against whatever
    /// weekday the calendar rolled it into, which is a wrong answer delivered
    /// with confidence.
    static func validDate(_ text: String?) -> String? {
        guard let text, text.count == 10 else { return nil }
        let parts = text.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let y = Int(parts[0]), let m = Int(parts[1]), let d = Int(parts[2]),
              text.allSatisfy({ $0.isNumber || $0 == "-" })
        else { return nil }

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .current
        guard let made = cal.date(from: DateComponents(year: y, month: m, day: d)) else { return nil }
        let back = cal.dateComponents([.year, .month, .day], from: made)
        return back.year == y && back.month == m && back.day == d ? text : nil
    }

    /// Minutes past midnight, whole, inside a day. Matches the 059 CHECK, so a
    /// bad value is dropped here rather than rejected by Postgres after a
    /// round trip.
    static func validDepartMinutes(_ minutes: Int?) -> Int? {
        guard let minutes, (0...1439).contains(minutes) else { return nil }
        return minutes
    }

    /// The calendar every trip date is read and written in.
    ///
    /// Fixed to Amsterdam rather than the device's. A trip is planned against
    /// Dutch and Belgian opening hours, and a phone set to Los Angeles would
    /// otherwise turn a Saturday drive into a Friday one at the date boundary —
    /// the farm would be reported shut and nobody would know why.
    static var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .current
        return cal
    }

    /// A `Date` as the `yyyy-mm-dd` the column stores.
    static func dayString(_ date: Date) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// A stored `yyyy-mm-dd` back as a `Date` at local noon, or nil.
    ///
    /// Noon and not midnight: a date pinned to midnight lands on the wrong day
    /// the moment anything shifts it by an hour, and a daylight-saving boundary
    /// does exactly that twice a year.
    static func day(from text: String?) -> Date? {
        guard let text = validDate(text) else { return nil }
        let parts = text.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(
            year: parts[0], month: parts[1], day: parts[2], hour: 12
        ))
    }

    /// The day the planner offers when nobody has chosen one: the Saturday
    /// coming, today included if today is Saturday. Mirrors `nextSaturday()` in
    /// the web's MapSearchContext — a farm trip is a weekend errand, and
    /// defaulting to a weekday would answer a question nobody asked.
    static func nextSaturday(from: Date = Date()) -> String {
        let cal = calendar
        // Calendar counts Sunday as 1; Saturday is 7.
        let weekday = cal.component(.weekday, from: from)
        let ahead = (7 - weekday) % 7
        let day = cal.date(byAdding: .day, value: ahead, to: from) ?? from
        return dayString(day)
    }
}

/// The `trips` columns from migrations 058 and 059, exactly as they are stored.
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
    var trip_date: String?
    var depart_minutes: Int?

    /// Always all nine, so clearing an end writes NULL rather than leaving
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
        trip_date         = TripEndpoints.validDate(endpoints.date)
        depart_minutes    = TripEndpoints.validDepartMinutes(endpoints.departMinutes)
    }

    var endpoints: TripEndpoints {
        TripEndpoints(
            origin: TripPlace.make(lat: origin_lat, lng: origin_lng, label: origin_label),
            destination: TripPlace.make(lat: destination_lat, lng: destination_lng, label: destination_label),
            radiusKm: TripEndpoints.validRadius(radius_km),
            // Postgres hands a `date` back as `yyyy-mm-dd`, which is what the
            // planner holds, so there is nothing to convert — only to check.
            date: TripEndpoints.validDate(trip_date),
            departMinutes: TripEndpoints.validDepartMinutes(depart_minutes)
        )
    }

    /// The columns to select when opening a trip. Here so a new column cannot be
    /// added to the row without the query that reads it.
    static let columns =
        "origin_lat, origin_lng, origin_label, destination_lat, destination_lng, destination_label, radius_km, trip_date, depart_minutes"
}
