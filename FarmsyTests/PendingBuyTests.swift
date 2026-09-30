import Testing
@testable import Farmsy

/// #75 opens the Plus sheet to signed-out visitors and replays the tap once they
/// sign in. The replay must not charge someone who is already a member — someone
/// who reinstalled, or bought on the web or on the other store. iOS lifetime is a
/// non-renewing purchase, so StoreKit will sell it a second time without
/// complaint, and Stripe/founding/admin members have no store-side protection at
/// all. `pendingBuyOutcome` is the one place that decision is made.
struct PendingBuyTests {

    @Test("a member who signs in is never charged again")
    func memberNeverBuys() {
        #expect(ProUpsellSheet.pendingBuyOutcome(profileLoaded: true, hasFullAccess: true) == .alreadyMember)
    }

    @Test("a genuine non-member still buys")
    func nonMemberProceeds() {
        #expect(ProUpsellSheet.pendingBuyOutcome(profileLoaded: true, hasFullAccess: false) == .proceed)
    }

    /// The session lands before the profile does, and `hasFullAccess` reads false
    /// for a profile that never loaded — identical to a real non-member. Buying on
    /// that would be guessing with someone's money, so an unread profile abandons
    /// and the person taps again.
    @Test("an unread profile abandons rather than guessing un-subscribed")
    func unknownProfileAbandons() {
        #expect(ProUpsellSheet.pendingBuyOutcome(profileLoaded: false, hasFullAccess: false) == .abandon)
        // Belt and braces: even if access somehow read true, no profile means no buy.
        #expect(ProUpsellSheet.pendingBuyOutcome(profileLoaded: false, hasFullAccess: true) == .abandon)
    }
}
