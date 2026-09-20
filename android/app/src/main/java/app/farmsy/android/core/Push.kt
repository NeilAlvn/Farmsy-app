package app.farmsy.android.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.farmsy.android.MainActivity
import app.farmsy.android.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.Locale

// Push notifications: the phone tells the server how to reach it, and a
// notification tap opens the farm it is about. Port of Farmsy/Core/Push.swift.
//
// WHAT THIS DOES NOT DECIDE
// Whether a person gets alerts at all is the server's call (the two alert
// crons check the membership, the opt-out and the cooldown, then email and
// push). This file only keeps the FCM token registered for the signed-in
// account, and removed on sign-out.
//
// WHEN THE TOKEN IS SENT
// FCM hands out a token on first launch and rotates it now and then
// (`onNewToken`). It is posted when it differs from the last one posted for
// this account, so a launch costs no request in the common case.

object PushRegistrar {
    const val EXTRA_OSM_ID = "osmId"
    private const val PREFS = "push"
    private const val SENT_KEY = "pushTokenSent"   // "<userId>|<token>"
    private const val CHANNEL = "alerts"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /// A farm a notification tap asked for. The shell opens it and clears it.
    private val _pendingOsmId = MutableStateFlow<String?>(null)
    val pendingOsmId: StateFlow<String?> = _pendingOsmId.asStateFlow()
    fun consumePending() { _pendingOsmId.value = null }
    fun deliver(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OSM_ID)?.let { _pendingOsmId.value = it }
    }

    private var appContext: Context? = null
    private var deviceToken: String? = null
    private var userId: String? = null
    private var accessToken: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
    }

    /// Called whenever the session changes and on every resume. Asks FCM for
    /// the current token when notifications are allowed; the token also arrives
    /// unprompted through `onNewToken`.
    fun sync(userId: String?, accessToken: String?) {
        this.userId = userId
        this.accessToken = accessToken
        val ctx = appContext ?: return
        if (userId == null || !notificationsAllowed(ctx)) return
        scope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
            if (token != null) received(token)
        }
    }

    fun received(token: String) {
        deviceToken = token
        scope.launch { post() }
    }

    /// On sign-out, before the session is dropped: the phone must stop
    /// buzzing for an account it no longer holds.
    suspend fun unregister(accessToken: String) {
        val token = deviceToken ?: return
        runCatching {
            httpClient.delete("${Backend.WEB_API}/profile/push-token") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(lenientJson.encodeToString(TokenOnly(token)))
            }
        }
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(SENT_KEY)?.apply()
    }

    private suspend fun post() {
        val ctx = appContext ?: return
        val token = deviceToken ?: return
        val uid = userId ?: return
        val access = accessToken ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stamp = "$uid|$token"
        if (prefs.getString(SENT_KEY, null) == stamp) return
        val ok = runCatching {
            httpClient.post("${Backend.WEB_API}/profile/push-token") {
                header(HttpHeaders.Authorization, "Bearer $access")
                contentType(ContentType.Application.Json)
                setBody(lenientJson.encodeToString(Register("android", token, Locale.getDefault().language)))
            }.status.value == 200
        }.getOrDefault(false)
        if (ok) prefs.edit().putString(SENT_KEY, stamp).apply()
    }

    private fun notificationsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.push_channel_alerts), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /// Draw the banner. FCM only auto-displays a `notification` message while
    /// the app is in the background; in the foreground it lands in
    /// `onMessageReceived`, and an alert about a farm you are not looking at is
    /// still news, so both paths end up here.
    fun show(ctx: Context, title: String?, body: String?, osmId: String?) {
        if (!notificationsAllowed(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (osmId != null) putExtra(EXTRA_OSM_ID, osmId)
        }
        val pending = PendingIntent.getActivity(
            ctx, osmId?.hashCode() ?: 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title ?: ctx.getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify((osmId ?: title ?: "").hashCode(), n)
    }

    @Serializable private data class Register(val platform: String, val token: String, val locale: String)
    @Serializable private data class TokenOnly(val token: String)
}

/// FCM's callbacks. Declared in the manifest; the system instantiates it.
class FarmsyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistrar.received(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushRegistrar.show(
            this,
            message.notification?.title ?: message.data["title"],
            message.notification?.body ?: message.data["body"],
            message.data[PushRegistrar.EXTRA_OSM_ID],
        )
    }
}
