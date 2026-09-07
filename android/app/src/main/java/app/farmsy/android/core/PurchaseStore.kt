package app.farmsy.android.core

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.revenuecat.purchases.models.Period

/// In-app purchases via RevenueCat — mirrors iOS PurchaseStore.
///
/// The rule that governs this file: **the server is the source of truth for
/// access, not the store.** `profiles.subscription_status` is written by every
/// payment rail (Stripe on the web, Apple IAP, Google Play) and read back through
/// `/api/profile/status`. RevenueCat only *sells*; it never decides who is a member.
///
/// That is what lets someone who subscribed on farmsy.app open the app with full
/// access and never see a purchase screen.
class PurchaseStore {

    private val _yearly = MutableStateFlow<Package?>(null)
    val yearly: StateFlow<Package?> = _yearly.asStateFlow()

    private val _lifetime = MutableStateFlow<Package?>(null)
    val lifetime: StateFlow<Package?> = _lifetime.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError.asStateFlow()

    /// Prices as the Play Store formats them for the user's region ("€29,99").
    val yearlyPrice: String? get() = _yearly.value?.product?.price?.formatted
    val lifetimePrice: String? get() = _lifetime.value?.product?.price?.formatted

    /// Back-compat alias for the headline price. Mirrors iOS `displayPrice`.
    val displayPrice: String? get() = yearlyPrice

    /// Length of the yearly plan's free trial in days, or null when there isn't one.
    ///
    /// Read from the store, never hardcoded: Play only attaches the free phase for
    /// customers who are actually *eligible*, so a returning subscriber sees no
    /// trial. Printing "3 days free" at someone who won't get it is exactly the
    /// misrepresentation Apple's 3.1.2 (and plain honesty) forbids — so if the
    /// store says there's no free phase, we say nothing.
    val yearlyFreeTrialDays: Int?
        get() {
            val phase = _yearly.value?.product?.defaultOption?.freePhase ?: return null
            val period = phase.billingPeriod
            return when (period.unit) {
                Period.Unit.DAY -> period.value
                Period.Unit.WEEK -> period.value * 7
                Period.Unit.MONTH -> period.value * 30
                Period.Unit.YEAR -> period.value * 365
                else -> null
            }
        }

    companion object {
        /// The yearly membership — the only thing we sell in-app. Lifetime stays
        /// web-only, and there is no monthly plan on any rail:
        /// `profiles.subscription_plan` has a CHECK of ('yearly','lifetime'), so a
        /// monthly purchase would take the customer's money and then have the
        /// webhook write rejected by the database.
        const val YEARLY_PRODUCT_ID = "farmsy_membership_yearly"
        const val LIFETIME_PRODUCT_ID = "farmsy_membership_lifetime"

        private val enabled: Boolean get() = Backend.REVENUECAT_KEY.isNotEmpty()

        fun configure(context: Context) {
            if (!enabled) return
            Purchases.logLevel = LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(context, Backend.REVENUECAT_KEY).build()
            )
        }

        /// Ties RevenueCat's customer to the Supabase user, so the webhook can find
        /// the profile to grant. Must run after login and before any purchase UI —
        /// without it `app_user_id` is an anonymous RevenueCat id and the grant is
        /// skipped server-side.
        suspend fun identify(userId: String) {
            if (!enabled) return
            runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
        }

