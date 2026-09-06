package com.example.anubhav.data.repository

import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.utils.RelativeTimeFormatter
import com.example.anubhav.data.remote.dto.AdminRemovePostRpcResultDto
import com.example.anubhav.data.remote.dto.ModerationActionDto
import com.example.anubhav.data.remote.dto.PostDto
import com.example.anubhav.data.remote.dto.PostLikeCountDto
import com.example.anubhav.data.remote.dto.PostLikeDto
import com.example.anubhav.data.remote.dto.PostLikePostIdOnlyDto
import com.example.anubhav.data.remote.dto.PostSoftDeleteDto
import com.example.anubhav.data.remote.dto.PostUpdateDto
import com.example.anubhav.data.remote.dto.PostWithProfileDto
import com.example.anubhav.data.remote.dto.UpdateReportStatusDto
import com.example.anubhav.domain.model.AdminRemovePostResult
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.UserProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.util.UUID

class PostRepository {

    private val client = SupabaseClientProvider.client

    companion object {
        // Resilient projection: embeds author profile while gracefully supporting schema variations
        private const val POSTS_PROJECTION = "*, profiles(*)"
        private const val POSTS_FALLBACK_PROJECTION = "*, profiles!user_id(*)"
    }

    suspend fun getFeedPosts(
        currentUserId: String?,
        page: Int,
        pageSize: Int = 20
    ): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val rawPosts = try {
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_PROJECTION)) {
                        filter {
                            or {
                                eq("is_removed", false)
                                exact("is_removed", null)
                            }
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                        range(from = fromIndex, to = toIndex)
                    }
                    .decodeList<PostWithProfileDto>()
            } catch (embedException: Exception) {
                android.util.Log.w("PostRepository", "Primary projection failed; attempting fallback: ${embedException.message}")
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_FALLBACK_PROJECTION)) {
                        filter {
                            or {
                                eq("is_removed", false)
                                exact("is_removed", null)
                            }
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                        range(from = fromIndex, to = toIndex)
                    }
                    .decodeList<PostWithProfileDto>()
            }

            // Client-side safety: ensure only non-removed posts are displayed in normal feed
            val visiblePosts = rawPosts.filter { it.isRemoved != true }
            val postIds = visiblePosts.map { it.id }

            // 1. Check which posts the current user has liked (strictly bounded to the visible 20 posts)
            val userLikedPostIds = if (!currentUserId.isNullOrBlank() && postIds.isNotEmpty()) {
                val likes = client.postgrest.from("post_likes")
                    .select(Columns.raw("post_id")) {
                        filter {
                            eq("user_id", currentUserId)
                            isIn("post_id", postIds)
                        }
                    }
                    .decodeList<PostLikePostIdOnlyDto>()
                likes.map { it.postId }.toSet()
            } else {
                emptySet()
            }

            // 2. Batched like counts for owner posts (eliminates N+1 queries by querying in a single request)
            val ownerPostIds = if (!currentUserId.isNullOrBlank()) {
                visiblePosts.filter { it.userId == currentUserId }.map { it.id }
            } else {
                emptyList()
            }
            val ownerLikeCountsMap = if (ownerPostIds.isNotEmpty()) {
                fetchLikeCountsForPosts(ownerPostIds)
            } else {
                emptyMap()
            }

            // 3. Resolve posts into domain models
            val result = visiblePosts.map { dto ->
                mapDtoToPostWithAuthor(dto, currentUserId, userLikedPostIds, ownerLikeCountsMap)
            }

            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to fetch feed posts: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserPosts(
        targetUserId: String,
        currentUserId: String?,
        page: Int = 0,
        pageSize: Int = 20
    ): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val rawPosts = try {
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_PROJECTION)) {
                        filter {
                            eq("user_id", targetUserId)
                            or {
                                eq("is_removed", false)
                                exact("is_removed", null)
                            }
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                        range(from = fromIndex, to = toIndex)
                    }
                    .decodeList<PostWithProfileDto>()
            } catch (embedException: Exception) {
                android.util.Log.w("PostRepository", "Primary projection failed for getUserPosts; attempting fallback: ${embedException.message}")
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_FALLBACK_PROJECTION)) {
                        filter {
                            eq("user_id", targetUserId)
                            or {
                                eq("is_removed", false)
                                exact("is_removed", null)
                            }
                        }
                        order(column = "created_at", order = Order.DESCENDING)
                        range(from = fromIndex, to = toIndex)
                    }
                    .decodeList<PostWithProfileDto>()
            }

            val visiblePosts = rawPosts.filter { it.isRemoved != true }
            val postIds = visiblePosts.map { it.id }

            // Strictly bounded likes lookup
            val userLikedPostIds = if (!currentUserId.isNullOrBlank() && postIds.isNotEmpty()) {
                val likes = client.postgrest.from("post_likes")
                    .select(Columns.raw("post_id")) {
                        filter {
                            eq("user_id", currentUserId)
                            isIn("post_id", postIds)
                        }
                    }
                    .decodeList<PostLikePostIdOnlyDto>()
                likes.map { it.postId }.toSet()
            } else {
                emptySet()
            }

            // Batched like counts for owner posts
            val ownerPostIds = if (!currentUserId.isNullOrBlank()) {
                visiblePosts.filter { it.userId == currentUserId }.map { it.id }
            } else {
                emptyList()
            }
            val ownerLikeCountsMap = if (ownerPostIds.isNotEmpty()) {
                fetchLikeCountsForPosts(ownerPostIds)
            } else {
                emptyMap()
            }

            val result = visiblePosts.map { dto ->
                mapDtoToPostWithAuthor(dto, currentUserId, userLikedPostIds, ownerLikeCountsMap)
            }

            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to fetch user posts for $targetUserId: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPostById(postId: String, currentUserId: String? = null): Result<PostWithAuthor> = withContext(Dispatchers.IO) {
        try {
            val rawPost = try {
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_PROJECTION)) {
                        filter {
                            eq("id", postId)
                        }
                    }
                    .decodeSingleOrNull<PostWithProfileDto>()
            } catch (embedException: Exception) {
                client.postgrest.from("posts")
                    .select(Columns.raw(POSTS_FALLBACK_PROJECTION)) {
                        filter {
                            eq("id", postId)
                        }
                    }
                    .decodeSingleOrNull<PostWithProfileDto>()
            } ?: return@withContext Result.failure(NoSuchElementException("Post not found"))

            val isLiked = if (!currentUserId.isNullOrBlank()) {
                val likes = client.postgrest.from("post_likes")
                    .select(Columns.raw("post_id")) {
                        filter {
                            eq("user_id", currentUserId)
                            eq("post_id", postId)
                        }
                    }
                    .decodeList<PostLikePostIdOnlyDto>()
                likes.isNotEmpty()
            } else false

            val isOwner = currentUserId != null && rawPost.userId == currentUserId
            val ownerLikeCount = if (isOwner) {
                fetchLikeCountsForPosts(listOf(postId))[postId] ?: 0
            } else null

            val post = mapDtoToPostWithAuthor(
                dto = rawPost,
                currentUserId = currentUserId,
                userLikedPostIds = if (isLiked) setOf(postId) else emptySet(),
                ownerLikeCountsMap = if (ownerLikeCount != null) mapOf(postId to ownerLikeCount) else emptyMap()
            )

            Result.success(post)
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to fetch post by id $postId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun mapDtoToPostWithAuthor(
        dto: PostWithProfileDto,
        currentUserId: String?,
        userLikedPostIds: Set<String>,
        ownerLikeCountsMap: Map<String, Int>
    ): PostWithAuthor {
        val isOwner = (currentUserId != null && dto.userId == currentUserId)

        // Privacy Rule: Only compute and expose like count if current user is the owner
        val ownerLikeCount = if (isOwner) {
            ownerLikeCountsMap[dto.id] ?: 0
        } else {
            null
        }

        val imageUrl = dto.imagePath?.let { path ->
            client.storage.from("post-images").publicUrl(path)
        }

        val authorProfile = dto.profiles?.toDomain() ?: UserProfile(
            id = dto.userId,
            username = "user",
            displayName = "Anonymous"
        )

        return PostWithAuthor(
            id = dto.id,
            userId = dto.userId,
            postType = if (dto.postType.uppercase() == "IMAGE") PostType.IMAGE else PostType.TEXT,
            content = dto.content,
            imagePath = dto.imagePath,
            imageUrl = imageUrl,
            createdAt = dto.createdAt,
            relativeTime = RelativeTimeFormatter.format(dto.createdAt),
            author = authorProfile,
            isLikedByCurrentUser = userLikedPostIds.contains(dto.id),
            isOwner = isOwner,
            isRemoved = dto.isRemoved == true,
            removalReason = dto.removalReason,
            ownerLikeCount = ownerLikeCount
        )
    }

    private suspend fun fetchLikeCountsForPosts(postIds: List<String>): Map<String, Int> {
        if (postIds.isEmpty()) return emptyMap()

        // 1. Try server-side aggregation RPC (get_post_like_counts)
        try {
            val params = buildJsonObject {
                putJsonArray("post_ids") {
                    postIds.forEach { add(JsonPrimitive(it)) }
                }
            }
            val rpcResult = client.postgrest.rpc(
                function = "get_post_like_counts",
                parameters = params
            ).decodeList<PostLikeCountDto>()
            return rpcResult.associate { it.postId to it.likeCount.toInt() }
        } catch (rpcException: Exception) {
            android.util.Log.d("PostRepository", "RPC get_post_like_counts unavailable; using batched fallback: ${rpcException.message}")
        }

        // 2. Single batched query fallback
        return try {
            val likes = client.postgrest.from("post_likes")
                .select(Columns.raw("post_id")) {
                    filter {
                        isIn("post_id", postIds)
                    }
                }
                .decodeList<PostLikePostIdOnlyDto>()
            likes.groupingBy { it.postId }.eachCount()
        } catch (e: Exception) {
            android.util.Log.e("PostRepository", "Failed to fetch batched like counts", e)
            emptyMap()
        }
    }

    suspend fun createTextPost(userId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = content.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Post content cannot be empty"))
            }

            val dto = PostDto(
                userId = userId,
                postType = "TEXT",
                content = trimmed
            )
            client.postgrest.from("posts").insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createImagePost(
        userId: String,
        caption: String,
        imageBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val postId = UUID.randomUUID().toString()
        val storagePath = "${userId}/${postId}.jpg"
        val bucket = client.storage.from("post-images")

        // 1. Upload compressed image to Supabase Storage
        try {
            bucket.upload(storagePath, imageBytes) {
                upsert = true
            }
        } catch (uploadException: Exception) {
            android.util.Log.e("PostRepository", "Failed to upload post image to storage: ${uploadException.message}", uploadException)
            return@withContext Result.failure(uploadException)
        }

        // 2. Insert post record; if DB operation fails, clean up uploaded image to prevent orphan files
        try {
            val dto = PostDto(
                id = postId,
                userId = userId,
                postType = "IMAGE",
                content = caption.trim(),
                imagePath = storagePath
            )
            client.postgrest.from("posts").insert(dto)
            Result.success(Unit)
        } catch (dbException: Exception) {
            android.util.Log.e("PostRepository", "Failed to insert post into database; removing orphaned image: ${dbException.message}", dbException)
            try {
                bucket.delete(storagePath)
            } catch (_: Exception) {}
            Result.failure(dbException)
        }
    }

    /**
     * Updates an existing post with safe ordering:
     * 1. If replacing image: Upload new compressed image -> Update DB row -> Delete old image
     * 2. If removing image: Update DB row -> Delete old image
     * 3. If editing caption only: Update DB row
     */
    suspend fun updatePost(
        userId: String,
        postId: String,
        content: String,
        newImageBytes: ByteArray? = null,
        removeImage: Boolean = false,
        existingImagePath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bucket = client.storage.from("post-images")

        when {
            // Case 1: Replacing or adding image
            newImageBytes != null -> {
                val newStoragePath = "${userId}/${postId}_${System.currentTimeMillis()}.jpg"
                try {
                    bucket.upload(newStoragePath, newImageBytes) {
                        upsert = true
                    }
                } catch (uploadException: Exception) {
                    android.util.Log.e("PostRepository", "Failed to upload replacement post image", uploadException)
                    return@withContext Result.failure(uploadException)
                }

                try {
                    val updateDto = PostUpdateDto(
                        content = content.trim(),
                        postType = "IMAGE",
                        imagePath = newStoragePath
                    )
                    client.postgrest.from("posts").update(updateDto) {
                        filter {
                            eq("id", postId)
                            eq("user_id", userId)
                        }
                    }

                    // Database update succeeded: delete old image from storage if present
                    if (!existingImagePath.isNullOrBlank() && existingImagePath != newStoragePath) {
                        try {
                            bucket.delete(existingImagePath)
                        } catch (e: Exception) {
                            android.util.Log.w("PostRepository", "Non-critical: Failed to delete previous image", e)
                        }
                    }

                    Result.success(Unit)
                } catch (dbException: Exception) {
                    android.util.Log.e("PostRepository", "Failed to update post in DB, pruning new image", dbException)
                    try {
                        bucket.delete(newStoragePath)
                    } catch (_: Exception) {}
                    Result.failure(dbException)
                }
            }

            // Case 2: Removing existing image (converting to TEXT post)
            removeImage -> {
                try {
                    val updateDto = PostUpdateDto(
                        content = content.trim(),
                        postType = "TEXT",
                        imagePath = null
                    )
                    client.postgrest.from("posts").update(updateDto) {
                        filter {
                            eq("id", postId)
                            eq("user_id", userId)
                        }
                    }

                    // DB updated: prune old image from storage
                    if (!existingImagePath.isNullOrBlank()) {
                        try {
                            bucket.delete(existingImagePath)
                        } catch (e: Exception) {
                            android.util.Log.w("PostRepository", "Non-critical: Failed to delete removed image", e)
                        }
                    }

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            // Case 3: Caption-only edit
            else -> {
                try {
                    val updateDto = PostUpdateDto(
                        content = content.trim(),
                        postType = if (existingImagePath != null) "IMAGE" else "TEXT",
                        imagePath = existingImagePath
                    )
                    client.postgrest.from("posts").update(updateDto) {
                        filter {
                            eq("id", postId)
                            eq("user_id", userId)
                        }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Completely removes a post by an authorized administrator:
     * 1. Runs atomic server-side RPC `admin_remove_post` (handles admin authorization, audit logging,
     *    report auto-resolution, and hard post deletion in a single database transaction).
     * 2. If the RPC is unavailable, falls back to direct authorized PostgREST delete with audit logging
     *    and report resolution.
     * 3. Prunes associated image from Supabase Storage `post-images` bucket.
     * 4. Idempotently handles already-deleted posts without throwing errors.
     */
    suspend fun removePostByAdmin(
        postId: String,
        adminId: String,
        reason: String,
        imagePath: String? = null,
        reportId: String? = null,
        reportedUserId: String? = null
    ): Result<AdminRemovePostResult> = withContext(Dispatchers.IO) {
        val cleanedReason = reason.ifBlank { "Violated community standards" }
        var resolvedImagePath = imagePath
        var alreadyDeleted = false

        // Attempt Step 1: Atomic server-side RPC
        val rpcResult = try {
            val params = buildJsonObject {
                put("p_post_id", JsonPrimitive(postId))
                put("p_reason", JsonPrimitive(cleanedReason))
                if (!reportId.isNullOrBlank()) {
                    put("p_report_id", JsonPrimitive(reportId))
                }
            }
            val res = client.postgrest.rpc("admin_remove_post", params).decodeAs<AdminRemovePostRpcResultDto>()
            Result.success(res)
        } catch (rpcEx: Exception) {
            android.util.Log.d("PostRepository", "admin_remove_post RPC unavailable or failed, executing resilient fallback: ${rpcEx.message}")
            Result.failure(rpcEx)
        }

        if (rpcResult.isSuccess) {
            val dto = rpcResult.getOrThrow()
            alreadyDeleted = dto.alreadyDeleted
            if (!dto.imagePath.isNullOrBlank()) {
                resolvedImagePath = dto.imagePath
            }
        } else {
            // Fallback Step: Client-orchestrated removal using RLS permissions
            try {
                // 1. Fetch current post metadata if imagePath or authorId is missing
                var authorId = reportedUserId
                if (resolvedImagePath == null || authorId == null) {
                    val postRecord = try {
                        client.postgrest.from("posts")
                            .select(Columns.raw("id, user_id, image_path")) {
                                filter { eq("id", postId) }
                            }
                            .decodeSingleOrNull<PostDto>()
                    } catch (e: Exception) {
                        null
                    }

                    if (postRecord != null) {
                        if (resolvedImagePath == null) resolvedImagePath = postRecord.imagePath
                        if (authorId == null) authorId = postRecord.userId
                    } else {
                        // Post already deleted
                        alreadyDeleted = true
                    }
                }

                if (alreadyDeleted) {
                    // Auto-resolve report(s)
                    try {
                        val reportUpdate = UpdateReportStatusDto(
                            status = "RESOLVED",
                            resolution = "Post already removed",
                            reviewedBy = adminId,
                            reviewedAt = Instant.now().toString()
                        )
                        if (!reportId.isNullOrBlank()) {
                            client.postgrest.from("reports").update(reportUpdate) {
                                filter { eq("id", reportId) }
                            }
                        }
                    } catch (_: Exception) {}
                } else {
                    // 2. Insert audit log into moderation_actions
                    try {
                        val auditDto = ModerationActionDto(
                            adminId = adminId,
                            userId = authorId,
                            postId = postId,
                            reportId = reportId,
                            action = "POST_REMOVED",
                            reason = cleanedReason,
                            createdAt = Instant.now().toString()
                        )
                        client.postgrest.from("moderation_actions").insert(auditDto)
                    } catch (auditEx: Exception) {
                        android.util.Log.w("PostRepository", "Warning: Failed to insert audit log: ${auditEx.message}")
                    }

                    // 3. Resolve all reports referencing this post
                    try {
                        val reportUpdate = UpdateReportStatusDto(
                            status = "RESOLVED",
                            resolution = "Post removed: $cleanedReason",
                            reviewedBy = adminId,
                            reviewedAt = Instant.now().toString()
                        )
                        client.postgrest.from("reports").update(reportUpdate) {
                            filter {
                                eq("post_id", postId)
                            }
                        }
                        if (!reportId.isNullOrBlank()) {
                            client.postgrest.from("reports").update(reportUpdate) {
                                filter {
                                    eq("id", reportId)
                                }
                            }
                        }
                    } catch (repEx: Exception) {
                        android.util.Log.w("PostRepository", "Warning: Failed to auto-resolve reports: ${repEx.message}")
                    }

                    // 4. Delete the posts database record (allowed by RLS is_admin(auth.uid()))
                    client.postgrest.from("posts").delete {
                        filter {
                            eq("id", postId)
                        }
                    }
                }
            } catch (fallbackEx: Exception) {
                android.util.Log.e("PostRepository", "Fallback admin post removal failed: ${fallbackEx.message}", fallbackEx)
                return@withContext Result.failure(fallbackEx)
            }
        }

        // Clean up storage image if present
        if (!resolvedImagePath.isNullOrBlank()) {
            val sanitizedPath = sanitizeStoragePath(resolvedImagePath!!)
            try {
                client.storage.from("post-images").delete(sanitizedPath)
            } catch (storageEx: Exception) {
                // Non-fatal: Log storage warning, as database deletion has already succeeded
                android.util.Log.w("PostRepository", "Warning: Failed to clean up storage image $sanitizedPath: ${storageEx.message}")
            }
        }

        Result.success(AdminRemovePostResult(alreadyDeleted = alreadyDeleted, imagePath = resolvedImagePath))
    }

    /**
     * Backwards-compatible delegate for admin post removal.
     */
    suspend fun softDeletePostByAdmin(
        postId: String,
        adminId: String,
        reason: String,
        imagePath: String? = null,
        deleteStorageImage: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        removePostByAdmin(
            postId = postId,
            adminId = adminId,
            reason = reason,
            imagePath = imagePath
        ).map { }
    }

    fun sanitizeStoragePath(rawPath: String): String {
        return rawPath.trim()
            .removePrefix("post-images/")
            .substringAfter("/post-images/")
    }

    suspend fun deletePost(postId: String, imagePath: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Delete post record (cascade deletes post_likes; foreign keys on reports set post_id to NULL)
            client.postgrest.from("posts").delete {
                filter {
                    eq("id", postId)
                }
            }

            // Delete storage file if present
            if (!imagePath.isNullOrBlank()) {
                try {
                    client.storage.from("post-images").delete(imagePath)
                } catch (storageException: Exception) {
                    // Log but don't fail if the database row was already removed
                    storageException.printStackTrace()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(
        postId: String,
        userId: String,
        isCurrentlyLiked: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (isCurrentlyLiked) {
                client.postgrest.from("post_likes").delete {
                    filter {
                        eq("post_id", postId)
                        eq("user_id", userId)
                    }
                }
                Result.success(false)
            } else {
                val dto = PostLikeDto(
                    postId = postId,
                    userId = userId
                )
                client.postgrest.from("post_likes").insert(dto)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
