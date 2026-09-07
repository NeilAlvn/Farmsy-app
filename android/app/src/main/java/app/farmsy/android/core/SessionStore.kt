package app.farmsy.android.core

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class AuthException(message: String) : Exception(message) {
    class InvalidCredentials : AuthException("invalid_credentials")
    class EmailTaken : AuthException("email_taken")
    class Throttled : AuthException("throttled")
    /// The account exists and the password is right — they just haven't clicked
    /// the link in their email yet. Must not be shown as a credentials failure.
    class EmailNotVerified : AuthException("email_not_verified")
    class MissingFields(val fields: List<String>) : AuthException("missing_fields")
    class InvalidDob : AuthException("invalid_dob")
    class Server(val serverMessage: String) : AuthException(serverMessage)
}

/// Everything POST /api/auth/signup requires. Mirrors the web's two-step form:
/// credentials, then personal details + address. `refCode` is the only optional.
data class SignUpDetails(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val dob: String,            // ISO yyyy-MM-dd, must be a real date, 16+
    val streetAddress: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val refCode: String? = null,
)

/// Auth + subscription state — mirrors iOS SessionStore.swift.
/// Sign-in/up go through the farmsy.app API (same throttling and branded
/// verification emails as the website); the returned tokens then hydrate the
/// Supabase client session on-device.
class SessionStore(private val scope: CoroutineScope) {

    init {
        // `is_member` on every analytics event, read live from the profile.
        Observability.isMemberProvider = { hasFullAccess }
    }

    /// app_opened fires once per process, after the session restored (or the
    /// bootstrap gave up waiting), so `is_member` is right on the first event.
    private val appOpenedFired = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun fireAppOpened() {
        if (appOpenedFired.compareAndSet(false, true)) Observability.capture(AnalyticsEvent.APP_OPENED)
    }

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _isBootstrapped = MutableStateFlow(false)
    val isBootstrapped: StateFlow<Boolean> = _isBootstrapped.asStateFlow()

    val isAuthenticated: Boolean get() = _session.value != null
    val hasFullAccess: Boolean get() = _profile.value?.hasFullAccess ?: false
    val email: String get() = _session.value?.user?.email ?: ""

