import SwiftUI
import CoreLocation

/// Community — "Near you": what people reported and posted at farms around
/// you, newest first. A report card is one farm, one status, one day, with
/// how many people said so and what they found; "Confirm" adds your voice
/// with one tap. Plus sees minutes; free sees the day.
struct CommunityScreen: View {
    @Environment(FarmsStore.self) private var farms
    @Environment(SessionStore.self) private var session
    @Environment(LocationManager.self) private var locationManager
    @Environment(\.shell) private var shell
    @Environment(\.requestAuth) private var requestAuth
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0

    @State private var pings: [Ping] = []
    @State private var loading = true
    @State private var lightbox: LightboxSource?
    @State private var recent = RecentReports.shared
    @State private var confirming: String?

    /// One card per farm + status + Amsterdam day.
    struct ReportGroup: Identifiable {
        let farmOsmId: String
        let status: ReportStatus
        let day: String
        let newest: Date
        let count: Int
        let products: [String]
        var id: String { "\(farmOsmId)|\(status.rawValue)|\(day)" }
    }

    private enum Entry: Identifiable {
        case ping(Ping), report(ReportGroup)
        var id: String { switch self { case .ping(let p): "p" + p.id; case .report(let g): "r" + g.id } }
        var date: Date {
            switch self {
            case .ping(let p): FarmStatus.parseTimestamp(p.createdAt) ?? .distantPast
            case .report(let g): g.newest
            }
        }
    }

    private var pinIndex: [String: FarmPin] {
        Dictionary(farms.pins.map { ($0.osmId, $0) }, uniquingKeysWith: { a, _ in a })
    }

    private var groups: [ReportGroup] {
        // Everywhere when the phone has no location; the radius, widened to at
        // least 25 km, when it has — a report feed with nothing in it teaches
        // nobody to report.
        let near = recent.near(locationManager.location, radiusKm: max(radiusKm, 25), pins: pinIndex)
        var byKey: [String: [RecentReport]] = [:]
        for r in near {
            byKey["\(r.farmOsmId)|\(r.report.status.rawValue)|\(FarmStatus.amsterdamDay(r.report.createdAt))", default: []].append(r)
        }
        return byKey.values.map { rs in
            var seen = Set<String>()
            return ReportGroup(farmOsmId: rs[0].farmOsmId, status: rs[0].report.status,
                               day: FarmStatus.amsterdamDay(rs[0].report.createdAt),
                               newest: rs.map(\.report.createdAt).max() ?? .distantPast,
                               count: rs.count,
                               products: rs.flatMap(\.report.products).filter { seen.insert($0).inserted })
        }
    }

