package app.farmsy.android

import androidx.compose.runtime.staticCompositionLocalOf
import app.farmsy.android.core.FarmsStore
import app.farmsy.android.core.FavoritesStore
import app.farmsy.android.core.LocationHelper
import app.farmsy.android.core.PurchaseStore
import app.farmsy.android.core.SessionStore

// The Android twin of the iOS .environment() injections.
val LocalSession = staticCompositionLocalOf<SessionStore> { error("SessionStore not provided") }
val LocalFarms = staticCompositionLocalOf<FarmsStore> { error("FarmsStore not provided") }
val LocalFavorites = staticCompositionLocalOf<FavoritesStore> { error("FavoritesStore not provided") }
val LocalLocationHelper = staticCompositionLocalOf<LocationHelper> { error("LocationHelper not provided") }
val LocalPurchases = staticCompositionLocalOf<PurchaseStore> { error("PurchaseStore not provided") }

/// Environment hook for "this action needs an account" — mirrors iOS
/// AuthGate.swift. RootNav installs the real implementation (opens the
/// login sheet); the default is a no-op so previews keep working.
val LocalRequestAuth = staticCompositionLocalOf<() -> Unit> { {} }
