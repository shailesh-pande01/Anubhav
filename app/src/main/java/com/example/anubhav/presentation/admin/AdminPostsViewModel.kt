package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import com.example.anubhav.domain.model.AdminPostSummary
import com.example.anubhav.domain.model.ModerationActionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminPostsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isActionLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedFilter: String = "ALL", // "ALL", "MOST_REPORTED", "REMOVED"
    val posts: List<AdminPostSummary> = emptyList(),
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null,
    val error: String? = null
)

class AdminPostsViewModel(
    private val adminRepository: AdminRepository = AdminRepository(),
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPostsUiState())
    val uiState: StateFlow<AdminPostsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadPosts()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadPosts()
        }
    }

    fun onFilterChanged(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
        loadPosts()
    }

    fun loadPosts() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = adminRepository.getAdminPosts(
                searchQuery = state.searchQuery.ifBlank { null },
                filter = state.selectedFilter,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { posts ->
                    _uiState.update { it.copy(isLoading = false, posts = posts, error = null) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load posts.") }
                }
            )
        }
    }

    fun removePost(postId: String, authorId: String, reason: String, imagePath: String? = null) {
        if (_uiState.value.isActionLoading) return
        val adminId = authRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, actionErrorMessage = null, actionSuccessMessage = null) }
            val cleanedReason = reason.ifBlank { "Removed by admin" }
            val result = postRepository.removePostByAdmin(
                postId = postId,
                adminId = adminId,
                reason = cleanedReason,
                imagePath = imagePath,
                reportedUserId = authorId
            )
            result.fold(
                onSuccess = { res ->
                    loadPosts()
                    val msg = if (res.alreadyDeleted) "This post was already removed." else "Post removed successfully."
                    _uiState.update { it.copy(isActionLoading = false, actionSuccessMessage = msg) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            actionErrorMessage = sanitizeErrorMessage(err)
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = adminRepository.getAdminPosts(
                searchQuery = state.searchQuery.ifBlank { null },
                filter = state.selectedFilter,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { posts ->
                    _uiState.update { it.copy(isRefreshing = false, posts = posts) }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            )
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionSuccessMessage = null) }
    }

    fun clearActionErrorMessage() {
        _uiState.update { it.copy(actionErrorMessage = null) }
    }

    private fun sanitizeErrorMessage(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) || msg.contains("host", ignoreCase = true) -> {
                "Unable to connect. Please check your internet connection and try again."
            }
            msg.contains("session", ignoreCase = true) || msg.contains("auth", ignoreCase = true) ||
            msg.contains("privileges", ignoreCase = true) || msg.contains("denied", ignoreCase = true) -> {
                "Administrator authorization required. Please verify your permissions."
            }
            else -> {
                "Couldn't complete the action. Please try again."
            }
        }
    }
}
