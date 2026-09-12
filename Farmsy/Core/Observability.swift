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
            // Automatic screen capture is OFF. In SwiftUI every sheet is hosted in the
            // same PresentationHostingController<AnyView>, so the SDK's autocapture
            // reports that one meaningless class name for every screen — it floods the
            // feed and buries the thirteen events we name deliberately (and PostHog
            // bills on volume). Lifecycle events stay: "Application Opened" is one
            // useful session marker, not one-per-sheet.
            config.captureScreenViews = false
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

    /// Answers "is this person a member right now?" for the `is_member` property.
    /// Read at fire time, never cached at launch, so someone who buys mid-session
    /// flips on their next event. SessionStore installs the real answer.
    @MainActor static var isMemberProvider: @MainActor () -> Bool = { false }

    private static let appVersion: String =
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""

    /// Fire-and-forget product event. The name has to be an `AnalyticsEvent` —
    /// there is no string overload, so a typo cannot create a new event — and
    /// every event carries `is_member`, `platform` and `app_version`.
    @MainActor
    static func capture(_ event: AnalyticsEvent, _ props: [String: Any] = [:]) {
        guard !Backend.postHogKey.isEmpty else { return }
        var all = props
        all[AnalyticsProp.isMember] = isMemberProvider()
        all[AnalyticsProp.platform] = "ios"
        all[AnalyticsProp.appVersion] = appVersion
        PostHogSDK.shared.capture(event.rawValue, properties: all)
    }
}
