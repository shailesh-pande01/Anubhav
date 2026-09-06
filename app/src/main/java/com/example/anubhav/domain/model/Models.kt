package com.example.anubhav.domain.model

enum class PostType {
    TEXT,
    IMAGE
}

enum class AccountStatus {
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    BANNED;

    companion object {
        fun fromString(value: String?): AccountStatus {
            return when (value?.trim()?.uppercase()) {
                "RESTRICTED" -> RESTRICTED
                "SUSPENDED" -> SUSPENDED
                "BANNED" -> BANNED
                else -> ACTIVE
            }
        }
    }
}

enum class ReportReason(val displayName: String) {
    SPAM("Spam"),
    HARASSMENT("Harassment"),
    INAPPROPRIATE("Inappropriate content"),
    HATE_SPEECH("Hate speech"),
    VIOLENCE("Violence"),
    OTHER("Other");

    companion object {
        fun fromString(value: String?): ReportReason {
            return entries.firstOrNull {
                it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: OTHER
        }
    }
}

enum class ReportStatus {
    PENDING,
    REVIEWING,
    RESOLVED,
    DISMISSED;

    companion object {
        fun fromString(value: String?): ReportStatus {
            return when (value?.trim()?.uppercase()) {
                "REVIEWING" -> REVIEWING
                "RESOLVED" -> RESOLVED
                "DISMISSED" -> DISMISSED
                else -> PENDING
            }
        }
    }
}

enum class ModerationActionType(val displayLabel: String) {
    WARNING("Warning"),
    POST_REMOVED("Post Removed"),
    USER_RESTRICTED("User Restricted"),
    USER_SUSPENDED("User Suspended"),
    USER_BANNED("User Banned"),
    REPORT_DISMISSED("Report Dismissed"),
    STATUS_RESET("Status Reset");

    companion object {
        fun fromString(value: String?): ModerationActionType {
            return when (value?.trim()?.uppercase()) {
                "WARNING" -> WARNING
                "POST_REMOVED" -> POST_REMOVED
                "USER_RESTRICTED" -> USER_RESTRICTED
                "USER_SUSPENDED" -> USER_SUSPENDED
                "USER_BANNED" -> USER_BANNED
                "REPORT_DISMISSED" -> REPORT_DISMISSED
                "STATUS_RESET" -> STATUS_RESET
                else -> WARNING
            }
        }
    }
}

enum class AdminRole {
    SUPER_ADMIN,
    MODERATOR;

    companion object {
        fun fromString(value: String?): AdminRole {
            return when (value?.trim()?.uppercase()) {
                "SUPER_ADMIN" -> SUPER_ADMIN
                else -> MODERATOR
            }
        }
    }
}

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val location: String = "",
    val currentlyWorkingOn: String = "",
    val thingsIveDone: String = "",
    val profileImageUrl: String? = null,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val restrictedUntil: String? = null,
    val suspendedUntil: String? = null,
    val statusReason: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    /**
     * Checks if this account has an active restriction/suspension considering timestamp expiration.
     */
    fun isTemporarilyRestricted(): Boolean {
        if (accountStatus != AccountStatus.RESTRICTED) return false
        val until = restrictedUntil ?: return true
        return try {
            java.time.Instant.parse(until).isAfter(java.time.Instant.now())
        } catch (_: Exception) {
            true
        }
    }

    fun isTemporarilySuspended(): Boolean {
        if (accountStatus != AccountStatus.SUSPENDED) return false
        val until = suspendedUntil ?: return true
        return try {
            java.time.Instant.parse(until).isAfter(java.time.Instant.now())
        } catch (_: Exception) {
            true
        }
    }

    fun isPermanentlyBanned(): Boolean = accountStatus == AccountStatus.BANNED
}

data class PostWithAuthor(
    val id: String,
    val userId: String,
    val postType: PostType,
    val content: String,
    val imagePath: String? = null,
    val imageUrl: String? = null,
    val createdAt: String,
    val relativeTime: String = "",
    val author: UserProfile,
    val isLikedByCurrentUser: Boolean = false,
    val isOwner: Boolean = false,
    val isRemoved: Boolean = false,
    val removalReason: String? = null,
    /**
     * Important privacy rule:
     * Only the post owner can see the total number of likes on their own post.
     * For all other users, this is null.
     */
    val ownerLikeCount: Int? = null
)

data class Report(
    val id: String,
    val postId: String?,
    val reporterId: String? = null,
    val reportedUserId: String? = null,
    val reason: String,
    val description: String = "",
    val status: ReportStatus = ReportStatus.PENDING,
    val resolution: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String = ""
)

data class ReportWithDetails(
    val id: String,
    val postId: String?,
    val reporterId: String? = null,
    val reportedUserId: String? = null,
    val reason: String,
    val description: String = "",
    val status: ReportStatus = ReportStatus.PENDING,
    val resolution: String? = null,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val createdAt: String = "",
    val relativeTime: String = "",
    val reporter: UserProfile? = null,
    val reportedUser: UserProfile? = null,
    val post: PostWithAuthor? = null
)

data class ModerationAction(
    val id: String = "",
    val adminId: String,
    val userId: String? = null,
    val postId: String? = null,
    val reportId: String? = null,
    val action: ModerationActionType,
    val reason: String = "",
    val durationUntil: String? = null,
    val createdAt: String = "",
    val relativeTime: String = "",
    val adminDisplayName: String? = null,
    val targetUserDisplayName: String? = null
)

data class AdminDashboardStats(
    val totalUsers: Long = 0,
    val totalPosts: Long = 0,
    val pendingReports: Long = 0,
    val resolvedReports: Long = 0,
    val bannedUsers: Long = 0,
    val restrictedUsers: Long = 0,
    val suspendedUsers: Long = 0,
    val postsRemoved: Long = 0
)

data class HighAttentionPost(
    val postId: String,
    val author: UserProfile,
    val content: String,
    val imageUrl: String?,
    val totalReports: Int,
    val reportsByReason: Map<String, Int>
)

data class AdminUserSummary(
    val profile: UserProfile,
    val postCount: Int = 0,
    val reportsReceivedCount: Int = 0
)

data class AdminUserDetail(
    val profile: UserProfile,
    val postCount: Int = 0,
    val reportsReceivedCount: Int = 0,
    val warningsCount: Int = 0,
    val removedPostsCount: Int = 0,
    val suspensionsCount: Int = 0,
    val moderationHistory: List<ModerationAction> = emptyList()
)

data class AdminPostSummary(
    val post: PostWithAuthor,
    val reportsCount: Int = 0,
    val isRemoved: Boolean = false
)

data class AdminRemovePostResult(
    val alreadyDeleted: Boolean = false,
    val imagePath: String? = null,
    val authorId: String? = null
)

