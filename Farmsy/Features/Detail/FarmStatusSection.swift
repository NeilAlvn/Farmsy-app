import SwiftUI

/// "Was it open?" — the one question a visitor can answer that nobody else can.
///
/// Three buttons and a sentence. Tapping what you already said takes it back: a
/// button that only ever adds is one nobody can correct after a mis-tap. Once
/// you have said "open" or "sold out", a row of product chips asks what you
/// found on the shelf — the same ids the shopping list matches on.
///
/// The sentence says who is speaking. Every report here is another visitor's,
/// because there are no farmers on the platform yet, and "sold out" from the
/// shop itself is a different claim from "somebody found it sold out".
///
/// Free sees how many and how many days ago. Plus sees minutes, how many agree
/// today, and what they found: that timing is the thing Farmsy makes, and the
/// reason to pay.
struct FarmStatusSection: View {
    let osmId: String
    /// What this farm sells, so the "what did you find" chips are its products
    /// and not the whole picker.
    var sells: String? = nil
    /// Asks for an account. Reading is public; reporting is not.
    var onNeedsSignIn: () -> Void

    @Environment(SessionStore.self) private var session
    @Environment(\.shell) private var shell
    @State private var catalogue = ShoppingItems.shared

    @State private var loaded: FarmStatusAPI.Loaded?
    /// The farm the loaded reports belong to, so one farm's answers can never
    /// appear on another's card for a frame when the card swaps in place.
    @State private var loadedFor: String?
    @State private var isSending = false

    private var reports: [FarmReport] { loadedFor == osmId ? (loaded?.reports ?? []) : [] }
    private var summary: StatusSummary { FarmStatus.summarise(reports) }
    private var freshness: Freshness? { FarmStatus.freshness(reports) }

    private var myReport: FarmReport? {
        let mine = loadedFor == osmId ? (loaded?.mine ?? []) : []
        let today = FarmStatus.amsterdamDay(Date())
        return mine.first { FarmStatus.amsterdamDay($0.createdAt) == today }
    }
    private var mine: ReportStatus? { myReport?.status }

