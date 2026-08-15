import SwiftUI
import MapKit
import CoreLocation

/// The map is the app. A full-bleed map with a floating header (logo + account),
/// search and filters on top, a What's New button and a locate button, and the
/// farm card as a bottom sheet the parent presents.
struct MapScreen: View {
    var onOpenFarm: (FarmPin) -> Void
    var onOpenSaved: () -> Void = {}
    var onOpenSettings: () -> Void = {}
    var onOpenWhatsNew: () -> Void = {}

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    /// Whether any farm has posted — the What's New button only shows when true.
    @State private var hasPosts = false
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

    /// SwiftUI Map slows down past a few hundred annotations, so cap what we
    /// draw to the pins inside the current viewport.
    private let annotationCap = 130

    private var visiblePins: [FarmPin] {
        let all = farms.filtered
        guard let region = visibleRegion else { return Self.capRepresentative(all, annotationCap) }
        let latHalf = region.span.latitudeDelta / 2 * 1.15
        let lngHalf = region.span.longitudeDelta / 2 * 1.15
        let inView = all.filter {
            abs($0.lat - region.center.latitude) < latHalf &&
            abs($0.lng - region.center.longitude) < lngHalf
        }
        return Self.capRepresentative(inView, annotationCap)
    }

    /// Cap the drawn annotations without skewing the visible category mix.
    /// Taking the first N draws them in database order, which clusters one or
    /// two pin colors; spread the budget evenly across the pins in view instead.
    private static func capRepresentative(_ pins: [FarmPin], _ cap: Int) -> [FarmPin] {
        guard pins.count > cap else { return pins }
        let stride = Double(pins.count) / Double(cap)
        return (0..<cap).map { pins[Int(Double($0) * stride)] }
    }

    var body: some View {
        ZStack {
            mapCard
        }
        .frame(maxHeight: .infinity)
        // The header, search and filters float over the map at the top.
        .overlay(alignment: .top) {
            VStack(spacing: 10) {
                headerRow
                    .padding(.horizontal, 14)
                searchRow
                    .padding(.horizontal, 14)
            }
            .padding(.top, 4)
        }
        .sheet(isPresented: $showFilters) {
            FilterSheet()
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .onChange(of: focusPin?.osmId) { _, _ in flyToFocus() }
        // Locate bottom-right, clear of the sheet.
        .overlay(alignment: .bottomTrailing) {
            locateButton
                .padding(.trailing, 14)
                .padding(.bottom, 24)
        }
        .task { await checkForPosts() }
    }

    // MARK: - Header (logo, What's New, account)

    private var headerRow: some View {
        HStack(spacing: 8) {
            Text("Farmsy")
                .font(.displayItalic(24, weight: .medium))
                .foregroundStyle(Color.ink)
            Spacer()
            if hasPosts {
                CircleMapButton(icon: "newspaper", action: onOpenWhatsNew)
            }
            Menu {
                if session.isAuthenticated {
                    Button { onOpenSaved() } label: { Label(String(localized: "Saved"), systemImage: "heart") }
                    Button { onOpenSettings() } label: { Label(String(localized: "Settings"), systemImage: "gearshape") }
                } else {
                    Button { requestAuth() } label: { Label(String(localized: "Sign in"), systemImage: "person.crop.circle") }
                }
            } label: {
                CircleMapButtonLabel(icon: session.isAuthenticated ? "person.crop.circle.fill" : "person.crop.circle")
            }
        }
    }

    private var locateButton: some View {
        CircleMapButton(icon: "location.fill", size: 48, action: locateNearMe)
    }

    /// A cheap "does any post exist" check, to decide whether the What's New
    /// button appears at all (MOBILE-SPEC-MAP §1).
    private func checkForPosts() async {
        struct IdRow: Decodable { let id: String }
        let rows: [IdRow] = (try? await supabase
            .from("farm_pings")
            .select("id")
            .eq("status", value: "visible")
            .limit(1)
            .execute()
            .value) ?? []
        hasPosts = !rows.isEmpty
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
            ForEach(visiblePins) { pin in
                Annotation(pin.name, coordinate: pin.coordinate, anchor: .bottom) {
                    FarmPinView(category: pin.primaryCategory)
                        .onTapGesture {
                            Haptics.tap()
                            onOpenFarm(pin)
                        }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .onMapCameraChange(frequency: .onEnd) { context in
            visibleRegion = context.region
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
        Button {
            Haptics.tap()
            action()
        } label: {
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
        }
        .buttonStyle(.plain)
    }
}
