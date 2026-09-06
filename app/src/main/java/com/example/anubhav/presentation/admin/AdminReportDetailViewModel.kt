package com.example.anubhav.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AdminRepository
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.PostRepository
import com.example.anubhav.data.repository.ReportRepository
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminUserDetail
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.Report
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

data class AdminReportDetailUiState(
    val isLoading: Boolean = true,
    val isActionLoading: Boolean = false,
    val report: ReportWithDetails? = null,
    val reportedUserDetail: AdminUserDetail? = null,
    val previousReportsOnPost: List<Report> = emptyList(),
    val previousReportsOnUser: List<Report> = emptyList(),
    val actionSuccessMessage: String? = null,
    val actionErrorMessage: String? = null,
    val error: String? = null
)

class AdminReportDetailViewModel(
    private val reportRepository: ReportRepository = ReportRepository(),
    private val adminRepository: AdminRepository = AdminRepository(),
    private val postRepository: PostRepository = PostRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReportDetailUiState())
    val uiState: StateFlow<AdminReportDetailUiState> = _uiState.asStateFlow()

    fun loadReport(reportId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val reportResult = reportRepository.getReportById(reportId)
            reportResult.fold(
                onSuccess = { report ->
                    // Set report to REVIEWING automatically if it was PENDING
                    val currentAdminId = authRepository.getCurrentUserId().orEmpty()
                    if (report.status == ReportStatus.PENDING && currentAdminId.isNotBlank()) {
                        reportRepository.updateReportStatus(report.id, ReportStatus.REVIEWING, null, currentAdminId)
                    }

                    val reportedUserId = report.reportedUserId
                    val userDetailResult = reportedUserId?.let { adminRepository.getUserDetail(it).getOrNull() }
                    val postReportsResult = report.postId?.let { reportRepository.getReportsForPost(it).getOrNull() }
                    val userReportsResult = reportedUserId?.let { reportRepository.getReportsForUser(it).getOrNull() }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            report = if (report.status == ReportStatus.PENDING) report.copy(status = ReportStatus.REVIEWING) else report,
                            reportedUserDetail = userDetailResult,
                            previousReportsOnPost = postReportsResult ?: emptyList(),
                            previousReportsOnUser = userReportsResult ?: emptyList(),
                            error = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Couldn't load report details."
                        )
                    }
                }
            )
        }
    }

    fun dismissReport(reason: String) {
        val report = _uiState.value.report ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            // 1. Update report status to DISMISSED
            reportRepository.updateReportStatus(report.id, ReportStatus.DISMISSED, reason.ifBlank { "Dismissed by admin" }, adminId)
            // 2. Audit log
            adminRepository.moderateUser(
                userId = report.reportedUserId,
                action = ModerationActionType.REPORT_DISMISSED,
                reason = reason.ifBlank { "Report dismissed" },
                reportId = report.id,
                adminId = adminId
            )
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    report = it.report?.copy(status = ReportStatus.DISMISSED, resolution = reason),
                    actionSuccessMessage = "Report dismissed successfully."
                )
            }
        }
    }

    fun removePost(reason: String) {
        if (_uiState.value.isActionLoading) return
        val report = _uiState.value.report ?: return
        val postId = report.postId ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, actionErrorMessage = null, actionSuccessMessage = null) }
            val cleanedReason = reason.ifBlank { "Violated community standards" }
            val result = postRepository.removePostByAdmin(
                postId = postId,
                adminId = adminId,
                reason = cleanedReason,
                imagePath = report.post?.imagePath,
                reportId = report.id,
                reportedUserId = report.reportedUserId
            )
            result.fold(
                onSuccess = { res ->
                    val successMsg = if (res.alreadyDeleted) {
                        "This post was already removed. Report has been marked as resolved."
                    } else {
                        "Post completely removed and report resolved."
                    }
                    _uiState.update {
                        it.copy(
                            isActionLoading = false,
                            report = it.report?.copy(
                                status = ReportStatus.RESOLVED,
                                resolution = "Post removed: $cleanedReason",
                                post = null
                            ),
                            actionSuccessMessage = successMsg,
                            actionErrorMessage = null
                        )
                    }
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

    fun warnUser(reason: String) {
        val report = _uiState.value.report ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = report.reportedUserId,
                action = ModerationActionType.WARNING,
                reason = reason.ifBlank { "Warning issued for policy violation" },
                reportId = report.id,
                adminId = adminId
            )
            reportRepository.updateReportStatus(report.id, ReportStatus.RESOLVED, "Warning issued: $reason", adminId)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    report = it.report?.copy(status = ReportStatus.RESOLVED, resolution = "Warning issued"),
                    actionSuccessMessage = "Warning issued to @${report.reportedUser?.username}."
                )
            }
        }
    }

    fun restrictUser(durationDays: Int, reason: String) {
        val report = _uiState.value.report ?: return
        val adminId = authRepository.getCurrentUserId() ?: return
        val durationUntil = Instant.now().plus(durationDays.toLong(), ChronoUnit.DAYS).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = report.reportedUserId,
                action = ModerationActionType.USER_RESTRICTED,
                reason = reason.ifBlank { "Account restricted for $durationDays days" },
                durationUntil = durationUntil,
                reportId = report.id,
                adminId = adminId
            )
            reportRepository.updateReportStatus(report.id, ReportStatus.RESOLVED, "User restricted for $durationDays days", adminId)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    report = it.report?.copy(status = ReportStatus.RESOLVED, resolution = "User restricted"),
                    actionSuccessMessage = "User restricted for $durationDays days."
                )
            }
        }
    }

    fun suspendUser(durationDays: Int, reason: String) {
        val report = _uiState.value.report ?: return
        val adminId = authRepository.getCurrentUserId() ?: return
        val durationUntil = Instant.now().plus(durationDays.toLong(), ChronoUnit.DAYS).toString()

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = report.reportedUserId,
                action = ModerationActionType.USER_SUSPENDED,
                reason = reason.ifBlank { "Account suspended for $durationDays days" },
                durationUntil = durationUntil,
                reportId = report.id,
                adminId = adminId
            )
            reportRepository.updateReportStatus(report.id, ReportStatus.RESOLVED, "User suspended for $durationDays days", adminId)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    report = it.report?.copy(status = ReportStatus.RESOLVED, resolution = "User suspended"),
                    actionSuccessMessage = "User suspended for $durationDays days."
                )
            }
        }
    }

    fun banUser(reason: String) {
        val report = _uiState.value.report ?: return
        val adminId = authRepository.getCurrentUserId() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            adminRepository.moderateUser(
                userId = report.reportedUserId,
                action = ModerationActionType.USER_BANNED,
                reason = reason.ifBlank { "Account permanently banned for community violation" },
                reportId = report.id,
                adminId = adminId
            )
            reportRepository.updateReportStatus(report.id, ReportStatus.RESOLVED, "User permanently banned", adminId)
            _uiState.update {
                it.copy(
                    isActionLoading = false,
                    report = it.report?.copy(status = ReportStatus.RESOLVED, resolution = "User banned"),
                    actionSuccessMessage = "@${report.reportedUser?.username} has been permanently banned."
                )
            }
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

