import SwiftUI
import CoreLocation

/// One product, as a page: its photograph, the year bar (Nime's score bar,
/// turned into twelve months), how to choose one at the farm, how to keep and
/// preserve it, five things to make with it, grandmother's tips, and the two
/// actions that lead back into the app: find it nearby, put it on the list.
///
/// Opened from anywhere a product is named: Home tiles, the month page,
/// shopping rows, Discover. Takes a shopping id or a seasonal slug; the
/// profile store resolves either.
struct ProductSheet: View {
    let slug: String
    /// The slug on screen; a "goes with" chip swaps it in place rather than
    /// stacking a second sheet.
    @State private var current: String = ""
    /// What to show while the profiles load or when the slug has none.
    var fallbackLabel: String = ""
    var fallbackEmoji: String = "🌱"

    @Environment(\.dismiss) private var dismiss
    @Environment(\.shell) private var shell
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(TripStore.self) private var trip
    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    @State private var products = Products.shared
    @State private var catalogue = ShoppingItems.shared
    @State private var seasons = Seasons.shared

    private var shown: String { current.isEmpty ? slug : current }
    private var profile: ProductProfile? { products.profile(for: shown) }
    private var month: Int { Calendar.current.component(.month, from: Date()) }

    /// The shopping id this product goes on the list as.
    private var listId: String? {
        if let p = profile {
            if let s = p.shopping { return s }
            return ShoppingItem.custom(p.name).id
        }
        if catalogue.item(id: shown) != nil { return shown }
        return nil
    }
    private var onList: Bool { listId.map { trip.wantedProducts.contains($0) } ?? false }

