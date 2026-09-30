package app.farmsy.android.features.map

import org.junit.Assert.assertEquals
import org.junit.Test

/// The Android twin of `PendingBuyTests`. #75 opens the Plus sheet to signed-out
/// visitors and replays the tap once they sign in. The replay must not charge
/// someone who is already a member — someone who reinstalled, or bought on the web
/// or on iOS. Stripe, founding and admin members have no store-side protection at
/// all. `pendingBuyOutcome` is the one place that decision is made.
class PendingBuyTest {

    @Test
    fun `a member who signs in is never charged again`() {
        assertEquals(PendingBuyOutcome.ALREADY_MEMBER, pendingBuyOutcome(profileLoaded = true, hasFullAccess = true))
    }

    @Test
    fun `a genuine non-member still buys`() {
        assertEquals(PendingBuyOutcome.PROCEED, pendingBuyOutcome(profileLoaded = true, hasFullAccess = false))
    }

    /// The session lands before the profile does, and `hasFullAccess` reads false
    /// for a profile that never loaded — identical to a real non-member. Buying on
    /// that would be guessing with someone's money, so an unread profile abandons
    /// and the person taps again.
    @Test
    fun `an unread profile abandons rather than guessing un-subscribed`() {
        assertEquals(PendingBuyOutcome.ABANDON, pendingBuyOutcome(profileLoaded = false, hasFullAccess = false))
        // Belt and braces: even if access somehow read true, no profile means no buy.
        assertEquals(PendingBuyOutcome.ABANDON, pendingBuyOutcome(profileLoaded = false, hasFullAccess = true))
    }
}
