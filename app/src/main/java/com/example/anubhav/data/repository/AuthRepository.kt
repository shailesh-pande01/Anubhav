package com.example.anubhav.data.repository

import android.net.Uri
import android.util.Log
import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.supabase.SupabaseConfig
import com.example.anubhav.data.remote.dto.PostImagePathDto
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.parseSessionFromFragment
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface RecoveryStatus {
    data object Idle : RecoveryStatus
    data object Verifying : RecoveryStatus
    data class Ready(val email: String?) : RecoveryStatus
    data class Error(val isExpired: Boolean, val message: String) : RecoveryStatus
}

class AuthRepository {

    private val client = SupabaseClientProvider.client

    val sessionStatus: StateFlow<SessionStatus>
        get() = client.auth.sessionStatus

    val recoveryStatus: StateFlow<RecoveryStatus>
        get() = _recoveryStatus.asStateFlow()

    suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
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
            Log.e("AuthRepository", "Sign up failed for email: ${email.trim()}", e)
            Result.failure(mapAuthException(e, isUsernameLogin = false))
        }
    }

    suspend fun signIn(email: String, password: String, isUsernameLogin: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }
        try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
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
            Log.e("AuthRepository", "Sign in failed for email: ${email.trim()}", e)
            Result.failure(mapAuthException(e, isUsernameLogin = isUsernameLogin))
        }
    }

    suspend fun signInWithUsername(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }

        val normalizedUsername = username.trim().lowercase()
        if (normalizedUsername.isBlank()) {
            return@withContext Result.failure(Exception("Please enter your username."))
        }

        Log.d("AuthRepository", "Attempting username resolution for username: $normalizedUsername")

        // 1. Resolve username to email via get_email_by_username RPC
        val email = try {
            val params = buildJsonObject {
                put("p_username", normalizedUsername)
            }
            val rpcResult = client.postgrest.rpc(
                function = "get_email_by_username",
                parameters = params
            )

            // PostgREST returns a scalar text as a JSON string (e.g. "user@example.com") or null ("null").
            // decodeSingleOrNull assumes a JSON array and throws a SerializationException.
            // We safely extract and unquote the scalar response.
            val decoded = try {
                rpcResult.decodeAsOrNull<String>()
            } catch (_: Exception) {
                null
            }
            decoded?.trim()?.ifBlank { null } ?: parseEmailFromRpcData(rpcResult.data)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to call get_email_by_username RPC for username: $normalizedUsername", e)
            return@withContext Result.failure(mapAuthException(e, isUsernameLogin = true))
        }

        if (email.isNullOrBlank() || email.equals("null", ignoreCase = true)) {
            Log.d("AuthRepository", "Username '$normalizedUsername' not found in database")
            return@withContext Result.failure(Exception("Username not found. Check your username and try again."))
        }

        Log.d("AuthRepository", "Username resolved successfully. Initiating Supabase Auth signIn")

        // 2. Perform authentication with resolved email and password
        signIn(email = email, password = password, isUsernameLogin = true)
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
            Result.failure(mapAuthException(e, isUsernameLogin = false))
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
            // Explicitly sign out recovery session after successful update to return to a clean state
            try {
                client.auth.signOut(SignOutScope.LOCAL)
            } catch (e: Exception) {
                Log.w("AuthRepository", "Local sign out after password reset encountered non-critical error", e)
            }
            _recoveryStatus.value = RecoveryStatus.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to update password", e)
            Result.failure(mapAuthException(e, isUsernameLogin = false))
        }
    }

    suspend fun handleRecoveryIntent(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "anubhav") return false

        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        val isRecoveryHost = host == "reset-password" || (host == "auth" && path.startsWith("/reset-password"))
        if (!isRecoveryHost) return false

        Log.i("AuthRepository", "Detected password reset recovery deep link: $uri")
        _recoveryStatus.value = RecoveryStatus.Verifying

        return withContext(Dispatchers.IO) {
            try {
                // 1. Check for error in query or fragment
                val queryError = uri.getQueryParameter("error")
                val queryErrorCode = uri.getQueryParameter("error_code")
                val queryErrorDesc = uri.getQueryParameter("error_description")

                val fragment = uri.fragment.orEmpty()
                val fragmentParams = parseUriParameters(fragment)
                val fragmentError = fragmentParams["error"]
                val fragmentErrorCode = fragmentParams["error_code"]
                val fragmentErrorDesc = fragmentParams["error_description"]

                val error = fragmentError ?: queryError
                val errorCode = fragmentErrorCode ?: queryErrorCode
                val errorDesc = fragmentErrorDesc ?: queryErrorDesc

                if (!error.isNullOrBlank() || !errorCode.isNullOrBlank()) {
                    val isExpired = (errorCode?.contains("otp_expired", ignoreCase = true) == true) ||
                            (errorDesc?.contains("expired", ignoreCase = true) == true) ||
                            (error?.contains("expired", ignoreCase = true) == true)
                    val message = if (isExpired) {
                        "This password reset link has expired. Please request a new reset link."
                    } else {
                        "This reset link is no longer valid. Please request a new one."
                    }
                    Log.i("AuthRepository", "Recovery link error detected: isExpired=$isExpired, message=$message")
                    _recoveryStatus.value = RecoveryStatus.Error(isExpired = isExpired, message = message)
                    return@withContext true
                }

                // 2. Check for implicit tokens in fragment
                val accessToken = fragmentParams["access_token"]
                val refreshToken = fragmentParams["refresh_token"]
                val tokenType = fragmentParams["token_type"] ?: "bearer"
                val type = fragmentParams["type"] ?: "recovery"
                val expiresIn = fragmentParams["expires_in"]?.toLongOrNull() ?: 3600L

                if (!accessToken.isNullOrBlank()) {
                    Log.i("AuthRepository", "Processing recovery access token with Supabase Auth")
                    try {
                        // Validate token against Supabase Auth server
                        val userInfo = client.auth.retrieveUser(accessToken)
                        val userSession = client.auth.parseSessionFromFragment(fragment)
                        val sessionWithUser = userSession.copy(user = userInfo)

                        client.auth.importSession(
                            sessionWithUser,
                            false,
                            SessionSource.External
                        )
                        Log.i("AuthRepository", "Recovery session established successfully for user: ${userInfo.id}")
                        _recoveryStatus.value = RecoveryStatus.Ready(email = userInfo.email)
                        return@withContext true
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Failed to retrieve user or import recovery session", e)
                        val msg = e.message.orEmpty()
                        val isExpired = msg.contains("expired", ignoreCase = true) ||
                                msg.contains("otp_expired", ignoreCase = true) ||
                                msg.contains("invalid", ignoreCase = true)
                        val message = if (isExpired) {
                            "This password reset link has expired. Please request a new reset link."
                        } else {
                            "This reset link is no longer valid. Please request a new one."
                        }
                        _recoveryStatus.value = RecoveryStatus.Error(isExpired = isExpired, message = message)
                        return@withContext true
                    }
                }

                // 3. Check for PKCE code in query parameter
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    Log.i("AuthRepository", "Processing recovery PKCE code with Supabase Auth")
                    try {
                        val session = client.auth.exchangeCodeForSession(code = code, saveSession = false)
                        Log.i("AuthRepository", "Recovery code exchanged successfully for session")
                        _recoveryStatus.value = RecoveryStatus.Ready(email = session.user?.email)
                        return@withContext true
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Failed to exchange recovery code for session", e)
                        val msg = e.message.orEmpty()
                        val isExpired = msg.contains("expired", ignoreCase = true) ||
                                msg.contains("otp_expired", ignoreCase = true) ||
                                msg.contains("invalid", ignoreCase = true)
                        val message = if (isExpired) {
                            "This password reset link has expired. Please request a new reset link."
                        } else {
                            "This reset link is no longer valid. Please request a new one."
                        }
                        _recoveryStatus.value = RecoveryStatus.Error(isExpired = isExpired, message = message)
                        return@withContext true
                    }
                }

                // 4. No tokens, code, or recognized error
                Log.w("AuthRepository", "Recovery deep link did not contain valid error, access_token, or code")
                _recoveryStatus.value = RecoveryStatus.Error(
                    isExpired = false,
                    message = "Invalid password reset link. Please request a new one."
                )
                true
            } catch (e: Exception) {
                Log.e("AuthRepository", "Unexpected error processing recovery intent", e)
                _recoveryStatus.value = RecoveryStatus.Error(
                    isExpired = false,
                    message = "Something went wrong with this reset link. Please request a new password reset link."
                )
                true
            }
        }
    }

    suspend fun clearRecoveryState() = withContext(Dispatchers.IO) {
        _recoveryStatus.value = RecoveryStatus.Idle
        try {
            client.auth.signOut(SignOutScope.LOCAL)
        } catch (_: Exception) {}
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.auth.signOut()
            _recoveryStatus.value = RecoveryStatus.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes a complete, secure production-ready account deletion:
     * 1. Prunes user storage objects (post images and profile avatar) while authenticated.
     * 2. Invokes backend PostgreSQL RPC delete_user_account() which verifies identity from auth.uid(),
     *    auto-resolves pending moderation reports, cleans up database storage records, and deletes
     *    the user from auth.users (cascading to profiles, posts, likes, admin_users, and setting null on reports).
     * 3. Clears local session and cached state.
     * Guaranteed to never expose privileged backend secrets or technical exception messages.
     */
    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(Exception("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt"))
        }

        val currentUserId = getCurrentUserId()
            ?: return@withContext Result.failure(Exception("You must be logged in to delete your account."))

        try {
            Log.i("AuthRepository", "Initiating secure account deletion lifecycle for user: $currentUserId")

            // 1. Clean up Storage files while session is valid and authenticated
            cleanUpUserStorage(currentUserId)

            // 2. Call secure PostgreSQL RPC to delete database rows and auth.users atomically
            client.postgrest.rpc(function = "delete_user_account")

            // 3. Clear local session state
            try {
                client.auth.signOut(SignOutScope.LOCAL)
            } catch (e: Exception) {
                Log.w("AuthRepository", "Local sign out after account deletion encountered non-critical error", e)
            }
            _recoveryStatus.value = RecoveryStatus.Idle

            Log.i("AuthRepository", "Account deletion successfully completed for user: $currentUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Account deletion failed for user $currentUserId", e)
            Result.failure(mapDeleteAccountException(e))
        }
    }

    private suspend fun cleanUpUserStorage(userId: String) {
        // 1. Clean up post images
        try {
            val postBucket = client.storage.from("post-images")

            // Retrieve explicit post image paths from database
            val postImages = try {
                client.postgrest.from("posts")
                    .select(Columns.raw("image_path")) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<PostImagePathDto>()
                    .mapNotNull { it.imagePath }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Could not query user post images for storage cleanup: ${e.message}")
                emptyList()
            }

            // List files in user's post-images folder
            val folderFiles = try {
                postBucket.list(userId).map { "${userId}/${it.name}" }
            } catch (_: Exception) {
                emptyList()
            }

            val allPostPaths = (postImages + folderFiles).distinct()
            if (allPostPaths.isNotEmpty()) {
                try {
                    postBucket.delete(allPostPaths)
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Batch delete post images failed, deleting individually", e)
                    for (path in allPostPaths) {
                        runCatching { postBucket.delete(path) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "Non-fatal error during post-images storage cleanup", e)
        }

        // 2. Clean up profile image
        try {
            val profileBucket = client.storage.from("profile-images")
            val folderFiles = try {
                profileBucket.list(userId).map { "${userId}/${it.name}" }
            } catch (_: Exception) {
                emptyList()
            }
            val defaultAvatarPath = "${userId}/avatar.jpg"
            val allAvatarPaths = (folderFiles + defaultAvatarPath).distinct()
            if (allAvatarPaths.isNotEmpty()) {
                try {
                    profileBucket.delete(allAvatarPaths)
                } catch (e: Exception) {
                    for (path in allAvatarPaths) {
                        runCatching { profileBucket.delete(path) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AuthRepository", "Non-fatal error during profile-images storage cleanup", e)
        }
    }

    fun getCurrentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
            ?: client.auth.currentSessionOrNull()?.user?.id
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUserId() != null
    }

    companion object {
        private val _recoveryStatus = MutableStateFlow<RecoveryStatus>(RecoveryStatus.Idle)

        /**
         * Safely parse parameters from a URI query or fragment string (`key=value&...`).
         */
        fun parseUriParameters(rawString: String?): Map<String, String> {
            if (rawString.isNullOrBlank()) return emptyMap()
            return rawString.split('&')
                .mapNotNull { param ->
                    val parts = param.split('=', limit = 2)
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        val key = parts[0].trim()
                        val value = if (parts.size > 1) {
                            try {
                                URLDecoder.decode(parts[1].trim(), "UTF-8")
                            } catch (_: Exception) {
                                parts[1].trim()
                            }
                        } else ""
                        key to value
                    } else null
                }
                .toMap()
        }

        /**
         * Safely parse email string from raw RPC response data.
         * PostgREST returns scalar text as `"user@example.com"` or `null` ("null").
         */
        fun parseEmailFromRpcData(rawData: String?): String? {
            if (rawData == null) return null
            val trimmed = rawData.trim()
            if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true) || trimmed == "\"\"") {
                return null
            }
            val unquoted = if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
                trimmed.substring(1, trimmed.length - 1).trim()
            } else {
                trimmed
            }
            return if (unquoted.isEmpty() || unquoted.equals("null", ignoreCase = true)) null else unquoted
        }

        /**
         * Maps raw backend/network/supabase exceptions into clear user-friendly messages.
         * Never displays raw PostgREST or internal exception details to users.
         */
        fun mapAuthException(e: Throwable, isUsernameLogin: Boolean = false): Exception {
            // 1. Network / connectivity issues
            if (e is HttpRequestException || e is IOException) {
                return Exception("Couldn't connect. Please check your internet connection and try again.")
            }
            val msg = e.message.orEmpty()
            if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("connect timed out", ignoreCase = true)
            ) {
                return Exception("Couldn't connect. Please check your internet connection and try again.")
            }

            // 2. Recovery / Token expiration or invalid token
            if (msg.contains("otp_expired", ignoreCase = true) ||
                msg.contains("email link is invalid or has expired", ignoreCase = true) ||
                msg.contains("recovery link has expired", ignoreCase = true) ||
                msg.contains("token has expired", ignoreCase = true)
            ) {
                return Exception("This reset link has expired. Please request a new one.")
            }
            if (msg.contains("token is invalid", ignoreCase = true) ||
                msg.contains("bad_jwt", ignoreCase = true)
            ) {
                return Exception("This reset link is no longer valid. Please request a new one.")
            }
            if (msg.contains("weak_password", ignoreCase = true) ||
                msg.contains("password should be at least", ignoreCase = true)
            ) {
                return Exception("Password must be at least 6 characters.")
            }

            // 3. Invalid credentials
            val isInvalidCredentials = if (e is RestException) {
                e.error.contains("invalid_credentials", ignoreCase = true) ||
                (e.description?.contains("invalid login credentials", ignoreCase = true) == true) ||
                msg.contains("invalid login credentials", ignoreCase = true) ||
                msg.contains("invalid_credentials", ignoreCase = true)
            } else {
                msg.contains("invalid login credentials", ignoreCase = true) ||
                msg.contains("invalid_credentials", ignoreCase = true)
            }

            if (isInvalidCredentials) {
                return if (isUsernameLogin) {
                    Exception("Incorrect password. Please try again.")
                } else {
                    Exception("Invalid email or password. Please try again.")
                }
            }

            // 4. Email not confirmed
            if (msg.contains("email_not_confirmed", ignoreCase = true) ||
                msg.contains("Email not confirmed", ignoreCase = true)
            ) {
                return Exception("Please verify your email address before logging in.")
            }

            // 5. Rate limit
            if (msg.contains("over_request_rate_limit", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true)
            ) {
                return Exception("Too many login attempts. Please wait a moment and try again.")
            }

            // 6. Account already exists
            if (msg.contains("already registered", ignoreCase = true) ||
                msg.contains("already exists", ignoreCase = true)
            ) {
                return Exception("An account with this email already exists.")
            }

            // 7. Generic safe fallback
            return Exception("Something went wrong. Please try again.")
        }

        /**
         * Maps account deletion exceptions to clean, user-friendly messages.
         * Strictly sanitizes technical database and storage exceptions.
         */
        fun mapDeleteAccountException(e: Throwable): Exception {
            if (e is HttpRequestException || e is IOException) {
                return Exception("Couldn't connect. Please check your internet connection and try again.")
            }
            val msg = e.message.orEmpty()
            if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("connect timed out", ignoreCase = true)
            ) {
                return Exception("Couldn't connect. Please check your internet connection and try again.")
            }

            return Exception("Couldn't delete your account. Please try again.")
        }
    }
}
