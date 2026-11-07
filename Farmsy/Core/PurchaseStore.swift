import Foundation
import Observation
import RevenueCat
import UserNotifications

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

    /// Whether this store account may actually receive the yearly plan's
    /// introductory offer. `nil` until the check has come back.
    ///
    /// Kept separately from the offering on purpose: the offering describes the
    /// *product* and is cached for the session, while this describes the *customer*
    /// and can change underneath us — a different Apple ID in Settings, or a trial
    /// already spent on another device.
    private(set) var yearlyTrialEligibility: IntroEligibilityStatus?

    /// Length of the yearly plan's free trial in days, or nil when this customer
    /// won't get one.
    ///
    /// Two things have to be true, and for a long time we only checked the first.
    /// StoreKit reports a product's introductory offer to *everyone* — eligible or
    /// not — so `introductoryDiscount` says what the product carries, never what
    /// this person will be given. Eligibility is a separate question, and the
    /// answer lives in `yearlyTrialEligibility`.
    ///
    /// Without the second check a returning subscriber was shown "7 days free",
    /// "Nothing to pay today · cancel anytime" and "Start my free week", and was
    /// then charged on the spot: the misrepresentation App Review guideline 3.1.2
    /// exists to catch, and a rotten way to treat someone besides. No offer, no claim.
    var yearlyFreeTrialDays: Int? {
        guard let intro = yearlyPackage?.storeProduct.introductoryDiscount,
              intro.paymentMode == .freeTrial else { return nil }
        return Self.trialDays(eligibility: yearlyTrialEligibility, period: intro.subscriptionPeriod)
    }

    /// The gate itself, kept pure so it can be tested without a store.
    ///
    /// Only `.eligible` yields days. `.ineligible` and `.noIntroOfferExists` are
    /// plain noes. `.unknown` means RevenueCat could not tell, and `nil` means we
    /// have not asked yet — both resolve to no trial, because the failure we are
    /// fixing came from guessing in our own favour. Showing "Get yearly" to someone
    /// who turns out to be eligible costs us a little; promising a free week to
    /// someone who is charged immediately costs them money and us their trust.
    /// `nonisolated` because it touches no state: it is the rule, not the store,
    /// so it runs anywhere and the tests can call it directly.
    nonisolated static func trialDays(eligibility: IntroEligibilityStatus?,
                                      period: SubscriptionPeriod) -> Int? {
        guard eligibility == .eligible else { return nil }
        switch period.unit {
        case .day:   return period.value
        case .week:  return period.value * 7
        case .month: return period.value * 30
        case .year:  return period.value * 365
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
    /// launch, and a second call skips the fetch unless the first came back empty.
    ///
    /// Trial eligibility is the exception and is re-asked on every call. It is a
    /// fact about the store account rather than the product, so caching it for the
    /// session is what would let a stale "free week" reach someone who can't have one.
    func loadOffering(force: Bool = false) async {
        guard !Backend.revenueCatKey.isEmpty else { didLoadOffering = true; return }
        if force || offering == nil {
            offering = try? await Purchases.shared.offerings().current
            offeringFailed = (offering == nil)
            didLoadOffering = true
        }
        // Every call, cached offering or not. The prices can be reused across
        // screens; the eligibility answer belongs to the store account, so the
        // paywall re-asks it each time it appears.
        await refreshTrialEligibility()
    }

    /// Asks RevenueCat whether this store account may receive the yearly intro
    /// offer, and keeps the answer for `yearlyFreeTrialDays` to gate on.
    ///
    /// Left untouched when there is nothing to ask about (no key, no yearly
    /// product): `yearlyFreeTrialDays` already reads nil without a package, so
    /// there is no claim to make either way.
    func refreshTrialEligibility() async {
        guard !Backend.revenueCatKey.isEmpty,
              let product = yearlyPackage?.storeProduct else { return }
        yearlyTrialEligibility = await Purchases.shared
            .checkTrialOrIntroDiscountEligibility(product: product)
    }

    /// Buys the membership. Returns true once the purchase completes — the caller
    /// then refreshes the profile, because access is granted by the server (via
    /// RevenueCat's webhook), not by this return value.
    ///
    /// `userId` is the Supabase user id and is required: the sheet asks for
    /// sign-in before it gets here, so a purchase can never run under RevenueCat's
    /// anonymous id, which the webhook cannot grant. We still re-assert `logIn`
    /// right before buying, because the identify() fired at auth time can lose the
    /// race with the user reaching this button.
    func purchase(_ package: Package?, userId: UUID) async -> Bool {
        guard let package else {
            purchaseError = String(localized: "Membership isn't available right now. Please try again later.")
            Observability.capture(.purchaseFailed,
                                  [AnalyticsProp.reason: AnalyticsValue.Reason.productUnavailable.rawValue])
            return false
        }
        // The tap, before the store sheet appears.
        let plan = Self.plan(for: package)
        Observability.capture(.planTapped, [AnalyticsProp.plan: plan])
        if Purchases.shared.appUserID != userId.uuidString {
            _ = try? await Purchases.shared.logIn(userId.uuidString)
        }
        isPurchasing = true
        purchaseError = nil
        defer { isPurchasing = false }

        do {
            let result = try await Purchases.shared.purchase(package: package)
            if result.userCancelled {
                Observability.capture(.purchaseFailed,
                                      [AnalyticsProp.reason: AnalyticsValue.Reason.cancelled.rawValue])
                return false
            }
            Observability.capture(.purchaseCompleted,
                                  [AnalyticsProp.plan: plan,
                                   AnalyticsProp.store: AnalyticsValue.Store.appStore.rawValue])
            if let end = result.customerInfo.entitlements.active.values
                .first(where: { $0.periodType == .trial })?.expirationDate {
                let price = package.storeProduct.localizedPriceString
                Task { await TrialReminder.schedule(trialEnds: end, price: price) }
            }
            return true
        } catch {
            purchaseError = error.localizedDescription
            Observability.capture(.purchaseFailed,
                                  [AnalyticsProp.reason: Self.failureReason(error).rawValue])
            return false
        }
    }

    /// `plan` for the analytics events: which of the two products this package is.
    private static func plan(for package: Package) -> String {
        package.storeProduct.productIdentifier == lifetimeProductId
            ? AnalyticsValue.Plan.lifetime.rawValue
            : AnalyticsValue.Plan.yearly.rawValue
    }

    /// A short code for `reason`, never `localizedDescription` — that string is
    /// translated, and a German failure has to land in the same bucket as a Dutch one.
    private static func failureReason(_ error: Error) -> AnalyticsValue.Reason {
        let ns = error as NSError
        guard ns.domain == ErrorCode.errorDomain else { return .storeError }
        switch ErrorCode(rawValue: ns.code) {
        case .purchaseCancelledError?: return .cancelled
        case .networkError?, .offlineConnectionError?: return .network
        case .productNotAvailableForPurchaseError?: return .productUnavailable
        default: return .storeError
        }
    }

    /// Apple requires a visible restore path for non-consumables/subscriptions.
    func restore() async -> Bool {
        Observability.capture(.restoreTapped)
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

/// One local notification two days before a free trial turns into a charge.
/// No toggle (owner, 2026-10-15): everyone who starts a trial gets it — the
/// paywall's "nothing to pay today" is only honest if we say when that changes.
/// Asks for notification permission right after the purchase; a no means no
/// reminder, nothing else.
enum TrialReminder {
    static let id = "trial-ending"

    @MainActor
    static func schedule(trialEnds: Date, price: String) async {
        let fire = trialEnds.addingTimeInterval(-2 * 86_400)
        guard fire > .now else { return }   // sandbox trials last minutes
        guard await PushRegistrar.shared.requestAuthorization() else { return }
        let content = UNMutableNotificationContent()
        content.title = String(localized: "Your free trial ends in 2 days")
        content.body = String(localized: "After that, Farmsy Plus is \(price) a year. Cancel anytime in Settings.")
        content.sound = .default
        let when = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
        let request = UNNotificationRequest(identifier: id, content: content,
                                            trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false))
        try? await UNUserNotificationCenter.current().add(request)
    }
}
