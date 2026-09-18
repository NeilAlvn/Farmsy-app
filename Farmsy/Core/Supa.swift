import Foundation
import Supabase

// Backend endpoints. Only the public URL and the anon (publishable) key ship
// in the app — the service_role key and all Stripe secrets live exclusively
// on the server. Farm detail stays gated server-side via the web API.
enum Backend {
    static let supabaseURL = URL(string: "https://lxkyypmzxfkzddraxtat.supabase.co")!
    static let supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx4a3l5cG16eGZremRkcmF4dGF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4NjA5NzMsImV4cCI6MjA5MzQzNjk3M30.6WpDjAtun7TUixlqMBZrvDn57TXNKY8sIaGxPvX0fyU"
    static let webAPI: URL = {
        #if DEBUG
        // `--api-base http://localhost:3000/api` points a debug build at a local web checkout.
        if let i = CommandLine.arguments.firstIndex(of: "--api-base"), i + 1 < CommandLine.arguments.count,
           let u = URL(string: CommandLine.arguments[i + 1]) { return u }
        #endif
        return URL(string: "https://www.farmsy.app/api")!
    }()

    /// All public client keys live in Secrets.plist (gitignored). Every value is
    /// optional: a missing key disables its feature rather than crashing, so
    /// builds without secrets still run.
    private static let secrets: [String: Any] = {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let data = try? Data(contentsOf: url),
              let dict = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
        else { return [:] }
        return dict
    }()

    private static func secret(_ key: String) -> String {
        (secrets[key] as? String) ?? ""
    }

    /// RevenueCat's *public* SDK key — it can only start purchases, never read
    /// revenue or grant entitlements.
    static let revenueCatKey = secret("RevenueCatPublicKey")

    /// Sentry DSN (public by design) + PostHog project key/host.
    static let sentryDSN = secret("SentryDSN")
    static let postHogKey = secret("PostHogKey")
    static var postHogHost: String {
        let h = secret("PostHogHost")
        return h.isEmpty ? "https://eu.i.posthog.com" : h
    }
}

let supabase = SupabaseClient(
    supabaseURL: Backend.supabaseURL,
    supabaseKey: Backend.supabaseAnonKey
)
