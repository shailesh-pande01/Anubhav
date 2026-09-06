package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.ReportRepository
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReportsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedStatus: ReportStatus? = ReportStatus.PENDING, // default to PENDING as per spec
    val reports: List<ReportWithDetails> = emptyList(),
    val error: String? = null
)

class AdminReportsViewModel(
    private val reportRepository: ReportRepository = ReportRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReportsUiState())
    val uiState: StateFlow<AdminReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports(ReportStatus.PENDING)
    }

    fun setStatusFilter(status: ReportStatus?) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadReports(status)
    }

    fun loadReports(status: ReportStatus?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = reportRepository.getReports(statusFilter = status, page = 0, pageSize = 50)
            result.fold(
                onSuccess = { reports ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            reports = reports,
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Couldn't load reports. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun refresh() {
        val status = _uiState.value.selectedStatus
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = reportRepository.getReports(statusFilter = status, page = 0, pageSize = 50)
            result.fold(
                onSuccess = { reports ->
                    _uiState.update { it.copy(isRefreshing = false, reports = reports) }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            )
        }
    }
}