    /// Author name for posts/reviews, composed the way the web does: full name
    /// from the profile, else the email prefix, else "Someone". Mirrors iOS
    /// SessionStore.displayName.
    val displayName: String
        get() {
            val full = listOfNotNull(_profile.value?.firstName, _profile.value?.lastName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (full.isNotEmpty()) return full
            val prefix = email.substringBefore("@")
            return prefix.ifEmpty { "Someone" }
        }

    fun bootstrap() {
        // Never let a slow or missing network hold the splash hostage: if auth
        // hasn't reported within a couple of seconds, carry on as a guest.
        scope.launch {
            delay(2500)
            _isBootstrapped.value = true
            fireAppOpened()
        }
        scope.launch {
            // Mirror iOS: observe auth state; the Auth plugin loads the stored
            // session itself and emits through sessionStatus.
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _session.value = status.session
                        _isBootstrapped.value = true
                        // RevenueCat must know the Supabase user id before any
                        // purchase, or its webhook can't find the profile to grant.
                        status.session.user?.id?.let {
                            PurchaseStore.identify(it)
                            Observability.identify(it)
                        }
                        refreshProfile()
                        fireAppOpened()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _session.value = null
                        _profile.value = null
                        _isBootstrapped.value = true
                        PurchaseStore.signOut()
                        Observability.reset()
                        fireAppOpened()
                    }
                    // RefreshFailure (offline, expired refresh token) and any
                    // other terminal state: stop blocking the UI.
                    is SessionStatus.RefreshFailure -> { _isBootstrapped.value = true; fireAppOpened() }
                    else -> Unit // Initializing / load from storage in flight
                }
            }
        }
    }

    /// Subscription status comes from the farmsy.app API (service-role read on
    /// the server) rather than a direct `profiles` select — the table's RLS
    /// policies aren't a dependency of the app this way.
    suspend fun refreshProfile() = withContext(Dispatchers.IO) {
        val token = freshAccessToken() ?: return@withContext
        runCatching {
            val resp = httpClient.get("${Backend.WEB_API}/profile/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (resp.status.value == 200) {
                _profile.value = lenientJson.decodeFromString<Profile>(resp.bodyAsText())
            }
        } // Keep the last known profile on transient failures.
        Unit
    }

    /// Ask for a *current* token so an expired one is refreshed first — a
    /// stale cached token would 401 and silently look un-subscribed.
    private suspend fun freshAccessToken(): String? {
        val current = supabase.auth.currentSessionOrNull() ?: return null
        val expiresSoon = current.expiresAt < kotlinx.datetime.Clock.System.now()
            .plus(kotlin.time.Duration.parse("30s"))
        if (expiresSoon) runCatching { supabase.auth.refreshCurrentSession() }
        return supabase.auth.currentAccessTokenOrNull()
    }

    suspend fun accessToken(): String? = freshAccessToken()

    @Serializable
    private data class LoginTokens(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    )

    @Serializable
    private data class LoginResponse(val session: LoginTokens)

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        val code: String? = null,
        val fields: List<String>? = null,
    )

    suspend fun logIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val (body, status) = postJson("auth/login", mapOf("email" to email, "password" to password))
        when (status) {
            200 -> {
                val tokens = lenientJson.decodeFromString<LoginResponse>(body).session
                // Fetch the real user for the token instead of importing a session
                // with `user = null`. That null was poison: *every* downstream reader
                // goes through session.user — the id we hand RevenueCat as the
                // app_user_id, the email in Settings, favorites — so the app looked
                // signed out while holding a perfectly good token, and purchases
                // attached to an anonymous RevenueCat customer with no profile to
                // grant. retrieveUser() resolves the token to its actual user.
                val user = runCatching { supabase.auth.retrieveUser(tokens.accessToken) }.getOrNull()
                supabase.auth.importSession(
                    UserSession(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = 3600,
                        tokenType = "bearer",
                        user = user
                    )
                )
                // No /api/session/create call on purpose: active_sessions only backs
                // admin auth and the web's single-session guard, neither of which the
                // app uses. Creating one would mean owning a token lifecycle for no
                // user-facing benefit. (Confirmed with Aviah, 2026-07-14.)
                refreshProfile()
            }
            // The email-verified gate lives in the API now. This is NOT a wrong
            // password — telling the user so would send them to reset a password
            // that works fine.
            403 -> if (errorCode(body) == "email_not_verified") {
                throw AuthException.EmailNotVerified()
            } else throw AuthException.Server(serverMessage(body))
            401 -> throw AuthException.InvalidCredentials()
            429 -> throw AuthException.Throttled()
            else -> throw AuthException.Server(serverMessage(body))
        }
    }

    /// Creates the account. Deliberately does NOT log in afterwards: signup returns
    /// 200 but the account can't authenticate until the emailed link is clicked, so
    /// an auto-login would immediately 403 and read as "signup failed". The caller
    /// shows a "check your inbox" screen instead — same as the web.
    ///
    /// Every field except `refCode` is required server-side; the form gathers them
    /// all, but we still surface `missing_fields` so a drift between client and API
    /// shows up as a precise error rather than a generic one.
    suspend fun signUp(details: SignUpDetails) = withContext(Dispatchers.IO) {
        val payload = buildMap {
            put("email", details.email); put("password", details.password)
            put("firstName", details.firstName); put("lastName", details.lastName)
            put("dob", details.dob)
            put("streetAddress", details.streetAddress); put("city", details.city)
            put("postalCode", details.postalCode); put("country", details.country)
            // Uppercased to match the web's cookie contract.
            details.refCode?.takeIf { it.isNotBlank() }?.let { put("refCode", it.uppercase()) }
        }
        val (body, status) = postJson("auth/signup", payload)
        when (status) {
            // Verify-email screen next; no session yet, so this is not a login.
            200, 201 -> Observability.capture(
                AnalyticsEvent.SIGNUP_COMPLETED,
                mapOf(AnalyticsProp.METHOD to AnalyticsValue.Method.EMAIL.key),
            )
            409 -> throw AuthException.EmailTaken()
            429 -> throw AuthException.Throttled()
            400 -> when (errorCode(body)) {
                "missing_fields" -> throw AuthException.MissingFields(missingFields(body))
                "invalid_dob" -> throw AuthException.InvalidDob()
                "missing_credentials" -> throw AuthException.InvalidCredentials()
                else -> throw AuthException.Server(serverMessage(body))
            }
            else -> throw AuthException.Server(serverMessage(body))
        }
    }

    /// Branch on `code`, never the human-readable message (Aviah's contract).
    private fun errorCode(body: String): String? =
        runCatching { lenientJson.decodeFromString<ErrorBody>(body).code }.getOrNull()

    private fun missingFields(body: String): List<String> =
        runCatching { lenientJson.decodeFromString<ErrorBody>(body).fields ?: emptyList() }
            .getOrDefault(emptyList())

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
        _session.value = null
        _profile.value = null
    }

    /// In-app account deletion (Apple 5.1.1(v) / Play policy). Calls the server,
    /// which actually erases the account, then signs the local session out. The
    /// server can't touch a store subscription — Apple/Google own those contracts —
    /// so it flags `storeSubscriptionReminder` when the person still has a live
    /// store sub they should cancel themselves, and we surface that before leaving.
    ///
    /// `subscriptionSource` names the rail that actually charged them. It isn't
    /// always this platform: someone can subscribe on Android, install the iOS app,
    /// and delete from there — telling them to cancel in the App Store would send
    /// them somewhere with nothing to cancel while Play kept billing.
    suspend fun deleteAccount(): DeleteResult = withContext(Dispatchers.IO) {
        val token = freshAccessToken() ?: return@withContext DeleteResult(false, false, null)
        val result = runCatching {
            val resp = httpClient.post("${Backend.WEB_API}/account/delete") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (resp.status.value in 200..299) {
                val body = runCatching {
                    lenientJson.decodeFromString<DeleteResponse>(resp.bodyAsText())
                }.getOrNull()
                DeleteResult(
                    ok = true,
                    storeSubscriptionReminder = body?.storeSubscriptionReminder == true,
                    subscriptionSource = body?.subscriptionSource,
                )
            } else DeleteResult(false, false, null)
        }.getOrDefault(DeleteResult(false, false, null))
        // Only drop the local session once the server confirms the erase.
        if (result.ok) signOut()
        result
    }

    data class DeleteResult(
        val ok: Boolean,
        val storeSubscriptionReminder: Boolean,
        val subscriptionSource: String? = null,
    )

    @Serializable
    private data class DeleteResponse(
        val ok: Boolean = false,
        @SerialName("storeSubscriptionReminder") val storeSubscriptionReminder: Boolean = false,
        @SerialName("subscription_source") val subscriptionSource: String? = null,
    )

    private suspend fun postJson(path: String, body: Map<String, String>): Pair<String, Int> {
        val resp = httpClient.post("${Backend.WEB_API}/$path") {
            contentType(ContentType.Application.Json)
            setBody(
                kotlinx.serialization.json.buildJsonObject {
                    body.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
                }.toString()
            )
        }
        return resp.bodyAsText() to resp.status.value
    }

    private fun serverMessage(body: String): String =
        runCatching { lenientJson.decodeFromString<ErrorBody>(body).error }.getOrNull()
            ?: "server_error"
}
