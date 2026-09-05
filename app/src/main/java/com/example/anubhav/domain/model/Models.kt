package com.example.anubhav.domain.model

enum class PostType {
    TEXT,
    IMAGE
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
    val createdAt: String = "",
    val updatedAt: String = ""
)

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
    /**
     * Important privacy rule:
     * Only the post owner can see the total number of likes on their own post.
     * For all other users, this is null.
     */
    val ownerLikeCount: Int? = null
)
