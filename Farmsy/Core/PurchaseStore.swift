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

    func loadOffering() async {
        guard !Backend.revenueCatKey.isEmpty else { return }
        offering = try? await Purchases.shared.offerings().current
    }

    /// Buys the membership. Returns true once the purchase completes — the caller
    /// then refreshes the profile, because access is granted by the server (via
    /// RevenueCat's webhook), not by this return value.
    func purchase(_ package: Package?) async -> Bool {
        guard let package else {
            purchaseError = String(localized: "Membership isn't available right now. Please try again later.")
            return false
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
