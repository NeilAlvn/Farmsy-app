import SwiftUI
import CoreLocation

/// R4 — "farms on my way". The screen half of the corridor: it shows the farms the R2
/// engine (`Corridor.farmsAlongRoute`) finds beside the current drive, grouped into
/// readable stretches, in driving order, with a radius the user can widen.
///
/// Fed the FILTERED pin set the caller hands in — never every farm — so it can't offer
/// a farm the map is hiding (Aviah's one hard contract). Adding a stop re-routes: the
/// road updates, this recomputes, and the farm lands where the *new* road passes it.
/// Rows are controls — tapping one opens that farm.
///
/// R5b · each row says what the farm sells. The produce text comes from the flags
/// request the map already makes, joined on here rather than carried by every pin: the
/// corridor is the only thing that needs it, and a planner whose whole pitch is "cheese
/// on your way" was listing farms and naming towns.
///
/// R6 · each row says whether it is open WHEN YOU PASS, not whether it is open today.
/// The trip has a date and a departure time now (R7), and the corridor knows how far
/// along the drive each farm sits, so the question can finally be the real one.
struct RouteCorridorView: View {
    let road: [CLLocationCoordinate2D]
    let tripKm: Double?
    /// How long the road takes, in minutes. Nil when the route has no duration —
    /// the arrival estimate then falls back to the departure time itself, which
    /// asks about the day rather than the moment.
    let tripMinutes: Double?
    /// The day being planned for, `yyyy-mm-dd` (R7). Nil means today.
    let tripDate: String?
    /// When the drive sets off, minutes past midnight. Nil means 10:00, the same
    /// default the web's planner uses.
    let departMinutes: Int?
    /// Lowercase produce text per OSM id, from FarmsStore.produceByOsm (R5b).
    let produceByOsm: [String: String]
    let filteredFarms: [FarmPin]
    let stopIds: Set<String>
    let onOpenFarm: (FarmPin) -> Void
    let onAddStop: (FarmPin) -> Void

    /// nil = follow the drive; a value = the user has taken the slider over.
    @State private var chosenKm: Int? = nil

    private static let freeRows = 40

    // The radius follows the drive until the slider is touched: ~1/20th of the trip,
    // floored at 2 km so a short hop still finds something and capped at 20 because past
    // that "near the drive" stops meaning anything. A slider that keeps resetting itself
    // is worse than one that starts wrong.
    private var suggestedKm: Int {
        guard let tripKm else { return 10 }
        return max(2, min(20, Int((tripKm * 0.05).rounded())))
    }
    private var radiusKm: Int { chosenKm ?? suggestedKm }

    private var near: [Corridor.NearRoute<FarmPin>] {
        guard road.count >= 2 else { return [] }
        let pts = road.map { Corridor.Point(lat: $0.latitude, lng: $0.longitude) }
        return Corridor.farmsAlongRoute(pts, filteredFarms, radiusM: Double(radiusKm) * 1000) {
            Corridor.Point(lat: $0.lat, lng: $0.lng)
        }
        .filter { !stopIds.contains($0.farm.osmId) }
    }

