import SwiftUI
import CoreLocation
import MapKit

/// The trip planner (Aviah's spec + Neil's screenshots): two tabs — Plan (the
/// local draft) and My trips (saved trips from the DB). Stops come from "Add to
/// trip" on farm cards; the road route + totals come from POST /api/route; the
/// route is drawn on the map. My Trips is Pro-gated.
struct TripsView: View {
    var onOpenFarm: (FarmPin) -> Void
    /// The sheet's height — dropped to half when a saved trip is opened so the
    /// route is visible on the map above the sheet.
    @Binding var detent: PresentationDetent

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
    @State private var showOriginSearch = false

    enum Tab { case plan, mine }

    private var stops: [FarmPin] { trip.stopIds.compactMap { farms.pin(forOsmId: $0) } }
    /// A route needs at least two points — either a starting point + one farm, or
    /// two farms.
    private var canRoute: Bool { (trip.originCoord != nil && stops.count >= 1) || stops.count >= 2 }
    private var pinIndex: [String: FarmPin] {
        Dictionary(farms.pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a })
    }
    private let SLOTS = 8
    /// Visible rows before the list box scrolls internally.
    private let PLAN_SLOTS = 5
    private let MINE_SLOTS = 8
    private let ROW_HEIGHT: CGFloat = 52

    var body: some View {
        VStack(spacing: 0) {
            header
            tabs.padding(.horizontal, 14).padding(.top, 4)
            // Only the stop / saved list scrolls (inside its own fixed-height box);
            // everything else on the screen stays put, so the actions never get
            // pushed off and the layout doesn't waste space.
            if tab == .plan { planTab } else { mineTab }
            Spacer(minLength: 0)
            if tab == .plan {
                planActions
                    .padding(.horizontal, 14)
                    .padding(.top, 10)
                    .padding(.bottom, 12)
                    .background(Color.cream)
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
        .sheet(isPresented: $showOriginSearch) {
            PlaceSearchSheet(
                onPick: { coord, label in
                    trip.setOrigin(coord, label: label)   // flies via fitToken
                    Task { await trip.refreshRoute(pins: pinIndex) }
                    withAnimation { detent = .fraction(0.5) }
                },
                onLocate: { Task { await locate() } })
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }

    private var uid: String? { session.session?.user.id.uuidString.lowercased() }

    // MARK: - Header + tabs

    private var header: some View {
        HStack {
            Text("TRIP PLANNER")
                .font(.geist(11, .semibold)).kerning(1.2).foregroundStyle(Color.inkMuted)
            Spacer()
            circleButton("xmark") { dismiss() }
        }
        .padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 8)
    }

    private var tabs: some View {
        HStack(spacing: 10) {
            tabButton("Plan a trip", .plan)
            tabButton("My trips", .mine)
        }
    }

    private func tabButton(_ title: LocalizedStringKey, _ t: Tab) -> some View {
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
            Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color(hex: 0x6B7280)).frame(width: 32, height: 32)
                .background(Color(hex: 0xF3F4F6), in: Circle())
        }.buttonStyle(.plain)
    }

    // MARK: - Plan tab

    private var planTab: some View {
        VStack(spacing: 14) {
            // Starting point — a search bar that opens live place suggestions.
            Button { Haptics.tap(); showOriginSearch = true } label: {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").font(.system(size: 15)).foregroundStyle(Color.inkMuted)
                    Text(trip.originLabel?.isEmpty == false ? trip.originLabel! : String(localized: "Choose a starting point"))
                        .font(.geist(15))
                        .foregroundStyle(trip.originLabel?.isEmpty == false ? Color.ink : Color.inkMuted)
                        .lineLimit(1)
                    Spacer()
                    if trip.originLabel?.isEmpty == false {
                        Image(systemName: "xmark.circle.fill").font(.system(size: 16)).foregroundStyle(Color.inkMuted)
                            .onTapGesture { Haptics.tap(); trip.clearOrigin() }
                    } else {
                        Image(systemName: "location.circle").font(.system(size: 20)).foregroundStyle(Color.farmGreenMap)
                    }
                }
                .padding(.vertical, 14).padding(.horizontal, 16)
                .background(.white, in: Capsule())
                .overlay(Capsule().stroke(Color.hairline, lineWidth: 1))
            }
            .buttonStyle(.plain)

            // Trip overview — a fixed-height box whose list is the only thing that
            // scrolls. It always shows a few slots (stops first, then placeholders),
            // and scrolls internally once the stops outgrow the visible rows.
            let rows = max(stops.count, PLAN_SLOTS)
            VStack(alignment: .leading, spacing: 0) {
                Text("Trip overview").font(.geist(16, .bold)).foregroundStyle(Color.ink)
                    .padding(14)
                Divider()
                ScrollView(showsIndicators: true) {
                    VStack(spacing: 0) {
                        ForEach(0..<rows, id: \.self) { i in
                            if i < stops.count { filledRow(i: i, pin: stops[i]) } else { emptyRow(i: i) }
                            if i < rows - 1 { Divider().padding(.leading, 60) }
                        }
                    }
                }
                // maxHeight (not a fixed height) so the box collapses when the
                // sheet is dragged small — the actions below stay visible instead
                // of being pushed off — and caps at ~5 rows when there's room.
                .frame(maxHeight: ROW_HEIGHT * CGFloat(PLAN_SLOTS))
            }
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))

            if let reorderNote {
                Text(reorderNote).font(.geist(12)).foregroundStyle(Color.farmGreenMap)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            if stops.count >= 3 {
                Button { reorder() } label: {
                    Text("Best order").font(.geist(14, .semibold)).foregroundStyle(Color.farmGreen)
                }.buttonStyle(.plain)
            }
        }
        .padding(14)
    }

    /// Pinned below the scroll: travel mode, the live totals, and the trip
    /// actions — the controls Luuk flagged as getting buried under the stop list.
    private var planActions: some View {
        VStack(spacing: 10) {
            modeSelector
            totalsBar
            HStack(spacing: 10) {
                outlineButton("Save trip", icon: "bookmark") { naming = true; tripName = "" }
                    .disabled(stops.isEmpty)
                filledButton("Show route", icon: "location.north.fill") {
                    Task { await trip.refreshRoute(pins: pinIndex) }
                    dismiss()
                }
                .disabled(!canRoute)
            }
            outlineButton("Open in Google Maps", icon: "arrow.up.forward.square") { openGoogleMaps() }
                .disabled(stops.isEmpty)
        }
    }

    private var modeSelector: some View {
        HStack(spacing: 8) {
            ForEach(TravelMode.allCases, id: \.self) { m in
                Button {
                    Haptics.tap()
                    trip.setMode(m)
                    Task { await trip.refreshRoute(pins: pinIndex) }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: m.icon).font(.system(size: 13, weight: .semibold))
                        Text(m.label).font(.geist(13, .semibold))
                    }
                    .foregroundStyle(trip.mode == m ? .white : Color.ink)
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .background(trip.mode == m ? Color.farmGreenMap : .white,
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12)
                        .stroke(trip.mode == m ? Color.clear : Color.hairline, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
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
            if !canRoute {
                Text("Add farms to see time and distance").font(.geist(14)).foregroundStyle(Color.inkMuted)
            } else if trip.isRouting {
                Text("Finding the road…").font(.geist(14)).foregroundStyle(Color.inkMuted)
            } else {
                Text(totalsText).font(.geist(14, .semibold)).foregroundStyle(Color.ink)
            }
            Spacer()
            Image(systemName: trip.mode.icon).font(.system(size: 15)).foregroundStyle(Color.inkMuted.opacity(0.5))
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
        return String(format: "~%.0f km · %d min", km, trip.mode.minutes(km: km))
    }

    // MARK: - My trips tab

    @ViewBuilder
    private var mineTab: some View {
        if !session.isAuthenticated {
            gate(String(localized: "Sign in to view your trips"))
        } else if session.profile?.hasFullAccess != true {
            gate(String(localized: "Saved trips are a Farmsy Pro feature."))
        } else {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    // Draft banner.
                    Text(trip.stopIds.isEmpty ? String(localized: "No trip in progress")
                         : String(localized: "A draft with \(trip.stopIds.count) stops is waiting"))
                        .font(.geist(14)).foregroundStyle(Color.inkMuted)
                        .frame(maxWidth: .infinity).padding(.vertical, 14)
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color(hex: 0xE5E7EB), style: StrokeStyle(lineWidth: 1, dash: [4])))
                        .contentShape(Rectangle())
                        .onTapGesture { if !trip.stopIds.isEmpty { tab = .plan } }

                    // The saved-trip list — a fixed box showing up to eight rows,
                    // scrolling internally past that.
                    let rows = max(trip.savedTrips.count, MINE_SLOTS)
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("My Trips").font(.geist(16, .bold)).foregroundStyle(Color.ink)
                            Spacer()
                            Text("\(trip.savedTrips.count) saved").font(.geist(13)).foregroundStyle(Color.inkMuted)
                        }.padding(14)
                        Divider()
                        ScrollView(showsIndicators: true) {
                            VStack(spacing: 0) {
                                ForEach(0..<rows, id: \.self) { i in
                                    if i < trip.savedTrips.count { savedRow(i: i, t: trip.savedTrips[i]) }
                                    else { savedEmptyRow(i: i) }
                                    if i < rows - 1 { Divider().padding(.leading, 60) }
                                }
                            }
                        }
                        .frame(maxHeight: ROW_HEIGHT * CGFloat(MINE_SLOTS))
                    }
                    .background(.white, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.hairline, lineWidth: 1))

                    // Discovery at the bottom: farms near you worth planning next.
                    // Renders nothing when there's nothing sensible to show.
                    TripRecommendations(onOpenFarm: { pin in dismiss(); onOpenFarm(pin) })
                }
                .padding(14)
            }
        }
    }

    private func savedRow(i: Int, t: SavedTrip) -> some View {
        HStack(spacing: 12) {
            Text("\(i + 1)").font(.geist(12, .bold)).foregroundStyle(.white)
                .frame(width: 28, height: 28).background(Color.farmGreenMap, in: Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(t.name).font(.geist(15, .bold)).foregroundStyle(Color.ink).lineLimit(1)
                Text("\(t.stopCount) \(t.stopCount == 1 ? String(localized: "farm") : String(localized: "farms"))\(dateLabel(t.updatedAt))")
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
        .onTapGesture {
            Task { await trip.openTrip(t.id) }
            tab = .plan
            // Drop the sheet to half so the route shows on the map above it.
            withAnimation { detent = .fraction(0.5) }
        }
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

    private func outlineButton(_ title: LocalizedStringKey, icon: String, _ action: @escaping () -> Void) -> some View {
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

    private func filledButton(_ title: LocalizedStringKey, icon: String, _ action: @escaping () -> Void) -> some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 13, weight: .semibold))
                Text(title).font(.geist(14, .semibold))
            }
            .foregroundStyle(.white).frame(maxWidth: .infinity).padding(.vertical, 14)
            .background(Color.farmGreenMap.opacity(canRoute ? 1 : 0.5), in: RoundedRectangle(cornerRadius: 16))
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
        withAnimation { detent = .fraction(0.5) }
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
        var s = "https://www.google.com/maps/dir/?api=1&origin=\(origin)&destination=\(dest)&travelmode=\(trip.mode.googleMode)"
        if !mid.isEmpty { s += "&waypoints=\(mid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? mid)" }
        if let url = URL(string: s) { UIApplication.shared.open(url) }
    }
}

