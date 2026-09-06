package com.example.anubhav.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.core.image.ImageCompressor
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import com.example.anubhav.data.repository.ProfileRepository
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val profile: UserProfile? = null,
    val posts: List<PostWithAuthor> = emptyList(),
    val isLoadingMorePosts: Boolean = false,
    val hasMorePosts: Boolean = true,
    val currentPostsPage: Int = 0,
    val error: String? = null,
    val isCurrentUser: Boolean = true,
    // Edit profile form state
    val isEditLoading: Boolean = false,
    val isEditFormInitialized: Boolean = false,
    val editDisplayName: String = "",
    val editUsername: String = "",
    val editBio: String = "",
    val editLocation: String = "",
    val editCurrentlyWorkingOn: String = "",
    val editThingsIveDone: String = "",
    val editProfileImageUrl: String? = null,
    val pendingAvatarUri: Uri? = null,
    val isSavingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val editError: String? = null,
    val isProfileSavedSuccess: Boolean = false,
    val activeLikeOperations: Set<String> = emptySet(),
    val isAdmin: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: String? = null
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val adminRepository: com.example.anubhav.data.repository.AdminRepository = com.example.anubhav.data.repository.AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val profilePageSize = 20
    private var currentLoadedTargetId: String? = null

    fun loadProfile(userId: String?) {
        val currentUserId = authRepository.getCurrentUserId()
        val targetId = userId ?: currentUserId ?: return
        val isCurrent = (currentUserId != null && targetId == currentUserId)
        currentLoadedTargetId = targetId

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isCurrentUser = isCurrent,
                    currentPostsPage = 0
                )
            }

            // 1. Fetch profile
            val profileResult = profileRepository.getProfile(targetId)
            val profile = profileResult.getOrNull()
            profileResult.exceptionOrNull()?.let {
                android.util.Log.e("ProfileViewModel", "Failed to load profile for $targetId", it)
            }

            // 2. Fetch user's posts (first page)
            val postsResult = postRepository.getUserPosts(targetId, currentUserId, page = 0, pageSize = profilePageSize)
            val posts = postsResult.getOrNull() ?: emptyList()
            postsResult.exceptionOrNull()?.let {
                android.util.Log.e("ProfileViewModel", "Failed to load posts for $targetId", it)
            }

            // 3. If current user, check admin role
            val isAdmin = if (isCurrent) {
                adminRepository.checkIsAdmin().getOrNull() ?: false
            } else false

            val errorMsg = when {
                profileResult.isFailure -> "Couldn't load profile. Please check your connection."
                postsResult.isFailure -> "Couldn't load posts. Please check your connection."
                else -> null
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    profile = profile,
                    posts = posts,
                    hasMorePosts = posts.size >= profilePageSize,
                    currentPostsPage = 0,
                    isAdmin = isAdmin,
                    error = errorMsg,
                    // If this is the current user and edit form hasn't been modified yet, sync it
                    editDisplayName = if (!it.isEditFormInitialized) profile?.displayName.orEmpty() else it.editDisplayName,
                    editUsername = if (!it.isEditFormInitialized) profile?.username.orEmpty() else it.editUsername,
                    editBio = if (!it.isEditFormInitialized) profile?.bio.orEmpty() else it.editBio,
                    editLocation = if (!it.isEditFormInitialized) profile?.location.orEmpty() else it.editLocation,
                    editCurrentlyWorkingOn = if (!it.isEditFormInitialized) profile?.currentlyWorkingOn.orEmpty() else it.editCurrentlyWorkingOn,
                    editThingsIveDone = if (!it.isEditFormInitialized) profile?.thingsIveDone.orEmpty() else it.editThingsIveDone,
                    editProfileImageUrl = if (!it.isEditFormInitialized) profile?.profileImageUrl else it.editProfileImageUrl,
                    isEditFormInitialized = if (profile != null) true else it.isEditFormInitialized
                )
            }
        }
    }

    fun loadNextUserPostsPage() {
        val state = _uiState.value
        val targetId = currentLoadedTargetId ?: return
        if (state.isLoadingMorePosts || !state.hasMorePosts || state.isLoading) {
            return
        }

        viewModelScope.launch {
            val nextPage = state.currentPostsPage + 1
            _uiState.update { it.copy(isLoadingMorePosts = true) }
            val currentUserId = authRepository.getCurrentUserId()
            val result = postRepository.getUserPosts(
                targetUserId = targetId,
                currentUserId = currentUserId,
                page = nextPage,
                pageSize = profilePageSize
            )
            result.fold(
                onSuccess = { newPosts ->
                    _uiState.update {
                        it.copy(
                            isLoadingMorePosts = false,
                            posts = it.posts + newPosts,
                            hasMorePosts = newPosts.size >= profilePageSize,
                            currentPostsPage = nextPage
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMorePosts = false) }
                }
            )
        }
    }

    /**
     * Pre-populates the edit profile form with current authenticated user data.
     * Guaranteed not to overwrite active edits on recomposition.
     */
    fun loadProfileForEditing(forceRefresh: Boolean = false) {
        val currentUserId = authRepository.getCurrentUserId() ?: return
        val current = _uiState.value

        // If form is already populated with current profile, and not forced, keep
        if (!forceRefresh && current.isEditFormInitialized && current.profile != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditLoading = it.profile == null, editError = null) }

            val profile = current.profile ?: profileRepository.getProfile(currentUserId).getOrNull()

            if (profile != null) {
                _uiState.update {
                    it.copy(
                        profile = profile,
                        isEditLoading = false,
                        isEditFormInitialized = true,
                        editDisplayName = profile.displayName,
                        editUsername = profile.username,
                        editBio = profile.bio,
                        editLocation = profile.location,
                        editCurrentlyWorkingOn = profile.currentlyWorkingOn,
                        editThingsIveDone = profile.thingsIveDone,
                        editProfileImageUrl = profile.profileImageUrl,
                        pendingAvatarUri = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isEditLoading = false,
                        editError = "Could not load current profile."
                    )
                }
            }
        }
    }

    fun onEditDisplayNameChange(v: String) = _uiState.update { it.copy(editDisplayName = v, editError = null) }
    fun onEditUsernameChange(v: String) = _uiState.update { it.copy(editUsername = v.trim().lowercase(), editError = null) }
    fun onEditBioChange(v: String) = _uiState.update { it.copy(editBio = v) }
    fun onEditLocationChange(v: String) = _uiState.update { it.copy(editLocation = v) }
    fun onEditCurrentlyWorkingOnChange(v: String) = _uiState.update { it.copy(editCurrentlyWorkingOn = v) }
    fun onEditThingsIveDoneChange(v: String) = _uiState.update { it.copy(editThingsIveDone = v) }

    fun onAvatarSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(pendingAvatarUri = uri, editError = null) }
    }

    fun saveProfile(context: Context, onSuccess: () -> Unit) {
        val currentUserId = authRepository.getCurrentUserId() ?: return
        val state = _uiState.value
        if (state.isSavingProfile) return

        val displayName = state.editDisplayName.trim()
        val username = state.editUsername.trim().lowercase()

        if (displayName.isBlank()) {
            _uiState.update { it.copy(editError = "Display name cannot be empty.") }
            return
        }

        if (username.length < 3 || username.length > 30 || !username.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
            _uiState.update {
                it.copy(editError = "Username must be 3-30 characters with letters, numbers, '.', or '_'.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProfile = true, editError = null) }

            // Validate unique username if changed
            if (username != state.profile?.username) {
                val availableResult = profileRepository.isUsernameAvailable(username, currentUserId)
                if (availableResult.isFailure || availableResult.getOrNull() == false) {
                    _uiState.update {
                        it.copy(isSavingProfile = false, editError = "Username '$username' is already taken.")
                    }
                    return@launch
                }
            }

            // If a new avatar image was picked, upload it first
            var finalImageUrl = state.editProfileImageUrl ?: state.profile?.profileImageUrl
            val pendingUri = state.pendingAvatarUri
            if (pendingUri != null) {
                val compressResult = ImageCompressor.compressProfileImage(context, pendingUri)
                if (compressResult.isFailure) {
                    android.util.Log.e("ProfileViewModel", "Avatar compression failed", compressResult.exceptionOrNull())
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            editError = "Couldn't process selected photo. Please try another one."
                        )
                    }
                    return@launch
                }
                val compressedBytes = compressResult.getOrNull()
                if (compressedBytes == null) {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            editError = "Couldn't process selected photo. Please try another one."
                        )
                    }
                    return@launch
                }
                val oldAvatarUrl = state.profile?.profileImageUrl
                val uploadResult = profileRepository.uploadProfileImage(
                    userId = currentUserId,
                    imageBytes = compressedBytes,
                    oldAvatarUrl = oldAvatarUrl
                )
                if (uploadResult.isFailure) {
                    val err = uploadResult.exceptionOrNull()
                    android.util.Log.e("ProfileViewModel", "Avatar upload failed", err)
                    val msg = if (err?.message?.contains("NoSuchBucket", ignoreCase = true) == true ||
                        err?.message?.contains("Bucket not found", ignoreCase = true) == true) {
                        "Storage bucket not found on server. Please configure storage."
                    } else {
                        "Couldn't upload profile picture. Please try again."
                    }
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            editError = msg
                        )
                    }
                    return@launch
                }
                finalImageUrl = uploadResult.getOrNull()
            }

            val updatedProfile = UserProfile(
                id = currentUserId,
                username = username,
                displayName = displayName,
                bio = state.editBio.trim(),
                location = state.editLocation.trim(),
                currentlyWorkingOn = state.editCurrentlyWorkingOn.trim(),
                thingsIveDone = state.editThingsIveDone.trim(),
                profileImageUrl = finalImageUrl
            )

            val saveResult = profileRepository.saveProfile(updatedProfile)
            saveResult.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profile = updatedProfile,
                            pendingAvatarUri = null,
                            editProfileImageUrl = finalImageUrl,
                            isProfileSavedSuccess = true
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    android.util.Log.e("ProfileViewModel", "Profile save failed", err)
                    val errorMessage = when {
                        err.message?.contains("network", ignoreCase = true) == true ||
                        err.message?.contains("timeout", ignoreCase = true) == true ||
                        err.message?.contains("connect", ignoreCase = true) == true ->
                            "Couldn't connect to server. Please check your internet connection."
                        else ->
                            err.localizedMessage?.ifBlank { null } ?: "Couldn't save your profile. Please try again."
                    }
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            editError = errorMessage
                        )
                    }
                }
            )
        }
    }

    fun resetSavedSuccess() {
        _uiState.update { it.copy(isProfileSavedSuccess = false) }
    }

    fun toggleLike(post: PostWithAuthor) {
        val state = _uiState.value
        if (state.activeLikeOperations.contains(post.id)) return

        val currentUserId = authRepository.getCurrentUserId() ?: return
        val currentlyLiked = post.isLikedByCurrentUser
        val newLikedState = !currentlyLiked

        // Optimistic UI update
        _uiState.update { current ->
            val updated = current.posts.map { item ->
                if (item.id == post.id) {
                    val updatedCount = if (item.isOwner && item.ownerLikeCount != null) {
                        if (newLikedState) item.ownerLikeCount + 1 else (item.ownerLikeCount - 1).coerceAtLeast(0)
                    } else null
                    item.copy(isLikedByCurrentUser = newLikedState, ownerLikeCount = updatedCount)
                } else item
            }
            current.copy(posts = updated, activeLikeOperations = current.activeLikeOperations + post.id)
        }

        viewModelScope.launch {
            val result = postRepository.toggleLike(post.id, currentUserId, currentlyLiked)
            _uiState.update { it.copy(activeLikeOperations = it.activeLikeOperations - post.id) }

            if (result.isFailure) {
                _uiState.update { current ->
                    val reverted = current.posts.map { item ->
                        if (item.id == post.id) {
                            item.copy(isLikedByCurrentUser = currentlyLiked, ownerLikeCount = post.ownerLikeCount)
                        } else item
                    }
                    current.copy(posts = reverted)
                }
            }
        }
    }

    fun deletePost(post: PostWithAuthor) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(posts = current.posts.filter { it.id != post.id })
            }
            postRepository.deletePost(post.id, post.imagePath)
        }
    }

    fun signOut(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onLoggedOut()
        }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        if (_uiState.value.isDeletingAccount) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, deleteAccountError = null) }
            val result = authRepository.deleteAccount()
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeletingAccount = false, deleteAccountError = null) }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDeletingAccount = false,
                            deleteAccountError = error.message ?: "Couldn't delete your account. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun clearDeleteAccountError() {
        _uiState.update { it.copy(deleteAccountError = null) }
    }
}