        suspend fun signOut() {
            if (!enabled) return
            runCatching { Purchases.sharedInstance.awaitLogOut() }
        }
    }

    /// True once we've tried to load prices and come back with nothing. Lets the
    /// paywall show a retry instead of an eternal spinner.
    private val _offeringFailed = MutableStateFlow(false)
    val offeringFailed: StateFlow<Boolean> = _offeringFailed.asStateFlow()

    /// True once a load attempt has actually *finished* (success or failure), so the
    /// paywall can tell "still fetching" apart from "fetched, nothing to sell."
    /// Mirrors iOS `didLoadOffering`.
    private val _didLoadOffering = MutableStateFlow(false)
    val didLoadOffering: StateFlow<Boolean> = _didLoadOffering.asStateFlow()

    /// We finished a fetch and still have no purchasable yearly product — the
    /// App-Review-deadlock case (products rejected/unavailable, or a network
    /// failure). The paywall must show a real message + retry here, never an endless
    /// spinner. Mirrors iOS `productsUnavailable` = didLoadOffering && yearlyPackage == nil.
    val productsUnavailable: Boolean get() = _didLoadOffering.value && _yearly.value == null

    /// Fetches the offering once and keeps it. The paywall used to call this on
    /// every open, so each visit paid the full RevenueCat round-trip (plus Play's
    /// product lookup) before it could draw the buttons — a wait long enough to
    /// look broken. Prices don't change between screens, so hold them: prefetch at
    /// launch, and a second call is a no-op unless the first one failed.
    ///
    /// The fetch is bounded. RevenueCat retries internally when Play can't be
    /// reached (a misconfigured store credential does exactly that), and without a
    /// ceiling the caller waits on those retries with nothing on screen. Better to
    /// give up at 10s and offer a retry than to leave someone staring at a spinner.
    suspend fun loadOffering(force: Boolean = false) {
        // Match iOS: when billing is disabled (no key), a load "finished" with nothing
        // to sell — so didLoadOffering is true even though we never called the store.
        if (!enabled) { _didLoadOffering.value = true; return }
        if (!force && _yearly.value != null) return
        val ok = runCatching {
            kotlinx.coroutines.withTimeout(10_000) {
                val packages = Purchases.sharedInstance.awaitOfferings()
                    .current?.availablePackages.orEmpty()
                _yearly.value = packages.firstOrNull {
                    it.product.id.startsWith(YEARLY_PRODUCT_ID)
                } ?: packages.firstOrNull()
                _lifetime.value = packages.firstOrNull {
                    it.product.id.startsWith(LIFETIME_PRODUCT_ID)
                }
            }
        }.isSuccess
        _offeringFailed.value = !ok || _yearly.value == null
        // A load attempt has now finished (success or failure) — mirrors iOS.
        _didLoadOffering.value = true
    }

    /// Buys the membership. Returns true once the purchase completes — the caller
    /// then refreshes the profile, because access is granted by the server (via
    /// RevenueCat's webhook), not by this return value.
    ///
    /// `userId` is the Supabase user id. We re-assert `logIn` here, immediately
    /// before buying, because the identify() fired at auth time can lose the race
    /// with the user reaching this button — especially on a fresh Play install,
    /// where the SDK starts with an anonymous id. If the purchase attaches to that
    /// anonymous id, the webhook has no Supabase user to grant and the payment is
    /// stranded. Gating the buy on the real id closes that race for good.
    suspend fun purchase(activity: Activity, pkg: Package?, userId: String?): Boolean {
        if (pkg == null) {
            _purchaseError.value = "unavailable"
            Observability.capture(AnalyticsEvent.PURCHASE_FAILED, mapOf(AnalyticsProp.REASON to AnalyticsValue.Reason.PRODUCT_UNAVAILABLE.key))
            return false
        }
        // The tap, before the Play sheet appears.
        val plan = planOf(pkg)
        Observability.capture(AnalyticsEvent.PLAN_TAPPED, mapOf(AnalyticsProp.PLAN to plan))
        if (enabled && userId != null && Purchases.sharedInstance.appUserID != userId) {
            runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
        }
        _isPurchasing.value = true
        _purchaseError.value = null
        return try {
            Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            Observability.capture(
                AnalyticsEvent.PURCHASE_COMPLETED,
                mapOf(AnalyticsProp.PLAN to plan, AnalyticsProp.STORE to AnalyticsValue.Store.PLAY_STORE.key),
            )
            true
        } catch (e: Exception) {
            Observability.capture(AnalyticsEvent.PURCHASE_FAILED, mapOf(AnalyticsProp.REASON to failureReason(e).key))
            // A user-cancelled purchase isn't an error worth surfacing.
            if (e is PurchasesException && e.code == PurchasesErrorCode.PurchaseCancelledError) {
                false
            } else {
                _purchaseError.value = e.message
                false
            }
        } finally {
            _isPurchasing.value = false
        }
    }

    /// `plan` for the analytics events: which of the two products this package is.
    private fun planOf(pkg: Package): String =
        if (pkg.product.id.startsWith(LIFETIME_PRODUCT_ID)) AnalyticsValue.Plan.LIFETIME.key
        else AnalyticsValue.Plan.YEARLY.key

    /// A short code for `reason`, never `e.message` — that string is translated,
    /// and a German failure has to land in the same bucket as a Dutch one.
    private fun failureReason(e: Exception): AnalyticsValue.Reason {
        val code = (e as? PurchasesException)?.code ?: return AnalyticsValue.Reason.STORE_ERROR
        return when (code) {
            PurchasesErrorCode.PurchaseCancelledError -> AnalyticsValue.Reason.CANCELLED
            PurchasesErrorCode.NetworkError -> AnalyticsValue.Reason.NETWORK
            PurchasesErrorCode.ProductNotAvailableForPurchaseError -> AnalyticsValue.Reason.PRODUCT_UNAVAILABLE
            else -> AnalyticsValue.Reason.STORE_ERROR
        }
    }

    /// Play requires a restore path for subscriptions too.
    suspend fun restore(): Boolean {
        Observability.capture(AnalyticsEvent.RESTORE_TAPPED)
        if (!enabled) return false
        _isPurchasing.value = true
        _purchaseError.value = null
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            info.entitlements.active.isNotEmpty()
        } catch (e: Exception) {
            _purchaseError.value = e.message
            false
        } finally {
            _isPurchasing.value = false
        }
    }
}
