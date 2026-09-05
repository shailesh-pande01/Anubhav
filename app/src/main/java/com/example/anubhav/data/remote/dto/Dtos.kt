package com.example.anubhav.data.remote.dto

import com.example.anubhav.domain.model.PostType
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
data class PostDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("post_type") val postType: String,
    val content: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PostLikeDto(
    val id: String? = null,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PostWithProfileDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("post_type") val postType: String,
    val content: String = "",
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val profiles: ProfileDto? = null
)
