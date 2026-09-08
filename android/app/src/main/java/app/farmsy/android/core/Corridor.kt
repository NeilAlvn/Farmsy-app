package app.farmsy.android.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/// Which farms sit close to a drive, and how far off it each one is — R2.
///
/// Ported 1:1 from the web `src/lib/corridor.ts`. There is deliberately no server
/// side: the naive answer (ask the router for a detour per candidate) is 8,000+
/// routing calls that exhaust the quota and take minutes. Instead the road is fetched
/// once (TripStore already does, via POST /api/route) and every farm is measured
/// against that polyline locally. The app already holds all the pins, so this needs no
/// request and no DB round trip — it runs while a radius slider is dragged.
///
/// Measuring against the real road, not a straight line, is the point: a straight line
/// Amsterdam→Maastricht passes through places the motorway does not, so it would offer
/// farms an hour away and hide ones you drive past.
object Corridor {

    /// A geographic point. Both FarmPin and the trip's LatLng map onto this.
    interface Point {
        val lat: Double
        val lng: Double
    }

    private data class Pt(override val lat: Double, override val lng: Double) : Point

    /// One farm found beside the drive.
    data class NearRoute<T>(
        val farm: T,
        /// Metres from the driven line, at its closest.
        val offRoute: Double,
        /// 0 at the start of the drive, 1 at the end. Orders the stops.
        val along: Double,
    )

    // Metres per degree, near enough for a country. Everything here compares distances
    // a few km apart at one latitude, so curvature over that span doesn't change the
    // answer — and projecting to flat metres once is far cheaper than a haversine per
    // segment per farm across thousands of farms while a slider moves.
    private const val M_PER_DEG_LAT = 111_320.0

    private data class Scale(val x: Double, val y: Double)

    private fun scaleFor(lat: Double): Scale =
        Scale(x = M_PER_DEG_LAT * cos(lat * Math.PI / 180.0), y = M_PER_DEG_LAT)

    /// Square of the distance from p to the segment ab, and how far along ab the closest
    /// point lies (0 at a, 1 at b), all in projected metres.
    ///
    /// `t` comes back because the caller needs it: reporting a farm's position as the
    /// start of its nearest segment puts everything on a long final stretch at the
    /// beginning of that stretch, so a farm at the destination reads as two-thirds
    /// through the drive and the stops sort wrongly.
    private data class Closest(val d2: Double, val t: Double)

