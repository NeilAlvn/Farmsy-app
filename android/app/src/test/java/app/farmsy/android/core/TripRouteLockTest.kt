package app.farmsy.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Task 4: looking is free, Farmsy ordering the stops and drawing the road is
/// Plus. `TripStore.isRouteLocked` is the one place that decision is made, so
/// the stop list, the totals line and the Save/Show route/Maps actions in
/// TripsScreen all agree with each other and with the route fetch itself.
class TripRouteLockTest {

    @Test
    fun `a member never locks, however many stops`() {
        assertFalse(TripStore.isRouteLocked(hasFullAccess = true, stopCount = 0))
        assertFalse(TripStore.isRouteLocked(hasFullAccess = true, stopCount = 1))
        assertFalse(TripStore.isRouteLocked(hasFullAccess = true, stopCount = 2))
        assertFalse(TripStore.isRouteLocked(hasFullAccess = true, stopCount = 12))
    }

    @Test
    fun `a free visitor locks only once there is an order to sell`() {
        // A single farm is a plain directions request — free either way.
        assertFalse(TripStore.isRouteLocked(hasFullAccess = false, stopCount = 0))
        assertFalse(TripStore.isRouteLocked(hasFullAccess = false, stopCount = 1))
        // Two or more stops is Farmsy doing the ordering — Plus.
        assertTrue(TripStore.isRouteLocked(hasFullAccess = false, stopCount = 2))
        assertTrue(TripStore.isRouteLocked(hasFullAccess = false, stopCount = 7))
    }
}
