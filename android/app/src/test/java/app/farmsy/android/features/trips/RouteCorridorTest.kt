package app.farmsy.android.features.trips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/// R5b and R6, the parts of the corridor row that are arithmetic rather than layout.
///
/// R5b's bug is invisible: the produce line fell back to the town name, so a planner
/// whose whole pitch is "cheese on your way" listed farms and named towns. Nobody reads
/// "Utrecht" under a farm name and thinks something is missing.
///
/// R6's is quieter still. The row asked whether a farm is open TODAY while the user was
/// planning a Saturday, so a farm shut on the day they were driving read as open.
///
/// Mirrors `CorridorSellsTests` in the iOS suite.
class RouteCorridorTest {

    // ── R5b · what it sells ─────────────────────────────────────────────────

    @Test
    fun `a produce list becomes a row of things, not a sentence`() {
        assertEquals("cheese · milk", sells("cheese, milk"))
        assertEquals("cheese · milk · eggs", sells("cheese,milk,eggs"))
    }

    @Test
    fun `a long list is cut rather than truncated mid-word`() {
        // The row is one line. Five things clipped by the layout say less than four
        // that fit.
        assertEquals("a · b · c · d", sells("a, b, c, d, e, f"))
    }

    @Test
    fun `nothing to say returns null, so the caller falls back to the town`() {
        assertNull(sells(null))
        assertNull(sells(""))
        assertNull(sells("   "))
        assertNull(sells(",,, ,"))
    }

    @Test
    fun `spacing in the stored text does not reach the screen`() {
        assertEquals("cheese · milk", sells("  cheese ,   milk  "))
    }

    // ── R6 · when you get there ─────────────────────────────────────────────

    @Test
    fun `arrival is the departure plus the share of the drive already done`() {
        // Two thirds along a 120 minute drive that left at 10:00 is about 12:20.
        assertEquals(10 * 60 + 80, arrivalMinutes(10 * 60, 120.0, 2.0 / 3.0))
        assertEquals(10 * 60, arrivalMinutes(10 * 60, 120.0, 0.0), )
        assertEquals(12 * 60, arrivalMinutes(10 * 60, 120.0, 1.0))
    }

    @Test
    fun `without a duration the question falls back to the departure time`() {
        // "Open on Saturday" is weaker than "open when you arrive" and far better
        // than nothing, so a route with no duration still answers.
        assertEquals(9 * 60, arrivalMinutes(9 * 60, null, 0.5))
    }

    @Test
    fun `no departure time means ten in the morning, the same as the web`() {
        assertEquals(10 * 60, arrivalMinutes(null, null, 0.0))
        assertEquals(10 * 60 + 30, arrivalMinutes(null, 60.0, 0.5))
    }

    @Test
    fun `the weekday is Monday-indexed`() {
        // 2026-09-12 is a Saturday, 2026-09-14 a Monday.
        assertEquals(5, dayMonOf("2026-09-12"))
        assertEquals(0, dayMonOf("2026-09-14"))
        assertEquals(6, dayMonOf("2026-09-13"))   // Sunday is last, not first
    }

    @Test
    fun `a trip with no day asks about today, not about a day nobody chose`() {
        val today = LocalDate.now(app.farmsy.android.core.TripEndpoints.zone).dayOfWeek.value - 1
        assertEquals(today, dayMonOf(null))
        assertEquals(today, dayMonOf("2026-02-31"))   // not a day, so not a day to ask about
    }
}
