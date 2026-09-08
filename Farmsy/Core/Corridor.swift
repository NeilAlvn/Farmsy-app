import Foundation

/// Which farms sit close to a drive, and how far off it each one is — R2.
///
/// Ported 1:1 from the web `src/lib/corridor.ts`. There is deliberately no server side:
/// the naive answer (ask the router for a detour per candidate) is 8,000+ routing calls
/// that exhaust the quota and take minutes. Instead the road is fetched once (TripStore
/// already does, via POST /api/route) and every farm is measured against that polyline
/// locally — no request, no DB round trip, so it runs while a radius slider is dragged.
///
/// Measuring against the real road, not a straight line, is the point: a straight line
/// Amsterdam→Maastricht passes through places the motorway does not, so it would offer
/// farms an hour away and hide ones you drive past.
enum Corridor {

    /// A geographic point. Both FarmPin and the trip's coordinate map onto this.
    struct Point {
        let lat: Double
        let lng: Double
        init(lat: Double, lng: Double) { self.lat = lat; self.lng = lng }
    }

    /// One farm found beside the drive.
    struct NearRoute<T> {
        let farm: T
        /// Metres from the driven line, at its closest.
        let offRoute: Double
        /// 0 at the start of the drive, 1 at the end. Orders the stops.
        let along: Double
    }

    // Metres per degree, near enough for a country. Everything here compares distances a
    // few km apart at one latitude, so curvature over that span doesn't change the
    // answer — and projecting to flat metres once is far cheaper than a haversine per
    // segment per farm across thousands of farms while a slider moves.
    private static let mPerDegLat = 111_320.0

    private static func scaleFor(_ lat: Double) -> (x: Double, y: Double) {
        (x: mPerDegLat * cos(lat * .pi / 180), y: mPerDegLat)
    }

    /// Square of the distance from p to segment ab, and how far along ab the closest
    /// point lies (0 at a, 1 at b), all in projected metres.
    ///
    /// `t` comes back because the caller needs it: reporting a farm's position as the
    /// start of its nearest segment puts everything on a long final stretch at the
    /// beginning of that stretch, so a farm at the destination reads as two-thirds
    /// through the drive and the stops sort wrongly.
    private static func closestOnSegment(
        _ px: Double, _ py: Double,
        _ ax: Double, _ ay: Double,
        _ bx: Double, _ by: Double
    ) -> (d2: Double, t: Double) {
        let abx = bx - ax
        let aby = by - ay
        let len2 = abx * abx + aby * aby

        // A zero-length segment is a duplicated point in the polyline, which routers do
        // emit. Treat it as the point rather than dividing by zero.
        if len2 == 0 {
            let dx = px - ax
            let dy = py - ay
            return (dx * dx + dy * dy, 0)
        }

        // How far along ab the closest point lies, clamped to stay on the segment.
        var t = ((px - ax) * abx + (py - ay) * aby) / len2
        t = t < 0 ? 0 : (t > 1 ? 1 : t)

        let cx = ax + t * abx
        let cy = ay + t * aby
        let dx = px - cx
        let dy = py - cy
        return (dx * dx + dy * dy, t)
    }

    /// The farms within `radiusM` of the driven line, in the order you pass them.
    ///
    /// `route` is the polyline as the router returned it. `farms` is the set to search —
    /// on the app this must be the FILTERED pin set the map is showing, never every
    /// farm, or the drive offers farms the map is hiding. Filtering the pool happens at
    /// the call site; the bounding-box rejection here is what keeps it cheap.
    static func farmsAlongRoute<T>(
        _ route: [Point], _ farms: [T], radiusM: Double, point: (T) -> Point
    ) -> [NearRoute<T>] {
        if route.count < 2 || farms.isEmpty || radiusM <= 0 { return [] }

        let scale = scaleFor(route[route.count / 2].lat)

        // Project the road once. Per-farm would repeat this for every one of thousands.
        var xs = [Double](repeating: 0, count: route.count)
        var ys = [Double](repeating: 0, count: route.count)
        for i in route.indices {
            xs[i] = route[i].lng * scale.x
            ys[i] = route[i].lat * scale.y
        }

        // Cumulative length, so a farm's position along the drive can be reported
        // without walking the line a second time.
        var cum = [Double](repeating: 0, count: route.count)
        for i in 1..<route.count {
            let dx = xs[i] - xs[i - 1]
            let dy = ys[i] - ys[i - 1]
            cum[i] = cum[i - 1] + (dx * dx + dy * dy).squareRoot()
        }
        let total = cum[route.count - 1] == 0 ? 1 : cum[route.count - 1]

        // A box around the whole road, widened by the radius. Most farms in the country
        // are nowhere near any given drive; rejecting them with two comparisons is what
        // keeps this in the tens of milliseconds.
        var minX = Double.infinity, maxX = -Double.infinity
        var minY = Double.infinity, maxY = -Double.infinity
        for i in route.indices {
            if xs[i] < minX { minX = xs[i] }
            if xs[i] > maxX { maxX = xs[i] }
            if ys[i] < minY { minY = ys[i] }
            if ys[i] > maxY { maxY = ys[i] }
        }
        minX -= radiusM; maxX += radiusM; minY -= radiusM; maxY += radiusM

        let r2 = radiusM * radiusM
        var out: [NearRoute<T>] = []

        for farm in farms {
            let p = point(farm)
            let px = p.lng * scale.x
            let py = p.lat * scale.y
            if px < minX || px > maxX || py < minY || py > maxY { continue }

            var best = Double.infinity
            var bestAt = 0.0
            for i in 1..<route.count {
                let r = closestOnSegment(px, py, xs[i - 1], ys[i - 1], xs[i], ys[i])
                if r.d2 < best {
                    best = r.d2
                    // Where along the whole drive, not where the segment began.
                    bestAt = cum[i - 1] + r.t * (cum[i] - cum[i - 1])
                    // Close enough to stop looking. A farm 50m from the road will not be
                    // found nearer further along, and the roads here double back often
                    // enough for this to save real work.
                    if best < 2500 { break }
                }
            }
            if best <= r2 {
                out.append(NearRoute(farm: farm, offRoute: best.squareRoot(), along: bestAt / total))
            }
        }

        // In the order you drive past them, not by how near the road they are. Sorting
        // by distance from the tarmac reads as a relevance ranking, and this is a
        // journey: "what is next" is the only question a route planner is asked.
        return out.sorted { $0.along < $1.along }
    }

