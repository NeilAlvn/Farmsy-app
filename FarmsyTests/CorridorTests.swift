import Foundation
import Testing
@testable import Farmsy

/// The iOS mirror of the web `corridor.test.ts` — the four traps that cost time on web
/// are pinned here too: measure against the road not the straight line, interpolate
/// `along` inside the segment, sort by driving order not nearness, and project once at
/// mid-latitude. `Corridor` is pure, so these run without a simulator context.
struct CorridorTests {

    typealias P = Corridor.Point

    /// Utrecht → Amersfoort, roughly, as a router would return it.
    static let route: [P] = [
        P(lat: 52.0907, lng: 5.1214),
        P(lat: 52.1100, lng: 5.2000),
        P(lat: 52.1300, lng: 5.2800),
        P(lat: 52.1560, lng: 5.3878),
    ]

    static let mPerDegLat = 111_320.0

    /// The corridor over plain Points (farm == its own point).
    static func run(_ route: [P], _ farms: [P], _ r: Double) -> [Corridor.NearRoute<P>] {
        Corridor.farmsAlongRoute(route, farms, radiusM: r) { $0 }
    }

    @Test("a farm on the road is found and reads as no detour")
    func onRoad() {
        let onIt = P(lat: 52.1100, lng: 5.2000)
        let hit = Self.run(Self.route, [onIt], 5000).first
        #expect(hit != nil)
        #expect(hit!.offRoute < 1)
    }

    @Test("a farm beyond the radius is excluded, one inside it is kept")
    func radius() {
        let near = P(lat: 52.1300 + 2000 / Self.mPerDegLat, lng: 5.2800)
        let far = P(lat: 52.1300 + 40_000 / Self.mPerDegLat, lng: 5.2800)
        let found = Self.run(Self.route, [near, far], 5000)
        #expect(found.count == 1)
        #expect(found[0].farm.lat == near.lat)
    }

    @Test("distance off the road is metres, not degrees")
    func metres() {
        let north = P(lat: 52.1300 + 3000 / Self.mPerDegLat, lng: 5.2800)
        let hit = Self.run(Self.route, [north], 5000)[0]
        #expect(hit.offRoute > 2700 && hit.offRoute < 3300)
    }

    @Test("longitude is scaled by latitude")
    func lonScale() {
        // A degree of longitude is ~62% of a degree of latitude at 52°N.
        let north = P(lat: 52.1300 + 0.02, lng: 5.2800)
        let east = P(lat: 52.1300, lng: 5.2800 + 0.02)
        let a = Self.run(Self.route, [north], 50_000)[0].offRoute
        let b = Self.run(Self.route, [east], 50_000)[0].offRoute
        #expect(b < a)
        #expect(b / a < 0.8)
    }

    @Test("results come back in the order you would drive past them")
    func drivingOrder() {
        let start = P(lat: 52.0907, lng: 5.1214)
        let middle = P(lat: 52.1300, lng: 5.2800)
        let end = P(lat: 52.1560, lng: 5.3878)
        let found = Self.run(Self.route, [end, start, middle], 4000)
        #expect(found.map { $0.farm.lat } == [start.lat, middle.lat, end.lat])
    }

    @Test("driving order wins over nearness to the road")
    func orderBeatsNearness() {
        let earlyButFar = P(lat: 52.1100 + 3000 / Self.mPerDegLat, lng: 5.2000)
        let lateButNear = P(lat: 52.1560, lng: 5.3878)
        let found = Self.run(Self.route, [lateButNear, earlyButFar], 5000)
        #expect(found.map { $0.farm.lat } == [earlyButFar.lat, lateButNear.lat])
        #expect(found[0].offRoute > found[1].offRoute)
    }

    // The trap that caught a real bug: `along` recorded the start of the nearest segment.
    @Test("position along the drive runs 0 at the start to 1 at the end")
    func alongInterpolates() {
        let start = P(lat: 52.0907, lng: 5.1214)
        let end = P(lat: 52.1560, lng: 5.3878)
        let found = Self.run(Self.route, [start, end], 3000)
        let startAlong = found.first { $0.farm.lat == start.lat }!.along
        let endAlong = found.first { $0.farm.lat == end.lat }!.along
        #expect(startAlong < 0.1)
        #expect(endAlong > 0.9)
    }

    @Test("a repeated point in the polyline does not divide by zero")
    func dupPoint() {
        let dup: [P] = [
            P(lat: 52.0907, lng: 5.1214),
            P(lat: 52.0907, lng: 5.1214),
            P(lat: 52.1560, lng: 5.3878),
        ]
        let found = Self.run(dup, [P(lat: 52.0907, lng: 5.1214)], 1000)
        #expect(found.count == 1)
        #expect(found[0].offRoute.isFinite)
    }

    @Test("nothing is returned when there is no route, no farms, or no radius")
    func emptyCases() {
        #expect(Self.run([], [P(lat: 52, lng: 5)], 5000).isEmpty)
        #expect(Self.run([P(lat: 52, lng: 5)], [P(lat: 52, lng: 5)], 5000).isEmpty)
        #expect(Self.run(Self.route, [], 5000).isEmpty)
        #expect(Self.run(Self.route, [P(lat: 52.09, lng: 5.12)], 0).isEmpty)
    }

    @Test("measured against the road, not the straight line between its ends")
    func againstRoad() {
        let bent: [P] = [
            P(lat: 52.20, lng: 5.00),
            P(lat: 51.90, lng: 5.20),
            P(lat: 52.20, lng: 5.40),
        ]
        #expect(Self.run(bent, [P(lat: 52.20, lng: 5.20)], 5000).isEmpty)
    }

