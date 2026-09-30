import Foundation
import Testing
@testable import Farmsy

/// The shopping list is the one thing onboarding, the farm card and the picker
/// all write to. Order and idempotence are what the planner relies on.
@MainActor
struct TripStoreTests {

    @Test("toggle adds in pick order, toggling again removes, source is required")
    func toggleRoundTrip() {
        let trip = TripStore()
        trip.clearProducts()
        trip.toggleProduct("eggs", source: .picker)
        trip.toggleProduct("cheese", source: .farmDetail)
        #expect(trip.wantedProducts == ["eggs", "cheese"])
        trip.toggleProduct("eggs", source: .picker)
        #expect(trip.wantedProducts == ["cheese"])
        trip.clearProducts()
    }
}
