package app.farmsy.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.farmsy.android.ui.theme.FarmsyTheme

/// Referral codes are 6–12 chars, A–Z0–9 — the same shape the web validates before
/// dropping its cookie. Anything else in the link is ignored rather than sent on to
/// the API to be rejected.
private val REF_CODE = Regex("^[A-Z0-9]{6,12}$")

/// How long a captured code stays good, matching the web cookie's 7 days.
private const val REF_TTL_MS = 7L * 24 * 60 * 60 * 1000

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureReferral(intent)
        val app = application as FarmsyApp
        setContent {
            CompositionLocalProvider(
                LocalSession provides app.session,
                LocalFarms provides app.farms,
                LocalFavorites provides app.favorites,
                LocalLocationHelper provides app.locationHelper,
                LocalPurchases provides app.purchases,
            ) {
                FarmsyTheme {
                    RootNav()
                }
            }
        }
    }

    /// The app is `singleTop`-ish in practice: a referral link tapped while we're
    /// already running arrives here, not in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureReferral(intent)
    }

    /// Pull `?ref=CODE` out of a farmsy.app/join link and hold it until signup.
    ///
    /// This is the write that was missing: AuthSheet has always *read* and cleared
    /// `pendingRefCode`, so the plumbing looked complete — but nothing ever put a
    /// code in it, and every Android referral was silently dropped. The web does the
    /// same thing with a 7-day cookie; SharedPreferences plus an expiry is our
    /// equivalent.
    private fun captureReferral(intent: Intent?) {
        val code = intent?.data
            ?.takeIf { it.path?.startsWith("/join") == true }
            ?.getQueryParameter("ref")
            ?.trim()
            ?.uppercase()
            ?.takeIf { REF_CODE.matches(it) }
            ?: return

        getSharedPreferences("farmsy", Context.MODE_PRIVATE).edit()
            .putString("pendingRefCode", code)
            .putLong("pendingRefCodeAt", System.currentTimeMillis())
            .apply()
    }
}

/// The stored code, or null once it's older than the web's 7-day window.
fun Context.pendingRefCode(): String? {
    val prefs = getSharedPreferences("farmsy", Context.MODE_PRIVATE)
    val code = prefs.getString("pendingRefCode", "").orEmpty()
    if (code.isEmpty()) return null
    val savedAt = prefs.getLong("pendingRefCodeAt", 0L)
    if (savedAt > 0 && System.currentTimeMillis() - savedAt > REF_TTL_MS) return null
    return code
}
