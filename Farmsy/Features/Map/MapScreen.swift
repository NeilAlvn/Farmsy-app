import SwiftUI
import MapKit
import CoreLocation

/// Map-first discovery: search on top, live map (or list) of real Farmsy
/// pins, and a floating bottom bar with the view toggle and category menu.
struct MapScreen: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager

    @State private var showList = false
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
            if showList {
                FarmListView(
                    pins: farms.sortedByDistance(farms.filtered, from: locationManager.location),
                    onOpenFarm: onOpenFarm,
                    bottomInset: 150
                )
                .padding(.horizontal, 14)
                // Room for the floating search row above the list.
                .padding(.top, 64)
            } else {
                mapCard
            }
        }
        .frame(maxHeight: .infinity)
        // Search floats over the map; controls live at the bottom, lifted
        // above the floating tab bar.
        .overlay(alignment: .top) {
            VStack(alignment: .trailing, spacing: 10) {
                searchRow
                if !showList {
                    farmsCountBadge
                }
            }
            .padding(.horizontal, 14)
        }
        .overlay(alignment: .bottom) {
            bottomBar
                .padding(.bottom, 66)
        }
    }

    // MARK: - Search row (top)

    private var searchRow: some View {
        @Bindable var farms = farms
        return HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Color.inkMuted)
                TextField("Search by farm, city or postcode", text: $farms.searchText)
                    .autocorrectionDisabled()
            }
            .padding(.vertical, 13)
            .padding(.horizontal, 14)
            .background(.white, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 8, y: 2)

            Button {
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
            } label: {
                Image(systemName: "location.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .frame(width: 48, height: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 15, style: .continuous)
                            .fill(.white)
                            .stroke(Color.farmGreen, lineWidth: 1.5)
                    )
                    .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
            }
        }
    }

    /// Live pin count, floating just under the search row.
    private var farmsCountBadge: some View {
        Group {
            if farms.isLoading {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Loading farms…").font(.geist(13, .medium))
                }
            } else {
                Text("\(farms.filtered.count.formatted()) farms")
                    .font(.geist(13, .semibold))
                    .foregroundStyle(Color.inkMuted)
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(.white.opacity(0.95), in: Capsule())
        .shadow(color: .black.opacity(0.1), radius: 6, y: 2)
    }

    // MARK: - Bottom control bar

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Button {
                Haptics.tap()
                withAnimation(.spring(duration: 0.35)) { showList.toggle() }
            } label: {
                Image(systemName: showList ? "map" : "list.bullet")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .frame(width: 48, height: 48)
                    .background(.white, in: Circle())
                    .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
            }
            .accessibilityIdentifier("toggle-view")

            categoryMenu
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 12)
    }

    private var categoryMenu: some View {
        @Bindable var farms = farms
        return Menu {
            Button {
                farms.selectedCategory = nil
            } label: {
                Label("All Categories", systemImage: farms.selectedCategory == nil ? "checkmark" : "")
            }
            ForEach(FarmCategory.allCases) { cat in
                Button {
                    farms.selectedCategory = cat
                } label: {
                    if farms.selectedCategory == cat {
                        Label("\(cat.emoji) \(cat.label)", systemImage: "checkmark")
                    } else {
                        Text("\(cat.emoji) \(cat.label)")
                    }
                }
            }
        } label: {
            HStack(spacing: 6) {
                Text(farms.selectedCategory.map { "\($0.emoji) \($0.label)" } ?? String(localized: "🍽️ All Categories"))
                    .font(.geist(15, .semibold))
                    .foregroundStyle(Color.farmGreen)
                    .lineLimit(1)
                Image(systemName: "chevron.up")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.farmGreen)
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity)
            .background(
                Capsule()
                    .fill(.white)
                    .stroke(Color.farmGreen, lineWidth: 1.5)
            )
            .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
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
                    .foregroundStyle(Color.farmGreen)
                }
                .padding(16)
                .background(.white.opacity(0.97), in: RoundedRectangle(cornerRadius: 16))
                .padding(20)
            }
        }
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

// MARK: - List mode

struct FarmListView: View {
    let pins: [FarmPin]
    var onOpenFarm: (FarmPin) -> Void
    var bottomInset: CGFloat = 16

    @Environment(LocationManager.self) private var locationManager

    var body: some View {
        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 12) {
                ForEach(pins.prefix(120)) { pin in
                    FarmCard(pin: pin, onOpen: { onOpenFarm(pin) })
                }
                if pins.isEmpty {
                    VStack(spacing: 8) {
                        Text("🧺").font(.geist(44))
                        Text("No farms match your search")
                            .font(.geist(16, .medium))
                            .foregroundStyle(Color.inkMuted)
                    }
                    .padding(.top, 60)
                }
            }
            .padding(.top, 2)
            .padding(.bottom, bottomInset)
        }
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
                                .foregroundStyle(.yellow)
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
            .background(.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
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
