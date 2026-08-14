import SwiftUI
import MapKit
import CoreLocation

/// Discover tab: a scrolling feed of randomly picked farms that all have a
/// photo, so the feed always looks good. Pull down to reshuffle. Subscribers
/// also get a short description teaser fetched lazily from the farm API.
struct DiscoverFeedView: View {
    var onOpenFarm: (FarmPin) -> Void

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    @State private var feed: [FarmPin] = []
    @State private var teasers: [String: String] = [:]
    @State private var fetchingTeasers: Set<String> = []
    @State private var showAddFarm = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 16) {
                header
                addFarmBanner

                ForEach(feed) { pin in
                    DiscoverFeedCard(
                        pin: pin,
                        teaser: teasers[pin.osmId],
                        onOpen: { onOpenFarm(pin) }
                    )
                    .onAppear { loadTeaser(for: pin) }
                }

                if feed.isEmpty && !farms.pins.isEmpty {
                    emptyState
                } else if farms.isLoading {
                    ProgressView("Loading farms…")
                        .padding(.top, 60)
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 4)
            .padding(.bottom, 14)
        }
        .refreshable { reshuffle() }
        .onAppear { if feed.isEmpty { reshuffle() } }
        .onChange(of: farms.pins.count) {
            if feed.isEmpty { reshuffle() }
        }
        .sheet(isPresented: $showAddFarm) { AddFarmView() }
    }

    private func reshuffle() {
        feed = farms.feedPicks(near: locationManager.location)
    }

    /// Subscribers see a short story line on each card; the farm API is the
    /// only place descriptions live, so fetch lazily and cache per farm.
    private func loadTeaser(for pin: FarmPin) {
        guard pin.hasDescription,
              session.hasFullAccess,
              let token = session.session?.accessToken,
              teasers[pin.osmId] == nil,
              !fetchingTeasers.contains(pin.osmId) else { return }
        fetchingTeasers.insert(pin.osmId)
        Task {
            let detail = try? await FarmDetailAPI.fetch(osmId: pin.osmId, accessToken: token)
            // Cache an empty string on failure so we don't refetch forever.
            teasers[pin.osmId] = detail?.description ?? ""
            fetchingTeasers.remove(pin.osmId)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Kicker(text: String(localized: "Discover"))
            DisplayTitle(leading: String(localized: "Farms worth a "),
                         emphasis: String(localized: "detour"),
                         trailing: "", size: 30)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 6)
    }

    private var addFarmBanner: some View {
        Button {
            Haptics.tap()
            // Submissions carry contact details — needs an account.
            if session.isAuthenticated {
                showAddFarm = true
            } else {
                requestAuth()
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Color.farmGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Know a farm shop we're missing?")
                        .font(.geist(15, .bold))
                        .foregroundStyle(Color.ink)
                    Text("Add it to the map for everyone")
                        .font(.geist(13))
                        .foregroundStyle(Color.inkMuted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.inkMuted.opacity(0.6))
            }
            .padding(14)
            .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("add-farm-banner")
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text("🧺").font(.geist(44))
            Text("Nothing to discover right now")
                .font(.geist(16, .medium))
                .foregroundStyle(Color.inkMuted)
        }
        .padding(.top, 60)
    }
}

/// One farm in the Discover feed: hero photo, save heart, categories,
/// name, place + rating, optional story teaser, and action buttons.
struct DiscoverFeedCard: View {
    let pin: FarmPin
    let teaser: String?
    var onOpen: () -> Void

    @Environment(LocationManager.self) private var locationManager
    @Environment(FavoritesStore.self) private var favorites
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth

    private var distanceText: String? {
        guard let meters = pin.distance(from: locationManager.location) else { return nil }
        return meters < 1000 ? "\(Int(meters)) m" : String(format: "%.1f km", meters / 1000)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            hero

            VStack(alignment: .leading, spacing: 8) {
                Text(pin.name)
                    .font(.display(22, weight: .semibold))
                    .foregroundStyle(Color.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                HStack(spacing: 6) {
                    if let city = pin.city {
                        Text(city)
                            .font(.geist(14))
                            .foregroundStyle(Color.inkMuted)
                    }
                    if let distanceText {
                        Text("·").foregroundStyle(Color.inkMuted)
                        Text(distanceText)
                            .font(.geist(14, .semibold))
                            .foregroundStyle(Color.farmGreen)
                    }
                    if let rating = pin.avgRating {
                        Text("·").foregroundStyle(Color.inkMuted)
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.star)
                            Text(String(format: "%.1f (%d)", rating, pin.reviewCount))
                                .font(.geist(13, .semibold))
                                .foregroundStyle(Color.ink)
                        }
                    }
                }

                if let teaser, !teaser.isEmpty {
                    Text(teaser)
                        .font(.geist(14))
                        .foregroundStyle(Color.inkMuted)
                        .lineLimit(3)
                        .lineSpacing(2)
                }

                HStack(spacing: 10) {
                    Button {
                        Haptics.tap()
                        let item = MKMapItem(placemark: MKPlacemark(coordinate: pin.coordinate))
                        item.name = pin.name
                        item.openInMaps()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                                .font(.system(size: 13, weight: .semibold))
                            Text("Directions")
                                .font(.geist(14, .bold))
                        }
                        .foregroundStyle(Color.farmGreen)
                        .padding(.vertical, 11)
                        .frame(maxWidth: .infinity)
                        .background(
                            Capsule().fill(.white).stroke(Color.farmGreen, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        Haptics.tap()
                        onOpen()
                    } label: {
                        HStack(spacing: 6) {
                            Text("View farm")
                                .font(.geist(14, .bold))
                            Image(systemName: "arrow.right")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundStyle(.white)
                        .padding(.vertical, 11)
                        .frame(maxWidth: .infinity)
                        .background(Color.farmGreen, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 4)
            }
            .padding(14)
        }
        .background(.white, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 8, y: 3)
        .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .onTapGesture { onOpen() }
    }

    private var hero: some View {
        ZStack(alignment: .topTrailing) {
            Group {
                if let image = pin.image, let url = URL(string: image) {
                    AsyncImage(url: url) { phase in
                        if case .success(let img) = phase {
                            img.resizable().scaledToFill()
                        } else {
                            categoryTile
                        }
                    }
                } else {
                    categoryTile
                }
            }
            .frame(height: 195)
            .frame(maxWidth: .infinity)
            .clipped()

            Button {
                toggleFavorite()
            } label: {
                Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.ink)
                    .frame(width: 38, height: 38)
                    .background(.white.opacity(0.95), in: Circle())
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 1)
            }
            .buttonStyle(.plain)
            .padding(10)
        }
        .overlay(alignment: .bottomLeading) {
            // Two chips, not three, each pinned to one line: three full labels
            // ("🥬 Farm Produce" …) could run off the card's right edge and get
            // clipped mid-word by the rounded corner.
            HStack(spacing: 5) {
                ForEach(pin.categories.prefix(2)) { cat in
                    Text("\(cat.emoji) \(cat.label)")
                        .font(.geist(11, .semibold))
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                        .padding(.vertical, 4)
                        .padding(.horizontal, 8)
                        .background(.white.opacity(0.94), in: Capsule())
                }
            }
            .padding(10)
        }
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 22, topTrailingRadius: 22))
    }

    private var categoryTile: some View {
        ZStack {
            pin.primaryCategory.color.opacity(0.18)
            Text(pin.primaryCategory.emoji).font(.geist(48))
        }
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
