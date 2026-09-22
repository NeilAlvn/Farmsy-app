import SwiftUI
import CoreLocation

/// Home — "what should I know today?" Personal, not a directory: what is
/// available near you, the farms you follow, and what Plus would tell you.
/// Profile lives behind the person button in the header.
struct HomeScreen: View {
    @Environment(SessionStore.self) private var session
    @Environment(FarmsStore.self) private var farms
    @Environment(FavoritesStore.self) private var favorites
    @Environment(LocationManager.self) private var locationManager
    @Environment(\.shell) private var shell
    @Environment(\.requestAuth) private var requestAuth

    /// How far "near you" reaches. Shared with Shopping and the map's product tap.
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    static let radiusChoices: [Double] = [5, 10, 15, 25, 50]

    @State private var catalogue = ShoppingItems.shared
    @State private var seasons = Seasons.shared
    @State private var recent = RecentReports.shared
    @State private var query = ""
    @State private var nearby: [ProductNearby] = []
    @State private var nearbyReady = false

    private var location: CLLocation? { locationManager.location }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let name = session.profile?.firstName?.trimmingCharacters(in: .whitespaces) ?? ""
        let base = switch hour {
        case 5..<12: String(localized: "Good morning")
        case 12..<18: String(localized: "Good afternoon")
        default: String(localized: "Good evening")
        }
        return name.isEmpty ? base : "\(base), \(name)"
    }

    private var savedPins: [FarmPin] {
        farms.sortedByDistance(farms.pins.filter { favorites.isSaved($0.osmId) }, from: location)
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                band
                VStack(alignment: .leading, spacing: 0) {
                    greetingCard.padding(.top, -Space.s2)
                    if !session.hasFullAccess { plusRow }
                    availableSection
                    routeCard
                    thisWeekSection
                    yourFarmsSection
                    forYouSection
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .ignoresSafeArea(edges: .top)
        .task { await catalogue.loadIfNeeded() }
        .task { await seasons.loadIfNeeded() }
        .task { await farms.loadFlagsIfNeeded() }
        .task(id: session.hasFullAccess) { if session.hasFullAccess { await recent.refresh() } }
        .task(id: nearbyKey) { await computeNearby() }
        .onAppear { if location == nil { locationManager.request() } }
    }

    // MARK: - Header band

    private var band: some View {
        ZStack(alignment: .top) {
            AtmosphereBand()
            HStack {
                IconButton("person", label: String(localized: "Profile"), overMedia: true) {
                    shell.openProfile()
                }
                Spacer()
                Wordmark(onAtmosphere: true)
                Spacer()
                Menu {
                    ForEach(Self.radiusChoices, id: \.self) { km in
                        Button {
                            radiusKm = km
                        } label: {
                            if km == radiusKm {
                                Label("\(Int(km)) km", systemImage: "checkmark")
                            } else {
                                Text("\(Int(km)) km")
                            }
                        }
                    }
                } label: {
                    Image(systemName: "location")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(Color.ink)
                        .frame(width: 44, height: 44)
                        .background(Color.white.opacity(0.92), in: Circle())
                        .shadow(color: Color.ink.opacity(0.10), radius: 12, y: 8)
                }
                .accessibilityLabel(String(localized: "Search radius"))
            }
            .padding(.horizontal, Space.s4)
            .padding(.top, 56)
        }
        .frame(height: 176)
    }

    // MARK: - Greeting

    private var greetingCard: some View {
        VStack(alignment: .leading, spacing: Space.s4) {
            VStack(alignment: .leading, spacing: Space.s1) {
                Text(greeting).role(.heading)
                Text(location == nil
                     ? String(localized: "Local food near you")
                     : String(localized: "Local food within \(Int(radiusKm)) km"))
                    .role(.bodySm, .inkMuted)
            }
            SearchField(text: $query, placeholder: String(localized: "Search farms or products")) {
                let q = query.trimmingCharacters(in: .whitespaces)
                guard !q.isEmpty else { return }
                farms.searchText = q
                shell.showTab(.map)
            }
            Button {
                Haptics.tap()
                farms.clearAllFilters()
                farms.filterOpenNow = true
                shell.showTab(.map)
            } label: {
                Label(String(localized: "Open now near me"), systemImage: "clock")
            }
            .buttonStyle(PillButtonStyle(.primary, size: .medium, block: true))
        }
        .card()
    }

    // MARK: - Plus

    /// The one obvious way in for a free member, right under the greeting. The
    /// lock card at the bottom sells a feature; this sells the membership.
    private var plusRow: some View {
        Button { Haptics.tap(); shell.openPlus(.homeRow) } label: {
            HStack(spacing: Space.s3) {
                Image(systemName: "sparkles")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Color.farmGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Upgrade to Farmsy Plus").role(.subheading)
                    Text("Farmsy finds it, plans the route and tells you when it is fresh.").role(.caption, .inkMuted)
                }
                Spacer()
                Badge(text: "PLUS", fill: .vivid, ink: .ink)
            }
        }
        .buttonStyle(.plain)
        .card()
        .padding(.top, Space.s3)
    }

    // MARK: - Available near you

    private var nearbyKey: String {
        "\(location?.coordinate.latitude ?? 0),\(location?.coordinate.longitude ?? 0),\(radiusKm),\(catalogue.items.count),\(farms.flagsLoaded),\(farms.pins.count)"
    }

    private func computeNearby() async {
        guard let loc = location, farms.flagsLoaded, !catalogue.items.isEmpty, !farms.pins.isEmpty else { return }
        let items = catalogue.items, pins = farms.pins, produce = farms.produceByOsm
        let origin = loc.coordinate, radius = radiusKm
        nearby = await Task.detached(priority: .userInitiated) {
            FarmsStore.productsNearby(items: items, pins: pins, produce: produce, origin: origin, radiusKm: radius)
        }.value
        nearbyReady = true
    }

    @ViewBuilder
    private var availableSection: some View {
        SectionHeader(title: String(localized: "Available near you"),
                      action: (String(localized: "Shopping"), { shell.showTab(.shopping) }))
        if location == nil {
            locationCard
        } else if !nearbyReady {
            HStack(spacing: Space.s3) {
                ForEach(0..<3, id: \.self) { _ in
                    SkeletonBox(cornerRadius: Radius.tile).frame(width: 172, height: 120)
                }
            }
        } else if nearby.isEmpty {
            Text("No farm within \(Int(radiusKm)) km lists what it sells yet. Widen the radius from the header.")
                .role(.bodySm, .inkMuted)
                .card()
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s3) {
                    ForEach(nearby.prefix(10)) { p in
                        productTile(p)
                    }
                }
                .padding(.trailing, Space.s4)
            }
            .padding(.trailing, -Space.s4)
        }
    }

    /// Plus: the newest "open" report within the radius that names this
    /// product, as "confirmed 18 min ago". Free tiles say nothing about timing.
    private func confirmedLine(_ itemId: String) -> String? {
        guard session.hasFullAccess, let location else { return nil }
        let hit = recent.near(location, radiusKm: radiusKm, pins: farms.pinsById)
            .filter { $0.report.status == .open && $0.report.products.contains(itemId) }
            .max { $0.report.createdAt < $1.report.createdAt }
        guard let hit else { return nil }
        let mins = max(0, Int(Date().timeIntervalSince(hit.report.createdAt) / 60))
        if mins < 60 { return String(localized: "Confirmed \(mins) min ago") }
        if mins < 60 * 36 { return String(localized: "Confirmed \(mins / 60) h ago") }
        return String(localized: "Confirmed \(mins / 1440) d ago")
    }

    private func productTile(_ p: ProductNearby) -> some View {
        Button {
            Haptics.tap()
            shell.openProduct(p.item.id)
        } label: {
            VStack(alignment: .leading, spacing: Space.s2) {
                ProductImage(slug: p.item.imageSlug, fallback: p.item.emoji, size: 64)
                Text(p.item.label).role(.subheading).lineLimit(1)
                Text("\(p.count) farms · \(p.nearestKm.formatted(.number.precision(.fractionLength(1)))) km")
                    .role(.caption, .inkMuted)
                    .lineLimit(1)
                if let line = confirmedLine(p.item.id) {
                    HStack(spacing: 4) {
                        Circle().fill(Color.vividPositive).frame(width: 6, height: 6)
                        Text(line).role(.caption, .positive).lineLimit(1)
                    }
                }
            }
            .frame(width: 156, alignment: .leading)
            .padding(Space.s4)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var locationCard: some View {
        HStack(spacing: Space.s3) {
            Image(systemName: "location.slash")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(Color.farmGreen)
            VStack(alignment: .leading, spacing: 2) {
                Text("Where are you?").role(.subheading)
                Text("Allow location to see what is for sale around you.").role(.caption, .inkMuted)
            }
            Spacer()
            Button(String(localized: "Allow")) { Haptics.tap(); locationManager.request() }
                .buttonStyle(PillButtonStyle(.primary, size: .small))
        }
        .card()
    }

    // MARK: - Route planner entry

    /// A second way into the route planner — the only other entrance is Shopping →
    /// "Build my route", easy to miss. Sits right under what's for sale nearby.
    private var routeCard: some View {
        Button { Haptics.tap(); shell.openTrips() } label: {
            HStack(spacing: Space.s4) {
                Image(systemName: "car.fill").font(.system(size: 20, weight: .semibold)).foregroundStyle(Color.farmGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Plan a farm route").role(.subheading)
                    Text("Pick your stops, Farmsy orders them and draws the road.").role(.caption, .inkMuted)
                }
                Spacer()
                if !session.hasFullAccess { Badge(text: "PLUS", fill: .vivid, ink: .ink) }
            }
        }
        .buttonStyle(.plain)
        .card()
    }

    // MARK: - This week

    /// Season news. Renders nothing until the calendar has loaded, so a missing
    /// endpoint costs a section, not a screen.
    @ViewBuilder
    private var thisWeekSection: some View {
        let picks = Array(seasons.thisMonth.prefix(6))
        if !picks.isEmpty {
            SectionHeader(title: String(localized: "In season now"),
                          action: (String(localized: "See all"), { shell.showTab(.discover) }))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Space.s3) {
                    ForEach(picks) { item in
                        seasonTile(item)
                    }
                }
                .padding(.trailing, Space.s4)
            }
            .padding(.trailing, -Space.s4)
        }
    }

    private func seasonTile(_ item: SeasonalItem) -> some View {
        Button {
            Haptics.tap()
            shell.openProduct(item.slug)
        } label: {
            VStack(alignment: .leading, spacing: Space.s2) {
                ZStack(alignment: .topTrailing) {
                    ProductImage(slug: item.imageSlug, fallback: item.emoji, size: 124, corner: Radius.tile)
                    if item.isPeak(month: seasons.month) {
                        Badge(text: String(localized: "PEAK"), fill: .vivid, ink: .ink).padding(Space.s2)
                    }
                }
                Text(item.label).role(.subheading).lineLimit(1)
                Text(String(localized: "Find it nearby")).role(.caption, .inkMuted)
            }
            .frame(width: 124, alignment: .leading)
            .padding(Space.s3)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Your farms

    @ViewBuilder
    private var yourFarmsSection: some View {
        SectionHeader(title: String(localized: "Your farms"),
                      action: session.isAuthenticated && savedPins.count > 3
                          ? (String(localized: "See all"), { shell.showTab(.map) }) : nil)
        if !session.isAuthenticated {
            HStack(spacing: Space.s3) {
                Image(systemName: "heart")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(Color.farmGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Keep your favourites").role(.subheading)
                    Text("Sign in to follow farms and see them here.").role(.caption, .inkMuted)
                }
                Spacer()
                Button(String(localized: "Sign in")) { Haptics.tap(); requestAuth() }
                    .buttonStyle(PillButtonStyle(.primary, size: .small))
            }
            .card()
        } else if savedPins.isEmpty {
            HStack(spacing: Space.s3) {
                Image(systemName: "heart")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(Color.farmGreen)
                VStack(alignment: .leading, spacing: 2) {
                    Text("No farms followed yet").role(.subheading)
                    Text("Tap the heart on a farm to see it here, with today's opening.").role(.caption, .inkMuted)
                }
                Spacer()
            }
            .card()
        } else {
            VStack(spacing: Space.s2) {
                ForEach(savedPins.prefix(3)) { pin in
                    farmRow(pin)
                }
            }
        }
    }

    private func farmRow(_ pin: FarmPin) -> some View {
        let open = FarmFilters.isOpenToday(pin.openingHours)
        let km = pin.distance(from: location).map { $0 / 1000 }
        return Button {
            Haptics.tap()
            shell.openFarm(pin)
        } label: {
            HStack(spacing: Space.s3) {
                Text(pin.primaryCategory.emoji)
                    .font(.system(size: 22))
                    .frame(width: 48, height: 48)
                    .background(Color.creamFill, in: RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(pin.name).role(.subheading).lineLimit(1)
                    HStack(spacing: 6) {
                        Circle().fill(open ? Color.vividPositive : Color.hairline).frame(width: 8, height: 8)
                        Text(open ? String(localized: "Open today") : String(localized: "Closed today"))
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

    // MARK: - For you

    @ViewBuilder
    private var forYouSection: some View {
        SectionHeader(title: String(localized: "For you"))
        if session.hasFullAccess {
            Row(icon: "bell", title: String(localized: "Product alerts"),
                subtitle: String(localized: "Follow a product and hear when it turns up nearby")) {
                if let url = URL(string: "https://www.farmsy.app/alerts") { UIApplication.shared.open(url) }
            }
            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        } else {
            PlusLockCard(title: String(localized: "Alerts and live availability"),
                         text: String(localized: "Hear when strawberries turn up within your radius, and see how recently a farm was confirmed open."),
                         onUnlock: { shell.openPlus(.homeCard) })
        }
    }
}
