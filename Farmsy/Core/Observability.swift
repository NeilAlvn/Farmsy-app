import Foundation
import Sentry
import PostHog

/// Crash reporting (Sentry) + product analytics (PostHog).
///
/// Both are keyed from Secrets.plist and are **no-ops when their key is absent**,
/// so debug builds and un-provisioned CI builds behave normally.
///
/// PostHog note: the app shares the *same* project key as the website, and calls
/// `identify` with the Supabase user id — the same id passed to
/// `Purchases.logIn`. One project + one id means a person who signs up on the web
/// and later opens the app is a single user, not two fragments.
enum Observability {

    static func start() {
        if !Backend.sentryDSN.isEmpty {
            SentrySDK.start { options in
                options.dsn = Backend.sentryDSN
                options.sendDefaultPii = false          // no emails/IPs — matches web
                options.tracesSampleRate = 0.2
                #if DEBUG
                options.environment = "debug"
                options.enabled = false                  // don't report from the simulator
                #else
                options.environment = "production"
                #endif
            }
        }

        if !Backend.postHogKey.isEmpty {
            let config = PostHogConfig(apiKey: Backend.postHogKey, host: Backend.postHogHost)
            config.captureApplicationLifecycleEvents = true
            PostHogSDK.shared.setup(config)
        }
    }

    /// Tie analytics + crash reports to the signed-in user (Supabase id).
    static func identify(userId: String) {
        if !Backend.postHogKey.isEmpty { PostHogSDK.shared.identify(userId) }
        if !Backend.sentryDSN.isEmpty {
            SentrySDK.configureScope { scope in
                scope.setUser(User(userId: userId))
            }
        }
    }

    /// On sign-out, stop attributing events to the previous user.
    static func reset() {
        if !Backend.postHogKey.isEmpty { PostHogSDK.shared.reset() }
        if !Backend.sentryDSN.isEmpty { SentrySDK.configureScope { $0.setUser(nil) } }
    }

    /// Fire-and-forget product event.
    static func capture(_ event: String, _ props: [String: Any] = [:]) {
        guard !Backend.postHogKey.isEmpty else { return }
        PostHogSDK.shared.capture(event, properties: props)
    }
}
