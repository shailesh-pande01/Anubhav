package com.example.anubhav.presentation.post

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.core.image.ImageCompressor
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import com.example.anubhav.domain.model.PostType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPostUiState(
    val isLoading: Boolean = true,
    val postId: String = "",
    val content: String = "",
    val postType: PostType = PostType.TEXT,
    val existingImageUrl: String? = null,
    val existingImagePath: String? = null,
    val selectedImageUri: Uri? = null,
    val compressedImageBytes: ByteArray? = null,
    val isImageRemoved: Boolean = false,
    val isCompressing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
) {
    val hasImage: Boolean
        get() = !isImageRemoved && (selectedImageUri != null || existingImageUrl != null)

    val canSave: Boolean
        get() {
            if (isLoading || isSaving || isCompressing) return false
            return if (hasImage) {
                true // Image posts can have optional or non-empty captions
            } else {
                content.trim().isNotBlank() // Text posts must have non-empty content
            }
        }
}

class EditPostViewModel(
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditPostUiState())
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()

    fun loadPost(postId: String) {
        val currentUserId = authRepository.getCurrentUserId()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, postId = postId) }
            val result = postRepository.getPostById(postId, currentUserId)
            result.fold(
                onSuccess = { post ->
                    if (!post.isOwner) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "You do not have permission to edit this post."
                            )
                        }
                        return@fold
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            postId = post.id,
                            content = post.content,
                            postType = post.postType,
                            existingImageUrl = post.imageUrl,
                            existingImagePath = post.imagePath,
                            isImageRemoved = false,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Couldn't load post details. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun onContentChange(newContent: String) {
        _uiState.update { it.copy(content = newContent, error = null) }
    }

    fun onImageSelected(context: Context, uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, selectedImageUri = uri, isImageRemoved = false, error = null) }
            val compressResult = ImageCompressor.compressPostImage(context, uri)
            compressResult.fold(
                onSuccess = { compressedBytes ->
                    _uiState.update {
                        it.copy(
                            compressedImageBytes = compressedBytes,
                            isCompressing = false
                        )
                    }
                },
                onFailure = { err ->
                    android.util.Log.e("EditPostViewModel", "Image processing failed", err)
                    _uiState.update {
                        it.copy(
                            isCompressing = false,
                            selectedImageUri = null,
                            compressedImageBytes = null,
                            error = "Couldn't process this image. Please try another one."
                        )
                    }
                }
            )
        }
    }

    fun removeImage() {
        _uiState.update {
            it.copy(
                isImageRemoved = true,
                selectedImageUri = null,
                compressedImageBytes = null,
                error = null
            )
        }
    }

    fun saveChanges(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return

        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            _uiState.update { it.copy(error = "You must be logged in to edit a post.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val result = postRepository.updatePost(
                userId = currentUserId,
                postId = state.postId,
                content = state.content,
                newImageBytes = state.compressedImageBytes,
                removeImage = state.isImageRemoved,
                existingImagePath = state.existingImagePath
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                    onSuccess()
                },
                onFailure = { err ->
                    android.util.Log.e("EditPostViewModel", "Failed to update post", err)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "Couldn't update post. Please try again."
                        )
                    }
                }
            )
        }
    }
}
