import Foundation
import CoreLocation
import Testing
@testable import Farmsy

/// Home's "Available near you": counts per list item within the radius, nearest
/// distance, most farms first, and nothing for a product nobody nearby sells.
struct ProductsNearbyTests {

    static let origin = CLLocationCoordinate2D(latitude: 52.09, longitude: 5.12)

    /// A pin through the decoder, the only way to make one — same shape the RPC returns.
    static func pin(_ id: String, dLat: Double) -> FarmPin {
        let json = """
        {"id":"\(id)","osm_id":"\(id)","name":"\(id)","lat":\(origin.latitude + dLat),"lng":\(origin.longitude)}
        """
        return try! JSONDecoder().decode(FarmPin.self, from: Data(json.utf8))
    }

    static let eggs = ShoppingItem(id: "eggs", nl: "Eieren", en: "Eggs", terms: ["eggs", "eieren"])
    static let cheese = ShoppingItem(id: "cheese", nl: "Kaas", en: "Cheese", terms: ["cheese", "kaas"])
    static let fish = ShoppingItem(id: "fish", nl: "Vis", en: "Fish", terms: ["fish", "vis"])

    @Test("counts, nearest distance, order, and the radius cut")
    func nearby() {
        // 0.01° lat ≈ 1.1 km. Three farms inside 15 km, one at ~33 km.
        let pins = [Self.pin("a", dLat: 0.02), Self.pin("b", dLat: 0.05),
                    Self.pin("c", dLat: 0.09), Self.pin("far", dLat: 0.30)]
        let produce = ["a": "eieren, kaas", "b": "kaas", "c": "kaas, vis", "far": "vis, eieren"]
        let out = FarmsStore.productsNearby(items: [Self.eggs, Self.cheese, Self.fish], pins: pins,
                                            produce: produce, origin: Self.origin, radiusKm: 15)
        #expect(out.map(\.item.id) == ["cheese", "eggs", "fish"])
        #expect(out[0].count == 3)
        #expect(out[1].count == 1)   // the far farm's eggs are outside the radius
        #expect(abs(out[1].nearestKm - 2.2) < 0.2)
        #expect(out[2].count == 1)
    }

    @Test("a product nobody nearby sells is absent, not zero")
    func absent() {
        let out = FarmsStore.productsNearby(items: [Self.fish], pins: [Self.pin("a", dLat: 0.01)],
                                            produce: ["a": "kaas"], origin: Self.origin, radiusKm: 15)
        #expect(out.isEmpty)
    }

    /// `category` and `image` must survive the decoder. They are `let`s decoded
    /// off `/api/shopping/items`; giving them stored defaults would make Swift
    /// silently drop them (only a warning), and the hand-built items above would
    /// never catch it. Decode from JSON — the shape the endpoint returns.
    @Test("decoded category and image arrive; imageSlug prefers image")
    func decodesNewFields() throws {
        let json = """
        {"id":"cheese","nl":"Kaas","en":"Cheese","terms":["cheese","kaas"],"category":"dairy","image":"cheese-tile"}
        """
        let item = try JSONDecoder().decode(ShoppingItem.self, from: Data(json.utf8))
        #expect(item.category == "dairy")
        #expect(item.image == "cheese-tile")
        #expect(item.imageSlug == "cheese-tile")
    }

    /// An older server omits both fields: they decode to nil and imageSlug falls
    /// back to the id, which is the tile file name for every shopping item.
    @Test("missing category and image decode to nil, imageSlug falls back to id")
    func decodesWithoutNewFields() throws {
        let json = """
        {"id":"eggs","nl":"Eieren","en":"Eggs","terms":["eggs","eieren"]}
        """
        let item = try JSONDecoder().decode(ShoppingItem.self, from: Data(json.utf8))
        #expect(item.category == nil)
        #expect(item.image == nil)
        #expect(item.imageSlug == "eggs")
    }
}
