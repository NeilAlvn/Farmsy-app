package app.farmsy.android.core

import app.farmsy.android.BuildConfig

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

// Backend endpoints — mirrors iOS Supa.swift. Only the public URL and the
// anon (publishable) key ship in the app; farm detail stays gated
// server-side via the web API.
object Backend {
    const val SUPABASE_URL = "https://lxkyypmzxfkzddraxtat.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx4a3l5cG16eGZremRkcmF4dGF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4NjA5NzMsImV4cCI6MjA5MzQzNjk3M30.6WpDjAtun7TUixlqMBZrvDn57TXNKY8sIaGxPvX0fyU"
    const val WEB_API = "https://www.farmsy.app/api"

    /// RevenueCat's *public* SDK key — safe to ship (it can only start purchases,
    /// never read revenue or grant entitlements). Injected from the gitignored
    /// android/secrets.properties at build time; empty means "no purchases", and
    /// the app still runs.
    val REVENUECAT_KEY: String = BuildConfig.REVENUECAT_KEY
}

/// Shared HTTP client for the farmsy.app API calls (auth, profile, detail).
val httpClient = HttpClient(OkHttp)

/// Lenient JSON for API payloads whose column types vary by source row.
val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
