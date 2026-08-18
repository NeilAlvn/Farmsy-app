import SwiftUI
import MapKit
import CoreLocation

/// The map is the app. A full-bleed map with a floating header (logo + account),
/// search and filters on top, a What's New button and a locate button, and the
/// farm card as a bottom sheet the parent presents.
struct MapScreen: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(TripStore.self) private var trip

    @State private var showFilters = false

    /// A pin the parent asked us to fly to (e.g. tapped in the What's New sheet).
    var focusPin: FarmPin?

    @State private var camera: MapCameraPosition = .region(
        // Centered between NL and BE to start.
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 51.8, longitude: 4.7),
            span: MKCoordinateSpan(latitudeDelta: 3.4, longitudeDelta: 3.4)
        )
    )
    @State private var visibleRegion: MKCoordinateRegion?

    /// Drawing thousands of individual annotations is what made the map lag.
    /// Instead the viewport is divided into a grid and the pins in each cell are
    /// grouped into one marker — a single pin when a cell holds one farm, a green
    /// count bubble when it holds several. Zooming in splits the clusters apart.
    private static let gridCellsAcross = 6.5
    /// The trip route colour — a blue that stands apart from the green markers.
    static let routeColor = Color(hex: 0x2563EB)

    private var clusters: [MapCluster] {
        // Trip stops are drawn as their own always-visible numbered markers, so
        // keep them out of the clustering — otherwise a stop vanishes into a
        // cluster when zoomed out and you lose sight of the route's ends.
        let tripSet = Set(trip.stopIds)
        let all = farms.filtered.filter { !tripSet.contains($0.osmId) }
        guard let region = visibleRegion else {
            return Self.cluster(all, span: MKCoordinateSpan(latitudeDelta: 3.4, longitudeDelta: 3.4))
        }
        let latHalf = region.span.latitudeDelta / 2 * 1.2
        let lngHalf = region.span.longitudeDelta / 2 * 1.2
        let inView = all.filter {
            abs($0.lat - region.center.latitude) < latHalf &&
            abs($0.lng - region.center.longitude) < lngHalf
        }
        return Self.cluster(inView, span: region.span)
    }

    /// The trip's stops, as pins, in visiting order — drawn on top of everything.
    private var tripStopPins: [(index: Int, pin: FarmPin)] {
        trip.stopIds.enumerated().compactMap { i, id in
            farms.pins.first { $0.osmId == id }.map { (i, $0) }
        }
    }

    /// Grid-cluster pins by the current span. Cell size is the span divided by a
    /// fixed number of cells across, so clusters merge when zoomed out and split
    /// when zoomed in. The bucket's own centre positions the marker, so it does
    /// not jitter as pins come and go from the viewport.
    private static func cluster(_ pins: [FarmPin], span: MKCoordinateSpan) -> [MapCluster] {
        let cellLat = max(span.latitudeDelta / gridCellsAcross, 0.0001)
        let cellLng = max(span.longitudeDelta / gridCellsAcross, 0.0001)
        var buckets: [String: [FarmPin]] = [:]
        for pin in pins {
            let row = Int((pin.lat / cellLat).rounded(.down))
            let col = Int((pin.lng / cellLng).rounded(.down))
            buckets["\(row)_\(col)", default: []].append(pin)
        }
        return buckets.map { key, group in
            if group.count == 1 {
                return MapCluster(id: group[0].osmId, coordinate: group[0].coordinate, pins: group)
            }
            let lat = group.reduce(0.0) { $0 + $1.lat } / Double(group.count)
            let lng = group.reduce(0.0) { $0 + $1.lng } / Double(group.count)
            return MapCluster(id: key, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng), pins: group)
        }
    }

    private func zoomInto(_ cluster: MapCluster) {
        let current = visibleRegion?.span ?? MKCoordinateSpan(latitudeDelta: 3.4, longitudeDelta: 3.4)
        withAnimation(.easeInOut(duration: 0.4)) {
            camera = .region(MKCoordinateRegion(
                center: cluster.coordinate,
                span: MKCoordinateSpan(latitudeDelta: current.latitudeDelta / 3.2,
                                       longitudeDelta: current.longitudeDelta / 3.2)
            ))
        }
    }

    var body: some View {
        ZStack {
            mapCard
        }
        .frame(maxHeight: .infinity)
        // Search sits at the top with the locate button beside it — the wordmark,
        // What's New and account controls moved to the bottom panel.
        .overlay(alignment: .top) {
            HStack(spacing: 10) {
                searchRow
                CircleMapButton(icon: "location.fill", size: 44, action: locateNearMe)
            }
            .padding(.horizontal, 14)
            .padding(.top, 6)
        }
        .sheet(isPresented: $showFilters) {
            FilterSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .onChange(of: focusPin?.osmId) { _, _ in flyToFocus() }
        // Keep the trip route on the map live wherever stops are added — not only
        // while the Trips sheet is open. On add, fit the camera to the trip so the
        // whole trace is visible; a single stop just centres on that farm.
        .onChange(of: trip.stopIds) { old, new in
            Task { await trip.refreshRoute(pins: pinLookup) }
            if new.count > old.count { fitToTrip() }
        }
        // An explicit fit request — opening a saved trip, or setting the origin.
        .onChange(of: trip.fitToken) { _, _ in fitToTrip() }
    }

    private var pinLookup: [String: FarmPin] {
        Dictionary(farms.pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a })
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    /// Fit the whole trip — origin plus every stop — so the full route is visible
    /// in the map area not covered by the sheet. A single point just flies to it.
    private func fitToTrip() {
        var coords = trip.stopIds.compactMap { pinLookup[$0]?.coordinate }
        if let origin = trip.originCoord { coords.insert(origin, at: 0) }
        guard !coords.isEmpty else { return }
        if coords.count == 1 {
            withAnimation(.easeInOut(duration: 0.5)) {
                camera = .region(MKCoordinateRegion(center: coords[0],
                    span: MKCoordinateSpan(latitudeDelta: 0.12, longitudeDelta: 0.12)))
            }
            return
        }
        let lats = coords.map(\.latitude), lngs = coords.map(\.longitude)
        // Shift the centre north so the route sits in the upper half — the bottom
        // is under the trips sheet. Pad generously so the ends clear the edges.
        let latPad = (lats.max()! - lats.min()!)
        let center = CLLocationCoordinate2D(
            latitude: (lats.min()! + lats.max()!) / 2 - latPad * 0.55,
            longitude: (lngs.min()! + lngs.max()!) / 2)
        let span = MKCoordinateSpan(latitudeDelta: max(latPad * 2.6, 0.06),
                                    longitudeDelta: max((lngs.max()! - lngs.min()!) * 1.5, 0.06))
        withAnimation(.easeInOut(duration: 0.6)) {
            camera = .region(MKCoordinateRegion(center: center, span: span))
        }
    }

    // MARK: - Search row (top)

    private var searchRow: some View {
        @Bindable var farms = farms
        let filtersActive = farms.anyFilterOn
        return HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15))
                .foregroundStyle(Color.inkMuted)
            TextField("Search by farm, city or postcode", text: $farms.searchText)
                .autocorrectionDisabled()
            // Filters live on the search bar, web-style: tapping slides a sheet up.
            Button {
                Haptics.tap()
                showFilters = true
            } label: {
                Image(systemName: filtersActive ? "line.3.horizontal.decrease.circle.fill"
                                                 : "line.3.horizontal.decrease.circle")
                    .font(.system(size: 21))
                    .foregroundStyle(Color.farmGreenMap)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 13)
        .padding(.horizontal, 18)
        .background(.white, in: Capsule())
        .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
    }

    private func flyToFocus() {
        guard let pin = focusPin else { return }
        withAnimation(.easeInOut(duration: 0.6)) {
            camera = .region(MKCoordinateRegion(
                center: pin.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.15, longitudeDelta: 0.15)
            ))
        }
    }

    /// The quick filters, as a horizontally scrolling rail of toggle chips —
    /// the same set the web panel offers (Verified / Open now / Automaat /
    /// Zelfpluk / Has photos), plus "Near me" as an action.
    /// Center the map on the user — shared by the locate button and the "Near me"
    /// chip. Asks for permission if we don't have a fix yet.
    private func locateNearMe() {
        Haptics.tap()
        locationManager.request()
        if let loc = locationManager.location {
            withAnimation {
                camera = .region(MKCoordinateRegion(
                    center: loc.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
                ))
            }
        }
    }

    // MARK: - Map

    private var mapCard: some View {
        Map(position: $camera) {
            UserAnnotation()
            ForEach(clusters) { cluster in
                if cluster.isCluster {
                    Annotation(cluster.id, coordinate: cluster.coordinate, anchor: .center) {
                        ClusterBubble(count: cluster.pins.count)
                            .onTapGesture {
                                Haptics.tap()
                                zoomInto(cluster)
                            }
                    }
                    .annotationTitles(.hidden)
                } else {
                    Annotation(cluster.id, coordinate: cluster.coordinate, anchor: .bottom) {
                        FarmPinView(category: cluster.representative.primaryCategory)
                            .onTapGesture {
                                Haptics.tap()
                                onOpenFarm(cluster.representative)
                            }
                    }
                    .annotationTitles(.hidden)
                }
            }
            // The active trip's road line, above the map's own labels. A white
            // casing under a bright-blue line — deliberately a different colour
            // from the green pins/clusters so it reads as a route, not a marker.
            if trip.tracedLine.count >= 2 {
                MapPolyline(coordinates: trip.tracedLine)
                    .stroke(.white, style: StrokeStyle(lineWidth: 8, lineCap: .round, lineJoin: .round))
                    .mapOverlayLevel(level: .aboveLabels)
                // Solid over the road; a thin dashed line for the straight-line
                // fallback — that's how "this is an estimate" reads without a label.
                MapPolyline(coordinates: trip.tracedLine)
                    .stroke(Self.routeColor, style: trip.onRoads
                            ? StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round)
                            : StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round, dash: [2, 4]))
                    .mapOverlayLevel(level: .aboveLabels)
            }
            // Numbered trip-stop markers, above the route and always visible.
            ForEach(tripStopPins, id: \.pin.osmId) { item in
                Annotation(item.pin.name, coordinate: item.pin.coordinate, anchor: .center) {
                    TripStopMarker(number: item.index + 1)
                        .onTapGesture { Haptics.tap(); onOpenFarm(item.pin) }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .onMapCameraChange(frequency: .onEnd) { context in
            visibleRegion = context.region
            // Panning the map means the user is done with the search field —
            // drop the keyboard so the placeholder shows again.
            dismissKeyboard()
        }
        // Edge to edge: the map runs under the status bar and home indicator;
        // the search row and control bars float on top of it.
        .ignoresSafeArea()
        .overlay(alignment: .center) {
            if let error = farms.loadError {
                VStack(spacing: 10) {
                    Text(error)
                        .font(.geist(14, .medium))
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        Task { await farms.loadIfNeeded() }
                    }
                    .font(.geist(15, .semibold))
                    .foregroundStyle(Color.farmGreenMap)
                }
                .padding(16)
                .background(.white.opacity(0.97), in: RoundedRectangle(cornerRadius: 16))
                .padding(20)
            } else if farms.pins.isEmpty {
                // Farms haven't loaded yet — just a spinner in the centre of the map.
                ProgressView()
                    .tint(Color.farmGreenMap)
                    .controlSize(.large)
            }
        }
    }

}

