import RevenueCat
import SwiftUI

/// The one Plus sheet, presented everywhere via `shell.openPlus(_:)` — root, Trips,
/// Profile, farm card. Farm-free: it takes no farm, so it fronts any non-farm upsell
/// too. Story is "Farmsy finds it, plans it, tells you when it's fresh" (owner
/// decision, 2026-09-21): finding the right farms, the route, alerts and live
/// availability are Plus; looking (map, farm details, filters) stays free. The plan
/// buttons + purchase flow reuse `PlanButton` and the `PurchaseStore`.
struct ProUpsellSheet: View {
    var onClose: () -> Void = {}
    /// What asked for the sheet; reported on `auth_prompted` when a signed-out
    /// plan tap opens sign-in.
    var trigger: AnalyticsValue.Trigger? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(PurchaseStore.self) private var purchases
    @Environment(SessionStore.self) private var session
    @Environment(\.requestAuth) private var requestAuth
    @State private var isChecking = false
    /// The tap that was waiting for sign-in. Consumed the moment the session
    /// appears; overwritten by the next tap; cleared when the sheet closes.
    @State private var pending: PendingAction?

    private enum PendingAction { case buy(Package), restore }

    /// Which card is picked. Yearly by default — it carries the trial.
    enum Choice: String { case free, yearly, lifetime }
    @State private var choice: Choice = .yearly

