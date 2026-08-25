package app.farmsy.android

import android.app.Application
import app.farmsy.android.core.FarmsStore
import app.farmsy.android.core.Observability
import app.farmsy.android.core.FavoritesStore
import app.farmsy.android.core.LocationHelper
import app.farmsy.android.core.PurchaseStore
import app.farmsy.android.core.SessionStore
import app.farmsy.android.core.TripStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/// App-scoped singletons — the Android twin of the @Observable stores the
/// iOS app injects via .environment().
class FarmsyApp : Application() {

    // Default (not Main): stores do network + JSON work; StateFlow updates are
    // thread-safe and Compose collects them on the UI thread anyway.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var session: SessionStore
        private set
    lateinit var farms: FarmsStore
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var locationHelper: LocationHelper
        private set
    lateinit var purchases: PurchaseStore
        private set
    lateinit var trip: TripStore
        private set

    override fun onCreate() {
        super.onCreate()
        Observability.start(this)
        PurchaseStore.configure(this)
        session = SessionStore(appScope)
        farms = FarmsStore(appScope)
        favorites = FavoritesStore()
        locationHelper = LocationHelper(this)
        purchases = PurchaseStore()
        trip = TripStore(this, appScope)
        session.bootstrap()
        farms.loadIfNeeded()
        // Warm the store prices now, in the background, so the paywall has them
        // in hand the moment someone opens a locked farm. Fetching them on demand
        // meant staring at a spinner through a RevenueCat round-trip.
        appScope.launch { purchases.loadOffering() }
    }
}