    /// A stretch of the drive, and the farms beside that stretch.
    struct RouteLeg<T> {
        /// Kilometres from the start of the drive where this stretch begins.
        let fromKm: Double
        /// And where it ends.
        let toKm: Double
        /// In the order you drive past them.
        var farms: [NearRoute<T>]
    }

    /// How long a stretch should be for a drive of this length — about five stretches,
    /// whatever the distance, so the list reads the same on a hop to the next town as on
    /// a cross-country haul. Rounded to 5 km because the number ends up in a heading:
    /// "0–40 km" is a landmark where "0–38 km" is arithmetic.
    static func legLengthKm(_ tripKm: Double) -> Double {
        max(5, (tripKm / 25).rounded() * 5)
    }

    /// The corridor cut into stretches of road, in the order you drive them.
    ///
    /// The order is the drive, first thing passed to last, and nothing reorders it — how
    /// far off the road a farm sits is worth knowing (every row carries it) but is not a
    /// reason to move it earlier or later than you reach it. Five headings turn a
    /// thousand rows into "what is in the next half hour". Empty stretches are left out:
    /// a heading over no farms is a hole, not information.
    static func legsAlongRoute<T>(_ near: [NearRoute<T>], tripKm: Double) -> [RouteLeg<T>] {
        if near.isEmpty || !(tripKm > 0) { return [] }

        let step = legLengthKm(tripKm)
        let last = max(0, Int(ceil(tripKm / step)) - 1)
        let end = tripKm.rounded()
        var legs: [RouteLeg<T>] = []
        var index = -1

        // `near` arrives sorted by position, so the stretch index only ever goes up and a
        // change of index means a new stretch. No grouping table needed.
        for item in near {
            let i = max(0, min(last, Int(floor((item.along * tripKm) / step))))
            if i != index {
                let fromKm = Double(i) * step
                // The final stretch ends where the drive does, not where the step would
                // carry it — "160–180 km" on a 172 km drive is a road that is not there.
                let toKm = min(fromKm + step, max(end, fromKm + 1))
                legs.append(RouteLeg(fromKm: fromKm, toKm: toKm, farms: []))
                index = i
            }
            legs[legs.count - 1].farms.append(item)
        }

        return legs
    }

    /// The corners taken off a polyline, by Chaikin's method. DRAWING ONLY (the coverage
    /// band overlay), never measuring — it cuts inside a turn by a fraction of the point
    /// spacing. Replacing every corner with two points a quarter in from each side,
    /// twice, leaves a curve with no corner left to draw. Ported from web `smoothRoute`.
    static func smoothRoute(_ route: [Point], iterations: Int = 2) -> [Point] {
        var pts = route
        var n = 0
        while n < iterations && pts.count > 2 {
            var out: [Point] = [pts[0]]
            for i in 0..<(pts.count - 1) {
                let a = pts[i]
                let b = pts[i + 1]
                out.append(Point(lat: a.lat + 0.25 * (b.lat - a.lat), lng: a.lng + 0.25 * (b.lng - a.lng)))
                out.append(Point(lat: a.lat + 0.75 * (b.lat - a.lat), lng: a.lng + 0.75 * (b.lng - a.lng)))
            }
            out.append(pts[pts.count - 1])
            pts = out
            n += 1
        }
        return pts
    }

    /// The same road with the fine detail taken out. Only for drawing the coverage band,
    /// never for measuring — a router's polyline carries every slip road at a few metres
    /// apart, and drawn kilometres wide each wiggle becomes a spike the size of a town.
    /// Both ends are always kept. Ported from web `simplifyRoute`.
    static func simplifyRoute(_ route: [Point], minSpacingM: Double) -> [Point] {
        if route.count < 3 || minSpacingM <= 0 { return route }

        let scale = scaleFor(route[route.count / 2].lat)
        let min2 = minSpacingM * minSpacingM
        var out: [Point] = [route[0]]
        var lastX = route[0].lng * scale.x
        var lastY = route[0].lat * scale.y

        for i in 1..<(route.count - 1) {
            let x = route[i].lng * scale.x
            let y = route[i].lat * scale.y
            let dx = x - lastX
            let dy = y - lastY
            if dx * dx + dy * dy < min2 { continue }
            out.append(route[i])
            lastX = x
            lastY = y
        }

        out.append(route[route.count - 1])
        return out
    }
}
