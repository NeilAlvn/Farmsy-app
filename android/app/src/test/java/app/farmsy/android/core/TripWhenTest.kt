package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/// R7: a saved trip remembers when it was for.
///
/// The same rules live in `Farmsy/Core/TripEndpoints.swift`, in
/// `src/lib/tripEndpoints.ts`, and again in migration 059 as a CHECK. Four
/// places, one truth — the same arrangement as R1.
///
/// The bug guarded here is quieter than a missing day. It is a day that is not
/// a day. `2026-02-31` matches `yyyy-mm-dd` and is not a date; a lenient parser
/// rolls it into March, and the trip then compares opening hours against the
/// wrong weekday and reports it with full confidence.
class TripWhenTest {

    // ── A date that is really a date ────────────────────────────────────────

    @Test
    fun `a real date survives`() {
        assertEquals("2026-09-12", TripEndpoints.validDate("2026-09-12"))
        assertEquals("2024-02-29", TripEndpoints.validDate("2024-02-29"))   // leap year
        assertEquals("2026-12-31", TripEndpoints.validDate("2026-12-31"))
        assertEquals("2026-09-12", TripEndpoints.validDate("  2026-09-12  "))
    }

    @Test
    fun `a date that matches the shape but is not a day is refused`() {
        // Each of these passes a pattern and fails a calendar.
        for (bad in listOf("2026-02-31", "2026-02-29", "2026-13-01", "2026-00-10", "2026-04-31")) {
            assertNull(bad, TripEndpoints.validDate(bad))
        }
    }

    @Test
    fun `anything that is not yyyy-mm-dd is refused`() {
        for (bad in listOf("", "   ", "2026-9-12", "12-09-2026", "2026/09/12",
                           "2026-09-12T00:00:00Z", "yyyy-mm-dd", "2026-09-1x", "20260912")) {
            assertNull(bad, TripEndpoints.validDate(bad))
        }
        assertNull(TripEndpoints.validDate(null))
    }

    // ── Minutes past midnight ───────────────────────────────────────────────

    @Test
    fun `a departure inside the day survives`() {
        assertEquals(0, TripEndpoints.validDepartMinutes(0))        // midnight is a time
        assertEquals(570, TripEndpoints.validDepartMinutes(570))
        assertEquals(1439, TripEndpoints.validDepartMinutes(1439))  // 23:59 is the last minute
    }

    @Test
    fun `a departure outside the day is refused, matching the 059 CHECK`() {
        assertNull(TripEndpoints.validDepartMinutes(-1))
        assertNull(TripEndpoints.validDepartMinutes(1440))          // 24:00 is tomorrow
        assertNull(TripEndpoints.validDepartMinutes(99999))
        assertNull(TripEndpoints.validDepartMinutes(null))
    }

    // ── The round trip ──────────────────────────────────────────────────────

    @Test
    fun `a day survives being saved and reopened`() {
        val planned = TripEndpoints(
            origin = TripPlace.make(52.0907, 5.1214, "Utrecht"),
            destination = TripPlace.make(53.2194, 6.5665, "Groningen"),
            radiusKm = 8.0,
            date = "2026-09-12",
            departMinutes = 570,
        )
        val reopened = TripEndpointRow.from(planned).endpoints
        assertEquals("2026-09-12", reopened.date)
        assertEquals(570, reopened.departMinutes)
        assertEquals(planned, reopened)
    }

    @Test
    fun `a trip from before R7 reopens with no day, and invents none`() {
        // Every trip saved before 059 has NULL in both columns. Decoding one
        // must produce a usable plan, not an error and not today's date frozen
        // into a trip that never had one.
        val row = TripEndpointRow.from(TripEndpoints.NONE)
        assertNull(row.tripDate)
        assertNull(row.departMinutes)
        assertNull(row.endpoints.date)
        assertNull(row.endpoints.departMinutes)
    }

    @Test
    fun `a bad day never reaches the database`() {
        // The CHECK in 059 would reject the row and lose the whole save. Drop
        // the bad field here instead: a trip with no day still saves.
        val row = TripEndpointRow.from(
            TripEndpoints(date = "2026-02-31", departMinutes = 1440)
        )
        assertNull(row.tripDate)
        assertNull(row.departMinutes)
    }

    @Test
    fun `every column the row writes is a column the query reads back`() {
        // The failure this prevents: a column added to the row and forgotten in
        // the select, so the value is written and never seen again.
        for (column in listOf("trip_date", "depart_minutes", "radius_km",
                              "origin_lat", "origin_lng", "origin_label",
                              "destination_lat", "destination_lng", "destination_label")) {
            assertTrue("$column is selected", TripEndpointRow.COLUMNS.contains(column))
        }
    }

    // ── The calendar helpers the picker is built on ─────────────────────────

    @Test
    fun `a date survives being turned into a LocalDate and back`() {
        for (iso in listOf("2026-09-12", "2024-02-29", "2026-01-01", "2026-12-31")) {
            assertEquals(iso, TripEndpoints.day(iso)?.let(TripEndpoints::dayString))
        }
    }

    @Test
    fun `an unusable day makes no date at all`() {
        assertNull(TripEndpoints.day("2026-02-31"))
        assertNull(TripEndpoints.day("not a date"))
        assertNull(TripEndpoints.day(null))
    }

    @Test
    fun `the default day is always a Saturday`() {
        // Every day of one week, so the modulo is exercised in both directions
        // rather than only on whatever day the suite happens to run.
        for (offset in 0L until 7L) {
            val from = LocalDate.of(2026, 9, 7).plusDays(offset)
            val saturday = TripEndpoints.nextSaturday(from)
            val asDate = TripEndpoints.day(saturday)
            assertNotNull(saturday, asDate)
            assertEquals("$saturday is a Saturday", DayOfWeek.SATURDAY, asDate!!.dayOfWeek)
            assertTrue("$saturday is not behind $from", !asDate.isBefore(from))
        }
    }

    @Test
    fun `today counts when today is Saturday`() {
        // Otherwise somebody opening the planner on a Saturday morning is
        // offered next weekend for the trip they are about to drive.
        val saturday = LocalDate.of(2026, 9, 12)
        assertEquals("the fixture really is a Saturday", DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertEquals("2026-09-12", TripEndpoints.nextSaturday(saturday))
    }

    @Test
    fun `the default day is never in the past`() {
        val today = TripEndpoints.dayString(LocalDate.now(TripEndpoints.zone))
        assertTrue("yyyy-mm-dd sorts as it reads", TripEndpoints.nextSaturday() >= today)
    }
}
