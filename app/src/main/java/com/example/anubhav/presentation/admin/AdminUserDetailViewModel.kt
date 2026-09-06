package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminUserDetail
import com.example.anubhav.domain.model.ModerationActionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class AdminUserDetailUiState(
    val isLoading: Boolean = true,
    val isActionLoading: Boolean = false,
    val userDetail: AdminUserDetail? = null,
    val actionSuccessMessage: String? = null,
    val error: String? = null
)

class AdminUserDetailViewModel(
    private val adminRepository: AdminRepository = AdminRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUserDetailUiState())
    val uiState: StateFlow<AdminUserDetailUiState> = _uiState.asStateFlow()

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = adminRepository.getUserDetail(userId)
            result.fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(isLoading = false, userDetail = detail, error = null) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "Couldn't load user details.") }
                }
            )
        }
    }

    fun warnUser(reason: String) {
        val user = _uiState.value.userDetail?.profile ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = user.id,
                action = ModerationActionType.WARNING,
                reason = reason.ifBlank { "Administrative warning" },
                adminId = adminId
            )
            loadUser(user.id)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    actionSuccessMessage = "Warning recorded for @${user.username}."
                )
            }
        }
    }

    fun restrictUser(durationDays: Int, reason: String) {
        val user = _uiState.value.userDetail?.profile ?: return
        val adminId = authRepository.getCurrentUserId() ?: return
        val durationUntil = Instant.now().plus(durationDays.toLong(), ChronoUnit.DAYS).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = user.id,
                action = ModerationActionType.USER_RESTRICTED,
                reason = reason.ifBlank { "Account restricted for $durationDays days" },
                durationUntil = durationUntil,
                adminId = adminId
            )
            loadUser(user.id)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    actionSuccessMessage = "@${user.username} restricted for $durationDays days."
                )
            }
        }
    }

    fun suspendUser(durationDays: Int, reason: String) {
        val user = _uiState.value.userDetail?.profile ?: return
        val adminId = authRepository.getCurrentUserId() ?: return
        val durationUntil = Instant.now().plus(durationDays.toLong(), ChronoUnit.DAYS).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = user.id,
                action = ModerationActionType.USER_SUSPENDED,
                reason = reason.ifBlank { "Account suspended for $durationDays days" },
                durationUntil = durationUntil,
                adminId = adminId
            )
            loadUser(user.id)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    actionSuccessMessage = "@${user.username} suspended for $durationDays days."
                )
            }
        }
    }

    fun banUser(reason: String) {
        val user = _uiState.value.userDetail?.profile ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = user.id,
                action = ModerationActionType.USER_BANNED,
                reason = reason.ifBlank { "Permanently banned by administrator" },
                adminId = adminId
            )
            loadUser(user.id)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    actionSuccessMessage = "@${user.username} has been permanently banned."
                )
            }
        }
    }

    fun resetToActive(reason: String) {
        val user = _uiState.value.userDetail?.profile ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = user.id,
                action = ModerationActionType.STATUS_RESET,
                reason = reason.ifBlank { "Account restored to Active" },
                adminId = adminId
            )
            loadUser(user.id)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    actionSuccessMessage = "@${user.username} status reset to Active."
                )
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionSuccessMessage = null) }
    }
}
