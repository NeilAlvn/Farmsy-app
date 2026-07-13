import Foundation
import Supabase

// Backend endpoints. Only the public URL and the anon (publishable) key ship
// in the app — the service_role key and all Stripe secrets live exclusively
// on the server. Farm detail stays gated server-side via the web API.
enum Backend {
    static let supabaseURL = URL(string: "https://lxkyypmzxfkzddraxtat.supabase.co")!
    static let supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx4a3l5cG16eGZremRkcmF4dGF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4NjA5NzMsImV4cCI6MjA5MzQzNjk3M30.6WpDjAtun7TUixlqMBZrvDn57TXNKY8sIaGxPvX0fyU"
    static let webAPI = URL(string: "https://www.farmsy.app/api")!

    /// RevenueCat's *public* SDK key (safe to ship — it can only start purchases,
    /// never read revenue or grant entitlements). Read from Secrets.plist, which
    /// is gitignored; the app degrades gracefully to "no purchases" if absent, so
    /// builds without it still run.
    static let revenueCatKey: String = {
        guard let url = Bundle.main.url(forResource: "Secrets", withExtension: "plist"),
              let data = try? Data(contentsOf: url),
              let dict = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any],
              let key = dict["RevenueCatPublicKey"] as? String
        else { return "" }
        return key
    }()
}

let supabase = SupabaseClient(
    supabaseURL: Backend.supabaseURL,
    supabaseKey: Backend.supabaseAnonKey
)
