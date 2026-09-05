package com.example.anubhav.data.repository

import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.utils.RelativeTimeFormatter
import com.example.anubhav.data.remote.dto.PostDto
import com.example.anubhav.data.remote.dto.PostLikeDto
import com.example.anubhav.data.remote.dto.PostWithProfileDto
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.UserProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PostRepository {

    private val client = SupabaseClientProvider.client

    suspend fun getFeedPosts(
        currentUserId: String?,
        page: Int,
        pageSize: Int = 20
    ): Result<List<PostWithAuthor>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val rawPosts = client.postgrest.from("posts")
                .select(Columns.raw("*, profiles(*)")) {
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<PostWithProfileDto>()

            val postIds = rawPosts.map { it.id }

            // 1. Check which posts the current user has liked
            val userLikedPostIds = if (!currentUserId.isNullOrBlank() && postIds.isNotEmpty()) {
                val likes = client.postgrest.from("post_likes")
                    .select {
                        filter {
                            eq("user_id", currentUserId)
                        }
                    }
                    .decodeList<PostLikeDto>()
                likes.map { it.postId }.toSet()
            } else {
                emptySet()
            }

            // 2. Resolve posts into domain models
            val result = rawPosts.map { dto ->
                val isOwner = (currentUserId != null && dto.userId == currentUserId)

                // Privacy Rule: Only compute and expose like count if current user is the owner
                val ownerLikeCount = if (isOwner) {
                    val likesOnMyPost = client.postgrest.from("post_likes")
                        .select {
                            filter {
                                eq("post_id", dto.id)
                            }
                        }
                        .decodeList<PostLikeDto>()
                    likesOnMyPost.size
                } else {
                    null // Strictly null for non-owners
                }

                val imageUrl = dto.imagePath?.let { path ->
                    client.storage.from("post-images").publicUrl(path)
                }

                val authorProfile = dto.profiles?.toDomain() ?: UserProfile(
                    id = dto.userId,
                    username = "user",
                    displayName = "Anonymous"
                )

                PostWithAuthor(
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
                    ownerLikeCount = ownerLikeCount
                )
            }

            Result.success(result)
        } catch (e: Exception) {
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

            val rawPosts = client.postgrest.from("posts")
                .select(Columns.raw("*, profiles(*)")) {
                    filter {
                        eq("user_id", targetUserId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<PostWithProfileDto>()

            val userLikedPostIds = if (!currentUserId.isNullOrBlank() && rawPosts.isNotEmpty()) {
                val likes = client.postgrest.from("post_likes")
                    .select {
                        filter {
                            eq("user_id", currentUserId)
                        }
                    }
                    .decodeList<PostLikeDto>()
                likes.map { it.postId }.toSet()
            } else {
                emptySet()
            }

            val result = rawPosts.map { dto ->
                val isOwner = (currentUserId != null && dto.userId == currentUserId)

                val ownerLikeCount = if (isOwner) {
                    val likesOnMyPost = client.postgrest.from("post_likes")
                        .select {
                            filter {
                                eq("post_id", dto.id)
                            }
                        }
                        .decodeList<PostLikeDto>()
                    likesOnMyPost.size
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

                PostWithAuthor(
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
                    ownerLikeCount = ownerLikeCount
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun deletePost(postId: String, imagePath: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Delete post record (cascade deletes post_likes)
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
