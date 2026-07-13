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

    suspend fun loadOffering() {
        if (!enabled) return
        runCatching {
            val packages = Purchases.sharedInstance.awaitOfferings()
                .current?.availablePackages.orEmpty()
            _yearly.value = packages.firstOrNull {
                it.product.id.startsWith(YEARLY_PRODUCT_ID)
            } ?: packages.firstOrNull()
            _lifetime.value = packages.firstOrNull {
                it.product.id.startsWith(LIFETIME_PRODUCT_ID)
            }
        }
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
            return false
        }
        if (enabled && userId != null && Purchases.sharedInstance.appUserID != userId) {
            runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
        }
        _isPurchasing.value = true
        _purchaseError.value = null
        return try {
            Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            true
        } catch (e: Exception) {
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

    /// Play requires a restore path for subscriptions too.
    suspend fun restore(): Boolean {
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