    /// The farm's own products, as chips. Falls back to the whole picker for a
    /// farm that never said what it sells — the visitor is telling us.
    private var items: [ShoppingItem] {
        guard let sells, !sells.isEmpty else { return Array(catalogue.items.prefix(12)) }
        let own = catalogue.items.filter { ProductMatch.covers(sells, terms: $0.terms) }
        return own.isEmpty ? Array(catalogue.items.prefix(12)) : own
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if summary.total > 0 { headline }
            HStack(spacing: 8) {
                Text("Was it open?")
                    .font(.ui(11, .semibold))
                    .textCase(.uppercase)
                    .foregroundStyle(Color.inkMuted)
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                button(.open, icon: "door.left.hand.open", label: "Open")
                button(.closed, icon: "door.left.hand.closed", label: "Closed")
                button(.soldOut, icon: "shippingbox", label: "Sold out")
            }
            if mine == .open || mine == .soldOut, !items.isEmpty {
                foundChips
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task(id: osmId) { await reload() }
        .task { await catalogue.loadIfNeeded() }
    }

    // MARK: - Pieces

    /// Counts and how long ago, never a percentage. Two reports rendered as
    /// "50% found it open" is a lie with a decimal point in it.
    @ViewBuilder
    private var headline: some View {
        if session.hasFullAccess, let f = freshness {
            freshLine(f)
        } else {
            let days = summary.daysAgo ?? 0
            Group {
                switch summary.lead {
                case .open:
                    Text("\(summary.open) visitors got in, most recently \(days) days ago")
                case .trouble:
                    Text("\(summary.trouble) visitors found it shut or sold out, most recently \(days) days ago")
                case .mixed:
                    Text("\(summary.open) got in, \(summary.trouble) did not, in the last few weeks")
                case .none:
                    EmptyView()
                }
            }
            .font(.ui(12))
            .foregroundStyle(summary.lead == .trouble ? Color.critical : Color.inkMuted)
            .fixedSize(horizontal: false, vertical: true)
            if !session.hasFullAccess, freshness != nil {
                Button {
                    shell.openPlus(.farmDetail)
                } label: {
                    Label(String(localized: "Confirmed today — see when with Plus"), systemImage: "lock.fill")
                        .font(.ui(12, .semibold))
                        .foregroundStyle(Color.farmGreen)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func freshLine(_ f: Freshness) -> some View {
        let when = f.minutesAgo < 60
            ? String(localized: "\(f.minutesAgo) min ago")
            : String(localized: "\(f.minutesAgo / 60) h ago")
        let who = f.confirmations == 1 ? String(localized: "1 person") : String(localized: "\(f.confirmations) people")
        let verb = switch f.status {
        case .open: String(localized: "Open")
        case .closed: String(localized: "Closed")
        case .soldOut: String(localized: "Sold out")
        }
        let found = f.products.compactMap { id in catalogue.item(id: id)?.label }
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Circle().fill(f.status == .open ? Color.vividPositive : Color.vividCritical).frame(width: 8, height: 8)
                Text("\(verb) · confirmed \(when) by \(who)")
                    .font(.ui(12, .semibold))
                    .foregroundStyle(f.status == .open ? Color.positive : Color.critical)
            }
            if !found.isEmpty {
                Text("On the shelf: \(found.joined(separator: ", "))")
                    .font(.ui(12)).foregroundStyle(Color.inkMuted)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var foundChips: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(mine == .soldOut ? "What was still there?" : "What did you find?")
                .font(.ui(11, .semibold)).textCase(.uppercase).foregroundStyle(Color.inkMuted)
                .padding(.top, 2)
            FlowRow(spacing: 6) {
                ForEach(items) { item in
                    let on = myReport?.products.contains(item.id) ?? false
                    Chip(label: item.label, emoji: item.emoji, selected: on) {
                        Task { await toggleProduct(item.id) }
                    }
                    .disabled(isSending)
                }
            }
        }
    }

    private func button(_ status: ReportStatus, icon: String, label: LocalizedStringKey) -> some View {
        let on = mine == status
        return Button {
            Haptics.tap()
            Task { await send(status) }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: on ? "checkmark" : icon)
                    .font(.system(size: 11, weight: .semibold))
                Text(label).font(.ui(12, .semibold))
            }
            .foregroundStyle(on ? Color.farmGreen : Color.inkMuted)
            .padding(.vertical, 7).padding(.horizontal, 11)
            .background(on ? Color.farmGreenSoft : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(on ? Color.farmGreen : Color.hairline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(isSending)
        .opacity(isSending ? 0.6 : 1)
    }

    // MARK: - Actions

    private func reload() async {
        let token = session.session?.accessToken
        let result = await FarmStatusAPI.load(osmId: osmId, token: token)
        loaded = result
        loadedFor = osmId
    }

    private func send(_ status: ReportStatus) async {
        guard let token = session.session?.accessToken else { onNeedsSignIn(); return }
        isSending = true
        defer { isSending = false }
        // Tapping what you already said takes it back.
        let ok = mine == status
            ? await FarmStatusAPI.clear(osmId: osmId, token: token)
            : await FarmStatusAPI.report(osmId: osmId, status: status, token: token)
        if ok { await reload() }
    }

    /// The products ride on the same row as the status, so a toggle re-sends
    /// today's report with the new list. One per person per day still holds.
    private func toggleProduct(_ id: String) async {
        guard let token = session.session?.accessToken, let my = myReport else { return }
        Haptics.tap()
        isSending = true
        defer { isSending = false }
        var products = my.products
        if let i = products.firstIndex(of: id) { products.remove(at: i) } else { products.append(id) }
        if await FarmStatusAPI.report(osmId: osmId, status: my.status, products: products, token: token) {
            await reload()
        }
    }
}
