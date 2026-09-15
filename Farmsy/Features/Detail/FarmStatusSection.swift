import SwiftUI

/// "Was it open?" — the one question a visitor can answer that nobody else can.
///
/// Three buttons and a sentence. Tapping what you already said takes it back: a
/// button that only ever adds is one nobody can correct after a mis-tap.
///
/// The sentence says who is speaking. Every report here is another visitor's,
/// because there are no farmers on the platform yet, and "sold out" from the
/// shop itself is a different claim from "somebody found it sold out".
struct FarmStatusSection: View {
    let osmId: String
    /// Asks for an account. Reading is public; reporting is not.
    var onNeedsSignIn: () -> Void

    @Environment(SessionStore.self) private var session

    @State private var loaded: FarmStatusAPI.Loaded?
    /// The farm the loaded reports belong to, so one farm's answers can never
    /// appear on another's card for a frame when the card swaps in place.
    @State private var loadedFor: String?
    @State private var isSending = false

    private var summary: StatusSummary {
        FarmStatus.summarise(loadedFor == osmId ? (loaded?.reports ?? []) : [])
    }

    private var mine: ReportStatus? {
        FarmStatus.myReportToday(loadedFor == osmId ? (loaded?.mine ?? []) : [])
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if summary.total > 0 { headline }
            HStack(spacing: 8) {
                Text("Was it open?")
                    .font(.geist(11, .semibold))
                    .textCase(.uppercase)
                    .foregroundStyle(Color.inkMuted)
                Spacer(minLength: 0)
            }
            HStack(spacing: 8) {
                button(.open, icon: "door.left.hand.open", label: "Open")
                button(.closed, icon: "door.left.hand.closed", label: "Closed")
                button(.soldOut, icon: "shippingbox", label: "Sold out")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task(id: osmId) { await reload() }
    }

    // MARK: - Pieces

    /// Counts and how long ago, never a percentage. Two reports rendered as
    /// "50% found it open" is a lie with a decimal point in it.
    @ViewBuilder
    private var headline: some View {
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
        .font(.geist(12))
        .foregroundStyle(summary.lead == .trouble ? Color.warnRed : Color.inkMuted)
        .fixedSize(horizontal: false, vertical: true)
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
                Text(label).font(.geist(12, .semibold))
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
}
