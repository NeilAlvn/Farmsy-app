package app.farmsy.android.core

import android.content.Context
import app.farmsy.android.BuildConfig
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.PostHog
import io.sentry.android.core.SentryAndroid
import io.sentry.Sentry

/// Crash reporting (Sentry) + product analytics (PostHog) — mirrors iOS
/// Observability.
///
/// Both are keyed from the gitignored secrets.properties and are **no-ops when
/// their key is absent**, so debug builds behave normally.
///
/// PostHog note: the app shares the *same* project key as the website and calls
/// `identify` with the Supabase user id — the same id passed to
/// `Purchases.logIn`. One project + one id means a web signup who later opens the
/// app is a single user, not two fragments.
object Observability {

    fun start(context: Context) {
        if (Backend.SENTRY_DSN.isNotEmpty()) {
            SentryAndroid.init(context) { options ->
                options.dsn = Backend.SENTRY_DSN
                options.isSendDefaultPii = false          // no emails/IPs — matches web
                options.tracesSampleRate = 0.2
                options.environment = if (BuildConfig.DEBUG) "debug" else "production"
                options.isEnabled = !BuildConfig.DEBUG     // don't report from dev
            }
        }
        if (Backend.POSTHOG_KEY.isNotEmpty()) {
            val config = PostHogAndroidConfig(
                apiKey = Backend.POSTHOG_KEY,
                host = Backend.POSTHOG_HOST,
            )
            PostHogAndroid.setup(context, config)
        }
    }

    /// Tie analytics + crash reports to the signed-in user (Supabase id).
    fun identify(userId: String) {
        if (Backend.POSTHOG_KEY.isNotEmpty()) PostHog.identify(userId)
        if (Backend.SENTRY_DSN.isNotEmpty()) {
            Sentry.configureScope { scope ->
                scope.user = io.sentry.protocol.User().apply { id = userId }
            }
        }
    }

    /// On sign-out, stop attributing events to the previous user.
    fun reset() {
        if (Backend.POSTHOG_KEY.isNotEmpty()) PostHog.reset()
        if (Backend.SENTRY_DSN.isNotEmpty()) Sentry.configureScope { it.user = null }
    }

    /// Fire-and-forget product event.
    fun capture(event: String, props: Map<String, Any> = emptyMap()) {
        if (Backend.POSTHOG_KEY.isEmpty()) return
        PostHog.capture(event, properties = props)
    }
}