/// One quick-filter toggle. Matches the web rail: a bordered pill on the map,
/// filling with the soft map-green and white text when on, a map-green glyph
/// when off. Floats on the map so it uses the on-map green, not the brand green.
struct FilterChip: View {
    let title: String
    let icon: String
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(isOn ? .white : Color.farmGreenMap)
                Text(title)
                    .font(.geist(13, .semibold))
                    .foregroundStyle(isOn ? .white : Color.ink)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(
                Capsule()
                    .fill(isOn ? Color.farmGreenMap : .white)
                    .stroke(isOn ? Color.farmGreenMap : Color.hairline, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.10), radius: 5, y: 1)
        }
        .buttonStyle(.plain)
    }
}

/// A floating map control — a circular white button that sits over the map
/// (MOBILE-SPEC-DETAIL §10): the What's New button, the account button, locate.
struct CircleMapButtonLabel: View {
    let icon: String
    var size: CGFloat = 44
    var tint: Color = Color(hex: 0x4B5563)

    var body: some View {
        Image(systemName: icon)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(.white.opacity(0.94), in: Circle())
            .overlay(Circle().stroke(.white.opacity(0.6), lineWidth: 1))
            .shadow(color: .black.opacity(0.22), radius: 10, y: 3)
    }
}

struct CircleMapButton: View {
    let icon: String
    var size: CGFloat = 44
    var tint: Color = Color(hex: 0x4B5563)
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            CircleMapButtonLabel(icon: icon, size: size, tint: tint)
        }
        .buttonStyle(.plain)
    }
}

