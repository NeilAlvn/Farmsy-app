import Testing
@testable import Farmsy

/// Task 4: looking is free, Farmsy ordering the stops and drawing the road is
/// Plus. `TripStore.isRouteLocked` is the one place that decision is made, so
/// the stop list, the totals line and the Save/Show route/Maps buttons in
/// TripsView all agree with each other and with the route fetch itself.
struct TripRouteLockTests {

    @Test("a member never locks, however many stops")
    func memberNeverLocks() {
        #expect(TripStore.isRouteLocked(hasFullAccess: true, stopCount: 0) == false)
        #expect(TripStore.isRouteLocked(hasFullAccess: true, stopCount: 1) == false)
        #expect(TripStore.isRouteLocked(hasFullAccess: true, stopCount: 2) == false)
        #expect(TripStore.isRouteLocked(hasFullAccess: true, stopCount: 12) == false)
    }

    @Test("a free visitor locks only once there is an order to sell")
    func freeLocksAtTwoStops() {
        // A single farm is a plain directions request — free either way.
        #expect(TripStore.isRouteLocked(hasFullAccess: false, stopCount: 0) == false)
        #expect(TripStore.isRouteLocked(hasFullAccess: false, stopCount: 1) == false)
        // Two or more stops is Farmsy doing the ordering — Plus.
        #expect(TripStore.isRouteLocked(hasFullAccess: false, stopCount: 2) == true)
        #expect(TripStore.isRouteLocked(hasFullAccess: false, stopCount: 7) == true)
    }
}
