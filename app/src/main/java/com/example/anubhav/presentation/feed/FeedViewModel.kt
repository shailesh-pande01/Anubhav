package com.example.anubhav.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import com.example.anubhav.domain.model.PostWithAuthor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val posts: List<PostWithAuthor> = emptyList(),
    val error: String? = null,
    val hasMorePages: Boolean = true,
    val currentPage: Int = 0,
    val activeLikeOperations: Set<String> = emptySet()
)

class FeedViewModel(
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val pageSize = 20

    init {
        loadInitialFeed()
    }

    fun loadInitialFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentPage = 0) }
            val currentUserId = authRepository.getCurrentUserId()
            val result = postRepository.getFeedPosts(currentUserId, page = 0, pageSize = pageSize)
            result.fold(
                onSuccess = { fetchedPosts ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            posts = fetchedPosts,
                            hasMorePages = fetchedPosts.size >= pageSize,
                            currentPage = 0,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Couldn't load posts. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val currentUserId = authRepository.getCurrentUserId()
            val result = postRepository.getFeedPosts(currentUserId, page = 0, pageSize = pageSize)
            result.fold(
                onSuccess = { fetchedPosts ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            posts = fetchedPosts,
                            hasMorePages = fetchedPosts.size >= pageSize,
                            currentPage = 0,
                            error = null
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = "Couldn't refresh posts. Please check your connection."
                        )
                    }
                }
            )
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages || state.isLoading || state.isRefreshing) {
            return
        }

        viewModelScope.launch {
            val nextPage = state.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }
            val currentUserId = authRepository.getCurrentUserId()
            val result = postRepository.getFeedPosts(currentUserId, page = nextPage, pageSize = pageSize)
            result.fold(
                onSuccess = { newPosts ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            posts = it.posts + newPosts,
                            hasMorePages = newPosts.size >= pageSize,
                            currentPage = nextPage
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    fun toggleLike(post: PostWithAuthor) {
        val state = _uiState.value
        if (state.activeLikeOperations.contains(post.id)) return // Double-tap protection

        val currentUserId = authRepository.getCurrentUserId() ?: return
        val currentlyLiked = post.isLikedByCurrentUser
        val newLikedState = !currentlyLiked

        // Optimistic UI update
        _uiState.update { current ->
            val updatedPosts = current.posts.map { item ->
                if (item.id == post.id) {
                    val updatedCount = if (item.isOwner && item.ownerLikeCount != null) {
                        if (newLikedState) item.ownerLikeCount + 1 else (item.ownerLikeCount - 1).coerceAtLeast(0)
                    } else {
                        null
                    }
                    item.copy(isLikedByCurrentUser = newLikedState, ownerLikeCount = updatedCount)
                } else item
            }
            current.copy(
                posts = updatedPosts,
                activeLikeOperations = current.activeLikeOperations + post.id
            )
        }

        viewModelScope.launch {
            val result = postRepository.toggleLike(post.id, currentUserId, currentlyLiked)
            _uiState.update { current ->
                current.copy(activeLikeOperations = current.activeLikeOperations - post.id)
            }

            if (result.isFailure) {
                // Revert optimistic update on failure
                _uiState.update { current ->
                    val reverted = current.posts.map { item ->
                        if (item.id == post.id) {
                            item.copy(
                                isLikedByCurrentUser = currentlyLiked,
                                ownerLikeCount = post.ownerLikeCount
                            )
                        } else item
                    }
                    current.copy(posts = reverted)
                }
            }
        }
    }

    fun deletePost(post: PostWithAuthor) {
        viewModelScope.launch {
            // Optimistic removal from feed
            _uiState.update { current ->
                current.copy(posts = current.posts.filter { it.id != post.id })
            }
            val result = postRepository.deletePost(post.id, post.imagePath)
            if (result.isFailure) {
                // Refresh feed to restore if server deletion failed
                loadInitialFeed()
            }
        }
    }
}