    /// Free against Plus, row by row. Looking is free; Farmsy doing the work is
    /// Plus (owner, 2026-09-21). The table is the value story — it sits above
    /// the prices, Cropsy's v3 order (owner, 2026-10-15).
    private let rows: [(String, Bool)] = [
        (String(localized: "Every farm on the map"), true),
        (String(localized: "Open today, confirmed"), false),
        (String(localized: "Who has your shopping list"), false),
        (String(localized: "The best route past your stops"), false),
        (String(localized: "A heads-up when it's fresh in"), false),
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

    enum PendingBuyOutcome: Equatable { case alreadyMember, proceed, abandon }

    /// What a pending buy does once sign-in completes. One place, like
    /// `TripStore.isRouteLocked`, so the rule can be tested without a store or a
    /// session: never charge an existing member, and never charge on a profile we
    /// could not read. `hasFullAccess` is false for a profile that never loaded,
    /// which is exactly why the loaded flag is a separate argument.
    static func pendingBuyOutcome(profileLoaded: Bool, hasFullAccess: Bool) -> PendingBuyOutcome {
        guard profileLoaded else { return .abandon }
        return hasFullAccess ? .alreadyMember : .proceed
    }

    /// A tap that waited for sign-in must not charge someone who is already a
    /// member — reinstalled, or bought on the web or the other store. The session
    /// lands before the profile does, so wait for one before deciding. On doubt,
    /// do nothing: a second tap costs a tap, a second charge costs money (iOS
    /// lifetime is non-renewing, so StoreKit will happily sell it twice).
    private func resumePendingBuy(_ package: Package) async {
        isChecking = true
        for attempt in 0..<6 {
            if attempt > 0 { try? await Task.sleep(nanoseconds: 500_000_000) }
            await session.refreshProfile()
            if session.profile != nil { break }
        }
        isChecking = false
        switch Self.pendingBuyOutcome(profileLoaded: session.profile != nil,
                                      hasFullAccess: session.hasFullAccess) {
        case .alreadyMember: close()
        case .proceed: buy(package)
        // Leave the sheet open rather than guess un-subscribed: the person taps again.
        case .abandon: break
        }
    }

    /// Sign-in first, then the store. Signed in already: straight to the store.
    private func buy(_ package: Package?) {
        guard let uid = session.session?.user.id else {
            if let package { pending = .buy(package) }
            AuthView.nextMode = .signUp
            Observability.capture(.authPrompted, [AnalyticsProp.trigger: (trigger ?? .homeRow).rawValue])
            requestAuth()
            return
        }
        Task { if await purchases.purchase(package, userId: uid) { Haptics.success(); await awaitGrant() } }
    }

    private func restore() {
        guard session.session != nil else {
            pending = .restore
            Observability.capture(.authPrompted, [AnalyticsProp.trigger: AnalyticsValue.Trigger.restore.rawValue])
            requestAuth()
            return
        }
        Task { if await purchases.restore() { await awaitGrant() } }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                header
                VStack(spacing: Space.s4) {
                    comparison
                    purchaseArea
                }
                .padding(.horizontal, Space.s4)
                .padding(.top, Space.s4)
                .padding(.bottom, 28)
            }
        }
        .background(Color.cream.ignoresSafeArea())
        .task { await purchases.loadOffering() }
        .onChange(of: session.isAuthenticated) { _, authed in
            guard authed, let action = pending else { return }
            pending = nil
            switch action {
            // Restore is safe for an existing member; a buy is not.
            case .buy(let package): Task { await resumePendingBuy(package) }
            case .restore: restore()
            }
        }
    }

    /// Green band, Nime's shape: brand + PLUS, then what Plus does for you —
    /// the value, not the product name.
    private var header: some View {
        VStack(alignment: .leading, spacing: Space.s3) {
            HStack {
                HStack(spacing: 6) {
                    Text("farmsy").font(.ui(20, .semibold)).foregroundStyle(.white)
                    Badge(text: "PLUS", fill: .vivid, ink: .ink)
                }
                Spacer()
                Button { Haptics.tap(); close() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 32, height: 32).background(Color.white.opacity(0.15), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Close"))
            }
            Text("Fresh from the farm, without the search.")
                .font(.ui(28, .semibold)).foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, Space.s2)
            Text("Know which farm is open today and what's in, then drive past them in one route.")
                .font(.ui(15)).foregroundStyle(Color.white.opacity(0.82))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, Space.s5)
        .padding(.top, Space.s5)
        .padding(.bottom, Space.s6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.farmGreen)
    }

    /// Free vs Plus. The Plus column is one green strip so the eye reads it as
    /// "everything", the free column shows what's locked.
    private var comparison: some View {
        let plusWidth: CGFloat = 64, freeWidth: CGFloat = 56
        return VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer()
                Text("Free").font(.ui(13, .medium)).foregroundStyle(Color.inkMuted)
                    .frame(width: freeWidth)
                Text("Plus").font(.ui(13, .semibold)).foregroundStyle(.white)
                    .frame(width: plusWidth).padding(.vertical, 10).background(Color.farmGreenMap)
            }
            ForEach(rows, id: \.0) { row in
                HStack(spacing: 0) {
                    Text(row.0).font(.ui(14)).foregroundStyle(Color.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.leading, Space.s4).padding(.vertical, 11)
                    Image(systemName: row.1 ? "checkmark" : "lock")
                        .font(.system(size: 13, weight: row.1 ? .semibold : .regular))
                        .foregroundStyle(row.1 ? Color.inkMuted : Color.inkFaint)
                        .frame(width: freeWidth)
                        .accessibilityLabel(Text(row.1 ? "Included" : "Not included"))
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 17)).foregroundStyle(.white)
                        .frame(width: plusWidth).frame(maxHeight: .infinity)
                        .background(Color.farmGreenMap)
                        .accessibilityLabel(Text("Included"))
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .background(Color.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
    }

    @ViewBuilder
    private var purchaseArea: some View {
        if let error = purchases.purchaseError {
            Text(error).font(.ui(14, .medium)).foregroundStyle(Color.warnRed)
                .multilineTextAlignment(.center)
        }
        if purchases.isPurchasing || isChecking {
            ProgressView().tint(Color.farmGreen).padding(.vertical, Space.s6)
        } else if purchases.productsUnavailable {
            VStack(spacing: 10) {
                Text("Memberships can't be loaded right now.")
                    .font(.ui(14, .medium)).foregroundStyle(Color.inkMuted)
                    .multilineTextAlignment(.center)
                Button("Try again") { Haptics.tap(); Task { await purchases.loadOffering(force: true) } }
                    .font(.ui(15, .semibold)).foregroundStyle(Color.farmGreenMap)
            }
        } else if let yearlyPrice = purchases.yearlyPrice {
            // Trial only offered to someone who's never had one — StoreKit reports
            // ineligible as trialDays == nil, so every free-days line simply doesn't
            // show (Aviah: never advertise a trial someone won't get).
            let trialDays = purchases.yearlyFreeTrialDays
            if trialDays != nil {
                // Hidden, not removed, off the yearly card — the cards must not jump.
                Label("Nothing to pay today · cancel anytime", systemImage: "checkmark.seal.fill")
                    .font(.ui(14, .semibold)).foregroundStyle(Color.farmGreen)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(Color.farmGreenSoft, in: RoundedRectangle(cornerRadius: Radius.input, style: .continuous))
                    .opacity(choice == .yearly ? 1 : 0)
                    .accessibilityHidden(choice != .yearly)
            }
            planCards(yearlyPrice: yearlyPrice, trialDays: trialDays)
                .padding(.top, trialDays == nil ? 0 : Space.s2)
            ctaButton(trialDays: trialDays)
            Text(legalLine(yearlyPrice: yearlyPrice, trialDays: trialDays))
                .font(.ui(12)).foregroundStyle(Color.inkMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: Space.s4) {
                Button("Restore purchases") { Haptics.tap(); restore() }
                Link("Terms", destination: URL(string: "https://farmsy.app/terms")!)
                Link("Privacy", destination: URL(string: "https://farmsy.app/privacy")!)
            }
            .font(.ui(13)).foregroundStyle(Color.inkMuted)
        } else {
            ProgressView().tint(Color.farmGreen).padding(.vertical, Space.s6)
        }
    }

    /// Free · Yearly · Lifetime. There is no weekly or monthly to sell, so the
    /// left card is the free tier: €0 next to the per-month yearly price makes
    /// Plus read as small, and picking it is the polite way out.
    private func planCards(yearlyPrice: String, trialDays: Int?) -> some View {
        HStack(alignment: .center, spacing: Space.s2) {
            planCard(.free, title: String(localized: "Free"), price: Self.zeroPrice(like: purchases.yearlyPackage),
                     detail: String(localized: "Just the map"))
            planCard(.yearly, title: String(localized: "Yearly"), price: yearlyPrice,
                     detail: purchases.yearlyPackage?.storeProduct.localizedPricePerMonth
                        .map { String(localized: "\($0) / month") },
                     badge: trialDays.map { String(localized: "\($0) days free") })
            if let lifetime = purchases.lifetimePrice {
                planCard(.lifetime, title: String(localized: "Lifetime"), price: lifetime,
                         detail: String(localized: "Pay once"))
            }
        }
    }

    private func planCard(_ c: Choice, title: String, price: String, detail: String?, badge: String? = nil) -> some View {
        let on = choice == c
        return Button {
            Haptics.tap()
            choice = c
            Observability.capture(.planSelected, [AnalyticsProp.plan: c.rawValue])
        } label: {
            VStack(spacing: 4) {
                Text(title).font(.ui(14, on ? .semibold : .medium))
                    .foregroundStyle(on ? Color.farmGreen : Color.ink)
                Text(price).font(.ui(c == .yearly ? 22 : 19, .semibold)).foregroundStyle(Color.ink)
                    .minimumScaleFactor(0.6).lineLimit(1)
                if let detail {
                    Text(detail).font(.ui(12)).foregroundStyle(on ? Color.farmGreenMap : Color.inkMuted)
                        .minimumScaleFactor(0.7).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 4)
            .padding(.top, badge == nil ? 14 : 20)
            .padding(.bottom, 14)
            .background(on ? Color.farmGreenSoft : Color.surface,
                        in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.tile, style: .continuous)
                    .stroke(on ? Color.farmGreenMap : Color.hairline, lineWidth: on ? 2 : 1)
            )
            .overlay(alignment: .top) {
                if let badge {
                    Text(badge).font(.ui(11, .semibold)).foregroundStyle(.white)
                        .padding(.horizontal, 9).frame(height: 22)
                        .background(Color.farmGreenMap, in: Capsule())
                        .fixedSize()
                        .offset(y: -11)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func ctaButton(trialDays: Int?) -> some View {
        let label: String
        switch choice {
        case .free: label = String(localized: "Continue with free")
        case .yearly:
            // The length comes from the store; "week" only when it is one.
            switch trialDays {
            case nil: label = String(localized: "Get yearly")
            case 7?: label = String(localized: "Start my free week")
            case let days?: label = String(localized: "Try free for \(days) days")
            }
        case .lifetime: label = String(localized: "Buy lifetime access")
        }
        let filled = choice != .free
        return Button {
            Haptics.tap()
            switch choice {
            case .free: close()
            case .yearly: buy(purchases.yearlyPackage)
            case .lifetime: buy(purchases.lifetimePackage)
            }
        } label: {
            Text(label).font(.ui(17, .semibold))
                .foregroundStyle(filled ? Color.white : Color.farmGreen)
                .frame(maxWidth: .infinity).padding(.vertical, 17)
                .background(filled ? Color.farmGreenMap : Color.surface, in: Capsule())
                .overlay(Capsule().stroke(Color.farmGreenMap, lineWidth: filled ? 0 : 1.5))
        }
        .buttonStyle(.plain)
        .padding(.top, Space.s2)
    }

    /// Apple's auto-renew disclosure for the picked plan (guideline 3.1.2).
    private func legalLine(yearlyPrice: String, trialDays: Int?) -> String {
        switch choice {
        case .free:
            return String(localized: "You keep the map with every farm. Start Plus whenever you like.")
        case .lifetime:
            return String(localized: "One payment. Never renews, nothing to cancel.")
        case .yearly:
            if let days = trialDays {
                return String(localized: "\(days) days free, then \(yearlyPrice) per year. Renews automatically unless you cancel at least 24 hours before the trial ends, in your App Store settings.")
            }
            return String(localized: "\(yearlyPrice) per year. Renews automatically unless you cancel at least 24 hours before the period ends, in your App Store settings.")
        }
    }

    /// "€0" in the store's own currency format, so the free card matches the
    /// other two ("$0", "0 €").
    static func zeroPrice(like package: Package?) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.maximumFractionDigits = 0
        if let locale = package?.storeProduct.priceFormatter?.locale { f.locale = locale }
        return f.string(from: 0) ?? "0"
    }
}