    private var feed: [Entry] {
        (pings.map(Entry.ping) + groups.map(Entry.report)).sorted { $0.date > $1.date }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Community"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                LazyVStack(alignment: .leading, spacing: Space.s2) {
                    SectionHeader(title: String(localized: "Near you"))
                        .padding(.top, 0)
                    if loading {
                        ForEach(0..<3, id: \.self) { _ in
                            SkeletonBox(cornerRadius: Radius.card).frame(height: 150)
                        }
                    } else if feed.isEmpty {
                        EmptyState(icon: "bubble.left.and.bubble.right",
                                   title: String(localized: "Nothing posted yet"),
                                   text: String(localized: "Visit a farm and tell people what you found."),
                                   action: (String(localized: "Open the map"), { shell.showTab(.map) }))
                    } else {
                        ForEach(feed) { entry in
                            switch entry {
                            case .ping(let ping): pingCard(ping)
                            case .report(let group): reportCard(group)
                            }
                        }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content)
            }
            .refreshable { await load(force: true) }
        }
        .background(Color.cream.ignoresSafeArea())
        .fullScreenCover(item: $lightbox) { src in
            ImageLightbox(source: src) {
                var t = Transaction(); t.disablesAnimations = true
                withTransaction(t) { lightbox = nil }
            }
            .presentationBackground(.clear)
        }
        .task { await load() }
    }

    // MARK: - Cards

    private func pingCard(_ ping: Ping) -> some View {
        PingCard(ping: ping,
                 farmName: farms.pin(forOsmId: ping.farmOsmId)?.name,
                 onOpenFarm: {
                     if let pin = farms.pin(forOsmId: ping.farmOsmId) { shell.openFarm(pin) }
                 },
                 onOpenImage: { idx in
                     var t = Transaction(); t.disablesAnimations = true
                     withTransaction(t) {
                         lightbox = LightboxSource(
                             images: ping.images, startIndex: idx,
                             eyebrow: String(localized: "From a post"),
                             title: ping.authorName,
                             subtitle: farms.pin(forOsmId: ping.farmOsmId)?.name,
                             postText: ping.body)
                     }
                 })
    }

    private func reportCard(_ g: ReportGroup) -> some View {
        let pin = pinIndex[g.farmOsmId]
        let catalogue = ShoppingItems.shared
        let found = g.products.compactMap { catalogue.item(id: $0)?.label }
        let dot: Color = g.status == .open ? .vividPositive : (g.status == .soldOut ? .vividWarning : .vividCritical)
        let verb = switch g.status {
        case .open: String(localized: "Open")
        case .closed: String(localized: "Closed")
        case .soldOut: String(localized: "Sold out")
        }
        let who = g.count == 1 ? String(localized: "1 person") : String(localized: "\(g.count) people")
        return VStack(alignment: .leading, spacing: Space.s2) {
            HStack(spacing: Space.s2) {
                Circle().fill(dot).frame(width: 10, height: 10)
                Text(verb).role(.subheading)
                Text(verbatim: "·").role(.caption, .inkFaint)
                Text(when(g.newest)).role(.caption, .inkMuted)
                Spacer()
                Text(who).role(.caption, .inkMuted)
            }
            Button {
                if let pin { Haptics.tap(); shell.openFarm(pin) }
            } label: {
                Text(pin?.name ?? g.farmOsmId).role(.body, .farmGreen).lineLimit(1)
            }
            .buttonStyle(.plain)
            if !found.isEmpty {
                Text("On the shelf: \(found.joined(separator: ", "))").role(.bodySm, .inkMuted)
            }
            HStack {
                Text("Visitor report").role(.caption, .inkFaint)
                Spacer()
                Button(confirming == g.id ? String(localized: "Confirmed") : String(localized: "Confirm")) {
                    Task { await confirm(g) }
                }
                .buttonStyle(PillButtonStyle(.soft, size: .small))
                .disabled(confirming == g.id)
            }
        }
        .card()
    }

    /// Plus: minutes and hours. Free: the day, which the summary already gives.
    private func when(_ date: Date) -> String {
        let mins = max(0, Int(Date().timeIntervalSince(date) / 60))
        if session.hasFullAccess {
            if mins < 60 { return String(localized: "\(mins) min ago") }
            if mins < 60 * 36 { return String(localized: "\(mins / 60) h ago") }
        }
        let days = mins / 1440
        if days == 0 { return String(localized: "today") }
        if days == 1 { return String(localized: "yesterday") }
        return String(localized: "\(days) days ago")
    }

    // MARK: - Data

    private func load(force: Bool = false) async {
        async let posts: [Ping] = (try? await supabase
            .from("farm_pings")
            .select("id, farm_osm_id, author_name, body, like_count, created_at, farm_ping_images(url, sort_order)")
            .eq("status", value: "visible")
            .order("created_at", ascending: false)
            .limit(30)
            .execute()
            .value) ?? []
        await recent.refresh(force: force)
        await ShoppingItems.shared.loadIfNeeded()
        pings = await posts
        loading = false
    }

    /// One tap agrees: the same status, on the same farm, from you. The server's
    /// one-per-person-per-day rule makes a second tap a correction, not spam.
    private func confirm(_ g: ReportGroup) async {
        guard let token = session.session?.accessToken else { requestAuth(); return }
        Haptics.success()
        if await FarmStatusAPI.report(osmId: g.farmOsmId, status: g.status, token: token) {
            confirming = g.id
            await recent.refresh(force: true)
        }
    }
}
