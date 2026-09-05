package com.example.anubhav.data.repository

import android.util.Log
import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.supabase.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    private val client = SupabaseClientProvider.client

    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentUserOrNull()?.id
                ?: client.auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                Result.success(userId)
            } else {
                Result.failure(Exception("Signup initiated. If email confirmation is enabled on Supabase, please verify your email."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val userId = client.auth.currentUserOrNull()?.id
                ?: client.auth.currentSessionOrNull()?.user?.id
            if (userId != null) {
                Result.success(userId)
            } else {
                Result.failure(Exception("Login succeeded but user session could not be retrieved."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithUsername(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            val email = try {
                val params = buildJsonObject {
                    put("p_username", username.trim().lowercase())
                }
                val rpcResult = client.postgrest.rpc(
                    function = "get_email_by_username",
                    parameters = params
                )
                rpcResult.decodeSingleOrNull<String>()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to resolve username '$username' via get_email_by_username RPC", e)
                return@withContext Result.failure(
                    Exception("Could not find an account with username '$username'. Please check your username or log in with your email address.")
                )
            }

            if (email.isNullOrBlank()) {
                return@withContext Result.failure(Exception("No account found with username '$username'."))
            }

            signIn(email = email, password = password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = "anubhav://reset-password"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
            ?: client.auth.currentSessionOrNull()?.user?.id
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUserId() != null
    }
}
