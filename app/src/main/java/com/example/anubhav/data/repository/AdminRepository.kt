package com.example.anubhav.data.repository

import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.core.utils.RelativeTimeFormatter
import com.example.anubhav.data.remote.dto.AdminDashboardStatsDto
import com.example.anubhav.data.remote.dto.ModerationActionDto
import com.example.anubhav.data.remote.dto.PostDto
import com.example.anubhav.data.remote.dto.PostWithProfileDto
import com.example.anubhav.data.remote.dto.ProfileDto
import com.example.anubhav.data.remote.dto.ProfileStatusUpdateDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminDashboardStats
import com.example.anubhav.domain.model.AdminPostSummary
import com.example.anubhav.domain.model.AdminUserDetail
import com.example.anubhav.domain.model.AdminUserSummary
import com.example.anubhav.domain.model.HighAttentionPost
import com.example.anubhav.domain.model.ModerationAction
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.UserProfile
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminRepository(
    private val authRepository: AuthRepository = AuthRepository()
) {

    private val client = SupabaseClientProvider.client

    /**
     * Checks if current authenticated user has administrative privileges using server-side RPC.
     */
    suspend fun checkIsAdmin(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isAdmin = client.postgrest.rpc(
                function = "check_is_current_user_admin"
            ).decodeAsOrNull<Boolean>() ?: false
            Result.success(isAdmin)
        } catch (e: Exception) {
            android.util.Log.d("AdminRepository", "checkIsAdmin check error or not admin: ${e.message}")
            Result.success(false)
        }
    }

    /**
     * Fetches high-level metrics for Admin Dashboard home.
     * Executes single aggregation RPC.
     */
    suspend fun getDashboardStats(): Result<AdminDashboardStats> = withContext(Dispatchers.IO) {
        try {
            val statsList = client.postgrest.rpc(
                function = "get_admin_dashboard_stats"
            ).decodeList<AdminDashboardStatsDto>()

            val stats = statsList.firstOrNull()?.toDomain() ?: AdminDashboardStats()
            Result.success(stats)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to fetch admin stats via RPC", e)
            Result.failure(e)
        }
    }

    /**
     * Finds posts with multiple reports for the 'Needs Attention' overview.
     */
    suspend fun getHighAttentionPosts(): Result<List<HighAttentionPost>> = withContext(Dispatchers.IO) {
        try {
            val reports = client.postgrest.from("reports")
                .select {
                    filter {
                        eq("status", "PENDING")
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(100)
                }
                .decodeList<ReportDto>()

            val groupedByPost = reports.filter { it.postId != null }.groupBy { it.postId!! }
            if (groupedByPost.isEmpty()) return@withContext Result.success(emptyList())

            val postIds = groupedByPost.keys.toList()
            val posts = client.postgrest.from("posts")
                .select(Columns.raw("id, user_id, post_type, content, image_path, created_at, profiles(id, username, display_name, profile_image_url)")) {
                    filter {
                        isIn("id", postIds)
                        eq("is_removed", false)
                    }
                }
                .decodeList<PostWithProfileDto>()

            val highAttention = posts.mapNotNull { postDto ->
                val postReports = groupedByPost[postDto.id] ?: return@mapNotNull null
                val author = postDto.profiles?.toDomain() ?: UserProfile(
                    id = postDto.userId,
                    username = "user",
                    displayName = "Anonymous"
                )
                val imageUrl = postDto.imagePath?.let { client.storage.from("post-images").publicUrl(it) }
                val reasonsCount = postReports.groupingBy { it.reason }.eachCount()

                HighAttentionPost(
                    postId = postDto.id,
                    author = author,
                    content = postDto.content,
                    imageUrl = imageUrl,
                    totalReports = postReports.size,
                    reportsByReason = reasonsCount
                )
            }.sortedByDescending { it.totalReports }

            Result.success(highAttention)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to load high attention posts", e)
            Result.failure(e)
        }
    }

    /**
     * User management list with search & status filters.
     */
    suspend fun getUsers(
        searchQuery: String? = null,
        statusFilter: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): Result<List<AdminUserSummary>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val profiles = client.postgrest.from("profiles")
                .select {
                    filter {
                        if (!statusFilter.isNullOrBlank() && statusFilter != "ALL") {
                            eq("account_status", statusFilter.uppercase())
                        }
                        if (!searchQuery.isNullOrBlank()) {
                            val pattern = "%${searchQuery.trim().lowercase()}%"
                            or {
                                ilike("username", pattern)
                                ilike("display_name", pattern)
                            }
                        }
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<ProfileDto>()

            if (profiles.isEmpty()) return@withContext Result.success(emptyList())

            val userIds = profiles.map { it.id }

            // Fetch report counts received by each user
            val reportsReceived = client.postgrest.from("reports")
                .select(Columns.raw("reported_user_id")) {
                    filter {
                        isIn("reported_user_id", userIds)
                    }
                }
                .decodeList<ReportDto>()
            val reportsCountMap = reportsReceived.groupingBy { it.reportedUserId }.eachCount()

            // Fetch post counts for each user
            val postsList = client.postgrest.from("posts")
                .select(Columns.raw("user_id")) {
                    filter {
                        isIn("user_id", userIds)
                        eq("is_removed", false)
                    }
                }
                .decodeList<PostDto>()
            val postCountMap = postsList.groupingBy { it.userId }.eachCount()

            val summaries = profiles.map { profileDto ->
                val profile = profileDto.toDomain()
                AdminUserSummary(
                    profile = profile,
                    postCount = postCountMap[profile.id] ?: 0,
                    reportsReceivedCount = reportsCountMap[profile.id] ?: 0
                )
            }

            Result.success(summaries)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to fetch users list", e)
            Result.failure(e)
        }
    }

    /**
     * Detailed user moderation view with historical action counts and timeline.
     */
    suspend fun getUserDetail(userId: String): Result<AdminUserDetail> = withContext(Dispatchers.IO) {
        try {
            val profileDto = client.postgrest.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileDto>()
                ?: return@withContext Result.failure(NoSuchElementException("User not found"))

            val profile = profileDto.toDomain()

            // 1. Post count
            val posts = client.postgrest.from("posts")
                .select(Columns.raw("id")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<PostDto>()
            val totalPosts = posts.size

            // 2. Reports count
            val reports = client.postgrest.from("reports")
                .select(Columns.raw("id")) {
                    filter {
                        eq("reported_user_id", userId)
                    }
                }
                .decodeList<ReportDto>()
            val reportsCount = reports.size

            // 3. Moderation history for this user
            val actions = client.postgrest.from("moderation_actions")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<ModerationActionDto>()
                .map { it.toDomain() }

            val warningsCount = actions.count { it.action == ModerationActionType.WARNING }
            val removedPostsCount = actions.count { it.action == ModerationActionType.POST_REMOVED }
            val suspensionsCount = actions.count { it.action == ModerationActionType.USER_SUSPENDED }

            val detail = AdminUserDetail(
                profile = profile,
                postCount = totalPosts,
                reportsReceivedCount = reportsCount,
                warningsCount = warningsCount,
                removedPostsCount = removedPostsCount,
                suspensionsCount = suspensionsCount,
                moderationHistory = actions
            )

            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes user moderation action (WARN, RESTRICT, SUSPEND, BAN, UNBAN).
     * Updates profile status and records audit log.
     */
    suspend fun moderateUser(
        userId: String?,
        action: ModerationActionType,
        reason: String,
        durationUntil: String? = null,
        reportId: String? = null,
        adminId: String,
        postId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val newStatus = when (action) {
                ModerationActionType.USER_BANNED -> AccountStatus.BANNED
                ModerationActionType.USER_SUSPENDED -> AccountStatus.SUSPENDED
                ModerationActionType.USER_RESTRICTED -> AccountStatus.RESTRICTED
                ModerationActionType.STATUS_RESET -> AccountStatus.ACTIVE
                else -> null // WARNING does not alter status directly
            }

            if (newStatus != null && !userId.isNullOrBlank()) {
                val updateDto = ProfileStatusUpdateDto(
                    accountStatus = newStatus.name,
                    restrictedUntil = if (newStatus == AccountStatus.RESTRICTED) durationUntil else null,
                    suspendedUntil = if (newStatus == AccountStatus.SUSPENDED) durationUntil else null,
                    statusReason = reason
                )
                client.postgrest.from("profiles").update(updateDto) {
                    filter {
                        eq("id", userId)
                    }
                }
            }

            // Record in moderation_actions audit table
            val auditDto = ModerationActionDto(
                adminId = adminId,
                userId = userId,
                postId = postId,
                reportId = reportId,
                action = action.name,
                reason = reason,
                durationUntil = durationUntil
            )
            client.postgrest.from("moderation_actions").insert(auditDto)

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to moderate user", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches complete moderation audit history with filters.
     */
    suspend fun getModerationActions(
        actionFilter: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): Result<List<ModerationAction>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val rawActions = client.postgrest.from("moderation_actions")
                .select {
                    filter {
                        if (!actionFilter.isNullOrBlank() && actionFilter != "ALL") {
                            eq("action", actionFilter.uppercase())
                        }
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<ModerationActionDto>()

            if (rawActions.isEmpty()) return@withContext Result.success(emptyList())

            val userIds = (rawActions.mapNotNull { it.userId } + rawActions.map { it.adminId }).distinct()
            val profiles = if (userIds.isNotEmpty()) {
                client.postgrest.from("profiles")
                    .select {
                        filter {
                            isIn("id", userIds)
                        }
                    }
                    .decodeList<ProfileDto>()
                    .associate { it.id to it.displayName }
            } else emptyMap()

            val domainActions = rawActions.map { dto ->
                val actionDomain = dto.toDomain()
                actionDomain.copy(
                    relativeTime = dto.createdAt?.let { RelativeTimeFormatter.format(it) } ?: "",
                    adminDisplayName = profiles[dto.adminId] ?: "Admin",
                    targetUserDisplayName = dto.userId?.let { profiles[it] }
                )
            }

            Result.success(domainActions)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to fetch moderation actions", e)
            Result.failure(e)
        }
    }

    /**
     * Admin post browsing with search, most reported, and removed filters.
     */
    suspend fun getAdminPosts(
        searchQuery: String? = null,
        filter: String? = null, // "ALL", "MOST_REPORTED", "REMOVED"
        page: Int = 0,
        pageSize: Int = 20
    ): Result<List<AdminPostSummary>> = withContext(Dispatchers.IO) {
        try {
            val fromIndex = (page * pageSize).toLong()
            val toIndex = ((page + 1) * pageSize - 1).toLong()

            val posts = client.postgrest.from("posts")
                .select(Columns.raw("id, user_id, post_type, content, image_path, is_removed, removed_at, removed_by, removal_reason, created_at, profiles(id, username, display_name, profile_image_url)")) {
                    filter {
                        if (filter == "REMOVED") {
                            eq("is_removed", true)
                        }
                        if (!searchQuery.isNullOrBlank()) {
                            ilike("content", "%${searchQuery.trim()}%")
                        }
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(from = fromIndex, to = toIndex)
                }
                .decodeList<PostWithProfileDto>()

            val postIds = posts.map { it.id }
            val reports = if (postIds.isNotEmpty()) {
                client.postgrest.from("reports")
                    .select(Columns.raw("post_id")) {
                        filter {
                            isIn("post_id", postIds)
                        }
                    }
                    .decodeList<ReportDto>()
            } else emptyList()

            val reportCountMap = reports.groupingBy { it.postId }.eachCount()

            var summaries = posts.map { dto ->
                val imageUrl = dto.imagePath?.let { client.storage.from("post-images").publicUrl(it) }
                val author = dto.profiles?.toDomain() ?: UserProfile(
                    id = dto.userId,
                    username = "user",
                    displayName = "Anonymous"
                )
                val postWithAuthor = PostWithAuthor(
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
                AdminPostSummary(
                    post = postWithAuthor,
                    reportsCount = reportCountMap[dto.id] ?: 0,
                    isRemoved = dto.isRemoved == true
                )
            }

            if (filter == "MOST_REPORTED") {
                summaries = summaries.sortedByDescending { it.reportsCount }
            }

            Result.success(summaries)
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to fetch admin posts", e)
            Result.failure(e)
        }
    }
}
