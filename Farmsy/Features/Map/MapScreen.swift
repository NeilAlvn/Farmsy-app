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
    /// True while an AI-search parse is in flight (spinner in the bar).
    @State private var aiSearching = false

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
    private static let gridCellsAcross = 10.0
    /// Below this span (roughly neighbourhood zoom) stop clustering entirely and
    /// draw every farm as its own pin — otherwise a dense area stays hidden behind
    /// a count bubble even after the user has zoomed right onto it.
    private static let declusterSpan = 0.06
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
        // Zoomed in far enough to read as a neighbourhood: skip grouping and draw
        // every farm individually, so nothing stays buried in a bubble up close.
        if span.latitudeDelta < declusterSpan {
            return pins.map { MapCluster(id: $0.osmId, coordinate: $0.coordinate, pins: [$0]) }
        }
        let cellLat = max(span.latitudeDelta / gridCellsAcross, 0.0001)
        let cellLng = max(span.longitudeDelta / gridCellsAcross, 0.0001)
        var buckets: [String: [FarmPin]] = [:]
        for pin in pins {
            let row = Int((pin.lat / cellLat).rounded(.down))
            let col = Int((pin.lng / cellLng).rounded(.down))
            buckets["\(row)_\(col)", default: []].append(pin)
        }
        return buckets.flatMap { key, group -> [MapCluster] in
            // Only make a count bubble for double digits. A "2"–"9" bubble is just
            // a handful of pins a tap away from being useful, so draw them as
            // individual farm pins instead — no bubble a user has to zoom past.
            if group.count < 10 {
                return group.map { MapCluster(id: $0.osmId, coordinate: $0.coordinate, pins: [$0]) }
            }
            let lat = group.reduce(0.0) { $0 + $1.lat } / Double(group.count)
            let lng = group.reduce(0.0) { $0 + $1.lng } / Double(group.count)
            return [MapCluster(id: key, coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng), pins: group)]
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
            VStack(spacing: 8) {
                HStack(spacing: 10) {
                    searchRow
                    CircleMapButton(icon: "location.fill", size: 44, action: locateNearMe)
                }
                if farms.aiIntent != nil { aiSummaryBar } else { quickChips }
            }
            .padding(.horizontal, 14)
            .padding(.top, 6)
        }
        // An AI search that named a place flies the map there (the web doesn't yet).
        .onChange(of: farms.aiPlaceToken) { _, _ in flyToAIPlace() }
        // First appearance: a search or product tap from another tab may already
        // be waiting; otherwise open on the user rather than on the whole country.
        .onAppear {
            if farms.aiIntent != nil {
                flyToAIPlace()
            } else if visibleRegion == nil, let loc = locationManager.location {
                camera = .region(MKCoordinateRegion(center: loc.coordinate,
                                                    span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)))
            }
        }
        // Emptying the search bar drops the AI intent so the map returns to all farms.
        .onChange(of: farms.searchText) { _, text in
            if text.trimmingCharacters(in: .whitespaces).isEmpty, farms.aiIntent != nil {
                farms.clearAISearch()
            }
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
        // Prefer the actual road line so the whole traced route is framed (it curves
        // beyond the stops); fall back to origin + stops before the route arrives.
        var coords: [CLLocationCoordinate2D]
        if trip.routeLine.count >= 2 {
            coords = trip.routeLine
        } else {
            coords = trip.stopIds.compactMap { pinLookup[$0]?.coordinate }
            if let origin = trip.originCoord { coords.insert(origin, at: 0) }
        }
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

    // MARK: - Quick chips

    /// The four filters people reach for most, one tap under the search bar.
    /// "Open now" leads: farm hours are irregular and seasonal, and a wasted
    /// drive is the thing this map exists to prevent.
    private var quickChips: some View {
        @Bindable var farms = farms
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                Chip(label: String(localized: "Open now"), selected: farms.filterOpenNow,
                     dot: farms.filterOpenNow ? nil : Color.vividPositive) { farms.filterOpenNow.toggle() }
                Chip(label: String(localized: "Open today"), selected: farms.filterOpenToday) { farms.filterOpenToday.toggle() }
                Chip(label: String(localized: "Pick your own"), emoji: "🍓", selected: farms.filterZelfpluk) { farms.filterZelfpluk.toggle() }
                Chip(label: String(localized: "Vending machine"), emoji: "🥚", selected: farms.filterAutomaat) { farms.filterAutomaat.toggle() }
            }
            .padding(.horizontal, 2)
        }
        .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
    }

    // MARK: - Search row (top)

    private var searchRow: some View {
        @Bindable var farms = farms
        let filtersActive = farms.anyFilterOn || farms.aiIntent != nil
        return HStack(spacing: 10) {
            if aiSearching {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: farms.aiIntent != nil ? "sparkles" : "magnifyingglass")
                    .font(.system(size: 15))
                    .foregroundStyle(farms.aiIntent != nil ? Color.farmGreenMap : Color.inkMuted)
            }
            // Return key runs the AI parse ("cheese near Utrecht", "waar kan ik
            // aardbeien plukken"); plain typing still filters by name/city live.
            TextField("Search or ask — e.g. cheese near you", text: $farms.searchText)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { runSmartSearch() }
            // Filters live on the search bar, web-style: tapping slides a sheet up.
            Button {
                Haptics.tap()
                Observability.capture(.filtersOpened)
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

    /// Shows what the AI understood — the summary sentence (in the query's own
    /// language) plus the parsed values as chips, and an × to clear.
    private var aiSummaryBar: some View {
        let ai = farms.aiIntent
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "sparkles").font(.system(size: 13)).foregroundStyle(Color.farmGreenMap)
                Text(ai?.summary ?? "").font(.ui(13)).foregroundStyle(Color.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 4)
                Button {
                    Haptics.tap()
                    farms.clearAISearch()
                } label: {
                    Image(systemName: "xmark.circle.fill").font(.system(size: 16)).foregroundStyle(Color.inkMuted)
                }.buttonStyle(.plain)
            }
            let chips = aiChips
            if !chips.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(chips, id: \.self) { chip in
                            Text(chip)
                                .font(.ui(11, .semibold))
                                .foregroundStyle(Color.farmGreenDeep)
                                .padding(.vertical, 4).padding(.horizontal, 9)
                                .background(Color.farmGreenMap.opacity(0.12), in: Capsule())
                        }
                    }
                }
            }
        }
        .padding(.vertical, 11)
        .padding(.horizontal, 16)
        .background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
    }

    /// The parsed values as short chip labels.
    private var aiChips: [String] {
        guard let ai = farms.aiIntent else { return [] }
        var out: [String] = []
        out += ai.categories.compactMap { FarmCategory.from($0)?.label }
        out += ai.products.map { $0.capitalized }
        // The two new axes, using the same client labels as the filter groups.
        out += ai.locationTypes.compactMap { id in FarmAxis.placeTypes.first { $0.id == id }?.label }
        out += ai.methods.compactMap { id in FarmAxis.methods.first { $0.id == id }?.label }
        // Place, with the radius when one came back (or a nearMe default).
        if ai.nearMe {
            let km = Int(ai.radiusKm ?? 15)
            out.append("📍 " + String(localized: "Near you") + " · \(km) km")
        } else if let place = ai.place, !place.isEmpty {
            let km = Int(ai.radiusKm ?? 25)
            out.append("📍 \(place) · \(km) km")
        }
        if ai.openNow  { out.append(String(localized: "Open today")) }
        if ai.zelfpluk { out.append(String(localized: "Pick-your-own")) }
        if ai.automaat { out.append(String(localized: "Open 24/7 (automaat)")) }
        if ai.verified { out.append(String(localized: "Verified farms")) }
        // De-dup while preserving order (category + product can repeat, e.g. cheese).
        var seen = Set<String>()
        return out.filter { seen.insert($0.lowercased()).inserted }
    }

    private func runSmartSearch() {
        let q = farms.searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard q.count >= 2 else { return }
        dismissKeyboard()
        aiSearching = true
        Task {
            let intent = await SmartSearchAPI.parse(q)
            aiSearching = false
            // Empty/parse-failure → leave the live keyword filter in place.
            if let intent, !intent.isEmpty {
                Haptics.tap()
                await farms.applyAISearch(intent, userLocation: locationManager.location)
            }
        }
    }

    /// Fly the map to the AI-resolved centre (server `center`, or the user's own
    /// location for a nearMe query) — no client geocode any more; span sized to
    /// the search radius so the matching farms all sit in frame.
    private func flyToAIPlace() {
        guard let center = farms.aiCenter else { return }
        // ~1° lat ≈ 111 km; frame a bit wider than the radius so pins clear the edge.
        let km = farms.aiIntent?.radiusKm ?? (farms.aiIntent?.nearMe == true ? 15 : 25)
        let span = max(0.08, (km / 111) * 2.4)
        withAnimation(.easeInOut(duration: 0.6)) {
            camera = .region(MKCoordinateRegion(
                center: center,
                span: MKCoordinateSpan(latitudeDelta: span, longitudeDelta: span)))
        }
    }

    private func flyToFocus() {
        guard let pin = focusPin else { return }
        // Recenter on the farm but keep the user's current zoom when they're already
        // zoomed in — forcing a fixed span zoomed the map out and dissolved the
        // clusters around the pin. Only zoom in if they were further out than this.
        let cap = 0.15
        let current = visibleRegion?.span.latitudeDelta ?? cap
        let delta = min(current, cap)
        // Shift the map centre south of the pin so the pin sits in the upper part
        // of the map — the detail sheet covers the lower ~55%, so centring exactly
        // would hide it. ~0.28·span lands it around the top quarter of the screen,
        // i.e. the middle of the still-visible strip.
        let center = CLLocationCoordinate2D(latitude: pin.coordinate.latitude - delta * 0.28,
                                            longitude: pin.coordinate.longitude)
        withAnimation(.easeInOut(duration: 0.5)) {
            camera = .region(MKCoordinateRegion(
                center: center,
                span: MKCoordinateSpan(latitudeDelta: delta, longitudeDelta: delta)
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
                        FarmPinView(category: cluster.representative.primaryCategory,
                                    isHighlighted: cluster.representative.osmId == focusPin?.osmId)
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
        // A plain tap anywhere on the map dismisses the search keyboard. onEnd
        // camera changes only fire when the map actually moves, so a tap that
        // doesn't pan wouldn't drop it — this covers that. Simultaneous so it
        // runs alongside the map's own panning and the pin/cluster taps.
        .simultaneousGesture(TapGesture().onEnded { dismissKeyboard() })
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
                        .font(.ui(14, .medium))
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        Task { await farms.loadIfNeeded() }
                    }
                    .font(.ui(15, .semibold))
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
                    .font(.ui(13, .semibold))
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
            .font(.ui(count > 99 ? 13 : 15, .bold))
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
            .font(.ui(14, .bold))
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
                        .font(.ui(18, .bold))
                        .foregroundStyle(Color.ink)
                        .multilineTextAlignment(.leading)
                    Spacer()
                    if let distanceText {
                        Text(distanceText)
                            .font(.ui(15, .semibold))
                            .foregroundStyle(Color.farmGreen)
                    }
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Color.inkMuted.opacity(0.6))
                        .padding(.top, 3)
                }

                if pin.address != nil || pin.city != nil {
                    Text([pin.address, pin.postalCode, pin.city].compactMap(\.self).joined(separator: ", "))
                        .font(.ui(14))
                        .foregroundStyle(Color.inkMuted)
                        .lineLimit(1)
                }

                HStack(spacing: 6) {
                    ForEach(pin.categories.prefix(4)) { cat in
                        Text(cat.emoji).font(.ui(17))
                    }
                    if let rating = pin.avgRating {
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.star)
                            Text(String(format: "%.1f", rating))
                                .font(.ui(13, .semibold))
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
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    /// The farm-free Pro upsell (Aviah later-7 option 2). Shown when a locked Pro
    /// filter is tapped by a signed-in non-member; a signed-out tap routes to sign-in
    /// first (a membership hangs off an account).
    @State private var showPro = false

    /// The five Pro groups are gated when the account isn't a member — paid on both
    /// platforms, no exception (Aviah later-8: the Android no-IAP concern was wrong,
    /// Play IAP works and carries most revenue, so the escalation was withdrawn). The
    /// time filters were never free; the two axis groups moved into Pro from the rail.
    /// Time and place-type filters are free now: opening hours are the farm's own
    /// information, not intelligence. Plus sells matching, routing and alerts.
    private var proLocked: Bool { false }

    var body: some View {
        @Bindable var farms = farms
        VStack(spacing: 0) {
            HStack {
                Text("Filters").font(.ui(18, .bold)).foregroundStyle(Color.ink)
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

                    // FARMSY PRO — five groups (Aviah's closed set). Shown to
                    // everyone, dimmed + a lock when the account isn't a member; a
                    // locked tap opens the upsell (signed out → sign-in first) rather
                    // than toggling. The three time filters were never free; the two
                    // axis groups moved into Pro from the free rail.
                    divider
                    sectionHeader(String(localized: "When and what kind"))
                    proRow(id: AnalyticsValue.Filter.openNow, icon: "clock.badge.checkmark", label: String(localized: "Open right now"),
                           isOn: farms.filterOpenNow) { farms.filterOpenNow.toggle() }
                    proRow(id: AnalyticsValue.Filter.openSaturday, icon: "calendar", label: String(localized: "Open Saturday"),
                           isOn: farms.filterOpenSaturday) { farms.filterOpenSaturday.toggle() }
                    proRow(id: AnalyticsValue.Filter.openSunday, icon: "calendar", label: String(localized: "Open Sunday"),
                           isOn: farms.filterOpenSunday) { farms.filterOpenSunday.toggle() }

                    // Type of place — an axis group, now Pro. Combines with categories.
                    divider
                    sectionHeader(String(localized: "Type of place"))
                    ForEach(FarmAxis.placeTypes) { v in
                        proRow(id: v.id, icon: v.icon, label: v.label,
                               isOn: farms.selectedPlaceTypes.contains(v.id)) {
                            if farms.selectedPlaceTypes.contains(v.id) { farms.selectedPlaceTypes.remove(v.id) }
                            else { farms.selectedPlaceTypes.insert(v.id) }
                        }
                    }

                    divider
                    sectionHeader(String(localized: "How it's grown"))
                    ForEach(FarmAxis.methods) { v in
                        proRow(id: v.id, icon: v.icon, label: v.label,
                               isOn: farms.selectedMethods.contains(v.id)) {
                            if farms.selectedMethods.contains(v.id) { farms.selectedMethods.remove(v.id) }
                            else { farms.selectedMethods.insert(v.id) }
                        }
                    }
                }
                .padding(.bottom, 24)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        // The two new axes read from the flags maps — make sure they're loaded.
        .task { await farms.loadFlagsIfNeeded() }
        .sheet(isPresented: $showPro) {
            ProUpsellSheet()
                .presentationDetents([.fraction(0.7), .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
    }

    /// A Pro filter row: for a member it behaves like any toggle row; for a non-member
    /// it renders dimmed with a lock and a tap opens the upsell (signed out → sign-in
    /// first) instead of toggling — "shown, not hidden" so people see what Pro buys.
    @ViewBuilder
    private func proRow(id: String, icon: String, label: String, isOn: Bool, toggle: @escaping () -> Void) -> some View {
        if proLocked {
            row(icon: icon, emoji: nil, tint: Color.inkMuted, label: label,
                trailing: nil, isOn: false, lockedTrailing: true) {
                Haptics.tap()
                if session.isAuthenticated {
                    // A signed-in non-member on a locked row: the tap, then the sheet
                    // it opens. A signed-out tap goes to sign-in, not to a paywall.
                    Observability.capture(.proFilterTapped, [AnalyticsProp.filter: id])
                    Observability.capture(.paywallViewed,
                                          [AnalyticsProp.trigger: AnalyticsValue.Trigger.filterRow.rawValue])
                    showPro = true
                } else {
                    requestAuth()
                }
            }
            .opacity(0.5)
        } else {
            row(icon: icon, emoji: nil, tint: Color.inkMuted, label: label,
                trailing: nil, isOn: isOn) { toggle() }
        }
    }

    private var divider: some View {
        Rectangle().fill(Color.hairline).frame(height: 1).padding(.vertical, 4)
    }

    private func sectionHeader(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.ui(11, .semibold)).kerning(1.1)
            .foregroundStyle(Color.inkMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 6).padding(.bottom, 2)
    }

    private func row(icon: String?, emoji: String?, tint: Color, label: String,
                     trailing: String?, isOn: Bool, lockedTrailing: Bool = false,
                     action: @escaping () -> Void) -> some View {
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
                .font(.ui(15))
                .foregroundStyle(Color.ink)
                .lineLimit(1)
            Spacer(minLength: 6)
            if let trailing {
                Text(trailing)
                    .font(.ui(13, .semibold))
                    .foregroundStyle(Color.inkMuted)
                    .padding(.vertical, 3).padding(.horizontal, 8)
                    .background(Color(hex: 0xF3F4F6), in: Capsule())
            }
            if lockedTrailing {
                // A member-only row for a non-member: a lock instead of a checkmark.
                Image(systemName: "lock.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.inkMuted)
            } else if isOn {
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
