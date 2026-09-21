import SwiftUI

/// The one Plus sheet, presented everywhere via `shell.openPlus(_:)` — root, Trips,
/// Profile, farm card. Farm-free: it takes no farm, so it fronts any non-farm upsell
/// too. Story is "Farmsy finds it, plans it, tells you when it's fresh" (owner
/// decision, 2026-09-21): finding the right farms, the route, alerts and live
/// availability are Plus; looking (map, farm details, filters) stays free. The plan
/// buttons + purchase flow reuse `PlanButton` and the `PurchaseStore`.
struct ProUpsellSheet: View {
    var onClose: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(PurchaseStore.self) private var purchases
    @Environment(SessionStore.self) private var session
    @State private var isChecking = false

    // Owner copy, 2026-09-21: looking is free, Farmsy doing the work is Plus — the
    // sheet sells finding, planning and freshness, not filters (those are free).
    // String(localized:) rather than bare literals: Text(String) does not look
    // the catalog up, so these four lines were English in every language.
    private let features: [String] = [
        String(localized: "The farms that cover your shopping list"),
        String(localized: "A route past all of them, in the best order"),
        String(localized: "Alerts when your products arrive nearby"),
        String(localized: "Confirmed open today, by people who were just there"),
    ]

    /// Poll the profile after a purchase — the grant lands a few seconds after the
    /// call returns (RevenueCat's webhook writes subscription_status). On success the
    /// sheet closes.
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
                    // Header: "Farmsy" kicker + the one Plus title, everywhere it's sold.
                    Kicker(text: String(localized: "Farmsy"))
                        .padding(.top, 4)
                    Text("Farmsy Plus")
                        .font(.ui(28, .semibold)).foregroundStyle(Color.ink)
                        .multilineTextAlignment(.center)
                    // Subheading — the one Plus story, everywhere it's sold.
                    Text("Farmsy finds it, plans it, and tells you when it's fresh.")
                        .font(.ui(14)).foregroundStyle(Color.inkMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)

                    // Plan cards first, then the feature list — Aviah's order.
                    purchaseArea.padding(.horizontal, 20)

                    // "Included in both plans" — unboxed ticks (a list inside a card
                    // inside a sheet is a third frame around something framed twice).
                    VStack(alignment: .leading, spacing: 12) {
                        Divider().background(Color.hairline)
                        Text("Included in both plans")
                            .font(.ui(13, .semibold)).foregroundStyle(Color.inkMuted)
                        ForEach(features, id: \.self) { line in
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Color.farmGreen)
                                    .padding(.top, 2)
                                Text(line).font(.ui(14)).foregroundStyle(Color.ink)
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
            Text(error).font(.ui(14, .medium)).foregroundStyle(Color.warnRed)
                .multilineTextAlignment(.center)
        }
        if purchases.isPurchasing || isChecking {
            ProgressView().tint(Color.farmGreen)
        } else if purchases.productsUnavailable {
            VStack(spacing: 10) {
                Text("Memberships can't be loaded right now.")
                    .font(.ui(14, .medium)).foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
                Button("Try again") { Haptics.tap(); Task { await purchases.loadOffering(force: true) } }
                    .font(.ui(15, .semibold)).foregroundStyle(Color.farmGreenMap)
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
                // Every mention of the trial length reads from the store (P0-4b), as an
                // interpolated argument — it sits in a different place in the sentence
                // in each language. No offer means no trial sentence at all.
                PlanButton(
                    label: trialDays.map { String(localized: "Try free for \($0) days") }
                        ?? String(localized: "Get yearly"),
                    detail: purchases.yearlyPrice.map { price in
                        trialDays.map { String(localized: "\($0) days free · then \(price)/year") }
                            ?? String(localized: "\(price) / year")
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
                    .font(.ui(12)).foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center).padding(.top, 4)
            }
            Button("Restore purchases") {
                Haptics.tap()
                Task { if await purchases.restore() { await awaitGrant() } }
            }
            .font(.ui(14, .medium)).foregroundStyle(Color.inkMuted).padding(.top, 8)
        }
    }
}
