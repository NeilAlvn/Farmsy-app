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
    @State private var showAddFarm = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 16) {
                header
                addFarmBanner

                ForEach(feed) { pin in
                    DiscoverFeedCard(pin: pin, onOpen: { onOpenFarm(pin) })
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
            .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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

/// One farm in the Discover feed: a featured photo tile with the name, place,
/// rating and category chips over the photograph, a TOP PICK badge when the farm
/// is verified, and the save heart. Tapping opens the farm.
struct DiscoverFeedCard: View {
    let pin: FarmPin
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
        // A featured photo tile, like the web's "Fresh from the farm" shelf:
        // everything sits over the photograph — the name, place, category chips —
        // with a bottom gradient carrying the white text, a TOP PICK badge for a
        // verified farm, and the save heart. Tapping opens the farm.
        ZStack(alignment: .bottomLeading) {
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
            .frame(height: 210)
            .frame(maxWidth: .infinity)
            .clipped()

            // Farm photos are mostly light at the bottom — soil, crates, gravel —
            // so white text needs the gradient's help.
            LinearGradient(colors: [.black.opacity(0.80), .black.opacity(0.22), .clear],
                           startPoint: .bottom, endPoint: .top)

            VStack(alignment: .leading, spacing: 6) {
                Text(pin.name)
                    .font(.geist(16, .bold))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                HStack(spacing: 6) {
                    if let city = pin.city {
                        Text(city)
                            .font(.geist(12))
                            .foregroundStyle(.white.opacity(0.85))
                    }
                    if let distanceText {
                        Text("·").foregroundStyle(.white.opacity(0.7))
                        Text(distanceText)
                            .font(.geist(12, .semibold))
                            .foregroundStyle(.white.opacity(0.9))
                    }
                    if let rating = pin.avgRating {
                        Text("·").foregroundStyle(.white.opacity(0.7))
                        HStack(spacing: 3) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.star)
                            Text(String(format: "%.1f", rating))
                                .font(.geist(12, .semibold))
                                .foregroundStyle(.white)
                        }
                    }
                }

                HStack(spacing: 5) {
                    ForEach(pin.categories.prefix(2)) { cat in
                        Text("\(cat.emoji) \(cat.label)")
                            .font(.geist(10, .bold))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                            .padding(.vertical, 3)
                            .padding(.horizontal, 7)
                            .background(.white.opacity(0.95), in: Capsule())
                    }
                }
            }
            .padding(12)
        }
        .frame(height: 210)
        .frame(maxWidth: .infinity)
        .overlay(alignment: .topLeading) {
            if pin.isVerified {
                Text("TOP PICK")
                    .font(.geist(9, .bold))
                    .kerning(0.5)
                    .foregroundStyle(Color.farmGreen)
                    .padding(.vertical, 3)
                    .padding(.horizontal, 8)
                    .background(.white.opacity(0.92), in: Capsule())
                    .padding(10)
            }
        }
        .overlay(alignment: .topTrailing) {
            Button {
                toggleFavorite()
            } label: {
                Image(systemName: favorites.isSaved(pin.osmId) ? "heart.fill" : "heart")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(favorites.isSaved(pin.osmId) ? Color.warnRed : Color.ink)
                    .frame(width: 34, height: 34)
                    .background(.white.opacity(0.95), in: Circle())
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 1)
            }
            .buttonStyle(.plain)
            .padding(8)
        }
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.10), radius: 8, y: 3)
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onTapGesture { onOpen() }
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
