package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.domain.model.AdminDashboardStats
import com.example.anubhav.domain.model.HighAttentionPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAdmin: Boolean = false,
    val stats: AdminDashboardStats = AdminDashboardStats(),
    val highAttentionPosts: List<HighAttentionPost> = emptyList(),
    val error: String? = null
)

class AdminDashboardViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Verify admin privilege server-side
            val isAdminResult = adminRepository.checkIsAdmin()
            val isAdmin = isAdminResult.getOrNull() ?: false
            if (!isAdmin) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAdmin = false,
                        error = "Access denied. Administrator privileges required."
                    )
                }
                return@launch
            }

            // 2. Load stats & attention items
            val statsResult = adminRepository.getDashboardStats()
            val attentionResult = adminRepository.getHighAttentionPosts()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAdmin = true,
                    stats = statsResult.getOrNull() ?: AdminDashboardStats(),
                    highAttentionPosts = attentionResult.getOrNull() ?: emptyList(),
                    error = null
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val statsResult = adminRepository.getDashboardStats()
            val attentionResult = adminRepository.getHighAttentionPosts()
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    stats = statsResult.getOrNull() ?: it.stats,
                    highAttentionPosts = attentionResult.getOrNull() ?: it.highAttentionPosts
                )
            }
        }
    }
}