    /// The weekday the drive is on, Monday-indexed, decided in Amsterdam.
    ///
    /// The trip is planned against Dutch and Belgian opening hours, so the day is
    /// theirs and not the phone's. A device set to Los Angeles would otherwise ask
    /// about Friday for a Saturday drive.
    private var dayMon: Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Amsterdam") ?? .current
        let date = tripDate.flatMap(TripEndpoints.day) ?? Date()
        let js = cal.component(.weekday, from: date) - 1   // 0=Sun … 6=Sat
        return [6, 0, 1, 2, 3, 4, 5][js]
    }

    /// Roughly when the drive reaches a farm, in minutes past midnight.
    ///
    /// A farm two thirds of the way along a two hour drive is passed about eighty
    /// minutes in. It is an estimate and it does not need to be better than one:
    /// it decides which of three words appears beside a name.
    ///
    /// Without a duration there is no arrival, so the question falls back to the
    /// departure time. "Open on Saturday" is weaker than "open when you arrive" and
    /// still far better than nothing.
    private func arrivalMinutes(along: Double) -> Int {
        let depart = Double(departMinutes ?? 10 * 60)
        guard let tripMinutes else { return Int(depart) }
        return Int((depart + along * tripMinutes).rounded())
    }

    var body: some View {
        let near = self.near
        VStack(alignment: .leading, spacing: 12) {
            // Header — title + count.
            VStack(alignment: .leading, spacing: 2) {
                Text("Farms on my way").font(.geist(17, .bold)).foregroundStyle(Color.ink)
                Text(near.isEmpty
                     ? String(localized: "Farms you would pass on this drive")
                     : String(localized: "\(near.count) farms on your route"))
                    .font(.geist(13)).foregroundStyle(Color.inkMuted)
            }

            // Radius control.
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text("Farms within").font(.geist(13, .medium)).foregroundStyle(Color.inkMuted)
                    Text("\(radiusKm) km").font(.geist(13, .bold)).foregroundStyle(Color.ink)
                }
                Slider(
                    value: Binding(
                        get: { Double(radiusKm) },
                        set: { chosenKm = Int($0.rounded()) }
                    ),
                    in: 2...20, step: 1
                )
                .tint(Color.farmGreen)
            }

            if near.isEmpty {
                // Say what to do — widen, or clear a filter — never a spinner or a blank.
                Text("No farms this close to the road. Try a wider distance, or clear a filter.")
                    .font(.geist(14)).foregroundStyle(Color.inkMuted)
                    .padding(.vertical, 4)
            } else {
                // Too many to hold in your head — cap the render and say so.
                let capped = near.count > Self.freeRows
                let shown = capped ? Array(near.prefix(Self.freeRows)) : near
                let legs: [Corridor.RouteLeg<FarmPin>] = (tripKm != nil && tripKm! > 0)
                    ? Corridor.legsAlongRoute(shown, tripKm: tripKm!)
                    : [Corridor.RouteLeg(fromKm: 0, toKm: 0, farms: shown)]

                ForEach(Array(legs.enumerated()), id: \.offset) { _, leg in
                    if leg.toKm > leg.fromKm {
                        Text("\(Int(leg.fromKm.rounded()))–\(Int(leg.toKm.rounded())) km · "
                             + String(localized: "\(leg.farms.count) within \(radiusKm) km"))
                            .font(.geist(12, .semibold)).foregroundStyle(Color.inkMuted)
                            .padding(.top, 4)
                    }
                    ForEach(leg.farms, id: \.farm.osmId) { n in
                        CorridorRow(farm: n.farm, offRouteM: n.offRoute,
                                    produce: produceByOsm[n.farm.osmId],
                                    arrivalMinutes: arrivalMinutes(along: n.along),
                                    dayMon: dayMon,
                                    onOpen: { onOpenFarm(n.farm) },
                                    onAdd: { onAddStop(n.farm) })
                    }
                }
                if capped {
                    Text("Show all \(near.count)")
                        .font(.geist(13, .semibold)).foregroundStyle(Color.farmGreen)
                        .padding(.top, 6)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// One corridor row: a control. Photo (or category tile), name, what it sells, whether it
/// is open when you pass and how far off the road, and an add button. Tapping the row
/// opens the farm; the add button puts it on the drive (which re-routes).
private struct CorridorRow: View {
    let farm: FarmPin
    let offRouteM: Double
    /// Comma-separated produce, lowercase, or nil (R5b).
    let produce: String?
    /// Roughly when the drive gets here, minutes past midnight (R6).
    let arrivalMinutes: Int
    /// The weekday of the drive, Monday-indexed.
    let dayMon: Int
    let onOpen: () -> Void
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            // Photo, or the category emoji on its tint when there's none.
            ZStack {
                RoundedRectangle(cornerRadius: 10).fill(farm.primaryCategory.color.opacity(0.18))
                if let image = farm.image, let url = URL(string: image) {
                    AsyncImage(url: url) { img in
                        img.resizable().scaledToFill()
                    } placeholder: { Color.clear }
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                } else {
                    Text(farm.primaryCategory.emoji).font(.system(size: 22))
                }
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(farm.name).font(.geist(14, .semibold)).foregroundStyle(Color.ink).lineLimit(1)

                // R5b · what it sells, falling back to the town. Four at most: the row
                // is one line, and a list that truncates mid-word says less than a
                // shorter one that doesn't.
                Text(RouteCorridorSells.sells(produce) ?? farm.city ?? "")
                    .font(.geist(12)).foregroundStyle(Color.inkMuted).lineLimit(1)

                HStack(spacing: 6) {
                    // R6 · open when you PASS, not open today. Unknown draws nothing
                    // rather than a guess — an unknown farm called open is a locked gate
                    // 40 minutes away. Half an hour of width covers stopping to look.
                    switch FarmFilters.statusOnDayBetween(
                        farm.openingHours, dayMon: dayMon,
                        fromMinutes: arrivalMinutes, toMinutes: arrivalMinutes + 30
                    ) {
                    case .open: openDot(Color.farmGreen, String(localized: "Open when you pass"))
                    case .closed: openDot(Color.inkMuted, String(localized: "Shut when you pass"))
                    case .unknown: EmptyView()
                    }
                    Text("\(Self.formatDistance(offRouteM)) " + String(localized: "off route"))
                        .font(.geist(12)).foregroundStyle(Color.inkMuted).lineLimit(1)
                }
            }
            Spacer(minLength: 0)

            Button { Haptics.tap(); onAdd() } label: {
                ZStack {
                    Circle().fill(Color.farmGreen.opacity(0.10))
                    Image(systemName: "plus").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.farmGreen)
                }.frame(width: 34, height: 34)
            }.buttonStyle(.plain)
        }
        .padding(10)
        .background(.white, in: RoundedRectangle(cornerRadius: 14))
        // A hairline so the card reads whether it sits on cream or on the white
        // overview box it's rendered inside.
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.hairline, lineWidth: 1))
        .contentShape(Rectangle())
        .onTapGesture { Haptics.tap(); onOpen() }
    }

    private func openDot(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(label).font(.geist(12, .medium)).foregroundStyle(color)
        }
    }


    private static func formatDistance(_ m: Double) -> String {
        m < 1000 ? "\(Int(m.rounded())) m" : String(format: "%.1f km", m / 1000)
    }
}

/// The produce text as a row reads it: at most four things, separated the way the web
/// separates them. Nil when there is nothing to say, so the caller falls back to the
/// town rather than printing an empty line.
///
/// Its own type rather than a method on the row so it can be tested without a view.
enum RouteCorridorSells {
    static func sells(_ produce: String?) -> String? {
        let parts = (produce ?? "")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .prefix(4)
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}
