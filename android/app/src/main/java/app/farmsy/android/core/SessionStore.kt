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
    class Server(val serverMessage: String) : AuthException(serverMessage)
}

/// Auth + subscription state — mirrors iOS SessionStore.swift.
/// Sign-in/up go through the farmsy.app API (same throttling and branded
/// verification emails as the website); the returned tokens then hydrate the
/// Supabase client session on-device.
class SessionStore(private val scope: CoroutineScope) {

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _isBootstrapped = MutableStateFlow(false)
    val isBootstrapped: StateFlow<Boolean> = _isBootstrapped.asStateFlow()

    val isAuthenticated: Boolean get() = _session.value != null
    val hasFullAccess: Boolean get() = _profile.value?.hasFullAccess ?: false
    val email: String get() = _session.value?.user?.email ?: ""

    fun bootstrap() {
        // Never let a slow or missing network hold the splash hostage: if auth
        // hasn't reported within a couple of seconds, carry on as a guest.
        scope.launch {
            delay(2500)
            _isBootstrapped.value = true
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
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _session.value = null
                        _profile.value = null
                        _isBootstrapped.value = true
                        PurchaseStore.signOut()
                        Observability.reset()
                    }
                    // RefreshFailure (offline, expired refresh token) and any
                    // other terminal state: stop blocking the UI.
                    is SessionStatus.RefreshFailure -> _isBootstrapped.value = true
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
    private data class ErrorBody(val error: String? = null)

    suspend fun logIn(email: String, password: String) = withContext(Dispatchers.IO) {
        val (body, status) = postJson("auth/login", mapOf("email" to email, "password" to password))
        when (status) {
            200 -> {
                val tokens = lenientJson.decodeFromString<LoginResponse>(body).session
                supabase.auth.importSession(
                    UserSession(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = 3600,
                        tokenType = "bearer",
                        user = null
                    )
                )
                refreshProfile()
            }
            401 -> throw AuthException.InvalidCredentials()
            429 -> throw AuthException.Throttled()
            else -> throw AuthException.Server(serverMessage(body))
        }
    }

    suspend fun signUp(email: String, password: String, refCode: String?) = withContext(Dispatchers.IO) {
        val payload = buildMap {
            put("email", email); put("password", password)
            if (!refCode.isNullOrEmpty()) put("refCode", refCode)
        }
        val (body, status) = postJson("auth/signup", payload)
        when (status) {
            200, 201 -> logIn(email, password)
            409 -> throw AuthException.EmailTaken()
            429 -> throw AuthException.Throttled()
            else -> throw AuthException.Server(serverMessage(body))
        }
    }

    suspend fun signOut() {
        runCatching { supabase.auth.signOut() }
        _session.value = null
        _profile.value = null
    }

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
