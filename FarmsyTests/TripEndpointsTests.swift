import Foundation
import Testing
@testable import Farmsy

/// R1: where a saved drive starts and ends.
///
/// The same rules live in three places: `TripEndpoints` here, `tripEndpoints.ts`
/// on the web, and migration 058 as CHECK constraints. The duplication is the
/// point, the same way T-1's access matrix is duplicated — three implementations
/// checked against one truth. If a rule changes here it changes there, in the
/// same commit.
///
/// Two bugs are guarded.
///
/// The one that is in production today: a trip saved with a start reopens
/// without it, because `TripRow` is `(user_id, name)` and the columns were never
/// written. Round-tripping is the test for that.
///
/// The one that would replace it: half a coordinate. A latitude with no
/// longitude survives a write and fails later, inside a route calculation, where
/// nothing points back here.
struct TripEndpointsTests {

    static let utrecht = TripPlace(lat: 52.0907, lng: 5.1214, label: "Utrecht")
    static let groningen = TripPlace(lat: 53.2194, lng: 6.5665, label: "Groningen")

    // MARK: - The bug in production

    @Test("a drive survives being saved and reopened")
    func roundTrip() {
        let plan = TripEndpoints(origin: Self.utrecht, destination: Self.groningen, radiusKm: 8)
        #expect(TripEndpointRow(plan).endpoints == plan)
    }

    @Test("a trip saved before R1 opens as a working plan, not an error")
    func legacyTrip() {
        let empty = TripEndpointRow(.none)
        #expect(empty.endpoints == TripEndpoints.none)
    }

    @Test("clearing a start writes NULL rather than leaving yesterday behind")
    func clearingWritesNull() {
        let row = TripEndpointRow(TripEndpoints(origin: nil, destination: Self.groningen, radiusKm: nil))
        #expect(row.origin_lat == nil)
        #expect(row.origin_lng == nil)
        #expect(row.origin_label == nil)
        #expect(row.destination_lat == Self.groningen.lat)
    }

    // MARK: - A coordinate is a pair or it is nothing

    @Test("half a coordinate is no coordinate")
    func halfPair() {
        #expect(TripPlace.make(lat: 52.09, lng: nil, label: "Utrecht") == nil)
        #expect(TripPlace.make(lat: nil, lng: 5.12, label: "Utrecht") == nil)
    }

    @Test("NaN and infinity are refused")
    func notFinite() {
        #expect(TripPlace.make(lat: .nan, lng: 5.12, label: "x") == nil)
        #expect(TripPlace.make(lat: .infinity, lng: 5.12, label: "x") == nil)
        #expect(TripPlace.make(lat: 52.09, lng: .nan, label: "x") == nil)
    }

    @Test("a coordinate off the planet is refused", arguments: [
        (91.0, 5.0), (-91.0, 5.0), (52.0, 181.0), (52.0, -181.0),
    ])
    func offPlanet(lat: Double, lng: Double) {
        #expect(TripPlace.make(lat: lat, lng: lng, label: "x") == nil)
    }

    @Test("a point with no label keeps its coordinates rather than being dropped")
    func labelFallback() {
        #expect(TripPlace.make(lat: 52.0907, lng: 5.1214, label: nil)?.label == "52.0907, 5.1214")
        #expect(TripPlace.make(lat: 52.0907, lng: 5.1214, label: "   ")?.label == "52.0907, 5.1214")
    }

    // MARK: - The radius travels with the pair

    @Test("a radius outside what the panel offers is dropped, not clamped")
    func radiusBounds() {
        #expect(TripEndpoints.validRadius(0) == nil)
        #expect(TripEndpoints.validRadius(-5) == nil)
        #expect(TripEndpoints.validRadius(500) == nil)
        #expect(TripEndpoints.validRadius(8) == 8)
    }

    @Test("a never-chosen radius stays nil so the panel keeps suggesting one")
    func radiusStaysNil() {
        let row = TripEndpointRow(TripEndpoints(origin: Self.utrecht, destination: nil, radiusKm: nil))
        #expect(row.endpoints.radiusKm == nil)
    }

    // MARK: - The wire has to match the other two implementations

    @Test("the encoded column names are migration 058's, exactly")
    func columnNames() throws {
        let data = try JSONEncoder().encode(TripEndpointRow(
            TripEndpoints(origin: Self.utrecht, destination: Self.groningen, radiusKm: 8)))
        let object = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        #expect(object.keys.sorted() == [
            "destination_label", "destination_lat", "destination_lng",
            "origin_label", "origin_lat", "origin_lng", "radius_km",
        ])
    }
}
