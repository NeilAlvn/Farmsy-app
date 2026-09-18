import SwiftUI
import CoreLocation

/// Shopping — "help me get my local groceries". The list is free. Which farms
/// answer it, and the route between them, is Farmsy Plus: that is the thing a
/// map cannot do, and the reason to pay.
///
/// The list itself is `TripStore.wantedProducts` (already persisted, already
/// what the trip planner's chips read), so nothing is stored twice.
struct ShoppingScreen: View {
    @Environment(SessionStore.self) private var session
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(TripStore.self) private var trip
    @Environment(\.shell) private var shell

    @AppStorage("searchRadiusKm") private var radiusKm = 15.0
    /// Previous lists, newest first, as JSON — small, local, and only ever
    /// written when a route is built from a list.
    @AppStorage("shoppingHistory") private var historyJSON = "[]"

    @State private var catalogue = ShoppingItems.shared
    @State private var plan: ShoppingPlanner.Plan?
    @State private var matchingFarms: Int?
    @State private var isPlanning = false
    /// The picker's search field. Typing filters the chips; Enter with no
    /// matching chip adds what was typed as a custom item.
    @State private var query = ""
    /// The stop a "Change farm" sheet is open for.
    @State private var swapping: ShoppingPlanner.Pick?

    private var picked: [ShoppingItem] { trip.wantedProducts.compactMap { catalogue.item(id: $0) } }
    private var origin: CLLocationCoordinate2D? { trip.originCoord ?? locationManager.location?.coordinate }
    private var history: [[String]] {
        (try? JSONDecoder().decode([[String]].self, from: Data(historyJSON.utf8))) ?? []
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Shopping"))
                .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    listSection
                    farmsSection
                    addSection
                    historySection
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, TabBarInset.content + (picked.isEmpty ? 0 : 72))
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .overlay(alignment: .bottom) { if !picked.isEmpty { actionBar } }
        .sheet(item: $swapping) { pick in
            swapSheet(pick)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(Radius.sheet)
        }
        .task { await catalogue.loadIfNeeded() }
        .task { await farms.loadFlagsIfNeeded() }
        .task(id: matchKey) { await countMatches() }
        .onChange(of: trip.wantedProducts) { _, _ in plan = nil }
    }

    // MARK: - The list

    @ViewBuilder
    private var listSection: some View {
        if picked.isEmpty {
            Text("What do you need this week?").role(.heading)
            Text("Add products below. Farmsy finds the farms that sell them and the shortest way round.")
                .role(.bodySm, .inkMuted)
                .padding(.top, Space.s1)
        } else {
            SectionHeader(title: String(localized: "This week's list"),
                          action: (String(localized: "Clear"), { trip.clearProducts() }))
                .padding(.top, 0)
            VStack(spacing: Space.s2) {
                ForEach(picked) { item in
                    HStack(spacing: Space.s3) {
                        Button { Haptics.tap(); if !item.isCustom { shell.openProduct(item.id) } } label: {
                            HStack(spacing: Space.s3) {
                                ProductImage(slug: item.imageSlug, fallback: item.emoji, size: 36, corner: 8)
                                Text(item.label).role(.body)
                            }
                        }
                        .buttonStyle(.plain)
                        Spacer()
                        Button {
                            Haptics.tap()
                            trip.toggleProduct(item.id)
                        } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.inkMuted)
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(String(localized: "Remove \(item.label)"))
                    }
                    .padding(.horizontal, Space.s4)
                    .padding(.vertical, Space.s2)
                    .frame(minHeight: 56)
                    .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                }
            }
        }
    }

    @ViewBuilder
    private var addSection: some View {
        SectionHeader(title: String(localized: "Add products"))
        SearchField(text: $query, placeholder: String(localized: "Search or type a product"), onSubmit: addTyped)
            .padding(.bottom, Space.s3)
        if catalogue.items.isEmpty {
            if catalogue.loadFailed {
                Text("The product list couldn't be loaded. Check your connection and try again.")
                    .role(.bodySm, .inkMuted)
            } else {
                SkeletonBox(cornerRadius: Radius.card).frame(height: 120)
            }
        } else {
            let q = ProductMatch.fold(query)
            let available = catalogue.items.filter { !trip.wantedProducts.contains($0.id) }
            let shown = q.isEmpty ? available
                : available.filter { ProductMatch.fold($0.label).contains(q) || $0.terms.contains { $0.contains(q) } }
            if !q.isEmpty && !shown.contains(where: { ProductMatch.fold($0.label) == q }) {
                // What was typed is not a chip: offer it as its own item.
                Chip(label: String(localized: "Add “\(query.trimmingCharacters(in: .whitespaces))”"),
                     icon: "plus", selected: true) { addTyped() }
                    .padding(.bottom, Space.s3)
            }
            if q.isEmpty, !catalogue.categories.isEmpty {
                ForEach(catalogue.categories) { cat in
                    let group = shown.filter { ($0.category ?? "other") == cat.id }
                    if !group.isEmpty {
                        Text(cat.label).role(.label, .inkFaint).textCase(.uppercase)
                            .padding(.top, Space.s3).padding(.bottom, Space.s2)
                        chips(group)
                    }
                }
            } else {
                chips(shown)
            }
        }
    }

    private func chips(_ items: [ShoppingItem]) -> some View {
        FlowRow(spacing: Space.s2) {
            ForEach(items) { item in
                Chip(label: item.label, emoji: item.emoji) { trip.toggleProduct(item.id) }
            }
        }
    }

    /// Enter in the search field: the one chip that matches, else a custom item.
    private func addTyped() {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let q = ProductMatch.fold(text)
        let exact = catalogue.items.first { ProductMatch.fold($0.label) == q && !trip.wantedProducts.contains($0.id) }
        Haptics.tap()
        trip.toggleProduct(exact?.id ?? ShoppingItem.custom(text).id)
        query = ""
    }

    // MARK: - The Plus bar

    /// One bar, one job at a time, pinned above the tab pill: first "Find
    /// farms", then "Build my route". Free users get the Plus sheet from either.
    private var actionBar: some View {
        let stops = plan?.picks.count ?? 0
        return Button {
            Haptics.tap()
            guard session.hasFullAccess else { shell.openPlus(); return }
            if let plan, !plan.isEmpty { buildRoute(plan) } else { findFarms() }
        } label: {
            HStack(spacing: Space.s2) {
                if isPlanning {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: !session.hasFullAccess ? "lock.fill" : (stops > 0 ? "car.fill" : "sparkles"))
                        .font(.system(size: 15, weight: .semibold))
                }
                if stops > 0 {
                    Text(String(localized: "Build my route · \(stops) stops"))
                } else {
                    Text(String(localized: "Find farms for my list"))
                }
                Spacer()
                Badge(text: "PLUS", fill: .vivid, ink: .ink)
            }
            .padding(.horizontal, Space.s5)
        }
        .buttonStyle(PillButtonStyle(.primary, size: .large, block: true))
        .disabled(isPlanning || origin == nil)
        .padding(.horizontal, Space.s4)
        .padding(.bottom, TabBarInset.height + 12 + 12)
        .shadow(color: Color.ink.opacity(0.14), radius: 16, y: 8)
    }

    // MARK: - Farms for the list

    private var matchKey: String {
        "\(trip.wantedProducts.joined(separator: ","))|\(origin?.latitude ?? 0),\(origin?.longitude ?? 0)|\(radiusKm)|\(farms.flagsLoaded)|\(farms.pins.count)"
    }

    /// The free half of the answer: how many farms in the radius sell any of it.
    private func countMatches() async {
        guard let origin, farms.flagsLoaded, !picked.isEmpty else { matchingFarms = nil; return }
        let terms = picked.flatMap(\.terms)
        let pins = farms.pins, produce = farms.produceByOsm, radius = radiusKm
        matchingFarms = await Task.detached(priority: .userInitiated) {
            let here = CLLocation(latitude: origin.latitude, longitude: origin.longitude)
            return pins.filter { pin in
                guard let sells = produce[pin.osmId], (pin.distance(from: here) ?? .infinity) / 1000 <= radius else { return false }
                return ProductMatch.covers(sells, terms: terms)
            }.count
        }.value
    }

    @ViewBuilder
    private var farmsSection: some View {
        if !picked.isEmpty {
            SectionHeader(title: String(localized: "Farms for your list"))
            if origin == nil {
                HStack(spacing: Space.s3) {
                    Image(systemName: "location.slash")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(Color.farmGreen)
                    Text("Allow location so Farmsy can look around you.").role(.bodySm, .inkMuted)
                    Spacer()
                    Button(String(localized: "Allow")) { Haptics.tap(); locationManager.request() }
                        .buttonStyle(PillButtonStyle(.primary, size: .small))
                }
                .card()
            } else if let n = matchingFarms {
                VStack(alignment: .leading, spacing: Space.s3) {
                    Text(n == 0
                         ? String(localized: "No farm within \(Int(radiusKm)) km lists any of this.")
                         : String(localized: "\(n) farms within \(Int(radiusKm)) km sell something on your list."))
                        .role(.body)
                    if n > 0 {
                        if session.hasFullAccess {
                            planView
                        } else {
                            Text("Farmsy Plus picks the fewest farms that cover your list and builds the trip. Tap the button below.")
                                .role(.bodySm, .inkMuted)
                        }
                    }
                }
                .card()
            } else {
                SkeletonBox(cornerRadius: Radius.card).frame(height: 88)
            }
        }
    }

    @ViewBuilder
    private var planView: some View {
        if let plan {
            if plan.isEmpty {
                Text("Nothing nearby covers the list well enough to plan a trip.").role(.bodySm, .inkMuted)
            } else {
                VStack(spacing: Space.s2) {
                    ForEach(Array(plan.picks.enumerated()), id: \.element.osmId) { i, pick in
                        pickRow(index: i, pick: pick)
                    }
                }
                if !plan.missing.isEmpty {
                    Text("Not found nearby: \(labels(plan.missing).joined(separator: ", "))")
                        .role(.caption, .inkMuted)
                }
            }
        } else {
            Text("Farmsy Plus picks the fewest farms that cover your list. Tap the button below.")
                .role(.bodySm, .inkMuted)
        }
    }

    private func pickRow(index: Int, pick: ShoppingPlanner.Pick) -> some View {
        let pin = farms.pin(forOsmId: pick.osmId)
        let km = pin?.distance(from: locationManager.location).map { $0 / 1000 }
        return VStack(alignment: .leading, spacing: Space.s2) {
            Button {
                Haptics.tap()
                if let pin { shell.openFarm(pin) }
            } label: {
                HStack(alignment: .top, spacing: Space.s3) {
                    Text(verbatim: "\(index + 1)")
                        .font(.ui(13, .bold))
                        .foregroundStyle(Color.ink)
                        .frame(width: 26, height: 26)
                        .background(Color.vivid, in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(pin?.name ?? pick.osmId).role(.subheading).lineLimit(1)
                        Text(labels(pick.covers).joined(separator: " · ")).role(.caption, .inkMuted).lineLimit(2)
                        HStack(spacing: Space.s2) {
                            Text(String(localized: "\(pick.covers.count) of \(picked.count) products")).role(.caption, .inkFaint)
                            if let km {
                                Text(verbatim: "· \(km.formatted(.number.precision(.fractionLength(1)))) km").role(.caption, .inkFaint)
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }
            }
            .buttonStyle(.plain)
            Button(String(localized: "Change farm")) { Haptics.tap(); swapping = pick }
                .buttonStyle(PillButtonStyle(.text, size: .small))
        }
        .padding(Space.s3)
        .background(Color.creamFill, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
    }

    /// Up to five other farms that could take this stop's place.
    private func swapSheet(_ pick: ShoppingPlanner.Pick) -> some View {
        let plan = self.plan ?? ShoppingPlanner.Plan(picks: [], missing: [])
        let options = origin.map {
            ShoppingPlanner.alternatives(to: pick, in: plan, wanted: picked, farms: candidates(),
                                         origin: $0, radiusKm: radiusKm)
        } ?? []
        return VStack(alignment: .leading, spacing: Space.s3) {
            Text(String(localized: "Change farm")).role(.heading).padding(.top, Space.s5)
            Text(String(localized: "Other farms nearby that sell what this stop is for."))
                .role(.bodySm, .inkMuted)
            if options.isEmpty {
                Text(String(localized: "No other farm within \(Int(radiusKm)) km sells these.")).role(.body, .inkMuted)
                    .padding(.top, Space.s4)
            }
            ScrollView(showsIndicators: false) {
                VStack(spacing: Space.s2) {
                    ForEach(options, id: \.osmId) { alt in
                        let pin = farms.pin(forOsmId: alt.osmId)
                        let km = pin?.distance(from: locationManager.location).map { $0 / 1000 }
                        Button {
                            Haptics.success()
                            self.plan = ShoppingPlanner.replacing(pick, with: alt, in: plan)
                            swapping = nil
                        } label: {
                            HStack(spacing: Space.s3) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(pin?.name ?? alt.osmId).role(.subheading).lineLimit(1)
                                    Text(labels(alt.covers).joined(separator: " · ")).role(.caption, .inkMuted).lineLimit(2)
                                }
                                Spacer()
                                if let km {
                                    Text(verbatim: "\(km.formatted(.number.precision(.fractionLength(1)))) km").role(.caption, .inkFaint)
                                }
                                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.inkMuted)
                            }
                            .padding(Space.s4)
                            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, Space.s5)
        .background(Color.cream.ignoresSafeArea())
    }

    private func candidates() -> [ShoppingPlanner.Candidate] {
        farms.pins.map {
            ShoppingPlanner.Candidate(osmId: $0.osmId, coord: $0.coordinate, sells: farms.produceByOsm[$0.osmId] ?? "")
        }
    }

    private func labels(_ ids: [String]) -> [String] { ids.map { catalogue.item(id: $0)?.label ?? $0 } }

    private func findFarms() {
        guard let origin else { return }
        isPlanning = true
        Task {
            await farms.loadFlagsIfNeeded()
            plan = ShoppingPlanner.plan(wanted: picked, farms: candidates(), origin: origin, radiusKm: radiusKm)
            isPlanning = false
        }
    }

    /// The picks become trip stops; the planner orders them and draws the road.
    private func buildRoute(_ plan: ShoppingPlanner.Plan) {
        remember(trip.wantedProducts)
        trip.addStops(plan.picks.map(\.osmId))
        trip.requestFit()
        shell.openTrips()
    }

    // MARK: - History

    private func remember(_ list: [String]) {
        var all = history.filter { $0 != list }
        all.insert(list, at: 0)
        if let data = try? JSONEncoder().encode(Array(all.prefix(5))) {
            historyJSON = String(decoding: data, as: UTF8.self)
        }
    }

    @ViewBuilder
    private var historySection: some View {
        let previous = history.filter { $0 != trip.wantedProducts }
        if !previous.isEmpty {
            SectionHeader(title: String(localized: "Previous lists"))
            VStack(spacing: Space.s2) {
                ForEach(Array(previous.enumerated()), id: \.offset) { _, list in
                    HStack(spacing: Space.s3) {
                        Text(labels(list).joined(separator: ", ")).role(.bodySm).lineLimit(2)
                        Spacer()
                        Button(String(localized: "Repeat")) {
                            Haptics.tap()
                            trip.clearProducts()
                            for id in list { trip.toggleProduct(id) }
                        }
                        .buttonStyle(PillButtonStyle(.soft, size: .small))
                    }
                    .padding(Space.s4)
                    .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                }
            }
        }
    }
}
