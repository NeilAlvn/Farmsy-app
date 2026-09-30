import Foundation
import CoreLocation
import Testing
@testable import Farmsy

/// `rankForIntent` orders AI-search results by the server's weights. It used to
/// score both sides of every comparison inside `sorted`, so a 1,000-farm result
/// ran the opening-hours parser ~20,000 times per map render — Sentry's
/// "App Hanging" wave on 1.3 (37). It now scores each farm once. These tests pin
/// the order so that change cannot move anything.
struct RankForIntentTests {

    static func pin(_ id: String, lat: Double = 52.0, lng: Double = 5.0, hours: String? = nil,
                    image: String? = nil, rating: Double? = nil, reviews: Int = 0,
                    verified: Bool = false) -> FarmPin {
        FarmPin(id: id, osmId: id, name: id, lat: lat, lng: lng, address: nil, city: nil,
                postalCode: nil, country: nil, phone: nil, website: nil, openingHours: hours,
                image: image, primaryTag: nil, farmType: [], avgRating: rating,
                reviewCount: reviews, hasDescription: false, isVerified: verified)
    }

    @Test("signals rank a verified, photographed, reviewed farm above a bare one")
    func signalsOrder() {
        let plain = Self.pin("plain")
        let good = Self.pin("good", image: "x.jpg", rating: 4.5, reviews: 30, verified: true)
        let out = FarmsStore.rankForIntent([plain, good], origin: nil, ranking: nil)
        #expect(out.map(\.id) == ["good", "plain"])
    }

    @Test("distance dominates when there is an origin")
    func distanceDominates() {
        let origin = CLLocationCoordinate2D(latitude: 52.0, longitude: 5.0)
        // ~111 km north: distance score 0. Its signals total 15+10+9+7.5 = 41.5.
        let far = Self.pin("far", lat: 53.0, lng: 5.0, image: "x.jpg", rating: 4.5, reviews: 30, verified: true)
        // ~140 m away: distance score ≈ 99.9, no signals.
        let near = Self.pin("near", lat: 52.001, lng: 5.001)
        let out = FarmsStore.rankForIntent([far, near], origin: origin, ranking: nil)
        #expect(out.map(\.id) == ["near", "far"])
    }

    @Test("open today counts, with the server's weight")
    func openTodayWeight() {
        let open = Self.pin("open", hours: "24/7")
        let shut = Self.pin("shut", hours: nil, image: "x.jpg")   // hasPhoto = 10 < openToday = 20
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: nil).map(\.id) == ["open", "shut"])
        var w = SearchRanking.default
        w.openToday = 5                                            // now photo (10) wins
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: w).map(\.id) == ["shut", "open"])
    }

    @Test("ties keep incoming order, and every farm survives")
    func stableAndComplete() {
        let list = (0..<200).map { Self.pin("p\($0)") }
        #expect(FarmsStore.rankForIntent(list, origin: nil, ranking: nil).map(\.id) == list.map(\.id))
    }

    @Test("an unknown ranking version falls back to the defaults")
    func unknownVersionFallsBack() {
        var w = SearchRanking.default
        w.version = 99
        w.openToday = 0
        let open = Self.pin("open", hours: "24/7")
        let shut = Self.pin("shut", image: "x.jpg")
        // Defaults apply (openToday 20 > hasPhoto 10), not the version-99 weights.
        #expect(FarmsStore.rankForIntent([shut, open], origin: nil, ranking: w).map(\.id) == ["open", "shut"])
    }
}
