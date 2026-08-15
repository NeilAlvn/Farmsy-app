import SwiftUI
import CoreLocation

/// The trip planner (Aviah's spec + Neil's screenshots): two tabs — Plan (the
/// local draft) and My trips (saved trips from the DB). Stops come from "Add to
/// trip" on farm cards; the road route + totals come from POST /api/route; the
/// route is drawn on the map. My Trips is Pro-gated.
struct TripsView: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @Environment(TripStore.self) private var trip

    @State private var tab: Tab = .plan
    @State private var naming = false
    @State private var tripName = ""
    @State private var armedDelete: String?
    @State private var reorderNote: String?

    enum Tab { case plan, mine }

    private var stops: [FarmPin] { trip.stopIds.compactMap { farms.pin(forOsmId: $0) } }
    private var pinIndex: [String: FarmPin] {
        Dictionary(farms.pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a })
    }
    private let SLOTS = 8

    var body: some View {
        VStack(spacing: 0) {
            header
            tabs.padding(.horizontal, 14).padding(.top, 4)
            ScrollView(showsIndicators: false) {
                if tab == .plan { planTab } else { mineTab }
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await trip.refreshRoute(pins: pinIndex) }
        .task { if let uid { await trip.loadTrips(userId: uid) } }
        .onChange(of: trip.stopIds) { _, _ in Task { await trip.refreshRoute(pins: pinIndex) } }
        .alert("Name your trip", isPresented: $naming) {
            TextField("My weekend trip", text: $tripName)
            Button("Save") { Task { await save() } }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var uid: String? { session.session?.user.id.uuidString.lowercased() }

    // MARK: - Header + tabs

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                .font(.system(size: 18, weight: .semibold)).foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Color.farmGreenMap, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text("Trip planner").font(.geist(19, .bold)).foregroundStyle(Color.ink)
                Text("Plan your farm adventure").font(.geist(13)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            circleButton("plus") { Haptics.tap(); trip.clear(); tab = .plan }
            circleButton("xmark") { dismiss() }
        }
        .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 6)
    }

    private var tabs: some View {
        HStack(spacing: 10) {
            tabButton("Plan a trip", .plan)
            tabButton("My trips", .mine)
        }
    }

    private func tabButton(_ title: String, _ t: Tab) -> some View {
        Button { Haptics.tap(); tab = t } label: {
            Text(title).font(.geist(15, .bold))
                .foregroundStyle(tab == t ? .white : Color.ink)
                .frame(maxWidth: .infinity).padding(.vertical, 13)
                .background(tab == t ? Color.farmGreenMap : .white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(tab == t ? Color.clear : Color.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func circleButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color(hex: 0x6B7280)).frame(width: 40, height: 40)
                .overlay(Circle().stroke(Color.hairline, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    // MARK: - Plan tab

    private var planTab: some View {
        VStack(spacing: 14) {
            // Starting point.
            HStack(spacing: 10) {
                Image(systemName: "mappin.circle").font(.system(size: 18)).foregroundStyle(Color.inkMuted)
                Text(trip.originLabel ?? String(localized: "Choose a starting point"))
                    .font(.geist(15)).foregroundStyle(trip.originLabel == nil ? Color.inkMuted : Color.ink)
                    .lineLimit(1)
                Spacer()
                Button { Task { await locate() } } label: {
                    Image(systemName: "location.circle").font(.system(size: 20)).foregroundStyle(Color.farmGreenMap)
                }.buttonStyle(.plain)
            }
            .padding(14)
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))

            // Trip overview.
            VStack(alignment: .leading, spacing: 0) {
                Text("Trip overview").font(.geist(16, .bold)).foregroundStyle(Color.ink)
                    .padding(14)
                Divider()
                ForEach(0..<max(SLOTS, stops.count), id: \.self) { i in
                    if i < stops.count { filledRow(i: i, pin: stops[i]) } else { emptyRow(i: i) }
                    if i < max(SLOTS, stops.count) - 1 { Divider().padding(.leading, 60) }
                }
            }
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))

            if let reorderNote {
                Text(reorderNote).font(.geist(12)).foregroundStyle(Color.farmGreenMap)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            // Time + distance.
            totalsBar

            // Save + Show route.
            HStack(spacing: 10) {
                outlineButton("Save trip", icon: "bookmark") { naming = true; tripName = "" }
                    .disabled(stops.isEmpty)
                filledButton("Show route", icon: "location.north.fill") {
                    Task { await trip.refreshRoute(pins: pinIndex) }
                    dismiss()
                }
                .disabled(stops.count < 2)
            }

            outlineButton("Open in Google Maps", icon: "arrow.up.forward.square") { openGoogleMaps() }
                .disabled(stops.isEmpty)

            if stops.count >= 3 {
                Button { reorder() } label: {
                    Text("Best order").font(.geist(14, .semibold)).foregroundStyle(Color.farmGreen)
                }.buttonStyle(.plain)
            }

            tipNote
        }
        .padding(14)
    }

    private func filledRow(i: Int, pin: FarmPin) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(pin.name).font(.geist(15, .semibold)).foregroundStyle(Color.ink).lineLimit(1)
                Text(legLabel(i)).font(.geist(12)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            Button { Haptics.tap(); trip.remove(pin.osmId) } label: {
                Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.inkMuted)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture { dismiss(); onOpenFarm(pin) }
    }

    private func emptyRow(i: Int) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(Color.inkMuted.opacity(0.6))
                .frame(width: 28, height: 28)
                .overlay(Circle().strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1.5, dash: [3])))
            VStack(alignment: .leading, spacing: 2) {
                Text("Pick a farm on the map").font(.geist(15)).foregroundStyle(Color.inkMuted)
                Text("—").font(.geist(12)).foregroundStyle(Color.inkMuted.opacity(0.5))
            }
            Spacer()
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
    }

    private var totalsBar: some View {
        HStack(spacing: 10) {
            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                .font(.system(size: 15)).foregroundStyle(Color.farmGreenMap)
            if stops.count < 2 {
                Text("Add farms to see time and distance").font(.geist(14)).foregroundStyle(Color.inkMuted)
            } else if trip.isRouting {
                Text("Finding the road…").font(.geist(14)).foregroundStyle(Color.inkMuted)
            } else {
                Text(totalsText).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
            }
            Spacer()
            Image(systemName: "car").font(.system(size: 15)).foregroundStyle(Color.inkMuted.opacity(0.5))
        }
        .padding(14)
        .background(Color(hex: 0xF3F6F2), in: RoundedRectangle(cornerRadius: 16))
    }

    private var totalsText: String {
        let km = (trip.distanceMeters ?? 0) / 1000
        let mins = Int((trip.durationSeconds ?? 0) / 60)
        let time = mins >= 60 ? "\(mins / 60) h \(mins % 60)" : "\(mins) min"
        return String(format: "~%.0f km · %@%@", km, time, trip.onRoads ? "" : " (est.)")
    }

    /// The drive to stop i from the previous stop (or the start).
    private func legLabel(_ i: Int) -> String {
        let coords = ([trip.originCoord].compactMap { $0 }) + stops.map(\.coordinate)
        let idx = trip.originCoord != nil ? i + 1 : i
        guard idx >= 1, idx < coords.count else { return "Start" }
        let km = TripGeometry.haversineKm(coords[idx - 1], coords[idx])
        return String(format: "~%.0f km · %d min", km, TripGeometry.roughDriveMinutes(km))
    }

    // MARK: - My trips tab

    @ViewBuilder
    private var mineTab: some View {
        if !session.isAuthenticated {
            gate(String(localized: "Sign in to view your trips"))
        } else if session.profile?.hasFullAccess != true {
            gate(String(localized: "Saved trips are a Farmsy Pro feature."))
        } else {
            VStack(spacing: 14) {
                // Draft banner.
                Text(trip.stopIds.isEmpty ? String(localized: "No trip in progress")
                     : String(localized: "A draft with \(trip.stopIds.count) stops is waiting"))
                    .font(.geist(14)).foregroundStyle(Color.inkMuted)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1, dash: [4])))
                    .contentShape(Rectangle())
                    .onTapGesture { if !trip.stopIds.isEmpty { tab = .plan } }

                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("My Trips").font(.geist(16, .bold)).foregroundStyle(Color.ink)
                        Spacer()
                        Text("\(trip.savedTrips.count) saved").font(.geist(13)).foregroundStyle(Color.inkMuted)
                    }.padding(14)
                    Divider()
                    ForEach(Array(0..<max(SLOTS, trip.savedTrips.count)), id: \.self) { i in
                        if i < trip.savedTrips.count { savedRow(i: i, t: trip.savedTrips[i]) }
                        else { savedEmptyRow(i: i) }
                        if i < max(SLOTS, trip.savedTrips.count) - 1 { Divider().padding(.leading, 60) }
                    }
                }
                .background(.white, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))

                tipNote
            }
            .padding(14)
        }
    }

    private func savedRow(i: Int, t: SavedTrip) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(t.name).font(.geist(15, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                Text("\(t.stopCount) farm\(t.stopCount == 1 ? "" : "s")\(dateLabel(t.updatedAt))")
                    .font(.geist(12)).foregroundStyle(Color.inkMuted)
            }
            Spacer()
            Button {
                if armedDelete == t.id { Task { if let uid { await trip.deleteTrip(t.id, userId: uid) } } }
                else { armedDelete = t.id }
            } label: {
                Image(systemName: armedDelete == t.id ? "trash.fill" : "xmark")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(armedDelete == t.id ? Color.warnRed : Color.inkMuted)
            }.buttonStyle(.plain)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture { Task { await trip.openTrip(t.id); tab = .plan } }
    }

    private func savedEmptyRow(i: Int) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(Color.inkMuted.opacity(0.6))
                .frame(width: 28, height: 28)
                .overlay(Circle().strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1.5, dash: [3])))
            Text("Plan a trip to fill this").font(.geist(15)).foregroundStyle(Color.inkMuted)
            Spacer()
            Image(systemName: "plus").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.farmGreenMap)
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        .onTapGesture { tab = .plan }
    }

    private func dateLabel(_ iso: String?) -> String {
        guard let iso, let d = ISO8601DateFormatter().date(from: iso) else { return "" }
        let f = DateFormatter(); f.dateFormat = "MMM d"
        return " · " + f.string(from: d)
    }

    private func gate(_ text: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "lock.fill").font(.system(size: 34)).foregroundStyle(Color.farmGreenMap)
            Text(text).font(.geist(15)).foregroundStyle(Color.inkMuted).multilineTextAlignment(.center)
            Spacer(); Spacer()
        }
        .frame(maxWidth: .infinity, minHeight: 260).padding(.horizontal, 40)
    }

    // MARK: - Shared

    private var tipNote: some View {
        HStack(spacing: 10) {
            Image(systemName: "lightbulb").font(.system(size: 15)).foregroundStyle(Color.farmGreenMap)
            Text("Farms that are open today are more likely to be worth the drive.")
                .font(.geist(13)).foregroundStyle(Color.inkMuted)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xF3F6F2), in: RoundedRectangle(cornerRadius: 16))
    }

    private func outlineButton(_ title: String, icon: String, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.geist(14, .semibold))
            }
            .foregroundStyle(Color.ink).frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))
        }.buttonStyle(.plain)
    }

    private func filledButton(_ title: String, icon: String, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.geist(14, .semibold))
            }
            .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(Color.farmGreenMap.opacity(stops.count < 2 ? 0.5 : 1), in: RoundedRectangle(cornerRadius: 16))
        }.buttonStyle(.plain)
    }

    // MARK: - Actions

    private func locate() async {
        locationManager.request()
        guard let loc = locationManager.location else { return }
        let label = (try? await CLGeocoder().reverseGeocodeLocation(
            CLLocation(latitude: loc.coordinate.latitude, longitude: loc.coordinate.longitude)).first?.locality)
            ?? String(format: "%.3f, %.3f", loc.coordinate.latitude, loc.coordinate.longitude)
        trip.setOrigin(loc.coordinate, label: label ?? "Here")
        await trip.refreshRoute(pins: pinIndex)
    }

    private func reorder() {
        Haptics.tap()
        let saved = trip.optimise(pins: pinIndex)
        reorderNote = saved >= 0.5 ? String(localized: "Reordered · about \(Int(saved)) km shorter")
                                    : String(localized: "Already the shortest order")
        Task { await trip.refreshRoute(pins: pinIndex) }
    }

    private func save() async {
        guard let uid, !tripName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        await trip.save(name: tripName.trimmingCharacters(in: .whitespaces), userId: uid, pins: pinIndex)
        Haptics.success()
        tab = .mine
    }

    private func openGoogleMaps() {
        let coords = ([trip.originCoord].compactMap { $0 }) + stops.map(\.coordinate)
        guard coords.count >= 2 else {
            if let f = stops.first, let url = URL(string: "https://www.google.com/maps/search/?api=1&query=\(f.lat),\(f.lng)") {
                UIApplication.shared.open(url)
            }
            return
        }
        let origin = "\(coords.first!.latitude),\(coords.first!.longitude)"
        let dest = "\(coords.last!.latitude),\(coords.last!.longitude)"
        let mid = coords.dropFirst().dropLast().map { "\($0.latitude),\($0.longitude)" }.joined(separator: "|")
        var s = "https://www.google.com/maps/dir/?api=1&origin=\(origin)&destination=\(dest)"
        if !mid.isEmpty { s += "&waypoints=\(mid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? mid)" }
        if let url = URL(string: s) { UIApplication.shared.open(url) }
    }
}
