import SwiftUI
import CoreLocation

/// Discover — inspiration, not utility. Three tabs under one title, the Nime
/// pattern: Season (the year as a rail of months, each with what is ripe and
/// what to make), Discover (what just arrived, grandmother's tips, pick your
/// own, the community), Farms (search, four chips, farms with a story).
struct DiscoverScreen: View {
    enum Tab: String, CaseIterable, Identifiable {
        case season, discover, farms
        var id: String { rawValue }
        var title: String {
            switch self {
            case .season: String(localized: "Season")
            case .discover: String(localized: "Discover")
            case .farms: String(localized: "Farms")
            }
        }
    }

    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(TripStore.self) private var trip
    @Environment(\.shell) private var shell
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var tab: Tab = .season
    @State private var seasons = Seasons.shared
    @State private var catalogue = ShoppingItems.shared
    @State private var tips = Tips.shared
    @State private var products = Products.shared
    @State private var recent: [Ping] = []
    @State private var recentLoaded = false
    @State private var openMonth: Int?
    @State private var farmQuery = ""
    @State private var farmChips: Set<FarmChip> = []

    private var location: CLLocation? { locationManager.location }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Discover"))
                .padding(.top, Space.s2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s2) {
                    ForEach(Tab.allCases) { t in
                        Chip(label: t.title, selected: tab == t) { tab = t }
                    }
                }
                .padding(.horizontal, Space.s4)
            }
            .padding(.bottom, Space.s3)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    switch tab {
                    case .season: SeasonRail(openMonth: $openMonth)
                    case .discover: discoverTab
                    case .farms: farmsTab
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
            .refreshable { await loadRecent() }
        }
        .background(Color.cream.ignoresSafeArea())
        .sheet(item: Binding(get: { openMonth.map(MonthID.init) }, set: { openMonth = $0?.month })) { m in
            MonthSheet(month: m.month)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .task { await farms.loadGalleriesIfNeeded() }
        .task { await seasons.loadIfNeeded() }
        .task { await catalogue.loadIfNeeded() }
        .task { await tips.loadIfNeeded() }
        .task { await products.loadIfNeeded() }
        .task { await loadRecent() }
    }

    private struct MonthID: Identifiable { let month: Int; var id: Int { month } }

    // MARK: - Discover tab

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

    @ViewBuilder
    private var discoverTab: some View {
        if !recentLoaded && tips.tips.isEmpty {
            ForEach(0..<3, id: \.self) { _ in SkeletonBox(cornerRadius: Radius.card).frame(height: 150) }
        } else {
            justArrivedSection
            recipeOfWeekSection
            tipsSection
            pickYourOwnSection
            communitySection
            if recent.isEmpty && tips.tips.isEmpty && pickYourOwn.isEmpty {
                EmptyState(icon: "leaf", title: String(localized: "Nothing new yet"),
                           text: String(localized: "Posts and finds from farms near you land here. Allow location, or check back later."))
            }
        }
    }

    @ViewBuilder
    private var justArrivedSection: some View {
        let arrived = Array(justArrived.prefix(8))
        if !arrived.isEmpty {
            SectionHeader(title: String(localized: "Just arrived")).padding(.top, 0)
            Text(String(localized: "Spotted at farms this month."))
                .role(.bodySm, .inkMuted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s3) {
                    ForEach(arrived, id: \.item.id) { entry in
                        Button {
                            Haptics.tap()
                            shell.openProduct(entry.item.id)
                        } label: {
                            VStack(alignment: .leading, spacing: Space.s2) {
                                ProductImage(slug: entry.item.imageSlug, fallback: entry.item.emoji, size: 64)
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

    /// One idea from a product at its peak this month, fixed for the ISO week
    /// so everyone sees the same one and it changes on Monday.
    private var recipeOfWeek: (ProductProfile, ProductProfile.Idea)? {
        let month = Calendar.current.component(.month, from: Date())
        let pool = products.all.filter { $0.state(in: month) == .peak }.flatMap { p in p.ideas.map { (p, $0) } }
        guard !pool.isEmpty else { return nil }
        let week = Calendar(identifier: .iso8601).component(.weekOfYear, from: Date())
        return pool[week % pool.count]
    }

    @ViewBuilder
    private var recipeOfWeekSection: some View {
        if let (p, idea) = recipeOfWeek {
            SectionHeader(title: String(localized: "Recipe of the week"),
                          action: (p.name, { Haptics.tap(); shell.openProduct(p.slug) }))
            IdeaCard(kicker: nil, title: idea.title, text: idea.body,
                     image: idea.image, fallbackImage: p.image, fallback: "🍽️",
                     ingredients: idea.ingredients)
        }
    }

    @ViewBuilder
    private var tipsSection: some View {
        if !tips.tips.isEmpty {
            SectionHeader(title: String(localized: "Grandmother's tips"))
            Text(String(localized: "How to buy, keep and use farm food. The things people used to know."))
                .role(.bodySm, .inkMuted)
            CardCarousel(items: tips.tips) { tip in
                IdeaCard(kicker: tip.kicker.text, title: tip.title.text, text: tip.body.text,
                         image: tip.image, fallback: "🧺",
                         ingredients: tip.ingredient.map { [$0] } ?? [])
            }
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
                            ProductImage(slug: "strawberry", fallback: "🍓", size: 48)
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

    // MARK: - Farms tab

    enum FarmChip: CaseIterable, Identifiable {
        case photos, verified, openToday, pickYourOwn
        var id: Self { self }
        var title: String {
            switch self {
            case .photos: String(localized: "With photos")
            case .verified: String(localized: "Verified")
            case .openToday: String(localized: "Open today")
            case .pickYourOwn: String(localized: "Pick your own")
            }
        }
        func matches(_ p: FarmPin) -> Bool {
            switch self {
            case .photos: p.image != nil
            case .verified: p.isVerified
            case .openToday: FarmFilters.isOpenToday(p.openingHours)
            case .pickYourOwn: FarmFilters.looksLikeZelfpluk(p.name)
            }
        }
    }

    /// Name or city, folded, plus every selected chip. Nearest first.
    private var farmResults: [FarmPin] {
        let q = ProductMatch.fold(farmQuery)
        let hits = farms.pins.filter { pin in
            (q.isEmpty || ProductMatch.fold(pin.name).contains(q) || ProductMatch.fold(pin.city ?? "").contains(q))
                && farmChips.allSatisfy { $0.matches(pin) }
        }
        return farms.sortedByDistance(hits, from: location)
    }

    @ViewBuilder
    private var farmsTab: some View {
        SearchField(text: $farmQuery, placeholder: String(localized: "Search farms by name or place"))
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Space.s2) {
                ForEach(FarmChip.allCases) { chip in
                    Chip(label: chip.title, selected: farmChips.contains(chip)) {
                        if farmChips.contains(chip) { farmChips.remove(chip) } else { farmChips.insert(chip) }
                    }
                }
            }
            .padding(.trailing, Space.s4)
        }
        .padding(.trailing, -Space.s4)
        .padding(.vertical, Space.s3)
        if !farmQuery.isEmpty || !farmChips.isEmpty {
            let results = farmResults
            Text(results.count == 1 ? String(localized: "1 farm") : String(localized: "\(results.count) farms"))
                .role(.label, .inkFaint).textCase(.uppercase)
            if results.isEmpty {
                EmptyState(icon: "magnifyingglass", title: String(localized: "No farm matches"),
                           text: String(localized: "Try fewer words, or clear a chip."))
            }
            ForEach(results.prefix(40)) { pin in
                FarmRow(pin: pin, km: pin.distance(from: location).map { $0 / 1000 }) { shell.openFarm(pin) }
            }
        } else {
            SectionHeader(title: String(localized: "Farms with a story")).padding(.top, 0)
            if !farms.galleriesLoaded {
                ForEach(0..<3, id: \.self) { _ in
                    SkeletonBox(cornerRadius: Radius.card).frame(height: 180)
                }
            } else {
                ForEach(Array(farms.featuredFarms.prefix(10))) { pin in
                    MultiImageFarmCard(pin: pin,
                                       images: farms.galleries[pin.osmId] ?? [],
                                       teaser: farms.featuredTeasers[pin.osmId],
                                       onOpen: { shell.openFarm(pin) })
                }
            }
            TripRecommendations(onOpenFarm: shell.openFarm)
                .padding(.top, Space.s4)
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

/// One farm as a row: cover or category glyph, name, city, distance.
struct FarmRow: View {
    let pin: FarmPin
    var km: Double?
    let action: () -> Void

    var body: some View {
        Button { Haptics.tap(); action() } label: {
            HStack(spacing: Space.s3) {
                ZStack {
                    Color.creamFill
                    if let url = pin.image.flatMap(URL.init) {
                        AsyncImage(url: url) { phase in
                            if case .success(let img) = phase { img.resizable().scaledToFill() }
                            else { Text(pin.primaryCategory.emoji).font(.system(size: 20)) }
                        }
                    } else {
                        Text(pin.primaryCategory.emoji).font(.system(size: 20))
                    }
                }
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(pin.name).role(.subheading).lineLimit(1)
                    HStack(spacing: 6) {
                        if let city = pin.city { Text(city) }
                        if let km { Text(verbatim: "· \(km.formatted(.number.precision(.fractionLength(1)))) km") }
                    }
                    .role(.caption, .inkMuted)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.inkMuted)
            }
            .padding(Space.s3)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
