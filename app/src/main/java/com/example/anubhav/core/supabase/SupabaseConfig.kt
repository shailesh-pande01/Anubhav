package com.example.anubhav.core.supabase

object SupabaseConfig {
    /**
     * Supabase project URL and anon public key.
     */
    const val SUPABASE_URL: String = "https://idiufntnksyvkmluiamk.supabase.co"
    const val SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImlkaXVmbnRua3N5dmttbHVpYW1rIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1NDc5MTksImV4cCI6MjEwNDEyMzkxOX0.QFbAYjBxUpBVPt3T3RmOO7sfoqJxLx_qBmr146qc5sg"

    fun normalizedUrl(): String {
        return SUPABASE_URL.trimEnd('/')
            .removeSuffix("/rest/v1")
            .removeSuffix("/rest")
    }

    fun isConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() &&
                !SUPABASE_URL.contains("your-project-ref") &&
                SUPABASE_ANON_KEY.isNotBlank() &&
                !SUPABASE_ANON_KEY.contains("your-anon-key")
    }
}