/// A cluster of farms, drawn as a green count bubble (the map green used on the
/// buttons). Tapping it zooms the map in so the cluster splits apart.
struct MapCluster: Identifiable {
    let id: String
    let coordinate: CLLocationCoordinate2D
    let pins: [FarmPin]
    var isCluster: Bool { pins.count > 1 }
    var representative: FarmPin { pins[0] }
}

struct ClusterBubble: View {
    let count: Int

    private var size: CGFloat {
        switch count {
        case ..<10: 38
        case ..<100: 46
        default: 54
        }
    }

    var body: some View {
        Text(count > 999 ? "999+" : "\(count)")
            .font(.geist(count > 99 ? 13 : 15, .bold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Color.farmGreenMap, in: Circle())
            .overlay(Circle().stroke(.white, lineWidth: 2.5))
            .shadow(color: .black.opacity(0.25), radius: 4, y: 2)
    }
}

/// A numbered stop on the active trip — a dark-green circle so it reads as part
/// of the route, above the softer route line.
struct TripStopMarker: View {
    let number: Int
    var body: some View {
        Text("\(number)")
            .font(.geist(14, .bold))
            .foregroundStyle(.white)
            .frame(width: 34, height: 34)
            .background(Color.farmGreen, in: Circle())
            .overlay(Circle().stroke(.white, lineWidth: 2.5))
            .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
    }
}

/// Teardrop pin in the category's color, like the web map markers.
struct FarmPinView: View {
    let category: FarmCategory
    var isHighlighted = false

