import SwiftUI

/// The year as a vertical rail of twelve months, the current one large and
/// green, the past dimmed, the future plain. Nothing is locked: a month is a
/// page you can read any time, which is the point of a calendar.
///
/// The rail is Cropsy's timeline (a straight rail, a circle per node, a
/// connector that stretches to the row's height), not a winding path: every
/// node is one tap and the thumb never has to hunt.
struct SeasonRail: View {
    @Binding var openMonth: Int?
    @State private var seasons = Seasons.shared

    private var months: [Int] { Array(1...12) }

    var body: some View {
        if seasons.loadFailed && !seasons.loaded {
            EmptyState(icon: "wifi.slash", title: String(localized: "The calendar didn't load"),
                       text: String(localized: "Check your connection and try again."),
                       action: (String(localized: "Try again"), { Task { await seasons.loadIfNeeded() } }))
        } else if !seasons.loaded {
            ForEach(0..<4, id: \.self) { _ in SkeletonBox(cornerRadius: Radius.card).frame(height: 84) }
        } else {
            Text(String(localized: "What is ripe, month by month, and what to make with it. Tap a month."))
                .role(.bodySm, .inkMuted)
                .padding(.bottom, Space.s2)
            VStack(spacing: 0) {
                ForEach(months, id: \.self) { m in
                    node(m)
                }
            }
        }
    }

