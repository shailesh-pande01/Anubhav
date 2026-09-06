package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.domain.model.AdminUserSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedStatus: String = "ALL",
    val users: List<AdminUserSummary> = emptyList(),
    val error: String? = null
)

class AdminUsersViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadUsers()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // Debounce search
            loadUsers()
        }
    }

    fun onStatusFilterChanged(status: String) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadUsers()
    }

    fun loadUsers() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = adminRepository.getUsers(
                searchQuery = state.searchQuery.ifBlank { null },
                statusFilter = state.selectedStatus,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { users ->
                    _uiState.update { it.copy(isLoading = false, users = users, error = null) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load users.") }
                }
            )
        }
    }

    fun refresh() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = adminRepository.getUsers(
                searchQuery = state.searchQuery.ifBlank { null },
                statusFilter = state.selectedStatus,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { users ->
                    _uiState.update { it.copy(isRefreshing = false, users = users) }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            )
        }
    }
}
