package com.example.anubhav.data.remote.dto

import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminDashboardStats
import com.example.anubhav.domain.model.ModerationAction
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.Report
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import com.example.anubhav.domain.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String? = "",
    val location: String? = "",
    @SerialName("currently_working_on") val currentlyWorkingOn: String? = "",
    @SerialName("things_ive_done") val thingsIveDone: String? = "",
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("account_status") val accountStatus: String? = "ACTIVE",
    @SerialName("restricted_until") val restrictedUntil: String? = null,
    @SerialName("suspended_until") val suspendedUntil: String? = null,
    @SerialName("status_reason") val statusReason: String? = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
) {
    fun toDomain(): UserProfile {
        return UserProfile(
            id = id,
            username = username,
            displayName = displayName,
            bio = bio.orEmpty(),
            location = location.orEmpty(),
            currentlyWorkingOn = currentlyWorkingOn.orEmpty(),
            thingsIveDone = thingsIveDone.orEmpty(),
            profileImageUrl = profileImageUrl,
            accountStatus = AccountStatus.fromString(accountStatus),
            restrictedUntil = restrictedUntil,
            suspendedUntil = suspendedUntil,
            statusReason = statusReason.orEmpty(),
            createdAt = createdAt.orEmpty(),
            updatedAt = updatedAt.orEmpty()
        )
    }
}

@Serializable
data class ProfileUpsertDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String = "",
    val location: String = "",
    @SerialName("currently_working_on") val currentlyWorkingOn: String = "",
    @SerialName("things_ive_done") val thingsIveDone: String = "",
    @SerialName("profile_image_url") val profileImageUrl: String? = null
)

@Serializable
data class ProfileStatusUpdateDto(
    @SerialName("account_status") val accountStatus: String,
    @SerialName("restricted_until") val restrictedUntil: String? = null,
    @SerialName("suspended_until") val suspendedUntil: String? = null,
    @SerialName("status_reason") val statusReason: String = ""
)

@Serializable
data class PostDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("post_type") val postType: String,
    val content: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("is_removed") val isRemoved: Boolean? = false,
    @SerialName("removed_at") val removedAt: String? = null,
    @SerialName("removed_by") val removedBy: String? = null,
    @SerialName("removal_reason") val removalReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PostUpdateDto(
    val content: String,
    @SerialName("post_type") val postType: String,
    @SerialName("image_path") val imagePath: String? = null
)

@Serializable
data class PostSoftDeleteDto(
    @SerialName("is_removed") val isRemoved: Boolean = true,
    @SerialName("removed_at") val removedAt: String,
    @SerialName("removed_by") val removedBy: String,
    @SerialName("removal_reason") val removalReason: String
)

@Serializable
data class PostLikeDto(
    val id: String? = null,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PostLikePostIdOnlyDto(
    @SerialName("post_id") val postId: String
)

@Serializable
data class PostLikeCountDto(
    @SerialName("post_id") val postId: String,
    @SerialName("like_count") val likeCount: Long = 0L
)

@Serializable
data class PostImagePathDto(
    @SerialName("image_path") val imagePath: String? = null
)

@Serializable
data class PostWithProfileDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("post_type") val postType: String = "TEXT",
    val content: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("is_removed") val isRemoved: Boolean? = false,
    @SerialName("removed_at") val removedAt: String? = null,
    @SerialName("removed_by") val removedBy: String? = null,
    @SerialName("removal_reason") val removalReason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val profiles: ProfileDto? = null
)

@Serializable
data class AdminUserDto(
    @SerialName("user_id") val userId: String,
    val role: String = "MODERATOR",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ReportDto(
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
) {
    fun toDomain(): Report {
        return Report(
            id = id,
            postId = postId,
            reporterId = reporterId,
            reportedUserId = reportedUserId,
            reason = reason,
            description = description,
            status = ReportStatus.fromString(status),
            resolution = resolution,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
            createdAt = createdAt
        )
    }
}

@Serializable
data class CreateReportDto(
    @SerialName("post_id") val postId: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reported_user_id") val reportedUserId: String,
    val reason: String,
    val description: String = ""
)

@Serializable
data class UpdateReportStatusDto(
    val status: String,
    val resolution: String? = null,
    @SerialName("reviewed_by") val reviewedBy: String,
    @SerialName("reviewed_at") val reviewedAt: String
)

@Serializable
data class ModerationActionDto(
    val id: String? = null,
    @SerialName("admin_id") val adminId: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("post_id") val postId: String? = null,
    @SerialName("report_id") val reportId: String? = null,
    val action: String,
    val reason: String = "",
    @SerialName("duration_until") val durationUntil: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toDomain(): ModerationAction {
        return ModerationAction(
            id = id.orEmpty(),
            adminId = adminId,
            userId = userId,
            postId = postId,
            reportId = reportId,
            action = ModerationActionType.fromString(action),
            reason = reason,
            durationUntil = durationUntil,
            createdAt = createdAt.orEmpty()
        )
    }
}

@Serializable
data class AdminDashboardStatsDto(
    @SerialName("total_users") val totalUsers: Long = 0,
    @SerialName("total_posts") val totalPosts: Long = 0,
    @SerialName("pending_reports") val pendingReports: Long = 0,
    @SerialName("resolved_reports") val resolvedReports: Long = 0,
    @SerialName("banned_users") val bannedUsers: Long = 0,
    @SerialName("restricted_users") val restrictedUsers: Long = 0,
    @SerialName("suspended_users") val suspendedUsers: Long = 0,
    @SerialName("posts_removed") val postsRemoved: Long = 0
) {
    fun toDomain(): AdminDashboardStats {
        return AdminDashboardStats(
            totalUsers = totalUsers,
            totalPosts = totalPosts,
            pendingReports = pendingReports,
            resolvedReports = resolvedReports,
            bannedUsers = bannedUsers,
            restrictedUsers = restrictedUsers,
            suspendedUsers = suspendedUsers,
            postsRemoved = postsRemoved
        )
    }
}

@Serializable
data class AdminRemovePostRpcResultDto(
    val success: Boolean = true,
    @SerialName("already_deleted") val alreadyDeleted: Boolean = false,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("user_id") val userId: String? = null
)

