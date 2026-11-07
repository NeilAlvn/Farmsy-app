import Testing
import RevenueCat
@testable import Farmsy

/// A product's introductory offer is reported by StoreKit to everyone, eligible or
/// not, so reading `introductoryDiscount` told us what the product carries and
/// nothing about the person looking at it. A returning subscriber saw "7 days free",
/// "Nothing to pay today · cancel anytime" and "Start my free week", and was charged
/// immediately. `PurchaseStore.trialDays` is the one place that decision is now made:
/// days come back only for `.eligible`, and every free-days line on the Plus sheet
/// keys off its result.
struct TrialEligibilityTests {

    private let week = SubscriptionPeriod(value: 1, unit: .week)

    @Test("an eligible customer gets the trial, in days")
    func eligibleGetsDays() {
        #expect(PurchaseStore.trialDays(eligibility: .eligible, period: week) == 7)
    }

    @Test("a returning subscriber is promised nothing")
    func ineligibleGetsNothing() {
        #expect(PurchaseStore.trialDays(eligibility: .ineligible, period: week) == nil)
    }

    @Test("no intro offer on the product is also no claim")
    func noOfferGetsNothing() {
        #expect(PurchaseStore.trialDays(eligibility: .noIntroOfferExists, period: week) == nil)
    }

    /// The two cases that used to charge people. `.unknown` is RevenueCat saying it
    /// cannot tell, and `nil` is us not having asked yet — the paywall can draw
    /// before the check returns. Both have to read as no trial: a missing badge is a
    /// small loss, an unexpected charge is someone's money.
    @Test("an unanswered or unknown check never promises a trial")
    func unknownGetsNothing() {
        #expect(PurchaseStore.trialDays(eligibility: .unknown, period: week) == nil)
        #expect(PurchaseStore.trialDays(eligibility: nil, period: week) == nil)
    }

    /// The units Apple can hand back, so a trial expressed in months or days isn't
    /// silently dropped on the floor.
    @Test("every period unit converts to days")
    func unitsConvert() {
        #expect(PurchaseStore.trialDays(eligibility: .eligible,
                                        period: SubscriptionPeriod(value: 3, unit: .day)) == 3)
        #expect(PurchaseStore.trialDays(eligibility: .eligible,
                                        period: SubscriptionPeriod(value: 2, unit: .week)) == 14)
        #expect(PurchaseStore.trialDays(eligibility: .eligible,
                                        period: SubscriptionPeriod(value: 1, unit: .month)) == 30)
        #expect(PurchaseStore.trialDays(eligibility: .eligible,
                                        period: SubscriptionPeriod(value: 1, unit: .year)) == 365)
    }
}
