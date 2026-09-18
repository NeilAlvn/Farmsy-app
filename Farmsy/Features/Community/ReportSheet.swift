import SwiftUI
import CoreLocation

/// Report without opening a farm card first: pick a farm near you, tap what
/// you found, tag what was on the shelf. Same row, same one-per-person-per-day
/// rule as the card's own section; this is only a shorter way in.
struct ReportSheet: View {
    var onSent: () async -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(FarmsStore.self) private var farms
    @Environment(LocationManager.self) private var locationManager
    @Environment(SessionStore.self) private var session
    @State private var catalogue = ShoppingItems.shared
    @State private var query = ""
    @State private var chosen: FarmPin?
    @State private var status: ReportStatus?
    @State private var products: Set<String> = []
    @State private var sending = false
    @State private var sent = false

    private var nearby: [FarmPin] {
        let q = ProductMatch.fold(query)
        let pool = q.isEmpty ? farms.pins : farms.pins.filter {
            ProductMatch.fold($0.name).contains(q) || ProductMatch.fold($0.city ?? "").contains(q)
        }
        return Array(farms.sortedByDistance(pool, from: locationManager.location).prefix(8))
    }

    private var items: [ShoppingItem] {
        guard let chosen, let sells = farms.produceByOsm[chosen.osmId], !sells.isEmpty else {
            return Array(catalogue.items.prefix(12))
        }
        let own = catalogue.items.filter { ProductMatch.covers(sells, terms: $0.terms) }
        return own.isEmpty ? Array(catalogue.items.prefix(12)) : own
    }

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "What did you see?"), compact: true, onBack: { dismiss() })
                .padding(.top, Space.s3)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: Space.s3) {
                    if sent {
                        EmptyState(icon: "checkmark.seal.fill",
                                   title: String(localized: "Thank you"),
                                   text: String(localized: "Everyone deciding whether to drive there today can see it now."),
                                   action: (String(localized: "Done"), { dismiss() }))
                    } else if let chosen {
                        farmRow(chosen, selected: true) { self.chosen = nil; status = nil; products = [] }
                        Text(String(localized: "Was it open?")).role(.label, .inkFaint).textCase(.uppercase).padding(.top, Space.s2)
                        HStack(spacing: Space.s2) {
                            statusChip(.open, String(localized: "Open"), .vividPositive)
                            statusChip(.closed, String(localized: "Closed"), .vividCritical)
                            statusChip(.soldOut, String(localized: "Sold out"), .vividWarning)
                        }
                        if status == .open || status == .soldOut {
                            Text(status == .soldOut ? String(localized: "What was still there?") : String(localized: "What did you find?"))
                                .role(.label, .inkFaint).textCase(.uppercase).padding(.top, Space.s2)
                            FlowRow(spacing: Space.s2) {
                                ForEach(items) { item in
                                    Chip(label: item.label, emoji: item.emoji, selected: products.contains(item.id)) {
                                        if products.contains(item.id) { products.remove(item.id) } else { products.insert(item.id) }
                                    }
                                }
                            }
                        }
                        Button {
                            Task { await send() }
                        } label: {
                            if sending { ProgressView().tint(.white) } else { Text(String(localized: "Send report")) }
                        }
                        .buttonStyle(PillButtonStyle(.primary, size: .large, block: true))
                        .disabled(status == nil || sending)
                        .padding(.top, Space.s3)
                    } else {
                        Text(String(localized: "Which farm?")).role(.label, .inkFaint).textCase(.uppercase)
                        SearchField(text: $query, placeholder: String(localized: "Search farms by name or place"))
                        if locationManager.location == nil && query.isEmpty {
                            Text(String(localized: "Allow location to see the farms around you, or search by name."))
                                .role(.caption, .inkMuted)
                        }
                        ForEach(nearby) { pin in
                            farmRow(pin, selected: false) { chosen = pin }
                        }
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.bottom, Space.s8)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await farms.loadFlagsIfNeeded(); await catalogue.loadIfNeeded() }
    }

    private func farmRow(_ pin: FarmPin, selected: Bool, action: @escaping () -> Void) -> some View {
        let km = pin.distance(from: locationManager.location).map { $0 / 1000 }
        return Button { Haptics.tap(); action() } label: {
            HStack(spacing: Space.s3) {
                Text(pin.primaryCategory.emoji).font(.system(size: 20))
                    .frame(width: 44, height: 44).background(Color.creamFill, in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(pin.name).role(.subheading).lineLimit(1)
                    HStack(spacing: 6) {
                        if let city = pin.city { Text(city) }
                        if let km { Text(verbatim: "· \(km.formatted(.number.precision(.fractionLength(1)))) km") }
                    }
                    .role(.caption, .inkMuted)
                }
                Spacer(minLength: 0)
                Image(systemName: selected ? "xmark" : "chevron.right")
                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.inkMuted)
            }
            .padding(Space.s3)
            .background(selected ? Color.farmGreenSoft : Color.surface,
                        in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func statusChip(_ s: ReportStatus, _ label: String, _ dot: Color) -> some View {
        Chip(label: label, selected: status == s, dot: status == s ? nil : dot) { status = s }
    }

    private func send() async {
        guard let chosen, let status, let token = session.session?.accessToken else { return }
        sending = true
        defer { sending = false }
        if await FarmStatusAPI.report(osmId: chosen.osmId, status: status, products: Array(products), token: token) {
            Haptics.success()
            sent = true
            await onSent()
        }
    }
}
