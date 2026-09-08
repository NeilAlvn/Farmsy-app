package app.farmsy.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// JVM unit tests for the corridor — the Android mirror of the web `corridor.test.ts`,
/// where the four traps that cost time on web are pinned: measure against the road not
/// the straight line, interpolate `along` inside the segment, sort by driving order not
/// nearness, and project once at mid-latitude. `Corridor` is pure Kotlin, no framework.
class CorridorTest {

    private data class F(override val lat: Double, override val lng: Double) : Corridor.Point

    // Utrecht → Amersfoort, roughly, as a router would return it.
    private val route: List<Corridor.Point> = listOf(
        F(52.0907, 5.1214),
        F(52.1100, 5.2000),
        F(52.1300, 5.2800),
        F(52.1560, 5.3878),
    )

    private val M_PER_DEG_LAT = 111_320.0

    @Test fun `a farm on the road is found and reads as no detour`() {
        val onIt = F(52.1100, 5.2000)
        val hit = Corridor.farmsAlongRoute(route, listOf(onIt), 5000.0).firstOrNull()
        assertTrue("expected the farm to be found", hit != null)
        assertTrue("expected ~0m off route, got ${hit!!.offRoute}", hit.offRoute < 1.0)
    }

    @Test fun `a farm beyond the radius is excluded, one inside it is kept`() {
        val near = F(52.1300 + 2000.0 / M_PER_DEG_LAT, 5.2800)
        val far = F(52.1300 + 40_000.0 / M_PER_DEG_LAT, 5.2800)
        val found = Corridor.farmsAlongRoute(route, listOf(near, far), 5000.0)
        assertEquals(1, found.size)
        assertEquals(near, found[0].farm)
    }

    @Test fun `distance off the road is metres, not degrees`() {
        val north = F(52.1300 + 3000.0 / M_PER_DEG_LAT, 5.2800)
        val hit = Corridor.farmsAlongRoute(route, listOf(north), 5000.0)[0]
        assertTrue("got ${hit.offRoute}", hit.offRoute > 2700.0 && hit.offRoute < 3300.0)
    }

    @Test fun `longitude is scaled by latitude`() {
        // A degree of longitude is ~62% of a degree of latitude at 52°N, so the same
        // offset east must come out nearer than the same offset north.
        val north = F(52.1300 + 0.02, 5.2800)
        val east = F(52.1300, 5.2800 + 0.02)
        val a = Corridor.farmsAlongRoute(route, listOf(north), 50_000.0)[0].offRoute
        val b = Corridor.farmsAlongRoute(route, listOf(east), 50_000.0)[0].offRoute
        assertTrue("east should be nearer than north", b < a)
        assertTrue("expected roughly cos(52°), got ratio ${b / a}", b / a < 0.8)
    }

    @Test fun `results come back in the order you would drive past them`() {
        val start = F(52.0907, 5.1214)
        val middle = F(52.1300, 5.2800)
        val end = F(52.1560, 5.3878)
        val found = Corridor.farmsAlongRoute(route, listOf(end, start, middle), 4000.0)
        assertEquals(listOf(start, middle, end), found.map { it.farm })
    }

    @Test fun `driving order wins over nearness to the road`() {
        // The far one comes first on the drive, the near one comes last. Ordering by
        // distance from the tarmac would flip them, sending the person backwards.
        val earlyButFar = F(52.1100 + 3000.0 / M_PER_DEG_LAT, 5.2000)
        val lateButNear = F(52.1560, 5.3878)
        val found = Corridor.farmsAlongRoute(route, listOf(lateButNear, earlyButFar), 5000.0)
        assertEquals(listOf(earlyButFar, lateButNear), found.map { it.farm })
        assertTrue("first result should be the further one", found[0].offRoute > found[1].offRoute)
    }

    // The trap that caught a real bug: `along` recorded the start of the nearest segment
    // rather than the closest point on it, so a farm at the destination read as 0.6.
    @Test fun `position along the drive runs 0 at the start to 1 at the end`() {
        val start = F(52.0907, 5.1214)
        val end = F(52.1560, 5.3878)
        val found = Corridor.farmsAlongRoute(route, listOf(start, end), 3000.0)
        val at = found.associate { it.farm to it.along }
        assertTrue("start read as ${at[start]}", at[start]!! < 0.1)
        assertTrue("end read as ${at[end]}", at[end]!! > 0.9)
    }

    @Test fun `a repeated point in the polyline does not divide by zero`() {
        val dup = listOf<Corridor.Point>(
            F(52.0907, 5.1214),
            F(52.0907, 5.1214),
            F(52.1560, 5.3878),
        )
        val found = Corridor.farmsAlongRoute(dup, listOf(F(52.0907, 5.1214)), 1000.0)
        assertEquals(1, found.size)
        assertTrue(found[0].offRoute.isFinite())
    }

    @Test fun `nothing is returned when there is no route, no farms, or no radius`() {
        assertTrue(Corridor.farmsAlongRoute(emptyList(), listOf(F(52.0, 5.0)), 5000.0).isEmpty())
        assertTrue(Corridor.farmsAlongRoute(listOf(F(52.0, 5.0)), listOf(F(52.0, 5.0)), 5000.0).isEmpty())
        assertTrue(Corridor.farmsAlongRoute(route, emptyList<F>(), 5000.0).isEmpty())
        assertTrue(Corridor.farmsAlongRoute(route, listOf(F(52.09, 5.12)), 0.0).isEmpty())
    }

    @Test fun `measured against the road, not the straight line between its ends`() {
        // A drive that dips well south. A farm on the direct line between the endpoints
        // is nowhere near the road, and must not be offered.
        val bent = listOf<Corridor.Point>(
            F(52.20, 5.00),
            F(51.90, 5.20),
            F(52.20, 5.40),
        )
        assertTrue(Corridor.farmsAlongRoute(bent, listOf(F(52.20, 5.20)), 5000.0).isEmpty())
    }

