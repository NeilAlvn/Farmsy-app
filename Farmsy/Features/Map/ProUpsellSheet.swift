import SwiftUI

/// A farm-free membership panel — the iOS twin of the web `SubscriptionGateModal`
/// (title, a line on what Pro is, the four feature lines, the plan buttons, a close).
/// Presented from a locked Pro filter tap (Aviah later-7, option 2): unlike
/// `LockedAccessView` it takes no farm, so it can front the Pro filters as well as any
/// other non-farm upsell. The plan buttons + purchase flow reuse `PlanButton` and the
/// `PurchaseStore`, identical to `LockedAccessView`. Copy is the web's `account.gate*`
/// keys, authored here as en catalog strings (not yet in a shared app catalog).
struct ProUpsellSheet: View {
    var onClose: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(PurchaseStore.self) private var purchases
    @Environment(SessionStore.self) private var session
    @State private var isChecking = false

    // Corrected copy — web `account.gateFeature1-4` at bbe3d0d. The old lines sold
    // saving + trip-planning (both free since 29 Aug) and called the filters
    // "coming" when they've shipped; Aviah fixed both. Lead with open-now (the line
    // with numbers behind it).
    private let features = [
        "See what is open right now, not just open today",
        "Filter by kind of place, and by how it is grown",
        "An email when a farm you saved posts something new",
        "Everything new we add to Pro, included",
    ]

    /// Poll the profile after a purchase — the grant lands a few seconds after the
    /// call returns (RevenueCat's webhook writes subscription_status). Same shape as
    /// LockedAccessView.awaitGrant; on success the sheet closes.
    private func awaitGrant() async {
        isChecking = true
        for _ in 0..<12 {
            await session.refreshProfile()
            if session.hasFullAccess { break }
            try? await Task.sleep(nanoseconds: 1_500_000_000)
        }
        isChecking = false
        if session.hasFullAccess { close() }
    }

    private func close() { onClose(); dismiss() }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { Haptics.tap(); close() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x6B7280))
                        .frame(width: 32, height: 32).background(Color(hex: 0xF3F4F6), in: Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.top, 16)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    // Header: "Farmsy" kicker + the reason title (a filter tap = unlock).
                    Kicker(text: String(localized: "Farmsy"))
                        .padding(.top, 4)
                    Text("Unlock Farmsy Pro")
                        .font(.display(28, weight: .semibold)).foregroundStyle(Color.ink)
                        .multilineTextAlignment(.center)
                    // Subheading (account.gateSubUnlock) — leads with open-now.
                    Text("Find what is open at this minute, and filter by the kind of place. Cancel anytime.")
                        .font(.geist(14)).foregroundStyle(Color.inkMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)

                    // Plan cards first, then the feature list — Aviah's order.
                    purchaseArea.padding(.horizontal, 20)

                    // "Included in both plans" — unboxed ticks (a list inside a card
                    // inside a sheet is a third frame around something framed twice).
                    VStack(alignment: .leading, spacing: 12) {
                        Divider().background(Color.hairline)
                        Text("Included in both plans")
                            .font(.geist(13, .semibold)).foregroundStyle(Color.inkMuted)
                        ForEach(features, id: \.self) { line in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Color.farmGreen)
                                    .padding(.top, 2)
                                Text(line).font(.geist(14)).foregroundStyle(Color.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 28)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await purchases.loadOffering() }
    }

    @ViewBuilder
    private var purchaseArea: some View {
        if let error = purchases.purchaseError {
            Text(error).font(.geist(14, .medium)).foregroundStyle(Color.warnRed)
                .multilineTextAlignment(.center)
        }
        if purchases.isPurchasing || isChecking {
            ProgressView().tint(Color.farmGreen)
        } else if purchases.productsUnavailable {
            VStack(spacing: 10) {
                Text("Memberships can't be loaded right now.")
                    .font(.geist(14, .medium)).foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
                Button("Try again") { Haptics.tap(); Task { await purchases.loadOffering(force: true) } }
                    .font(.geist(15, .semibold)).foregroundStyle(Color.farmGreenMap)
            }
        } else if purchases.yearlyPrice == nil {
            ProgressView().tint(Color.farmGreen)
        } else {
            let uid = session.session?.user.id
            // Trial only offered to someone who's never had one — StoreKit reports
            // ineligible as trialDays == nil, so the free-days copy simply doesn't show
            // (Aviah: never advertise a trial someone won't get). Copy = web gate keys.
            let trialDays = purchases.yearlyFreeTrialDays
            VStack(spacing: 8) {
                PlanButton(
                    label: trialDays != nil ? String(localized: "Try free for 3 days")
                                            : String(localized: "Get yearly"),
                    detail: purchases.yearlyPrice.map { price in
                        trialDays == nil ? String(localized: "\(price) / year")
                                         : String(localized: "3 days free · then \(price)/year")
                    },
                    filled: true
                ) {
                    Task {
                        if await purchases.purchase(purchases.yearlyPackage, userId: uid) {
                            Haptics.success(); await awaitGrant()
                        }
                    }
                }
                if let price = purchases.lifetimePrice {
                    // NOTE: Aviah's spec adds a "Best value" badge + a struck-through
                    // €59.99 anchor to the lifetime card — not expressible in the shared
                    // single-button PlanButton; flagged as a follow-up (needs custom
                    // plan cards). Copy + button text are correct here.
                    PlanButton(label: String(localized: "Buy lifetime access"),
                               detail: "\(price) · " + String(localized: "One-time · no renewals"),
                               filled: false) {
                        Task {
                            if await purchases.purchase(purchases.lifetimePackage, userId: uid) {
                                Haptics.success(); await awaitGrant()
                            }
                        }
                    }
                }
            }
            if let days = trialDays, let price = purchases.yearlyPrice {
                Text("Free for \(days) days, then \(price) per year. Cancel anytime in Settings.")
                    .font(.geist(12)).foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center).padding(.top, 4)
            }
            Button("Restore purchases") {
                Haptics.tap()
                Task { if await purchases.restore() { await awaitGrant() } }
            }
            .font(.geist(14, .medium)).foregroundStyle(Color.inkMuted).padding(.top, 8)
        }
    }
}