    @Test("the whole country is scanned fast enough to run on a slider")
    func fast() {
        let farms = (0..<8432).map { i in
            P(lat: 50.8 + Double(i % 100) * 0.028, lng: 3.4 + Double(i / 100) * 0.045)
        }
        let t0 = Date()
        let found = Self.run(Self.route, farms, 10_000)
        let ms = Date().timeIntervalSince(t0) * 1000
        #expect(!found.isEmpty)
        #expect(ms < 250)
    }

    // ── Stretches of road ────────────────────────────────────────────────────

    func at(_ along: Double, _ offRoute: Double, _ name: String) -> Corridor.NearRoute<String> {
        Corridor.NearRoute(farm: name, offRoute: offRoute, along: along)
    }

    @Test("a drive is cut into about five stretches, whatever its length")
    func aboutFive() {
        for km in [40.0, 60.0, 120.0, 200.0, 340.0, 600.0] {
            let legs = Int(ceil(km / Corridor.legLengthKm(km)))
            #expect(legs >= 3 && legs <= 8)
        }
    }

    @Test("short hops still get a stretch rather than a zero-length one")
    func shortHops() {
        #expect(Corridor.legLengthKm(4) == 5)
        #expect(Corridor.legLengthKm(0) == 5)
    }

    @Test("the order is the drive, and being far off the road does not move a farm")
    func stretchOrder() {
        let near = [
            at(0.05, 500, "first"),
            at(0.10, 4000, "second, far off"),
            at(0.90, 500, "third"),
            at(0.95, 200, "last"),
        ]
        let legs = Corridor.legsAlongRoute(near, tripKm: 100)
        #expect(legs.flatMap { $0.farms.map { $0.farm } } == ["first", "second, far off", "third", "last"])
        #expect(legs.first!.fromKm < legs.last!.fromKm)
    }

    @Test("a stretch with nothing beside it is left out, not shown empty")
    func noEmptyStretch() {
        let legs = Corridor.legsAlongRoute([at(0.02, 100, "a"), at(0.98, 100, "b")], tripKm: 100)
        #expect(legs.count == 2)
        #expect(legs[0].farms.count == 1)
        #expect(legs[1].farms.count == 1)
    }

    @Test("the last stretch ends where the drive ends, not where the step would")
    func lastStretchEnds() {
        let legs = Corridor.legsAlongRoute([at(0.999, 100, "last")], tripKm: 172)
        #expect(legs.last!.toKm == 172)
    }

    @Test("every farm lands in exactly one stretch")
    func everyFarmOnce() {
        let near = (0..<200).map { at(Double($0) / 200, Double($0) * 10, "f\($0)") }
        let legs = Corridor.legsAlongRoute(near, tripKm: 137)
        #expect(legs.reduce(0) { $0 + $1.farms.count } == 200)
        #expect(Set(legs.flatMap { $0.farms.map { $0.farm } }).count == 200)
    }

    @Test("nothing to group is not a stretch")
    func nothingToGroup() {
        #expect(Corridor.legsAlongRoute([Corridor.NearRoute<String>](), tripKm: 100).isEmpty)
        #expect(Corridor.legsAlongRoute([at(0.5, 100, "a")], tripKm: 0).isEmpty)
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    @Test("thinning the road keeps its ends and drops the detail between")
    func thinning() {
        let dense = (0..<200).map { P(lat: 52 + Double($0) * 0.00009, lng: 5) }
        let thin = Corridor.simplifyRoute(dense, minSpacingM: 100)
        #expect(thin.count < dense.count / 5)
        #expect(thin.first!.lat == dense.first!.lat)
        #expect(thin.last!.lat == dense.last!.lat)
    }

    @Test("thinning never returns fewer than the two ends")
    func thinningFloor() {
        let two = [P(lat: 52, lng: 5), P(lat: 52.001, lng: 5)]
        #expect(Corridor.simplifyRoute(two, minSpacingM: 100_000).count == 2)
        #expect(Corridor.simplifyRoute([P(lat: 52, lng: 5), P(lat: 52, lng: 5.2), P(lat: 52, lng: 5.4)], minSpacingM: 500_000).count == 2)
    }

    @Test("smoothing keeps both ends and leaves no sharp corner")
    func smoothing() {
        let corner = [P(lat: 52, lng: 5), P(lat: 52, lng: 5.2), P(lat: 52.2, lng: 5.2)]
        let smooth = Corridor.smoothRoute(corner, iterations: 2)
        #expect(smooth.first!.lat == corner.first!.lat)
        #expect(smooth.last!.lat == corner.last!.lat)
        var sharpest = 0.0
        for i in 1..<(smooth.count - 1) {
            let ax = smooth[i].lng - smooth[i - 1].lng, ay = smooth[i].lat - smooth[i - 1].lat
            let bx = smooth[i + 1].lng - smooth[i].lng, by = smooth[i + 1].lat - smooth[i].lat
            let turn = abs(atan2(ax * by - ay * bx, ax * bx + ay * by))
            sharpest = max(sharpest, turn)
        }
        #expect(sharpest < .pi / 5)
    }

    @Test("smoothing a line with nothing to smooth returns it unharmed")
    func smoothingNoop() {
        let two = [P(lat: 52, lng: 5), P(lat: 52.1, lng: 5.1)]
        let out = Corridor.smoothRoute(two, iterations: 2)
        #expect(out.count == 2)
        #expect(out[0].lat == two[0].lat)
        #expect(out[1].lng == two[1].lng)
    }
}
