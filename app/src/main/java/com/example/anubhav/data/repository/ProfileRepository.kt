package com.example.anubhav.data.repository

import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.data.remote.dto.ProfileDto
import com.example.anubhav.data.remote.dto.ProfileUpsertDto
import com.example.anubhav.domain.model.UserProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository {

    private val client = SupabaseClientProvider.client

    suspend fun getProfile(userId: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        try {
            val profileDto = client.postgrest.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<ProfileDto>()
            Result.success(profileDto?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUsernameAvailable(username: String, currentUserId: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val normalized = username.trim().lowercase()
            val matches = client.postgrest.from("profiles")
                .select {
                    filter {
                        eq("username", normalized)
                    }
                }.decodeList<ProfileDto>()

            val available = matches.none { it.id != currentUserId }
            Result.success(available)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = ProfileUpsertDto(
                id = profile.id,
                username = profile.username.trim().lowercase(),
                displayName = profile.displayName.trim(),
                bio = profile.bio.trim(),
                location = profile.location.trim(),
                currentlyWorkingOn = profile.currentlyWorkingOn.trim(),
                thingsIveDone = profile.thingsIveDone.trim(),
                profileImageUrl = profile.profileImageUrl
            )
            client.postgrest.from("profiles").upsert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(
        userId: String,
        imageBytes: ByteArray,
        oldAvatarUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bucket = client.storage.from("profile-images")
            val path = "${userId}/avatar.jpg"
            bucket.upload(path, imageBytes) {
                upsert = true
            }
            // Append a cache-busting timestamp parameter so Coil/Compose instantly reflects updates
            // while Supabase Storage stores only the single overwritten object ${userId}/avatar.jpg.
            val basePublicUrl = bucket.publicUrl(path)
            val publicUrl = "$basePublicUrl?t=${System.currentTimeMillis()}"

            // Safely prune legacy timestamped avatar file if one was previously stored
            if (!oldAvatarUrl.isNullOrBlank()) {
                cleanUpLegacyAvatarIfPresent(userId, oldAvatarUrl)
            }

            Result.success(publicUrl)
        } catch (e: Exception) {
            android.util.Log.e("ProfileRepository", "Failed to upload profile image to storage: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun cleanUpLegacyAvatarIfPresent(userId: String, oldAvatarUrl: String) {
        try {
            // Match legacy patterns like .../profile-images/<userId>/avatar_<timestamp>.jpg
            val legacyPattern = Regex("""profile-images[/\\]${Regex.escape(userId)}[/\\](avatar_\d+\.jpg)""")
            val match = legacyPattern.find(oldAvatarUrl)
            if (match != null) {
                val filename = match.groupValues[1]
                val oldPath = "$userId/$filename"
                android.util.Log.i("ProfileRepository", "Pruning obsolete legacy avatar: $oldPath")
                client.storage.from("profile-images").delete(oldPath)
            }
        } catch (t: Throwable) {
            android.util.Log.w("ProfileRepository", "Non-critical error cleaning legacy avatar: ${t.message}")
        }
    }
}
