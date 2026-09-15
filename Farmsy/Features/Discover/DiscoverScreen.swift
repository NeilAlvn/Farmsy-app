import SwiftUI
import CoreLocation

/// Discover — inspiration, not utility. What is in season, what people just
/// found at farms, where to pick your own this weekend, what the community is
/// saying, then the farms with a story. Every item is one tap from the map.
struct DiscoverScreen: View {
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(\.shell) private var shell
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var seasons = Seasons.shared
    @State private var catalogue = ShoppingItems.shared
    @State private var recent: [Ping] = []
    @State private var recentLoaded = false

    private var location: CLLocation? { locationManager.location }
    private var featured: [FarmPin] { Array(farms.featuredFarms.prefix(10)) }

    /// "Just arrived": products people mentioned at farms in the last month,
    /// most-mentioned first. A post saying "verse aardbeien vandaag" is the
    /// signal — the same one the product-alert cron uses.
    private var justArrived: [(item: ShoppingItem, farms: Int)] {
        guard !recent.isEmpty, !catalogue.items.isEmpty else { return [] }
        return catalogue.items.compactMap { item in
            let farms = Set(recent.filter { ProductMatch.matches($0.body, terms: item.terms) }.map(\.farmOsmId))
            return farms.isEmpty ? nil : (item, farms.count)
        }
        .sorted { $0.farms > $1.farms }
    }

    /// Pick-your-own farms within the radius that open on Saturday or Sunday,
    /// nearest first.
    private var pickYourOwn: [FarmPin] {
        guard let location else { return [] }
        let near = farms.pins.filter {
            FarmFilters.looksLikeZelfpluk($0.name)
                && ($0.distance(from: location) ?? .infinity) / 1000 <= max(radiusKm, 25)
                && (FarmFilters.isOpenOnDay($0.openingHours, dayMon: 5) || FarmFilters.isOpenOnDay($0.openingHours, dayMon: 6))
        }
        return farms.sortedByDistance(near, from: location)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Discover"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    seasonSection
                    justArrivedSection
                    pickYourOwnSection
                    communitySection
                    SectionHeader(title: String(localized: "Farms with a story"))
                    if !farms.galleriesLoaded {
                        ForEach(0..<3, id: \.self) { _ in
                            SkeletonBox(cornerRadius: Radius.card).frame(height: 180)
                        }
                    } else {
                        ForEach(featured) { pin in
                            MultiImageFarmCard(pin: pin,
                                               images: farms.galleries[pin.osmId] ?? [],
                                               teaser: farms.featuredTeasers[pin.osmId],
                                               onOpen: { shell.openFarm(pin) })
                        }
                    }
                    TripRecommendations(onOpenFarm: shell.openFarm)
                        .padding(.top, Space.s4)
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
            .refreshable { await loadRecent() }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await farms.loadGalleriesIfNeeded() }
        .task { await seasons.loadIfNeeded() }
        .task { await catalogue.loadIfNeeded() }
        .task { await loadRecent() }
    }

    // MARK: - Sections

    @ViewBuilder
    private var seasonSection: some View {
        if !seasons.thisMonth.isEmpty {
            SectionHeader(title: String(localized: "In season near you"))
                .padding(.top, 0)
            Text(String(localized: "Grown outdoors around here this month. Tap one to see who sells it."))
                .role(.bodySm, .inkMuted)
            FlowRow(spacing: Space.s2) {
                ForEach(seasons.thisMonth) { item in
                    Chip(label: item.label, emoji: item.emoji,
                         dot: item.isPeak(month: seasons.month) ? Color.vivid : nil) {
                        show(label: item.label, terms: item.terms)
                    }
                }
            }
            .padding(.bottom, Space.s2)
        }
    }

    @ViewBuilder
    private var justArrivedSection: some View {
        let arrived = Array(justArrived.prefix(8))
        if !arrived.isEmpty {
            SectionHeader(title: String(localized: "Just arrived"))
            Text(String(localized: "Spotted at farms this month."))
                .role(.bodySm, .inkMuted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s3) {
                    ForEach(arrived, id: \.item.id) { entry in
                        Button {
                            Haptics.tap()
                            show(label: entry.item.label, terms: entry.item.terms)
                        } label: {
                            VStack(alignment: .leading, spacing: Space.s2) {
                                Text(entry.item.emoji).font(.system(size: 28))
                                Text(entry.item.label).role(.subheading).lineLimit(1)
                                Text(entry.farms == 1 ? String(localized: "1 farm") : String(localized: "\(entry.farms) farms"))
                                    .role(.caption, .inkMuted)
                            }
                            .frame(width: 124, alignment: .leading)
                            .padding(Space.s4)
                            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.trailing, Space.s4)
            }
            .padding(.trailing, -Space.s4)
        }
    }

    @ViewBuilder
    private var pickYourOwnSection: some View {
        let picks = pickYourOwn
        if !picks.isEmpty {
            SectionHeader(title: String(localized: "Pick your own this weekend"),
                          action: (String(localized: "Map"), {
                              farms.clearAllFilters()
                              farms.filterZelfpluk = true
                              shell.showTab(.map)
                          }))
            VStack(spacing: Space.s2) {
                ForEach(picks.prefix(3)) { pin in
                    let today = FarmFilters.isOpenToday(pin.openingHours)
                    let km = pin.distance(from: location).map { $0 / 1000 }
                    Button {
                        Haptics.tap()
                        shell.openFarm(pin)
                    } label: {
                        HStack(spacing: Space.s3) {
                            Text("🍓").font(.system(size: 22))
                                .frame(width: 48, height: 48)
                                .background(Color.creamFill, in: RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(pin.name).role(.subheading).lineLimit(1)
                                HStack(spacing: 6) {
                                    Circle().fill(today ? Color.vividPositive : Color.hairline).frame(width: 8, height: 8)
                                    Text(today ? String(localized: "Open today") : String(localized: "Open this weekend"))
                                    if let km { Text(verbatim: "· \(km.formatted(.number.precision(.fractionLength(1)))) km") }
                                }
                                .role(.caption, .inkMuted)
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.inkMuted)
                        }
                        .padding(Space.s3)
                        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var communitySection: some View {
        if !recent.isEmpty {
            SectionHeader(title: String(localized: "From the community"),
                          action: (String(localized: "See all"), { shell.showTab(.community) }))
            VStack(spacing: Space.s2) {
                ForEach(recent.prefix(2)) { ping in
                    PingCard(ping: ping,
                             farmName: farms.pin(forOsmId: ping.farmOsmId)?.name,
                             onOpenFarm: {
                                 if let pin = farms.pin(forOsmId: ping.farmOsmId) { shell.openFarm(pin) }
                             })
                }
            }
        }
    }

    // MARK: - Data

    private func show(label: String, terms: [String]) {
        Task {
            await farms.showProduct(label: label, terms: terms, userLocation: location, radiusKm: radiusKm)
            shell.showTab(.map)
        }
    }

    /// Posts from the last thirty days, newest first. A week was the intent,
    /// but with today's posting volume a week is often empty.
    private func loadRecent() async {
        let since = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-30 * 86400))
        let rows: [Ping] = (try? await supabase
            .from("farm_pings")
            .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
            .eq("status", value: "visible")
            .gte("created_at", value: since)
            .order("created_at", ascending: false)
            .limit(60)
            .execute()
            .value) ?? []
        recent = rows
        recentLoaded = true
    }
}
