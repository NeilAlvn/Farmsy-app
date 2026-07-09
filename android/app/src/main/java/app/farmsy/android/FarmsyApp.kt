package app.farmsy.android

import android.app.Application
import app.farmsy.android.core.FarmsStore
import app.farmsy.android.core.FavoritesStore
import app.farmsy.android.core.LocationHelper
import app.farmsy.android.core.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/// App-scoped singletons — the Android twin of the @Observable stores the
/// iOS app injects via .environment().
class FarmsyApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var session: SessionStore
        private set
    lateinit var farms: FarmsStore
        private set
    lateinit var favorites: FavoritesStore
        private set
    lateinit var locationHelper: LocationHelper
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(appScope)
        farms = FarmsStore(appScope)
        favorites = FavoritesStore()
        locationHelper = LocationHelper(this)
        session.bootstrap()
        farms.loadIfNeeded()
    }
}
