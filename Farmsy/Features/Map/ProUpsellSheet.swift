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

    private let features = [
        "An email when a farm you saved posts something new",
        "The filters we are building next, as they land",
        "Everything new we add to Pro, included",
        "You keep a small independent project going",
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
                    Kicker(text: String(localized: "Farmsy Pro"))
                        .padding(.top, 4)
                    DisplayTitle(String(localized: "Unlock the *Pro* filters"), size: 30)

                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(features, id: \.self) { line in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 16)).foregroundStyle(Color.farmGreen)
                                    .padding(.top, 1)
                                Text(line).font(.geist(14)).foregroundStyle(Color.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                    }
                    .padding(16)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.creamCard).stroke(Color.hairline, lineWidth: 1))
                    .padding(.horizontal, 20)

                    purchaseArea.padding(.horizontal, 20)
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
            let trialDays = purchases.yearlyFreeTrialDays
            VStack(spacing: 8) {
                PlanButton(
                    label: trialDays.map { String(localized: "\($0) days free") } ?? String(localized: "Yearly"),
                    detail: purchases.yearlyPrice.map { price in
                        trialDays == nil ? String(localized: "\(price) / year")
                                         : String(localized: "then \(price) / year")
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
                    PlanButton(label: String(localized: "Lifetime"),
                               detail: "\(price) · " + String(localized: "One payment, yours forever"),
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