    private fun closestOnSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double,
    ): Closest {
        val abx = bx - ax
        val aby = by - ay
        val len2 = abx * abx + aby * aby

        // A zero-length segment is a duplicated point in the polyline, which routers do
        // emit. Treat it as the point rather than dividing by zero.
        if (len2 == 0.0) {
            val dx = px - ax
            val dy = py - ay
            return Closest(dx * dx + dy * dy, 0.0)
        }

        // How far along ab the closest point lies, clamped to stay on the segment.
        var t = ((px - ax) * abx + (py - ay) * aby) / len2
        t = if (t < 0.0) 0.0 else if (t > 1.0) 1.0 else t

        val cx = ax + t * abx
        val cy = ay + t * aby
        val dx = px - cx
        val dy = py - cy
        return Closest(dx * dx + dy * dy, t)
    }

    /// The farms within `radiusM` of the driven line, in the order you pass them.
    ///
    /// `route` is the polyline as the router returned it. `farms` is the set to search —
    /// on the app this must be the FILTERED pin set the map is showing, never every
    /// farm, or the drive offers farms the map is hiding. Filtering the pool happens at
    /// the call site; the bounding-box rejection here is what keeps it cheap.
    fun <T : Point> farmsAlongRoute(route: List<Point>, farms: List<T>, radiusM: Double): List<NearRoute<T>> {
        if (route.size < 2 || farms.isEmpty() || radiusM <= 0.0) return emptyList()

        val scale = scaleFor(route[route.size / 2].lat)

        // Project the road once. Per-farm would repeat this for every one of thousands.
        val xs = DoubleArray(route.size)
        val ys = DoubleArray(route.size)
        for (i in route.indices) {
            xs[i] = route[i].lng * scale.x
            ys[i] = route[i].lat * scale.y
        }

        // Cumulative length, so a farm's position along the drive can be reported
        // without walking the line a second time.
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) {
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            cum[i] = cum[i - 1] + hypot(dx, dy)
        }
        val total = cum[route.size - 1].takeIf { it != 0.0 } ?: 1.0

        // A box around the whole road, widened by the radius. Most farms in the country
        // are nowhere near any given drive; rejecting them with two comparisons is what
        // keeps this in the tens of milliseconds.
        var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
        for (i in route.indices) {
            if (xs[i] < minX) minX = xs[i]
            if (xs[i] > maxX) maxX = xs[i]
            if (ys[i] < minY) minY = ys[i]
            if (ys[i] > maxY) maxY = ys[i]
        }
        minX -= radiusM; maxX += radiusM; minY -= radiusM; maxY += radiusM

        val r2 = radiusM * radiusM
        val out = ArrayList<NearRoute<T>>()

        for (farm in farms) {
            val px = farm.lng * scale.x
            val py = farm.lat * scale.y
            if (px < minX || px > maxX || py < minY || py > maxY) continue

            var best = Double.POSITIVE_INFINITY
            var bestAt = 0.0
            for (i in 1 until route.size) {
                val (d2, t) = closestOnSegment(px, py, xs[i - 1], ys[i - 1], xs[i], ys[i])
                if (d2 < best) {
                    best = d2
                    // Where along the whole drive, not where the segment began.
                    bestAt = cum[i - 1] + t * (cum[i] - cum[i - 1])
                    // Close enough to stop looking. A farm 50m from the road won't be
                    // found nearer further along, and the roads here double back often
                    // enough for this to save real work.
                    if (best < 2500.0) break
                }
            }
            if (best <= r2) {
                out.add(NearRoute(farm, sqrt(best), bestAt / total))
            }
        }

        // In the order you drive past them, not by how near the road they are. Sorting
        // by distance from the tarmac reads as a relevance ranking, and this is a
        // journey: "what is next" is the only question a route planner is asked.
        out.sortBy { it.along }
        return out
    }

    /// A stretch of the drive, and the farms beside that stretch.
    data class RouteLeg<T>(
        /// Kilometres from the start of the drive where this stretch begins.
        val fromKm: Double,
        /// And where it ends.
        val toKm: Double,
        /// In the order you drive past them.
        val farms: List<NearRoute<T>>,
    )

    /// How long a stretch should be for a drive of this length — about five stretches,
    /// whatever the distance, so the list reads the same on a hop to the next town as on
    /// a cross-country haul. Rounded to 5 km because the number ends up in a heading:
    /// "0–40 km" is a landmark where "0–38 km" is arithmetic.
    fun legLengthKm(tripKm: Double): Double =
        maxOf(5.0, Math.round(tripKm / 25.0) * 5.0)

    /// The corridor cut into stretches of road, in the order you drive them.
    ///
    /// The order is the drive, first thing passed to last, and nothing reorders it — how
    /// far off the road a farm sits is worth knowing (every row carries it) but is not a
    /// reason to move it earlier or later than you reach it. Five headings turn a
    /// thousand rows into "what is in the next half hour". Empty stretches are left out:
    /// a heading over no farms is a hole, not information.
    fun <T> legsAlongRoute(near: List<NearRoute<T>>, tripKm: Double): List<RouteLeg<T>> {
        if (near.isEmpty() || tripKm <= 0.0) return emptyList()

        val step = legLengthKm(tripKm)
        val last = maxOf(0, Math.ceil(tripKm / step).toInt() - 1)
        val end = Math.round(tripKm).toDouble()
        val legs = ArrayList<RouteLeg<T>>()
        val bucketFarms = ArrayList<ArrayList<NearRoute<T>>>()
        var index = -1

        // `near` arrives sorted by position, so the stretch index only ever goes up and a
        // change of index means a new stretch. No grouping table needed.
        for (item in near) {
            val i = maxOf(0, minOf(last, Math.floor((item.along * tripKm) / step).toInt()))
            if (i != index) {
                val fromKm = i * step
                // The final stretch ends where the drive does, not where the step would
                // carry it — "160–180 km" on a 172 km drive is a road that is not there.
                val toKm = minOf(fromKm + step, maxOf(end, fromKm + 1.0))
                legs.add(RouteLeg(fromKm, toKm, emptyList()))
                bucketFarms.add(ArrayList())
                index = i
            }
            bucketFarms[bucketFarms.size - 1].add(item)
        }

        return legs.mapIndexed { i, leg -> leg.copy(farms = bucketFarms[i]) }
    }

    /// The corners taken off a polyline, by Chaikin's method. DRAWING ONLY (the coverage
    /// band overlay), never measuring — it cuts inside a turn by a fraction of the point
    /// spacing. Replacing every corner with two points a quarter in from each side,
    /// twice, leaves a curve with no corner left to draw. Ported from web `smoothRoute`.
    fun smoothRoute(route: List<Point>, iterations: Int = 2): List<Point> {
        var pts = route
        var n = 0
        while (n < iterations && pts.size > 2) {
            val out = ArrayList<Point>()
            out.add(pts[0])
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                out.add(Pt(a.lat + 0.25 * (b.lat - a.lat), a.lng + 0.25 * (b.lng - a.lng)))
                out.add(Pt(a.lat + 0.75 * (b.lat - a.lat), a.lng + 0.75 * (b.lng - a.lng)))
            }
            out.add(pts[pts.size - 1])
            pts = out
            n++
        }
        return pts
    }

    /// The same road with the fine detail taken out. Only for drawing the coverage band,
    /// never for measuring — a router's polyline carries every slip road at a few metres
    /// apart, and drawn kilometres wide each wiggle becomes a spike the size of a town.
    /// Both ends are always kept. Ported from web `simplifyRoute`.
    fun <T : Point> simplifyRoute(route: List<T>, minSpacingM: Double): List<T> {
        if (route.size < 3 || minSpacingM <= 0.0) return route

        val scale = scaleFor(route[route.size / 2].lat)
        val min2 = minSpacingM * minSpacingM
        val out = ArrayList<T>()
        out.add(route[0])
        var lastX = route[0].lng * scale.x
        var lastY = route[0].lat * scale.y

        for (i in 1 until route.size - 1) {
            val x = route[i].lng * scale.x
            val y = route[i].lat * scale.y
            val dx = x - lastX
            val dy = y - lastY
            if (dx * dx + dy * dy < min2) continue
            out.add(route[i])
            lastX = x
            lastY = y
        }

        out.add(route[route.size - 1])
        return out
    }
}
