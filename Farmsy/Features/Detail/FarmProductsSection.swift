import SwiftUI

/// What this farm sells, as tappable chips, and the way into the Shopping tab.
///
/// The chips are the list items whose served terms appear in the farm's produce
/// text (curated first, inferred as a fallback, same rule as the web). Tapping
/// one adds it to — or removes it from — the shopping list, so "add eggs to
/// shopping" is one tap from any farm card. Inferred produce is labelled,
/// because a language model read it out of the description and nobody confirmed it.
struct FarmProductsSection: View {
    let pin: FarmPin
    let detail: FarmDetail?

    @Environment(FarmsStore.self) private var farms
    @Environment(TripStore.self) private var trip
    @Environment(\.shell) private var shell
    /// Dismisses the farm card itself — this section lives inside that sheet,
    /// and AppShell's binding clears `selectedPin` when it closes.
    @Environment(\.dismiss) private var dismiss
    @State private var catalogue = ShoppingItems.shared

    private var sells: String? {
        detail?.displayProduce ?? farms.produceByOsm[pin.osmId]
    }

    private var inferred: Bool {
        (detail?.produce?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
            && detail?.produceInferred != nil
    }

    private var items: [ShoppingItem] {
        guard let sells else { return [] }
        return catalogue.items.filter { ProductMatch.covers(sells, terms: $0.terms) }
    }

    private var allOnList: Bool {
        !items.isEmpty && items.allSatisfy { trip.wantedProducts.contains($0.id) }
    }

    var body: some View {
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: Space.s3) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Products").font(.ui(17, .bold)).foregroundStyle(Color.ink)
                    if inferred {
                        Text("likely").role(.caption, .inkFaint)
                    }
                    Spacer()
                    Button(allOnList ? String(localized: "On your list") : String(localized: "Add all to shopping")) {
                        Haptics.success()
                        for item in items where !trip.wantedProducts.contains(item.id) {
                            trip.toggleProduct(item.id)
                        }
                        // Close the card before switching tabs: it is a sheet
                        // over the map, so switching underneath it left the
                        // Shopping tab hidden behind an open farm card.
                        if allOnList { dismiss(); shell.showTab(.shopping) }
                    }
                    .buttonStyle(PillButtonStyle(.text, size: .small))
                }
                FlowRow(spacing: Space.s2) {
                    ForEach(items) { item in
                        Chip(label: item.label, emoji: item.emoji,
                             selected: trip.wantedProducts.contains(item.id)) {
                            trip.toggleProduct(item.id)
                        }
                    }
                }
                Text("Tap a product to put it on your shopping list.")
                    .role(.caption, .inkMuted)
            }
            .task { await catalogue.loadIfNeeded() }
            .task { await farms.loadFlagsIfNeeded() }
        }
    }
}

/// "Report incorrect information": six reasons and an optional note, sent as a
/// contact message so it lands where Neil already reads (contact_submissions,
/// the activity log, the admin inbox email). No new table until the volume
/// justifies one.
struct ReportInfoSheet: View {
    let pin: FarmPin

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionStore.self) private var session

    enum Reason: String, CaseIterable, Identifiable {
        case closed, hours, products, location, duplicate, other
        var id: String { rawValue }
        var label: String {
            switch self {
            case .closed: String(localized: "Closed permanently")
            case .hours: String(localized: "Opening hours are wrong")
            case .products: String(localized: "Products are wrong")
            case .location: String(localized: "Wrong location")
            case .duplicate: String(localized: "Duplicate farm")
            case .other: String(localized: "Something else")
            }
        }
    }

    @State private var reason: Reason?
    @State private var note = ""
    @State private var sending = false
    @State private var failed = false
    @State private var sent = false

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(String(localized: "Report incorrect info"), compact: true) {
                IconButton("xmark", label: String(localized: "Close"), small: true) { dismiss() }
            }
            .padding(.top, Space.s2)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: Space.s4) {
                    if sent {
                        EmptyState(icon: "checkmark.circle",
                                   title: String(localized: "Thank you"),
                                   text: String(localized: "We check every report by hand and fix the listing."),
                                   action: (String(localized: "Done"), { dismiss() }))
                    } else {
                        Text(pin.name).role(.heading)
                        Text("What is wrong?").role(.bodySm, .inkMuted)
                        RowGroup {
                            ForEach(Reason.allCases) { r in
                                Row(icon: reason == r ? "checkmark.circle.fill" : "circle",
                                    title: r.label, chevron: false) { reason = r }
                            }
                        }
                        TextField(String(localized: "Anything else we should know? (optional)"), text: $note, axis: .vertical)
                            .font(.ui(16))
                            .lineLimit(3...6)
                            .padding(Space.s4)
                            .background(Color.surface, in: RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: Radius.input, style: .continuous).stroke(Color.hairline, lineWidth: 1))
                        if failed {
                            Text("Couldn't send. Check your connection and try again.").role(.caption, .critical)
                        }
                        Button {
                            Task { await send() }
                        } label: {
                            if sending { ProgressView().tint(.white) } else { Text("Send report") }
                        }
                        .buttonStyle(PillButtonStyle(.primary, size: .large, block: true))
                        .disabled(reason == nil || sending)
                    }
                }
                .padding(.horizontal, Space.s4)
                .padding(.vertical, Space.s4)
            }
        }
        .background(Color.cream.ignoresSafeArea())
    }

    private func send() async {
        guard let reason else { return }
        sending = true
        failed = false
        var request = URLRequest(url: Backend.webAPI.appending(path: "contact"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: String] = [
            "name": session.displayName,
            "email": session.email.isEmpty ? "anonymous@farmsy.app" : session.email,
            "topic": "Farm correction: \(reason.rawValue)",
            "message": "\(pin.name) (osm \(pin.osmId), \(pin.city ?? "-"))\nReason: \(reason.label)\n\(note)",
            "source": "ios_app",
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        let ok = (try? await URLSession.shared.data(for: request))
            .flatMap { ($0.1 as? HTTPURLResponse)?.statusCode }
            .map { (200..<300).contains($0) } ?? false
        sending = false
        if ok {
            Haptics.success()
            sent = true
        } else {
            failed = true
        }
    }
}