    var body: some View {
        ZStack {
            Image(systemName: "drop.fill")
                .font(.system(size: isHighlighted ? 50 : 38))
                .rotationEffect(.degrees(180))
                .foregroundStyle(isHighlighted ? Color.farmGreenDeep : category.color)
                .shadow(color: .black.opacity(0.25), radius: 3, y: 2)
            Circle()
                .fill(.white)
                .frame(width: isHighlighted ? 28 : 22, height: isHighlighted ? 28 : 22)
                .offset(y: isHighlighted ? -7 : -5)
            Text(category.emoji)
                .font(.system(size: isHighlighted ? 15 : 12))
                .offset(y: isHighlighted ? -7 : -5)
        }
        .animation(.spring(duration: 0.3), value: isHighlighted)
    }
}

struct FarmCard: View {
    let pin: FarmPin
    var onOpen: () -> Void

    @Environment(LocationManager.self) private var locationManager
    @Environment(FavoritesStore.self) private var favorites
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    private var distanceText: String? {
        guard let meters = pin.distance(from: locationManager.location) else { return nil }
        return meters < 1000
            ? "\(Int(meters)) m"
            : String(format: "%.1f km", meters / 1000)
    }

    var body: some View {
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top) {
                    Text(pin.name)
                        .font(.geist(18, .bold))
                        .foregroundStyle(Color.ink)
                        .multilineTextAlignment(.leading)
                    Spacer()
                    if let distanceText {
                        Text(distanceText)
                            .font(.geist(15, .semibold))
                            .foregroundStyle(Color.farmGreen)
                    }
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.inkMuted.opacity(0.6))
                        .padding(.top, 3)
                }

                if pin.address != nil || pin.city != nil {
                    Text([pin.address, pin.postalCode, pin.city].compactMap(\.self).joined(separator: ", "))
                        .font(.geist(14))
                        .foregroundStyle(Color.inkMuted)
                        .lineLimit(1)
                }

