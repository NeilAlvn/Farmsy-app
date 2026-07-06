import Foundation
import Supabase

// Backend endpoints. Only the public URL and the anon (publishable) key ship
// in the app — the service_role key and all Stripe secrets live exclusively
// on the server. Farm detail stays gated server-side via the web API.
enum Backend {
    static let supabaseURL = URL(string: "https://lxkyypmzxfkzddraxtat.supabase.co")!
    static let supabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx4a3l5cG16eGZremRkcmF4dGF0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc4NjA5NzMsImV4cCI6MjA5MzQzNjk3M30.6WpDjAtun7TUixlqMBZrvDn57TXNKY8sIaGxPvX0fyU"
    static let webAPI = URL(string: "https://www.farmsy.app/api")!
}

let supabase = SupabaseClient(
    supabaseURL: Backend.supabaseURL,
    supabaseKey: Backend.supabaseAnonKey
)
