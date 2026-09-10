import Foundation
import CoreLocation
import Testing
@testable import Farmsy

/// R8: the drive the user planned is the drive the maps app opens.
///
/// The same rules live on the web in `src/lib/mapsHandoff.test.ts`, and the two
/// suites are deliberately the same tests — a URL that differs between the
/// phone and the site is a bug found by whoever tries both.
///
/// The bug this exists for shipped with R1 and is silent. A drive planned
/// Utrecht → Groningen opened as a drive ending at the last farm, because the
/// handover predated trips having a destination of their own. Nothing errored
/// and nothing looked wrong until you were in the car.
///
/// The second silent one is Google's waypoint cap. Past nine, `dir/?api=1` drops
/// the surplus without a word, so a ten-stop drive opens missing stops.
struct MapsHandoffTests {

    let utrecht   = CLLocationCoordinate2D(latitude: 52.0907, longitude: 5.1214)
    let groningen = CLLocationCoordinate2D(latitude: 53.2194, longitude: 6.5665)
    let farmA     = CLLocationCoordinate2D(latitude: 52.5, longitude: 5.5)
    let farmB     = CLLocationCoordinate2D(latitude: 52.8, longitude: 6.0)

    /// The query items of a produced URL, by name.
    func params(_ url: URL?) -> [String: String] {
        guard let url, let c = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return [:] }
        var out: [String: String] = [:]
        for item in c.queryItems ?? [] { out[item.name] = item.value }
        return out
    }

    // MARK: - The bug R1 left behind

    @Test("a drive with a destination ends there, not at the last farm")
    func destinationWins() {
        let url = MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [farmA, farmB], destination: groningen))
        let p = params(url)
        #expect(p["origin"] == "52.0907,5.1214")
        #expect(p["destination"] == "53.2194,6.5665")
        // Both farms survive as stops on the way — neither is consumed as the end.
        #expect(p["waypoints"] == "52.5,5.5|52.8,6")
    }

    @Test("with no destination the last farm becomes one, as it did before R1")
    func withoutDestination() {
        let p = params(MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [farmA, farmB], destination: nil)))
        #expect(p["destination"] == "52.8,6")
        #expect(p["waypoints"] == "52.5,5.5")
    }

    // MARK: - Google's waypoint cap

    @Test("a drive longer than the cap keeps every stop")
    func pastTheCap() {
        let many = (0..<(MapsHandoff.apiWaypointLimit + 3)).map {
            CLLocationCoordinate2D(latitude: 52 + Double($0) / 100, longitude: 5)
        }
        let url = MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: many, destination: groningen))
        let s = url?.absoluteString ?? ""

        #expect(!s.contains("api=1"), "past the cap the path form is used")
        for stop in many {
            let lat = String(format: "%g", stop.latitude)
            #expect(s.contains("\(lat),5"), "stop \(lat) kept")
        }
        #expect(s.contains("53.2194,6.5665"), "destination kept")
    }

    @Test("exactly at the cap still uses the documented form")
    func atTheCap() {
        let many = (0..<MapsHandoff.apiWaypointLimit).map {
            CLLocationCoordinate2D(latitude: 52 + Double($0) / 100, longitude: 5)
        }
        let url = MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: many, destination: groningen))
        #expect(url?.absoluteString.contains("api=1") == true)
        #expect(params(url)["waypoints"]?.split(separator: "|").count == MapsHandoff.apiWaypointLimit)
    }

    // MARK: - Not enough to route

    @Test("one place and no start is a search, not a route from nowhere")
    func singlePlace() {
        let url = MapsHandoff.googleMapsURL(.init(origin: nil, stops: [farmA], destination: nil))
        #expect(url?.absoluteString.hasPrefix("https://www.google.com/maps/search/") == true)
        #expect(params(url)["query"] == "52.5,5.5")
    }

    @Test("nothing planned hands over nothing")
    func nothingToOpen() {
        #expect(MapsHandoff.googleMapsURL(.init(origin: nil, stops: [], destination: nil)) == nil)
        #expect(MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [], destination: nil)) == nil)
    }

    @Test("a destination and no stops is still a drive")
    func destinationOnly() {
        let p = params(MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [], destination: groningen)))
        #expect(p["origin"] == "52.0907,5.1214")
        #expect(p["destination"] == "53.2194,6.5665")
        #expect(p["waypoints"] == nil)
    }

    @Test("no start means the first farm is the start, and is not also a stop")
    func firstFarmIsTheStart() {
        let p = params(MapsHandoff.googleMapsURL(.init(origin: nil, stops: [farmA, farmB], destination: groningen)))
        #expect(p["origin"] == "52.5,5.5")
        #expect(p["waypoints"] == "52.8,6")
        #expect(p["destination"] == "53.2194,6.5665")
    }

    // MARK: - The URL itself

    @Test("the waypoint separator survives being a URL")
    func pipeIsEncoded() {
        let s = MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [farmA, farmB], destination: groningen))?.absoluteString ?? ""
        #expect(s.contains("%7C"), "pipe is encoded in the raw URL")
        #expect(!s.contains("|"), "and never raw")
    }

    @Test("a negative coordinate stays negative")
    func negativeCoordinates() {
        // Farmsy is NL and BE today, but the arithmetic must not depend on it:
        // a minus sign eaten by an encoder moves a farm to the other hemisphere.
        let west = CLLocationCoordinate2D(latitude: -33.9, longitude: -18.4)
        let p = params(MapsHandoff.googleMapsURL(.init(origin: utrecht, stops: [west], destination: groningen)))
        #expect(p["waypoints"] == "-33.9,-18.4")
    }

    @Test("the travel mode is passed through when there is one")
    func travelMode() {
        var plan = MapsHandoff.Plan(origin: utrecht, stops: [farmA], destination: groningen)
        plan.travelMode = "bicycling"
        #expect(params(MapsHandoff.googleMapsURL(plan))["travelmode"] == "bicycling")

        plan.travelMode = ""
        #expect(params(MapsHandoff.googleMapsURL(plan))["travelmode"] == nil)
    }
}
