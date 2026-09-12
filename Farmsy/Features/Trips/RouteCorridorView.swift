import SwiftUI
import CoreLocation

/// R4 — "farms on my way". The screen half of the corridor: it shows the farms the R2
/// engine (`Corridor.farmsAlongRoute`) finds beside the current drive, grouped into
/// readable stretches, in driving order, with a radius the user can widen.
///
/// Fed the FILTERED pin set the caller hands in — never every farm — so it can't offer
/// a farm the map is hiding (Aviah's one hard contract). Adding a stop re-routes: the
/// road updates, this recomputes, and the farm lands where the *new* road passes it.
/// Rows are controls — tapping one opens that farm. "Open when you pass" is R6's
/// `statusOnDay` for today, so it comes for free.
struct RouteCorridorView: View {
    let road: [CLLocationCoordinate2D]
    let tripKm: Double?
    let filteredFarms: [FarmPin]
    let stopIds: Set<String>
    /// R6 — the chosen day (0=Mon…6=Sun), the departure (minutes past midnight) and
    /// the drive's total duration. Together they answer "open *when you pass*, on
    /// the day you're going" instead of "open at all today".
    let dayMon: Int
    let departMinutes: Int
    let durationSeconds: Double?
    let onOpenFarm: (FarmPin) -> Void
    let onAddStop: (FarmPin) -> Void

    /// A farm's open/closed status at the moment the drive reaches it. Arrival is
    /// `depart + along · duration` (R6): `along` is the fraction of the drive at
    /// which the farm sits, so a shop 80% of the way along is asked about late in
    /// the trip, not at the start. A 30-minute visit window. With no duration
    /// (straight-line fallback) we can't place the arrival, so we fall back to
    /// "open at all on the chosen day" — the honest weaker answer.
    private func passStatus(_ n: Corridor.NearRoute<FarmPin>) -> FarmFilters.DayStatus {
        guard let durationSeconds, durationSeconds > 0 else {
            return FarmFilters.statusOnDay(n.farm.openingHours, dayMon: dayMon)
        }
        let arrival = departMinutes + Int((n.along * durationSeconds / 60).rounded())
        return FarmFilters.statusOnDayBetween(
            n.farm.openingHours, dayMon: dayMon,
            fromMinutes: arrival, toMinutes: arrival + 30
        )
    }

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
                                    status: passStatus(n),
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

/// One corridor row: a control. Photo (or category tile), name + what it sells + how far
/// off the road, an open dot, and an add button. Tapping the row opens the farm; the add
/// button puts it on the drive (which re-routes).
private struct CorridorRow: View {
    let farm: FarmPin
    let offRouteM: Double
    /// Open/closed at the arrival time, computed by the parent (R6).
    let status: FarmFilters.DayStatus
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
                HStack(spacing: 6) {
                    // R6 · open when you pass, on the day you're going. Unknown draws
                    // nothing rather than a guess — an unknown farm called open is a
                    // locked gate 40 min away.
                    switch status {
                    case .open: openDot(Color.farmGreen, String(localized: "Open when you pass"))
                    case .closed: openDot(Color.inkMuted, String(localized: "Closed then"))
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