    private var terms: [String] {
        if let s = profile?.shopping ?? (catalogue.item(id: shown) != nil ? shown : nil), let item = catalogue.item(id: s) { return item.terms }
        if let item = seasons.items.first(where: { $0.slug == (profile?.seasonal ?? shown) }) { return item.terms }
        return [ProductMatch.fold(profile?.name ?? fallbackLabel)]
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(profile?.name ?? fallbackLabel, compact: true, onBack: { dismiss() })
                .padding(.top, Space.s3)
            ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: Space.s3) {
                    ProductImage(slug: profile?.image ?? shown, fallback: fallbackEmoji, size: 200, corner: Radius.card)
                        .frame(maxWidth: .infinity)
                        .id("top")
                    if let p = profile {
                        yearBar(p)
                        if !p.regionNote.isEmpty { note(p.regionNote, icon: "map") }
                        if !p.greenhouseNote.isEmpty { note(p.greenhouseNote, icon: "leaf") }
                        actions
                        section(String(localized: "How to choose")) {
                            ForEach(p.choose, id: \.self) { line in bullet(line) }
                        }
                        section(String(localized: "Keeping")) {
                            Text(p.store.place).role(.body)
                            Text(p.store.how).role(.bodySm, .inkMuted)
                            if p.store.days > 0 {
                                Text(String(localized: "About \(p.store.days) days")).role(.caption, .inkFaint)
                            }
                        }
                        if !p.preserve.isEmpty {
                            section(String(localized: "Preserving")) {
                                ForEach(p.preserve) { x in
                                    HStack(alignment: .top, spacing: Space.s2) {
                                        Text(x.method.capitalized).role(.subheading).frame(width: 96, alignment: .leading)
                                        Text(x.how).role(.bodySm, .inkMuted)
                                    }
                                }
                            }
                        }
                        if !p.ideas.isEmpty {
                            SectionHeader(title: String(localized: "What to make with it"))
                            CardCarousel(items: p.ideas) { idea in
                                IdeaCard(kicker: nil, title: idea.title, text: idea.body,
                                         image: idea.image, fallbackImage: p.image, fallback: "🍽️",
                                         ingredients: idea.ingredients)
                            }
                        }
                        if !p.tips.isEmpty {
                            section(String(localized: "Grandmother's tip")) {
                                ForEach(p.tips, id: \.self) { t in bullet(t) }
                            }
                        }
                        if !p.pairs.isEmpty {
                            section(String(localized: "Goes with")) {
                                FlowRow(spacing: Space.s2) {
                                    ForEach(p.pairs, id: \.self) { pr in
                                        let twin = products.profile(for: pr)
                                        Chip(label: twin?.name ?? catalogue.item(id: pr)?.label ?? pr,
                                             emoji: catalogue.item(id: pr)?.emoji) {
                                            Haptics.tap()
                                            current = pr
                                        }
                                    }
                                }
                            }
                        }
                        if !p.funFact.isEmpty { note(p.funFact, icon: "sparkles").padding(.top, Space.s2) }
                    } else if products.loaded {
                        actions
                        Text(String(localized: "No profile for this product yet."))
                            .role(.bodySm, .inkMuted)
                    } else {
                        ForEach(0..<3, id: \.self) { _ in SkeletonBox(cornerRadius: Radius.card).frame(height: 80) }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s8)
            }
            .onChange(of: current) { withAnimation { proxy.scrollTo("top", anchor: .top) } }
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await products.loadIfNeeded() }
        .task { await catalogue.loadIfNeeded() }
        .task { await seasons.loadIfNeeded() }
    }

    // MARK: - Pieces

    /// Twelve cells. Peak vivid, available soft green, none tile; the current
    /// month ringed so "now" reads at a glance.
    private func yearBar(_ p: ProductProfile) -> some View {
        let letters = DateFormatter().veryShortStandaloneMonthSymbols ?? ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]
        return VStack(alignment: .leading, spacing: Space.s2) {
            HStack(spacing: 4) {
                ForEach(1...12, id: \.self) { m in
                    let s = p.state(in: m)
                    Text(letters[m - 1])
                        .font(.ui(11, s == .peak ? .bold : .medium))
                        .foregroundStyle(s == .none ? Color.inkFaint : Color.ink)
                        .frame(maxWidth: .infinity, minHeight: 28)
                        .background(s == .peak ? Color.vivid : (s == .available ? Color.farmGreenSoft : Color.creamFill),
                                    in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color.ink, lineWidth: m == month ? 2 : 0))
                }
            }
            HStack(spacing: Space.s3) {
                legend(Color.vivid, String(localized: "Peak"))
                legend(Color.farmGreenSoft, String(localized: "In season"))
                Spacer()
                let now = p.state(in: month)
                Text(now == .peak ? String(localized: "At its best now") : now == .available ? String(localized: "In season now") : String(localized: "Not in season now"))
                    .role(.caption, now == .none ? .inkMuted : .positive)
            }
        }
        .card(padding: Space.s4)
    }

    private func legend(_ c: Color, _ t: String) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 12, height: 12)
            Text(t).role(.caption, .inkMuted)
        }
    }

    private var actions: some View {
        HStack(spacing: Space.s2) {
            Button {
                Haptics.tap()
                Task {
                    await farms.showProduct(label: profile?.name ?? fallbackLabel, terms: terms,
                                            userLocation: locationManager.location, radiusKm: radiusKm)
                    dismiss()
                    shell.showTab(.map)
                }
            } label: { Label(String(localized: "Find nearby"), systemImage: "mappin.and.ellipse") }
            .buttonStyle(PillButtonStyle(.primary, size: .medium, block: true))
            if let listId {
                Button {
                    Haptics.success()
                    if !onList { trip.toggleProduct(listId) }
                } label: { Label(onList ? String(localized: "On your list") : String(localized: "Add to list"), systemImage: onList ? "checkmark" : "basket") }
                .buttonStyle(PillButtonStyle(onList ? .soft : .secondary, size: .medium))
                .disabled(onList)
            }
        }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: Space.s2) {
            Text(title).role(.label, .inkFaint).textCase(.uppercase)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(padding: Space.s4)
    }

    private func bullet(_ line: String) -> some View {
        HStack(alignment: .top, spacing: Space.s2) {
            Circle().fill(Color.farmGreen).frame(width: 6, height: 6).padding(.top, 8)
            Text(line).role(.bodySm).fixedSize(horizontal: false, vertical: true)
        }
    }

    private func note(_ text: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: Space.s2) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.farmGreen).padding(.top, 2)
            Text(text).role(.caption, .inkMuted).fixedSize(horizontal: false, vertical: true)
        }
    }
}
