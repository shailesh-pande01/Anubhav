package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.domain.model.ModerationAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminModerationHistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedActionFilter: String = "ALL",
    val actions: List<ModerationAction> = emptyList(),
    val error: String? = null
)

class AdminModerationHistoryViewModel(
    private val adminRepository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminModerationHistoryUiState())
    val uiState: StateFlow<AdminModerationHistoryUiState> = _uiState.asStateFlow()

    init {
        loadActions()
    }

    fun onFilterChanged(filter: String) {
        _uiState.update { it.copy(selectedActionFilter = filter) }
        loadActions()
    }

    fun loadActions() {
        val filter = _uiState.value.selectedActionFilter
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = adminRepository.getModerationActions(
                actionFilter = if (filter == "ALL") null else filter,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { actions ->
                    _uiState.update { it.copy(isLoading = false, actions = actions, error = null) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load moderation history.") }
                }
            )
        }
    }

    fun refresh() {
        val filter = _uiState.value.selectedActionFilter
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = adminRepository.getModerationActions(
                actionFilter = if (filter == "ALL") null else filter,
                page = 0,
                pageSize = 50
            )
            result.fold(
                onSuccess = { actions ->
                    _uiState.update { it.copy(isRefreshing = false, actions = actions) }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            )
        }
    }
}
