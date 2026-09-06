package com.example.anubhav.data.repository

import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.utils.RelativeTimeFormatter
import com.example.anubhav.data.remote.dto.CreateReportDto
import com.example.anubhav.data.remote.dto.PostWithProfileDto
import com.example.anubhav.data.remote.dto.ProfileDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.data.remote.dto.UpdateReportStatusDto
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.Report
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import com.example.anubhav.domain.model.UserProfile
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class ReportJoinDto(
    val id: String,
    @SerialName("post_id") val postId: String? = null,
    @SerialName("reporter_id") val reporterId: String? = null,
    @SerialName("reported_user_id") val reportedUserId: String? = null,
    val reason: String,
    val description: String = "",
    val status: String = "PENDING",
    val resolution: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

class ReportRepository(
    private val authRepository: AuthRepository = AuthRepository()
) {

    private val client = SupabaseClientProvider.client

    suspend fun submitReport(
        postId: String,
        reportedUserId: String,
        reason: String,
        description: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = authRepository.getCurrentUserId()
            ?: return@withContext Result.failure(IllegalStateException("You must be logged in to report a post."))

        if (currentUserId == reportedUserId) {
            return@withContext Result.failure(IllegalArgumentException("You cannot report your own post."))
        }

        try {
            val dto = CreateReportDto(
                postId = postId,
                reporterId = currentUserId,
                reportedUserId = reportedUserId,
                reason = reason,
                description = description.trim()
            )
            client.postgrest.from("reports").insert(dto)
            Result.success(Unit)
        } catch (e: Exception) {
            val isDuplicate = (e is RestException && (e.error.contains("unique", ignoreCase = true) || e.message?.contains("unique", ignoreCase = true) == true)) ||
                    e.message?.contains("unique_post_reporter", ignoreCase = true) == true ||
                    e.message?.contains("duplicate key", ignoreCase = true) == true

            if (isDuplicate) {
                Result.failure(DuplicateReportException("You already reported this post.\nOur team will review it."))
            } else {
                android.util.Log.e("ReportRepository", "Failed to submit report", e)
                Result.failure(Exception("Couldn't submit report. Please try again."))
            }
        }
    }

    suspend fun hasUserReportedPost(postId: String, reporterId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val existing = client.postgrest.from("reports")
                .select(Columns.raw("id")) {
                    filter {
                        eq("post_id", postId)
                        eq("reporter_id", reporterId)
                    }
                }
                .decodeList<ReportDto>()
            Result.success(existing.isNotEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReports(
        statusFilter: ReportStatus? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): Result<List<ReportWithDetails>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val rawReports = client.postgrest.from("reports")
                .select {
                    filter {
                        if (statusFilter != null) {
                            eq("status", statusFilter.name)
                        }
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<ReportDto>()

            if (rawReports.isEmpty()) return@withContext Result.success(emptyList())

            // Efficient batch loading of associated profiles & posts to eliminate N+1 queries
            val userIds = (rawReports.mapNotNull { it.reporterId } + rawReports.mapNotNull { it.reportedUserId }).distinct()
            val postIds = rawReports.mapNotNull { it.postId }.distinct()

            val profilesMap = fetchProfilesMap(userIds)
            val postsMap = fetchPostsMap(postIds)

            val details = rawReports.map { report ->
                val reporterProfile = report.reporterId?.let { profilesMap[it] }
                val reportedUserProfile = report.reportedUserId?.let { profilesMap[it] }
                val post = report.postId?.let { postsMap[it] }

                ReportWithDetails(
                    id = report.id,
                    postId = report.postId,
                    reporterId = report.reporterId,
                    reportedUserId = report.reportedUserId,
                    reason = report.reason,
                    description = report.description,
                    status = ReportStatus.fromString(report.status),
                    resolution = report.resolution,
                    reviewedBy = report.reviewedBy,
                    reviewedAt = report.reviewedAt,
                    createdAt = report.createdAt,
                    relativeTime = RelativeTimeFormatter.format(report.createdAt),
                    reporter = reporterProfile,
                    reportedUser = reportedUserProfile,
                    post = post
                )
            }

            Result.success(details)
        } catch (e: Exception) {
            android.util.Log.e("ReportRepository", "Failed to get reports", e)
            Result.failure(e)
        }
    }

    suspend fun getReportById(reportId: String): Result<ReportWithDetails> = withContext(Dispatchers.IO) {
        try {
            val report = client.postgrest.from("reports")
                .select {
                    filter {
                        eq("id", reportId)
                    }
                }
                .decodeSingleOrNull<ReportDto>()
                ?: return@withContext Result.failure(NoSuchElementException("Report not found"))

            val userIds = listOfNotNull(report.reporterId, report.reportedUserId).distinct()
            val profilesMap = fetchProfilesMap(userIds)
            val postsMap = report.postId?.let { fetchPostsMap(listOf(it)) } ?: emptyMap()

            val details = ReportWithDetails(
                id = report.id,
                postId = report.postId,
                reporterId = report.reporterId,
                reportedUserId = report.reportedUserId,
                reason = report.reason,
                description = report.description,
                status = ReportStatus.fromString(report.status),
                resolution = report.resolution,
                reviewedBy = report.reviewedBy,
                reviewedAt = report.reviewedAt,
                createdAt = report.createdAt,
                relativeTime = RelativeTimeFormatter.format(report.createdAt),
                reporter = report.reporterId?.let { profilesMap[it] },
                reportedUser = report.reportedUserId?.let { profilesMap[it] },
                post = report.postId?.let { postsMap[it] }
            )

            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(
        reportId: String,
        status: ReportStatus,
        resolution: String? = null,
        adminId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dto = UpdateReportStatusDto(
                status = status.name,
                resolution = resolution,
                reviewedBy = adminId,
                reviewedAt = Instant.now().toString()
            )
            client.postgrest.from("reports").update(dto) {
                filter {
                    eq("id", reportId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReportsForPost(postId: String): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            val reports = client.postgrest.from("reports")
                .select {
                    filter {
                        eq("post_id", postId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<ReportDto>()
                .map { it.toDomain() }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReportsForUser(userId: String): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            val reports = client.postgrest.from("reports")
                .select {
                    filter {
                        eq("reported_user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<ReportDto>()
                .map { it.toDomain() }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchProfilesMap(userIds: List<String>): Map<String, UserProfile> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            val profiles = client.postgrest.from("profiles")
                .select {
                    filter {
                        isIn("id", userIds)
                    }
                }
                .decodeList<ProfileDto>()
            profiles.associate { it.id to it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("ReportRepository", "Failed to batch fetch profiles", e)
            emptyMap()
        }
    }

    private suspend fun fetchPostsMap(postIds: List<String>): Map<String, PostWithAuthor> {
        if (postIds.isEmpty()) return emptyMap()
        return try {
            val posts = client.postgrest.from("posts")
                .select(Columns.raw("id, user_id, post_type, content, image_path, is_removed, removed_at, removed_by, removal_reason, created_at, profiles(id, username, display_name, profile_image_url, account_status, restricted_until, suspended_until)")) {
                    filter {
                        isIn("id", postIds)
                    }
                }
                .decodeList<PostWithProfileDto>()

            posts.associate { dto ->
                val imageUrl = dto.imagePath?.let { path ->
                    client.storage.from("post-images").publicUrl(path)
                }
                val author = dto.profiles?.toDomain() ?: UserProfile(
                    id = dto.userId,
                    username = "user",
                    displayName = "Anonymous"
                )
                dto.id to PostWithAuthor(
                    id = dto.id,
                    userId = dto.userId,
                    postType = if (dto.postType.uppercase() == "IMAGE") PostType.IMAGE else PostType.TEXT,
                    content = dto.content,
                    imagePath = dto.imagePath,
                    imageUrl = imageUrl,
                    createdAt = dto.createdAt,
                    relativeTime = RelativeTimeFormatter.format(dto.createdAt),
                    author = author,
                    isRemoved = dto.isRemoved == true,
                    removalReason = dto.removalReason
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ReportRepository", "Failed to batch fetch posts", e)
            emptyMap()
        }
    }
}

class DuplicateReportException(message: String) : Exception(message)