    private func node(_ m: Int) -> some View {
        let now = seasons.month
        let state: NodeState = m == now ? .current : (m < now ? .past : .future)
        let items = seasons.items(in: m)
        let peak = items.filter { $0.isPeak(month: m) }
        let ideas = seasons.ideas(in: m)
        return Button {
            Haptics.tap()
            openMonth = m
        } label: {
            HStack(alignment: .top, spacing: Space.s3) {
                // The rail: circle, then a connector that fills the row.
                VStack(spacing: 0) {
                    ZStack {
                        Circle()
                            .fill(state == .current ? Color.vivid : (state == .past ? Color.creamFill : Color.surface))
                            .overlay(Circle().stroke(state == .future ? Color.hairline : Color.clear, lineWidth: 1))
                        Text(monthShort(m))
                            .font(.ui(state == .current ? 15 : 12, .bold))
                            .foregroundStyle(state == .past ? Color.inkFaint : Color.ink)
                    }
                    .frame(width: state == .current ? 56 : 44, height: state == .current ? 56 : 44)
                    if m < 12 {
                        Rectangle().fill(Color.hairline).frame(width: 3).frame(maxHeight: .infinity)
                    }
                }
                .frame(width: 56)

                VStack(alignment: .leading, spacing: Space.s2) {
                    HStack(spacing: Space.s2) {
                        Text(monthName(m)).role(state == .current ? .heading : .subheading,
                                                 state == .past ? .inkMuted : .ink)
                        if state == .current {
                            Badge(text: String(localized: "NOW"), fill: .vivid, ink: .ink)
                        }
                    }
                    Text(items.isEmpty
                         ? String(localized: "Stored crops and the greenhouse.")
                         : String(localized: "\(items.count) in season · \(ideas.count) ideas"))
                        .role(.caption, .inkMuted)
                    if !peak.isEmpty || !items.isEmpty {
                        HStack(spacing: Space.s2) {
                            ForEach((peak.isEmpty ? items : peak).prefix(5)) { item in
                                ProductImage(slug: item.imageSlug, fallback: item.emoji, size: 40, corner: 10)
                            }
                        }
                        .opacity(state == .past ? 0.55 : 1)
                    }
                }
                .padding(.bottom, Space.s5)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .fixedSize(horizontal: false, vertical: true)
    }

    private enum NodeState { case past, current, future }

    static func monthName(_ m: Int) -> String {
        let f = DateFormatter()
        f.locale = Locale.current
        return f.standaloneMonthSymbols[m - 1].capitalized
    }
    private func monthName(_ m: Int) -> String { Self.monthName(m) }
    private func monthShort(_ m: Int) -> String {
        let f = DateFormatter(); f.locale = Locale.current
        return String(f.shortStandaloneMonthSymbols[m - 1].prefix(3)).capitalized
    }
}

/// One month: what is ripe (peak first), then five things to make, each with
/// one button that puts its ingredients on the shopping list.
struct MonthSheet: View {
    let month: Int
    @Environment(\.dismiss) private var dismiss
    @Environment(\.shell) private var shell
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var seasons = Seasons.shared
    @State private var openProduct: SeasonalItem?

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(SeasonRail.monthName(month), compact: true, onBack: { dismiss() })
                .padding(.top, Space.s3)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: Space.s2) {
                    let items = seasons.items(in: month)
                    SectionHeader(title: String(localized: "In season")).padding(.top, 0)
                    if items.isEmpty {
                        Text(String(localized: "Little grows outdoors this month. Farm shops sell what was stored: potatoes, onions, beets, apples, and the cheese and eggs that never stop."))
                            .role(.bodySm, .inkMuted)
                    }
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: Space.s3) {
                        ForEach(items) { item in
                            Button {
                                Haptics.tap()
                                openProduct = item
                            } label: {
                                VStack(spacing: Space.s2) {
                                    ZStack(alignment: .topTrailing) {
                                        ProductImage(slug: item.imageSlug, fallback: item.emoji, size: 96, corner: Radius.tile)
                                        if item.isPeak(month: month) {
                                            Badge(text: String(localized: "PEAK"), fill: .vivid, ink: .ink).padding(6)
                                        }
                                    }
                                    Text(item.label).role(.caption).lineLimit(1)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    let ideas = seasons.ideas(in: month)
                    if !ideas.isEmpty {
                        SectionHeader(title: String(localized: "What to make with it"))
                        CardCarousel(items: ideas) { idea in
                            IdeaCard(kicker: nil, title: idea.title.text, text: idea.body.text,
                                     image: idea.image, fallback: "🍽️", ingredients: idea.ingredients)
                        }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s8)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .sheet(item: $openProduct) { item in
            ProductSheet(slug: item.slug, fallbackLabel: item.label, fallbackEmoji: item.emoji)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
    }
}

/// Full-width cards that snap one per page, with dots. The Nime "For you"
/// carousel.
struct CardCarousel<Item: Identifiable, Content: View>: View {
    let items: [Item]
    @ViewBuilder let content: (Item) -> Content
    @State private var page: Int = 0

    var body: some View {
        VStack(spacing: Space.s3) {
            GeometryReader { geo in
                let w = geo.size.width
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: Space.s3) {
                        ForEach(items) { item in
                            content(item).frame(width: w)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.viewAligned)
                .scrollPosition(id: Binding(
                    get: { items.indices.contains(page) ? items[page].id : nil },
                    set: { id in if let i = items.firstIndex(where: { $0.id == id }) { page = i } }))
            }
            .frame(height: 380)
            if items.count > 1 {
                HStack(spacing: 6) {
                    ForEach(items.indices, id: \.self) { i in
                        Circle().fill(i == page ? Color.ink : Color.hairline).frame(width: 6, height: 6)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

/// Image, kicker, title, body, and one button that puts the ingredients on
/// the shopping list. Shared by the season ideas and the tips.
struct IdeaCard: View {
    let kicker: String?
    let title: String
    let text: String
    let image: String
    /// Shown when `image` is not bundled (a recipe picture not generated yet).
    var fallbackImage: String? = nil
    let fallback: String
    let ingredients: [String]

    @Environment(TripStore.self) private var trip
    @Environment(\.shell) private var shell
    @State private var catalogue = ShoppingItems.shared
    @State private var seasons = Seasons.shared
    @State private var added = false

    /// Shopping ids the ingredients resolve to: a shopping id as itself, a
    /// seasonal slug through the shopping item that shares its photograph,
    /// else a custom item named after it.
    private var listIds: [String] {
        ingredients.map { ing in
            if catalogue.item(id: ing) != nil { return ing }
            if let season = seasons.items.first(where: { $0.slug == ing }) {
                if let twin = catalogue.items.first(where: { $0.imageSlug == season.imageSlug }) { return twin.id }
                return ShoppingItem.custom(season.label).id
            }
            return ShoppingItem.custom(ing).id
        }
    }

    private var allOnList: Bool { !listIds.isEmpty && listIds.allSatisfy { trip.wantedProducts.contains($0) } }

    var body: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            ProductImage(slug: image, fallbackSlug: fallbackImage, fallback: fallback, size: 160, corner: Radius.tile)
                .frame(maxWidth: .infinity)
            if let kicker {
                Text(kicker).role(.label, .farmGreen).textCase(.uppercase)
            }
            Text(title).role(.heading).lineLimit(2).fixedSize(horizontal: false, vertical: true)
            Text(text).role(.bodySm, .inkMuted).lineLimit(4).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            if !ingredients.isEmpty {
                Button {
                    Haptics.success()
                    for id in listIds where !trip.wantedProducts.contains(id) { trip.toggleProduct(id) }
                    added = true
                } label: {
                    Label(allOnList || added ? String(localized: "On your list") : String(localized: "Put it on my list"),
                          systemImage: allOnList || added ? "checkmark" : "basket")
                }
                .buttonStyle(PillButtonStyle(allOnList || added ? .soft : .primary, size: .small))
                .disabled(allOnList || added)
            }
        }
        .padding(Space.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }
}