// MARK: - Recommendations near you (bottom of My Trips)

/// "Recommendations near you" — Aviah's spec. Built off the same inputs as the
/// onboarding shelf (nearby photo'd farms + batch teasers), with the *selection*
/// isolated in `select()` so a real recommendation source can replace it without
/// touching the view. Anchors on the user's location, falling back to a trip they
/// care about; excludes anything already planned or hearted; renders nothing when
/// there is nothing sensible nearby.
private struct TripRecommendations: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(FavoritesStore.self) private var favorites
    @Environment(TripStore.self) private var trip

    @State private var shown: [FarmPin] = []
    @State private var built = false
    @State private var extraTeasers: [String: String] = [:]

    /// User's location first; then a trip they care about (the draft's origin, or
    /// the centroid of its stops) so a denied-location user still gets something
    /// local rather than an empty section.
    private var anchor: CLLocationCoordinate2D? {
        if let loc = locationManager.location { return loc.coordinate }
        if let origin = trip.originCoord { return origin }
        let coords = trip.stopIds.compactMap { farms.pin(forOsmId: $0)?.coordinate }
        guard !coords.isEmpty else { return nil }
        let lat = coords.map(\.latitude).reduce(0, +) / Double(coords.count)
        let lng = coords.map(\.longitude).reduce(0, +) / Double(coords.count)
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }

    /// The one place "which farms are recommended" lives. Today: nearby photo'd
    /// farms, minus anything already planned or hearted, described ones mixed
    /// among the rest, six of them. Swap this body when a real source exists.
    private func select() -> [FarmPin] {
        guard let anchor else { return [] }
        let excluded = trip.plannedFarmIds
            .union(trip.stopIds)
            .union(favorites.osmIds)
        let pool = farms.nearbyWithImages(near: anchor, radiusKm: 100)
            .filter { !excluded.contains($0.osmId) }
        let described = pool.filter { !(farms.galleries[$0.osmId]?.isEmpty ?? true) }
        let plain = pool.filter { farms.galleries[$0.osmId]?.isEmpty ?? true }
        let picked = Array(described.prefix(6)) + Array(plain.prefix(6))
        return Array(picked.shuffled().prefix(6))
    }

    private func images(for pin: FarmPin) -> [String] {
        if let gallery = farms.galleries[pin.osmId], !gallery.isEmpty { return gallery }
        if let cover = pin.image { return [cover] }
        return []
    }
    private func teaser(for pin: FarmPin) -> String? {
        farms.featuredTeasers[pin.osmId] ?? extraTeasers[pin.osmId]
    }

    private var buildKey: String {
        "\(farms.pins.count)-\(farms.galleriesLoaded)-\(trip.plannedFarmIds.count)-\(anchor?.latitude ?? 0)-\(anchor?.longitude ?? 0)"
    }

    private func load() async {
        await farms.loadGalleriesIfNeeded()
        guard !farms.pins.isEmpty else { return }
        shown = select()
        built = true
        let targets = shown.map(\.osmId)
            .filter { farms.featuredTeasers[$0] == nil && extraTeasers[$0] == nil }
        guard !targets.isEmpty else { return }
        let result = await FarmDetailAPI.teasers(osmIds: targets)
        if !result.isEmpty { extraTeasers.merge(result) { _, new in new } }
    }

    var body: some View {
        Group {
            // Nothing sensible nearby → render nothing (no empty state).
            if built && shown.isEmpty {
                EmptyView()
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    Text("RECOMMENDATIONS NEAR YOU")
                        .font(.geist(11, .semibold)).kerning(1.2)
                        .foregroundStyle(Color.inkMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 6)
                    if !built {
                        ForEach(0..<2, id: \.self) { _ in
                            SkeletonBox(cornerRadius: 16).frame(height: 180)
                        }
                    } else {
                        ForEach(shown) { pin in
                            MultiImageFarmCard(pin: pin,
                                               images: images(for: pin),
                                               teaser: teaser(for: pin),
                                               onOpen: { onOpenFarm(pin) })
                        }
                    }
                }
            }
        }
        .task(id: buildKey) { await load() }
    }
}
