import Foundation
import Observation
import RevenueCat

/// In-app purchases via RevenueCat.
///
/// The rule that governs this whole file: **the server is the source of truth for
/// access, not the store.** `profiles.subscription_status` is written by every
/// payment rail (Stripe on the web, Apple IAP, Google Play) and read back through
/// `/api/profile/status`. RevenueCat only *sells*; it never decides who is a member.
///
/// That is what lets someone who subscribed on farmsy.app open the app and have
/// full access without being charged again — and it is what Apple requires
/// (guideline 3.1.1: subscriptions bought elsewhere must keep working).
@MainActor
@Observable
final class PurchaseStore {

    /// The yearly membership — the only thing we sell in-app. Lifetime stays
    /// web-only (Apple's cut on a one-off isn't worth it), and there is no
    /// monthly plan on any rail: `profiles.subscription_plan` has a CHECK
    /// constraint of ('yearly','lifetime'), so a monthly purchase would take the
    /// customer's money and then have the webhook write rejected.
    static let yearlyProductId = "farmsy_membership_yearly"
    static let lifetimeProductId = "farmsy_membership_lifetime"

    private(set) var offering: Offering?
    private(set) var isPurchasing = false
    private(set) var purchaseError: String?
    /// True once we've tried to load prices and come back with nothing, so the
    /// paywall can offer a retry instead of an eternal spinner.
    private(set) var offeringFailed = false
    /// True once a load attempt has actually finished (success or failure), so the
    /// paywall can tell "still fetching" apart from "fetched, nothing to sell."
    private(set) var didLoadOffering = false

    /// We finished a fetch and still have no purchasable yearly product. This is
    /// the App-Review deadlock case: the products can be in a Rejected/unavailable
    /// state, so RevenueCat hands back an offering with **no** available packages —
    /// the offering isn't nil, but there's nothing to buy. The paywall must show a
    /// real message + retry here, never an endless spinner (guideline 2.1: the
    /// reviewer needs to reach a working purchase, not a blank screen).
    var productsUnavailable: Bool { didLoadOffering && yearlyPackage == nil }

    /// The two things we sell. Yearly renews; lifetime is a one-off that never
    /// expires (the server treats a lifetime grant as un-revocable).
    var yearlyPackage: Package? {
        offering?.availablePackages.first { $0.storeProduct.productIdentifier == Self.yearlyProductId }
            ?? offering?.annual
    }
    var lifetimePackage: Package? {
        offering?.availablePackages.first { $0.storeProduct.productIdentifier == Self.lifetimeProductId }
            ?? offering?.lifetime
    }

    /// Prices as the store formats them for the user's region ("€29,99").
    var yearlyPrice: String? { yearlyPackage?.storeProduct.localizedPriceString }
    var lifetimePrice: String? { lifetimePackage?.storeProduct.localizedPriceString }

    /// Length of the yearly plan's free trial in days, or nil when there isn't one.
    ///
    /// Read from StoreKit, never hardcoded: Apple only reports an introductory offer
    /// for customers who are actually *eligible*, so a returning subscriber gets
    /// none. Advertising "3 days free" to someone who won't receive it is precisely
    /// the misrepresentation App Review guideline 3.1.2 exists to catch — and it's a
    /// rotten way to treat someone regardless. No offer, no claim.
    var yearlyFreeTrialDays: Int? {
        guard let intro = yearlyPackage?.storeProduct.introductoryDiscount,
              intro.paymentMode == .freeTrial else { return nil }
        let p = intro.subscriptionPeriod
        switch p.unit {
        case .day:   return p.value
        case .week:  return p.value * 7
        case .month: return p.value * 30
        case .year:  return p.value * 365
        @unknown default: return nil
        }
    }

    /// Back-compat for callers that just want the headline price.
    var displayPrice: String? { yearlyPrice }

    static func configure() {
        guard !Backend.revenueCatKey.isEmpty else { return }
        Purchases.logLevel = .warn
        Purchases.configure(withAPIKey: Backend.revenueCatKey)
    }

    /// Ties RevenueCat's customer to the Supabase user, so the webhook can find
    /// the profile to grant. Must run after login and before any purchase UI —
    /// without it, `app_user_id` is an anonymous RevenueCat id and the grant is
    /// skipped server-side.
    static func identify(userId: UUID) async {
        guard !Backend.revenueCatKey.isEmpty else { return }
        _ = try? await Purchases.shared.logIn(userId.uuidString)
    }

    static func signOut() async {
        guard !Backend.revenueCatKey.isEmpty else { return }
        _ = try? await Purchases.shared.logOut()
    }

    /// Fetches the offering once and keeps it. The paywall used to call this on
    /// every open, so each visit paid the full RevenueCat round-trip before it
    /// could draw the buttons. Prices don't change between screens: prefetch at
    /// launch, and a second call is a no-op unless the first one came back empty.
    func loadOffering(force: Bool = false) async {
        guard !Backend.revenueCatKey.isEmpty else { didLoadOffering = true; return }
        if !force, offering != nil { return }
        offering = try? await Purchases.shared.offerings().current
        offeringFailed = (offering == nil)
        didLoadOffering = true
    }

    /// Buys the membership. Returns true once the purchase completes — the caller
    /// then refreshes the profile, because access is granted by the server (via
    /// RevenueCat's webhook), not by this return value.
    ///
    /// `userId` is the Supabase user id. We re-assert `logIn` here, right before
    /// buying, because the identify() fired at auth time can lose the race with the
    /// user reaching this button — on a fresh install the SDK starts with an
    /// anonymous id. A purchase attached to that anonymous id has no Supabase user
    /// for the webhook to grant, and the payment is stranded. Re-asserting the id
    /// at purchase time closes that race.
    func purchase(_ package: Package?, userId: UUID?) async -> Bool {
        guard let package else {
            purchaseError = String(localized: "Membership isn't available right now. Please try again later.")
            return false
        }
        if let userId, Purchases.shared.appUserID != userId.uuidString {
            _ = try? await Purchases.shared.logIn(userId.uuidString)
        }
        isPurchasing = true
        purchaseError = nil
        defer { isPurchasing = false }

        do {
            let result = try await Purchases.shared.purchase(package: package)
            return !result.userCancelled
        } catch {
            purchaseError = error.localizedDescription
            return false
        }
    }

    /// Apple requires a visible restore path for non-consumables/subscriptions.
    func restore() async -> Bool {
        guard !Backend.revenueCatKey.isEmpty else { return false }
        isPurchasing = true
        purchaseError = nil
        defer { isPurchasing = false }

        do {
            let info = try await Purchases.shared.restorePurchases()
            return info.entitlements.active.isEmpty == false
        } catch {
            purchaseError = error.localizedDescription
            return false
        }
    }
}
