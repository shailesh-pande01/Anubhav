package com.example.anubhav.presentation.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.core.image.ImageCompressor
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val isTextMode: Boolean = true,
    val textContent: String = "",
    val selectedImageUri: Uri? = null,
    val compressedImageBytes: ByteArray? = null,
    val caption: String = "",
    val isCompressing: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
) {
    val canPost: Boolean
        get() {
            if (isPosting || isCompressing) return false
            return if (isTextMode) {
                textContent.trim().isNotBlank()
            } else {
                compressedImageBytes != null
            }
        }
}

class CreatePostViewModel(
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun setTextMode(isText: Boolean) {
        _uiState.update { it.copy(isTextMode = isText, error = null) }
    }

    fun onTextContentChange(content: String) {
        _uiState.update { it.copy(textContent = content, error = null) }
    }

    fun onCaptionChange(caption: String) {
        _uiState.update { it.copy(caption = caption, error = null) }
    }

    fun onImageSelected(context: Context, uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, selectedImageUri = uri, error = null) }
            val compressResult = ImageCompressor.compressImageWithDetails(context, uri)
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
                    android.util.Log.e("CreatePostViewModel", "Image processing failed: ${err.message}", err)
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
                selectedImageUri = null,
                compressedImageBytes = null,
                caption = ""
            )
        }
    }

    fun submitPost(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canPost) return

        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId == null) {
            _uiState.update { it.copy(error = "You must be logged in to create a post.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPosting = true, error = null) }

            val result = if (state.isTextMode) {
                postRepository.createTextPost(currentUserId, state.textContent)
            } else {
                val bytes = state.compressedImageBytes
                if (bytes == null) {
                    _uiState.update { it.copy(isPosting = false, error = "Please select an image.") }
                    return@launch
                }
                postRepository.createImagePost(currentUserId, state.caption, bytes)
            }

            result.fold(
                onSuccess = {
                    _uiState.update {
                        CreatePostUiState(isSuccess = true)
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    android.util.Log.e("CreatePostViewModel", "Post creation failed: ${err.message}", err)
                    val message = if (err.message?.contains("NoSuchBucket", ignoreCase = true) == true ||
                        err.message?.contains("Bucket not found", ignoreCase = true) == true) {
                        "Storage bucket not found on server. Please configure storage."
                    } else {
                        "Couldn't upload this post. Please try again."
                    }
                    _uiState.update {
                        it.copy(
                            isPosting = false,
                            error = message
                        )
                    }
                }
            )
        }
    }

    fun resetSuccess() {
        _uiState.update { CreatePostUiState() }
    }
}
