package com.example.anubhav.core.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.normalizedUrl(),
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = "anubhav"
                host = "reset-password"
                defaultRedirectUrl = "anubhav://reset-password"
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
