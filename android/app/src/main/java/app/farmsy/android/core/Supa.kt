package app.farmsy.android.core

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/// Supabase client — same project + anon key as iOS. Auth sessions persist
/// on-device automatically (supabase-kt stores them in SharedPreferences).
val supabase = createSupabaseClient(
    supabaseUrl = Backend.SUPABASE_URL,
    supabaseKey = Backend.SUPABASE_ANON_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Storage)
}
