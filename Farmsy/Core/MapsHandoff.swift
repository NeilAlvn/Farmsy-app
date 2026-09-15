import Foundation
import CoreLocation

/// Handing a planned drive to a maps app, and the one place that URL is built.
///
/// The web has the same rules in `src/lib/mapsHandoff.ts`. Three implementations
/// of one truth, the same arrangement as `TripEndpoints` — if a rule changes
/// here it changes there, in the same commit.
///
/// Two silent faults this closes, both invisible until somebody is in the car:
///
///  - **The destination went missing.** R1 gave a trip a destination of its own;
///    the handover never followed. It still made the last farm the destination,
///    so a drive planned Utrecht → Groningen with three stops opened in Maps as
///    a drive ending at the third farm. A stop is somewhere you pass through.
///    The destination is where you stop.
///  - **Stops past the ninth were dropped.** `dir/?api=1` takes at most nine
///    waypoints and discards the rest without a word, so a ten-stop drive opened
///    missing stops and said nothing. Past the cap the older path form
///    (`/maps/dir/A/B/C`) carries them all.
///
/// Everything is coordinates, never place names. A farm called "Chez Lies &
/// Fils" passes through several encoders on the way to a maps app, and a name
/// that resolves to the wrong shop is worse than a pin with no name at all.
enum MapsHandoff {

    /// Google's documented ceiling for `waypoints` on `dir/?api=1`.
    static let apiWaypointLimit = 9

    /// A planned drive, reduced to what a maps URL needs.
    struct Plan {
        /// Where the drive starts. Nil falls back to the first stop.
        var origin: CLLocationCoordinate2D?
        /// The stops, in the order they are driven.
        var stops: [CLLocationCoordinate2D]
        /// Where it ends. Nil falls back to the last stop.
        var destination: CLLocationCoordinate2D?
        /// `driving`, `walking`, `bicycling`, `transit`. Empty leaves it to Google.
        var travelMode: String = ""
    }

    /// The Google Maps URL for a planned drive, or nil when there is nothing to
    /// hand over.
    ///
    /// The rules, in the order they are applied:
    ///  - No stops and no destination is nothing to open.
    ///  - One place and no start is a search for it, not a route from nowhere.
    ///  - A destination of its own keeps every stop as a waypoint.
    ///  - No destination means the last stop becomes one, as it did before R1.
    static func googleMapsURL(_ plan: Plan) -> URL? {
        let stops = plan.stops
        guard !stops.isEmpty || plan.destination != nil else { return nil }

        // Where it ends, and which stops are therefore on the way.
        let end = plan.destination ?? stops[stops.count - 1]
        let via = plan.destination != nil ? stops : Array(stops.dropLast())

        // Nothing to route between: one place and nowhere to come from. A route
        // from the user's current location is Google's offer to make, not ours
        // to assume.
        if plan.origin == nil && via.isEmpty {
            return URL(string: "https://www.google.com/maps/search/?api=1&query=\(at(end))")
        }

        let start = plan.origin ?? via[0]
        let middle = plan.origin != nil ? via : Array(via.dropFirst())

        // Past the documented cap, api=1 drops waypoints without saying so. The
        // path form carries them all, so a long drive stays a long drive.
        if middle.count > apiWaypointLimit {
            let path = ([start] + middle + [end]).map(at).joined(separator: "/")
            return URL(string: "https://www.google.com/maps/dir/\(path)")
        }

        var s = "https://www.google.com/maps/dir/?api=1&origin=\(at(start))&destination=\(at(end))"
        if !plan.travelMode.isEmpty { s += "&travelmode=\(plan.travelMode)" }
        if !middle.isEmpty {
            // The pipe has to reach Google encoded. An unencoded one parses
            // differently in some clients and loses every stop after the first.
            // Only the pipe: percent-encoding the commas, dots and minus signs
            // too would still work but would not match what the web sends, and
            // two platforms sending different URLs for one drive is a bug
            // waiting to be found by somebody who tried both phones.
            let encoded = middle.map(at).joined(separator: "%7C")
            s += "&waypoints=\(encoded)"
        }
        return URL(string: s)
    }

    /// A coordinate as Google wants it. `%g`-style trimming is deliberate: the
    /// full binary expansion of a Double makes a needlessly long URL and buys no
    /// precision a road can use.
    private static func at(_ c: CLLocationCoordinate2D) -> String {
        "\(trim(c.latitude)),\(trim(c.longitude))"
    }

    private static func trim(_ d: Double) -> String {
        // Six decimals is about a tenth of a metre — far finer than a driveway.
        var s = String(format: "%.6f", d)
        while s.contains("."), s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s
    }
}
