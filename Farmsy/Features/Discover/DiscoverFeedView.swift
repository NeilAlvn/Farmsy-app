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
    @State private var pings: [Ping] = []
    @State private var showAddFarm = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 16) {
                header

                if !pings.isEmpty {
                    whatsNewSection
                }

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
        .refreshable { reshuffle(); await loadPings() }
        .onAppear { if feed.isEmpty { reshuffle() } }
        .onChange(of: farms.pins.count) {
            if feed.isEmpty { reshuffle() }
        }
        .task { await loadPings() }
        // Submissions now happen on the web form (it carries its own sign-in);
        // the native AddFarmView + /api/farms/submit stay live but unused for now.
        .sheet(isPresented: $showAddFarm) {
            if let url = URL(string: "https://www.farmsy.app/farmers/submit") {
                SafariView(url: url).ignoresSafeArea()
            }
        }
    }

    private func reshuffle() {
        feed = farms.feedPicks(near: locationManager.location)
    }

    /// The farms' latest posts, read straight from Supabase (RLS lets anyone read
    /// visible pings). Newest first, best-effort — a failure just leaves the
    /// section hidden.
    private func loadPings() async {
        do {
            let rows: [Ping] = try await supabase
                .from("farm_pings")
                .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
                .eq("status", value: "visible")
                .order("created_at", ascending: false)
                .limit(30)
                .execute()
                .value
            pings = rows
        } catch {
            // Leave the section hidden on failure.
        }
    }

    /// "WHAT'S NEW" — the recent farm posts, matching the web panel's section.
    private var whatsNewSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("WHAT'S NEW")
                .font(.geist(11, .semibold))
                .kerning(1.2)
                .foregroundStyle(Color.inkMuted)

            ForEach(pings.prefix(8)) { ping in
                PingCard(ping: ping,
                         farmName: farms.pin(forOsmId: ping.farmOsmId)?.name,
                         onOpenFarm: {
                             if let pin = farms.pin(forOsmId: ping.farmOsmId) { onOpenFarm(pin) }
                         })
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Kicker(text: String(localized: "Discover"))
            DisplayTitle(String(localized: "Farms worth a *detour*"), size: 30)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 6)
    }

    private var addFarmBanner: some View {
        Button {
            Haptics.tap()
            // Opens the web submission form, which handles its own sign-in.
            showAddFarm = true
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

/// One farm post in the "What's new" feed. Matches the web PingList card: an
/// initials avatar, the author, the farm it belongs to, time since, the body,
/// a row of equal-square photos, and the like count. Read-only for now — tapping
/// the card or a photo opens the farm.
struct PingCard: View {
    let ping: Ping
    let farmName: String?
    var onOpenFarm: () -> Void
    /// Tapping a photo opens the viewer at that index, rather than opening the farm.
    var onOpenImage: ((Int) -> Void)? = nil

    private var initials: String {
        let parts = ping.authorName.split(separator: " ").compactMap { $0.first }
        let s = String(parts.prefix(2)).uppercased()
        return s.isEmpty ? "?" : s
    }

    private var timeAgo: String {
        guard let date = ping.date else { return "" }
        let mins = Int(Date().timeIntervalSince(date) / 60)
        if mins < 1 { return String(localized: "just now") }
        if mins < 60 { return String(localized: "\(mins)m") }
        let hours = mins / 60
        if hours < 24 { return String(localized: "\(hours)h") }
        return String(localized: "\(hours / 24)d")
    }

    var body: some View {
        // Two tap regions, siblings not nested: the header + text open the farm,
        // the photos open the viewer. Keeping them separate avoids a nested-gesture
        // double-fire (opening the farm *and* the photo).
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Text(initials)
                        .font(.geist(14, .bold))
                        .foregroundStyle(Color.farmGreen)
                        .frame(width: 40, height: 40)
                        .background(Color.farmGreen.opacity(0.12), in: Circle())
                    VStack(alignment: .leading, spacing: 1) {
                        Text(ping.authorName)
                            .font(.geist(14, .semibold))
                            .foregroundStyle(Color.ink)
                            .lineLimit(1)
                        if let farmName {
                            Text(farmName)
                                .font(.geist(12, .medium))
                                .foregroundStyle(Color.farmGreenMap)
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 6)
                    Text(timeAgo)
                        .font(.geist(11))
                        .foregroundStyle(Color.inkMuted)
                }

                if !ping.body.isEmpty {
                    Text(ping.body)
                        .font(.geist(14))
                        .foregroundStyle(Color.ink)
                        .lineLimit(3)
                        .lineSpacing(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .contentShape(Rectangle())
            .tapCard(onOpenFarm)

            if !ping.images.isEmpty {
                FixedImageRow(urls: Array(ping.images.prefix(3)), height: 100,
                              onTap: onOpenImage)
            }

            HStack(spacing: 5) {
                Image(systemName: "heart")
                    .font(.system(size: 12))
                if ping.likeCount > 0 {
                    Text("\(ping.likeCount)").font(.geist(12))
                }
            }
            .foregroundStyle(Color.inkMuted)
            .contentShape(Rectangle())
            .tapCard(onOpenFarm)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.creamCard, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.hairline, lineWidth: 1)
        )
    }
}

/// A row of equal fixed-size photo containers. Whatever a photo's aspect ratio,
/// it fills its slot and is clipped — so two photos are two equal squares that
/// never overlap or spill. Base rectangles carry the layout; the image is an
/// overlay, so the widths stay equal regardless of the images' own sizes.
struct FixedImageRow: View {
    let urls: [String]
    var height: CGFloat = 100
    /// Tapping a photo — passes its index. When nil the row is not tappable.
    var onTap: ((Int) -> Void)? = nil

    var body: some View {
        // A single photo is shown as a square; two or three fill the row as equal
        // tiles of the given height.
        let single = urls.count == 1
        HStack(spacing: 6) {
            ForEach(Array(urls.enumerated()), id: \.element) { i, url in
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color(hex: 0xF3F4F6))
                    .frame(maxWidth: .infinity)
                    .modifier(SquareOrHeight(single: single, height: height))
                    .overlay(
                        AsyncImage(url: URL(string: url)) { phase in
                            if case .success(let img) = phase {
                                img.resizable().scaledToFill()
                            } else {
                                SkeletonBox(cornerRadius: 10)
                            }
                        }
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .modifier(OptionalTap(onTap: onTap.map { cb in { cb(i) } }))
            }
        }
    }
}

/// One image → square (1:1); several → the fixed row height.
private struct SquareOrHeight: ViewModifier {
    let single: Bool
    let height: CGFloat
    func body(content: Content) -> some View {
        if single { content.aspectRatio(1, contentMode: .fit) }
        else { content.frame(height: height) }
    }
}

/// Applies tapCard only when an action is provided, so a plain image row stays
/// non-interactive.
private struct OptionalTap: ViewModifier {
    let onTap: (() -> Void)?
    func body(content: Content) -> some View {
        if let onTap { content.tapCard(onTap) } else { content }
    }
}

/// A description clamped to three lines with a "View more" that does something
/// (opens the farm / the paywall), matching the web's clamp.
struct ClampedDescription: View {
    let text: String
    var lineLimit: Int = 3
    var onMore: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(text)
                .font(.geist(14))
                .foregroundStyle(Color.ink)
                .lineLimit(lineLimit)
                .lineSpacing(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            if text.count > 120 {
                Text("… View more")
                    .font(.geist(13, .semibold))
                    .foregroundStyle(Color.farmGreen)
            }
        }
    }
}
