package app.farmsy.android.core

import android.content.Context
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/// The onboarding answers, kept (P0-3) — mirrors iOS Preferences.swift.
///
/// Seven screens of questions used to be honoured for one session and then
/// forgotten. These five fields — the chosen categories and the four quick
/// flags — now live in `profiles.preferences` on the server and in
/// SharedPreferences on the device, and are applied to `FarmsStore` before the
/// map first renders.
///
/// The wire shape is the web's (src/lib/preferences.ts); snake_case keys.
@Serializable
data class Preferences(
    /// FarmCategory raw values, in the order they were picked. Empty means
    /// "show everything" — never "match nothing".
    val categories: List<String> = emptyList(),
    @SerialName("open_today") val openToday: Boolean = false,
    @SerialName("pick_your_own") val pickYourOwn: Boolean = false,
    val verified: Boolean = false,
    @SerialName("has_photos") val hasPhotos: Boolean = false,
) {
    /// The categories that still exist. A stored id that no longer exists is
    /// ignored, not an error.
    val knownCategories: Set<FarmCategory>
        get() = categories.mapNotNull { id -> FarmCategory.entries.firstOrNull { it.raw == id } }.toSet()
}

/// Keeps `FarmsStore`'s five preference fields, the device mirror and the
/// server in agreement. Mirrors iOS PreferencesSync.
///
/// THE CONFLICT RULE, written down because it will not be obvious in six months:
///   1. Server wins on login. When the profile loads and the server has
///      preferences, they replace whatever the device held.
///   2. Device wins while offline. A change made when the server cannot be
///      reached is kept on the device and marked pending.
///   3. Device writes through on reconnect. The next time the profile loads
///      with a pending change, the device's value is pushed, not overwritten.
///   4. A fresh account (server has nothing) takes the device's answers — that
///      is the onboarding-while-signed-out case.
///   5. Signed-out users keep preferences on the device only.
class PreferencesSync(
    context: Context,
    private val scope: CoroutineScope,
    private val session: SessionStore,
    private val farms: FarmsStore,
) {
    private val prefs = context.getSharedPreferences("farmsy", Context.MODE_PRIVATE)

    /// What was last applied from the device or the server. A change that only
    /// echoes it (applying server values makes the flows emit too) is not a
    /// user edit and must not be pushed back.
    @Volatile private var lastApplied: Preferences? = null

    /// Call once, right after the stores exist and before anything renders.
    @OptIn(FlowPreview::class)
    fun start() {
        // Device first, before the first render: a cold start with no network
        // still gets the map it was personalised to.
        loadDevice()?.let { apply(it) }

        scope.launch {
            combine(
                farms.selectedCategories, farms.filterOpenToday, farms.filterZelfpluk,
                farms.filterVerified, farms.filterHasPhotos,
            ) { _, _, _, _, _ -> farms.snapshotPreferences() }
                .drop(1)            // the initial state is not a change
                .debounce(1000)     // coalesce a burst of toggles into one write
                .collect { changed(it) }
        }
        scope.launch {
            session.profile.collect { profile -> if (profile != null) profileLoaded(profile) }
        }
    }

    // ── Inbound ──────────────────────────────────────────────────────────────

    /// The profile arrived: login, cold start with a session, or a foreground
    /// refresh. Applies the rule above.
    private suspend fun profileLoaded(profile: Profile) {
        val current = farms.snapshotPreferences()
        if (prefs.getBoolean(PENDING_KEY, false)) {
            push(current)                                  // rule 3
            return
        }
        val server = profile.preferences
        if (server != null) {
            if (server != current) apply(server)           // rule 1
            saveDevice(server)
        } else if (loadDevice() != null) {
            push(current)                                  // rule 4
        }
    }

    private fun apply(p: Preferences) {
        lastApplied = p
        farms.applyPreferences(p)
    }

    // ── Outbound ─────────────────────────────────────────────────────────────

    private suspend fun changed(snapshot: Preferences) {
        if (snapshot == lastApplied) return                // an echo, not an edit
        lastApplied = snapshot
        saveDevice(snapshot)                               // device mirror, always
        if (!session.isAuthenticated) return               // rule 5
        push(snapshot)
    }

    private suspend fun push(p: Preferences) {
        if (!session.isAuthenticated) return
        val token = session.accessToken()
        if (token == null) { setPending(true); return }
        val ok = runCatching {
            httpClient.put("${Backend.WEB_API}/profile/preferences") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(lenientJson.encodeToString(p))
            }.status.value == 200
        }.getOrDefault(false)
        setPending(!ok)                                    // rule 2 when !ok
    }

    // ── Device mirror ────────────────────────────────────────────────────────

    private fun loadDevice(): Preferences? =
        prefs.getString(DEVICE_KEY, null)?.let { raw ->
            runCatching { lenientJson.decodeFromString<Preferences>(raw) }.getOrNull()
        }

    private fun saveDevice(p: Preferences) {
        prefs.edit().putString(DEVICE_KEY, lenientJson.encodeToString(p)).apply()
    }

    private fun setPending(pending: Boolean) {
        prefs.edit().putBoolean(PENDING_KEY, pending).apply()
    }

    private companion object {
        const val DEVICE_KEY = "preferences"
        const val PENDING_KEY = "preferences_pending"
    }
}
