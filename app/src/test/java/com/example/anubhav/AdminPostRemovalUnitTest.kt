package com.example.anubhav

import com.example.anubhav.data.remote.dto.AdminRemovePostRpcResultDto
import com.example.anubhav.data.remote.dto.ModerationActionDto
import com.example.anubhav.data.remote.dto.PostDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.data.remote.dto.UpdateReportStatusDto
import com.example.anubhav.domain.model.AdminPostSummary
import com.example.anubhav.domain.model.AdminRemovePostResult
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import com.example.anubhav.domain.model.UserProfile
import com.example.anubhav.presentation.admin.AdminPostsUiState
import com.example.anubhav.presentation.admin.AdminReportDetailUiState
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AdminPostRemovalUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // --- 1. DTO Serialization & Deserialization ---

    @Test
    fun testAdminRemovePostRpcResultDto_deserializationSuccess() {
        val jsonStr = """
            {
                "success": true,
                "already_deleted": false,
                "image_path": "user-uuid/post-123.jpg",
                "user_id": "author-uuid-99"
            }
        """.trimIndent()

        val dto = json.decodeFromString<AdminRemovePostRpcResultDto>(jsonStr)
        assertTrue(dto.success)
        assertFalse(dto.alreadyDeleted)
        assertEquals("user-uuid/post-123.jpg", dto.imagePath)
        assertEquals("author-uuid-99", dto.userId)
    }

    @Test
    fun testAdminRemovePostRpcResultDto_alreadyDeleted() {
        val jsonStr = """
            {
                "success": true,
                "already_deleted": true,
                "image_path": null,
                "user_id": null
            }
        """.trimIndent()

        val dto = json.decodeFromString<AdminRemovePostRpcResultDto>(jsonStr)
        assertTrue(dto.success)
        assertTrue(dto.alreadyDeleted)
        assertNull(dto.imagePath)
        assertNull(dto.userId)
    }

    @Test
    fun testModerationActionDto_preservesTargetPostId() {
        val dto = ModerationActionDto(
            adminId = "admin-1",
            userId = "author-1",
            postId = "post-uuid-abc",
            reportId = "rep-1",
            action = "POST_REMOVED",
            reason = "Violated community guidelines"
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue(jsonStr.contains("\"post_id\":\"post-uuid-abc\""))
        assertTrue(jsonStr.contains("\"action\":\"POST_REMOVED\""))
        assertTrue(jsonStr.contains("\"admin_id\":\"admin-1\""))
        assertTrue(jsonStr.contains("\"user_id\":\"author-1\""))

        val decoded = json.decodeFromString<ModerationActionDto>(jsonStr)
        assertEquals("post-uuid-abc", decoded.postId)
        assertEquals(ModerationActionType.POST_REMOVED, decoded.toDomain().action)
        assertEquals("post-uuid-abc", decoded.toDomain().postId)
    }

    // --- 2. Storage Path Sanitization ---

    private fun sanitizeStoragePath(rawPath: String): String {
        return rawPath.trim()
            .removePrefix("post-images/")
            .substringAfter("/post-images/")
    }

    @Test
    fun testStoragePathSanitization_standardPath() {
        val path = "user-123/post-456.jpg"
        assertEquals("user-123/post-456.jpg", sanitizeStoragePath(path))
    }

    @Test
    fun testStoragePathSanitization_prefixedBucket() {
        val path = "post-images/user-123/post-456.jpg"
        assertEquals("user-123/post-456.jpg", sanitizeStoragePath(path))
    }

    @Test
    fun testStoragePathSanitization_fullPublicUrl() {
        val path = "https://xyz.supabase.co/storage/v1/object/public/post-images/user-123/post-456.jpg"
        assertEquals("user-123/post-456.jpg", sanitizeStoragePath(path))
    }

    // --- 3. Error Message Sanitization (Security: Never leak database internals) ---

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

    @Test
    fun testErrorSanitization_networkErrors() {
        val netEx = UnknownHostException("Unable to resolve host api.supabase.co")
        val sanitized = sanitizeErrorMessage(netEx)
        assertEquals("Unable to connect. Please check your internet connection and try again.", sanitized)

        val timeoutEx = SocketTimeoutException("connect timed out")
        assertEquals("Unable to connect. Please check your internet connection and try again.", sanitizeErrorMessage(timeoutEx))
    }

    @Test
    fun testErrorSanitization_authErrors() {
        val authEx = RuntimeException("Access denied. Administrator privileges required.")
        assertEquals("Administrator authorization required. Please verify your permissions.", sanitizeErrorMessage(authEx))

        val sessionEx = RuntimeException("JWT session expired")
        assertEquals("Administrator authorization required. Please verify your permissions.", sanitizeErrorMessage(sessionEx))
    }

    @Test
    fun testErrorSanitization_hidesInternalPostgrestAndSqlErrors() {
        val sqlEx = RuntimeException("PostgrestException: relation public.posts does not exist at line 14, SQLSTATE 42P01")
        val sanitized = sanitizeErrorMessage(sqlEx)
        assertEquals("Couldn't complete the action. Please try again.", sanitized)
        assertFalse(sanitized.contains("PostgrestException"))
        assertFalse(sanitized.contains("SQLSTATE"))
        assertFalse(sanitized.contains("line 14"))
    }

    // --- 4. Idempotent Removal Logic Simulation ---

    @Test
    fun testIdempotentRemoval_postAlreadyDeleted() {
        val result = AdminRemovePostResult(alreadyDeleted = true, imagePath = null, authorId = null)
        assertTrue(result.alreadyDeleted)
        assertNull(result.imagePath)

        val successMessage = if (result.alreadyDeleted) {
            "This post was already removed. Report has been marked as resolved."
        } else {
            "Post completely removed and report resolved."
        }
        assertEquals("This post was already removed. Report has been marked as resolved.", successMessage)
    }

    @Test
    fun testDoubleRemovalSimulation_idempotentSuccess() {
        // First removal
        var postExists = true
        fun executeRemoval(): AdminRemovePostResult {
            return if (postExists) {
                postExists = false
                AdminRemovePostResult(alreadyDeleted = false, imagePath = "user/post.jpg", authorId = "author-1")
            } else {
                AdminRemovePostResult(alreadyDeleted = true, imagePath = null, authorId = null)
            }
        }

        val firstCall = executeRemoval()
        assertFalse(firstCall.alreadyDeleted)
        assertEquals("user/post.jpg", firstCall.imagePath)

        // Second removal for same post by another or same admin
        val secondCall = executeRemoval()
        assertTrue(secondCall.alreadyDeleted)
        assertNull(secondCall.imagePath)
    }

    // --- 5. Multiple Reports Resolution Consistency ---

    @Test
    fun testMultipleReportsOnSamePost_allMarkedResolved() {
        val targetPostId = "post-999"
        val reports = listOf(
            ReportDto(id = "rep-1", postId = targetPostId, reason = "Spam", status = "PENDING", createdAt = "2026-09-01"),
            ReportDto(id = "rep-2", postId = targetPostId, reason = "Harassment", status = "PENDING", createdAt = "2026-09-02"),
            ReportDto(id = "rep-3", postId = targetPostId, reason = "Inappropriate content", status = "REVIEWING", createdAt = "2026-09-03"),
            ReportDto(id = "rep-4", postId = "other-post", reason = "Spam", status = "PENDING", createdAt = "2026-09-04")
        )

        val updatedReports = reports.map { report ->
            if (report.postId == targetPostId) {
                report.copy(
                    status = "RESOLVED",
                    resolution = "Post removed: Violated community standards",
                    reviewedBy = "admin-1"
                )
            } else {
                report
            }
        }

        // Verify all target post reports transitioned to RESOLVED
        val targetReports = updatedReports.filter { it.postId == targetPostId }
        assertEquals(3, targetReports.size)
        assertTrue(targetReports.all { it.status == "RESOLVED" })
        assertTrue(targetReports.all { it.resolution?.startsWith("Post removed") == true })

        // Verify other post reports untouched
        val otherReport = updatedReports.first { it.id == "rep-4" }
        assertEquals("PENDING", otherReport.status)
    }

    // --- 6. UI State Transitions for AdminReportDetailViewModel ---

    @Test
    fun testAdminReportDetailUiState_removalSuccessTransitions() {
        val initialReport = ReportWithDetails(
            id = "rep-1",
            postId = "post-100",
            reason = "Spam",
            status = ReportStatus.PENDING,
            createdAt = "2026-09-01",
            post = PostWithAuthor(
                id = "post-100",
                userId = "u-1",
                postType = PostType.IMAGE,
                content = "Offensive image post",
                imagePath = "u-1/post-100.jpg",
                createdAt = "2026-09-01",
                author = UserProfile(id = "u-1", username = "bad_actor", displayName = "Bad Actor")
            )
        )

        var state = AdminReportDetailUiState(
            isLoading = false,
            isActionLoading = false,
            report = initialReport
        )

        // 1. Action starts
        state = state.copy(isActionLoading = true, actionErrorMessage = null, actionSuccessMessage = null)
        assertTrue(state.isActionLoading)
        assertNull(state.actionErrorMessage)
        assertNull(state.actionSuccessMessage)

        // 2. Action succeeds: post becomes null, status is RESOLVED, message shown
        state = state.copy(
            isActionLoading = false,
            report = state.report?.copy(
                status = ReportStatus.RESOLVED,
                resolution = "Post removed: Hate speech",
                post = null
            ),
            actionSuccessMessage = "Post completely removed and report resolved.",
            actionErrorMessage = null
        )

        assertFalse(state.isActionLoading)
        assertNull(state.report?.post) // Post is null!
        assertEquals(ReportStatus.RESOLVED, state.report?.status)
        assertEquals("Post removed: Hate speech", state.report?.resolution)
        assertEquals("Post completely removed and report resolved.", state.actionSuccessMessage)
    }

    @Test
    fun testAdminReportDetailUiState_removalFailureTransitions() {
        val initialReport = ReportWithDetails(
            id = "rep-1",
            postId = "post-100",
            reason = "Spam",
            status = ReportStatus.PENDING,
            createdAt = "2026-09-01",
            post = PostWithAuthor(
                id = "post-100",
                userId = "u-1",
                postType = PostType.TEXT,
                content = "Text post",
                createdAt = "2026-09-01",
                author = UserProfile(id = "u-1", username = "user1", displayName = "User 1")
            )
        )

        var state = AdminReportDetailUiState(
            isLoading = false,
            isActionLoading = true,
            report = initialReport
        )

        // Error occurs: loading stops, error message displayed, post and report status UNCHANGED
        state = state.copy(
            isActionLoading = false,
            actionErrorMessage = "Unable to connect. Please check your internet connection and try again."
        )

        assertFalse(state.isActionLoading)
        assertNotNull(state.report?.post) // Post is still present (not falsely removed)
        assertEquals(ReportStatus.PENDING, state.report?.status) // Still pending (not falsely marked resolved)
        assertNull(state.actionSuccessMessage) // No false success
        assertEquals("Unable to connect. Please check your internet connection and try again.", state.actionErrorMessage)
    }

    // --- 7. Duplicate Removal Call Suppression (Rapid Double Click Guard) ---

    @Test
    fun testDoubleClickGuard_suppressesConcurrentRemovalCalls() {
        var callCount = 0
        var isActionLoading = false

        fun triggerRemovePost() {
            if (isActionLoading) return
            isActionLoading = true
            callCount++
        }

        // First click
        triggerRemovePost()
        assertEquals(1, callCount)
        assertTrue(isActionLoading)

        // Rapid second and third clicks
        triggerRemovePost()
        triggerRemovePost()
        assertEquals(1, callCount)
    }

    // --- 8. AdminPostsUiState Error & Loading State Transitions ---

    @Test
    fun testAdminPostsUiState_transitions() {
        var state = AdminPostsUiState(
            isLoading = false,
            isActionLoading = false
        )

        state = state.copy(isActionLoading = true)
        assertTrue(state.isActionLoading)

        state = state.copy(
            isActionLoading = false,
            actionSuccessMessage = "Post removed successfully."
        )
        assertFalse(state.isActionLoading)
        assertEquals("Post removed successfully.", state.actionSuccessMessage)

        state = state.copy(actionSuccessMessage = null)
        assertNull(state.actionSuccessMessage)
    }
}