                HStack(spacing: 6) {
                    ForEach(pin.categories.prefix(4)) { cat in
                        Text(cat.emoji).font(.geist(17))
                    }
                    if let rating = pin.avgRating {
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.star)
                            Text(String(format: "%.1f", rating))
                                .font(.geist(13, .semibold))
                                .foregroundStyle(Color.ink)
                        }
                    }
                    Spacer()
                    Button {
                        toggleFavorite()
                    } label: {
                        Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                            .font(.system(size: 19))
                            .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.inkMuted)
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
            .background(.white, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("farm-card")
    }

    private func toggleFavorite() {
        guard let userId = session.session?.user.id else {
            requestAuth()
            return
        }
        Haptics.tap()
        Task { await favorites.toggle(pin.osmId, userId: userId) }
    }
}

/// The filter sheet that slides up from the search bar — one unified list, like
/// the web: an "All" row, then the categories (each a coloured circle + name),
/// then the quick filters (grey circle + icon). Everything is multi-select; a
/// tick marks what's on. No section headers.
struct FilterSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager

    var body: some View {
        @Bindable var farms = farms
        VStack(spacing: 0) {
            HStack {
                Text("Filters").font(.geist(18, .bold)).foregroundStyle(Color.ink)
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280))
                        .frame(width: 32, height: 32)
                        .background(Color(hex: 0xF3F4F6), in: Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 6)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // "All" clears every filter — on when nothing is selected.
                    row(icon: nil, emoji: "🍽️", tint: Color.inkMuted,
                        label: String(localized: "All categories"),
                        trailing: "\(farms.pins.count)",
                        isOn: !farms.anyFilterOn) {
                        farms.clearAllFilters()
                    }
                    divider

                    ForEach(FarmCategory.allCases) { cat in
                        row(icon: nil, emoji: cat.emoji, tint: cat.color,
                            label: cat.label, trailing: nil,
                            isOn: farms.selectedCategories.contains(cat)) {
                            if farms.selectedCategories.contains(cat) { farms.selectedCategories.remove(cat) }
                            else { farms.selectedCategories.insert(cat) }
                        }
                    }
                    divider

                    row(icon: "checkmark.seal", emoji: nil, tint: Color.inkMuted,
                        label: String(localized: "Verified farm shops"), trailing: nil,
                        isOn: farms.filterVerified) { farms.filterVerified.toggle() }
                    row(icon: "bolt", emoji: nil, tint: Color.inkMuted,
                        label: String(localized: "Open 24/7 (automaat)"), trailing: nil,
                        isOn: farms.filterAutomaat) { farms.filterAutomaat.toggle() }
                    row(icon: "clock", emoji: nil, tint: Color.inkMuted,
                        label: String(localized: "Open today"), trailing: nil,
                        isOn: farms.filterOpenToday) { farms.filterOpenToday.toggle() }
                    row(icon: "leaf", emoji: nil, tint: Color.inkMuted,
                        label: String(localized: "Pick your own"), trailing: nil,
                        isOn: farms.filterZelfpluk) { farms.filterZelfpluk.toggle() }
                    row(icon: "camera", emoji: nil, tint: Color.inkMuted,
                        label: String(localized: "Has photos"), trailing: nil,
                        isOn: farms.filterHasPhotos) { farms.filterHasPhotos.toggle() }
                }
                .padding(.bottom, 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
    }

    private var divider: some View {
        Rectangle().fill(Color.hairline).frame(height: 1).padding(.vertical, 4)
    }

    private func row(icon: String?, emoji: String?, tint: Color, label: String,
                     trailing: String?, isOn: Bool, action: @escaping () -> Void) -> some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(emoji != nil ? tint : Color(hex: 0xF3F4F6))
                    .frame(width: 34, height: 34)
                if let emoji {
                    Text(emoji).font(.system(size: 15))
                } else if let icon {
                    Image(systemName: icon).font(.system(size: 15)).foregroundStyle(Color.inkMuted)
                }
            }
            Text(label)
                .font(.geist(15))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
            Spacer(minLength: 6)
            if let trailing {
                Text(trailing)
                    .font(.geist(13, .semibold))
                    .foregroundStyle(Color.inkMuted)
                    .padding(.vertical, 3).padding(.horizontal, 8)
                    .background(Color(hex: 0xF3F4F6), in: Capsule())
            }
            if isOn {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color.farmGreenMap)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .contentShape(Rectangle())
        .tapCard(action)
    }
}