    @Test fun `the whole country is scanned fast enough to run on a slider`() {
        val farms = (0 until 8432).map { i ->
            F(50.8 + (i % 100) * 0.028, 3.4 + (i / 100) * 0.045)
        }
        val t0 = System.nanoTime()
        val found = Corridor.farmsAlongRoute(route, farms, 10_000.0)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        assertTrue(found.isNotEmpty())
        assertTrue("took ${ms}ms for 8,432 farms", ms < 250.0)
    }

    // ── Stretches of road ────────────────────────────────────────────────────

    private fun at(along: Double, offRoute: Double, name: String): Corridor.NearRoute<String> =
        Corridor.NearRoute(name, offRoute, along)

    @Test fun `a drive is cut into about five stretches, whatever its length`() {
        for (km in listOf(40.0, 60.0, 120.0, 200.0, 340.0, 600.0)) {
            val legs = Math.ceil(km / Corridor.legLengthKm(km)).toInt()
            assertTrue("${km}km gave $legs stretches", legs in 3..8)
        }
    }

    @Test fun `short hops still get a stretch rather than a zero-length one`() {
        assertEquals(5.0, Corridor.legLengthKm(4.0), 0.0)
        assertEquals(5.0, Corridor.legLengthKm(0.0), 0.0)
    }

    @Test fun `the order is the drive, and being far off the road does not move a farm`() {
        val near = listOf(
            at(0.05, 500.0, "first"),
            at(0.10, 4000.0, "second, far off"),
            at(0.90, 500.0, "third"),
            at(0.95, 200.0, "last"),
        )
        val legs = Corridor.legsAlongRoute(near, 100.0)
        assertEquals(
            listOf("first", "second, far off", "third", "last"),
            legs.flatMap { l -> l.farms.map { it.farm } },
        )
        assertTrue(legs.first().fromKm < legs.last().fromKm)
    }

    @Test fun `a stretch with nothing beside it is left out, not shown empty`() {
        val legs = Corridor.legsAlongRoute(listOf(at(0.02, 100.0, "a"), at(0.98, 100.0, "b")), 100.0)
        assertEquals(2, legs.size)
        assertEquals(1, legs[0].farms.size)
        assertEquals(1, legs[1].farms.size)
    }

    @Test fun `the last stretch ends where the drive ends, not where the step would`() {
        // 172km: the step is 35km, so the final boundary would land at 175.
        val legs = Corridor.legsAlongRoute(listOf(at(0.999, 100.0, "last")), 172.0)
        assertEquals(172.0, legs.last().toKm, 0.0)
    }

    @Test fun `every farm lands in exactly one stretch`() {
        val near = (0 until 200).map { at(it / 200.0, it * 10.0, "f$it") }
        val legs = Corridor.legsAlongRoute(near, 137.0)
        assertEquals(200, legs.sumOf { it.farms.size })
        assertEquals(200, legs.flatMap { l -> l.farms.map { it.farm } }.toSet().size)
    }

    @Test fun `nothing to group is not a stretch`() {
        assertTrue(Corridor.legsAlongRoute(emptyList<Corridor.NearRoute<String>>(), 100.0).isEmpty())
        assertTrue(Corridor.legsAlongRoute(listOf(at(0.5, 100.0, "a")), 0.0).isEmpty())
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    @Test fun `thinning the road keeps its ends and drops the detail between`() {
        val dense = (0 until 200).map { F(52.0 + it * 0.00009, 5.0) }
        val thin = Corridor.simplifyRoute(dense, 100.0)
        assertTrue("kept ${thin.size} of ${dense.size}", thin.size < dense.size / 5)
        assertEquals(dense.first(), thin.first())
        assertEquals(dense.last(), thin.last())
    }

    @Test fun `thinning never returns fewer than the two ends`() {
        val two = listOf(F(52.0, 5.0), F(52.001, 5.0))
        assertEquals(two, Corridor.simplifyRoute(two, 100_000.0))
        assertEquals(2, Corridor.simplifyRoute(listOf(F(52.0, 5.0), F(52.0, 5.2), F(52.0, 5.4)), 500_000.0).size)
    }

    @Test fun `smoothing keeps both ends and leaves no sharp corner`() {
        val corner = listOf<Corridor.Point>(F(52.0, 5.0), F(52.0, 5.2), F(52.2, 5.2))
        val smooth = Corridor.smoothRoute(corner, 2)
        assertEquals(corner.first().lat, smooth.first().lat, 0.0)
        assertEquals(corner.last().lat, smooth.last().lat, 0.0)
        var sharpest = 0.0
        for (i in 1 until smooth.size - 1) {
            val ax = smooth[i].lng - smooth[i - 1].lng; val ay = smooth[i].lat - smooth[i - 1].lat
            val bx = smooth[i + 1].lng - smooth[i].lng; val by = smooth[i + 1].lat - smooth[i].lat
            val turn = Math.abs(Math.atan2(ax * by - ay * bx, ax * bx + ay * by))
            sharpest = maxOf(sharpest, turn)
        }
        assertTrue("sharpest turn was ${sharpest * 180 / Math.PI} degrees", sharpest < Math.PI / 5)
    }

    @Test fun `smoothing a line with nothing to smooth returns it unharmed`() {
        val two = listOf<Corridor.Point>(F(52.0, 5.0), F(52.1, 5.1))
        val out = Corridor.smoothRoute(two, 2)
        assertEquals(2, out.size)
        assertEquals(two[0].lat, out[0].lat, 0.0)
        assertEquals(two[1].lng, out[1].lng, 0.0)
    }
}
