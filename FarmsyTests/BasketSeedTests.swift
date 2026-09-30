import Foundation
import Testing
@testable import Farmsy

/// The onboarding basket writes into the same list the picker and the farm card
/// use. It must add what was picked, keep the pick order, and never duplicate.
struct BasketSeedTests {

    @Test("adds only what is missing, in pick order")
    func toAdd() {
        #expect(BasketSeed.toAdd(picked: ["eggs", "cheese", "milk"], current: ["cheese"]) == ["eggs", "milk"])
        #expect(BasketSeed.toAdd(picked: [], current: ["cheese"]).isEmpty)
        #expect(BasketSeed.toAdd(picked: ["eggs", "eggs"], current: []) == ["eggs"])
    }

    @Test("the offline fallback is twelve real catalogue ids with labels in both languages")
    func fallback() {
        let ids = BasketSeed.fallback.map(\.id)
        #expect(ids == ["eggs", "cheese", "milk", "potatoes", "vegetables", "fruits",
                        "meat", "honey", "bread", "strawberry", "apples", "butter"])
        for item in BasketSeed.fallback { #expect(!item.nl.isEmpty && !item.en.isEmpty) }
    }
}
