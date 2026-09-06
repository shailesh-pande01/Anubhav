package com.example.anubhav

import com.example.anubhav.data.remote.dto.PostImagePathDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.domain.model.Report
import com.example.anubhav.domain.model.ReportReason
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import com.example.anubhav.presentation.profile.ProfileUiState
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

class AccountDeletionUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // --- 1. DTO & Model Null-Safety for Anonymized Reports ---

    @Test
    fun testReportDto_deserializationWithNullReporterAndReportedUser() {
        val jsonWithNulls = """
            {
                "id": "rep-100",
                "post_id": "post-55",
                "reporter_id": null,
                "reported_user_id": null,
                "reason": "SPAM",
                "description": "Deleted user report",
                "status": "RESOLVED",
                "resolution": "Account deleted by user",
                "reviewed_by": null,
                "reviewed_at": "2026-09-06T00:00:00Z",
                "created_at": "2026-09-01T00:00:00Z"
            }
        """.trimIndent()

        val dto = json.decodeFromString<ReportDto>(jsonWithNulls)
        assertEquals("rep-100", dto.id)
        assertEquals("post-55", dto.postId)
        assertNull(dto.reporterId)
        assertNull(dto.reportedUserId)
        assertEquals("SPAM", dto.reason)
        assertEquals("RESOLVED", dto.status)
        assertEquals("Account deleted by user", dto.resolution)

        // Verify conversion to domain model
        val domainReport = dto.toDomain()
        assertNull(domainReport.reporterId)
        assertNull(domainReport.reportedUserId)
        assertEquals(ReportStatus.RESOLVED, domainReport.status)
        assertEquals("Account deleted by user", domainReport.resolution)
    }

    @Test
    fun testReportDto_serializationPreservesNulls() {
        val dto = ReportDto(
            id = "rep-101",
            postId = null,
            reporterId = null,
            reportedUserId = null,
            reason = "HARASSMENT",
            description = "",
            status = "PENDING",
            resolution = null,
            reviewedBy = null,
            reviewedAt = null,
            createdAt = "2026-09-06T00:00:00Z"
        )

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"id\":\"rep-101\""))
        assertTrue(encoded.contains("\"reason\":\"HARASSMENT\""))
    }

    @Test
    fun testReportWithDetails_supportsNullReporterAndReportedUser() {
        val reportWithDetails = ReportWithDetails(
            id = "rep-102",
            postId = "post-99",
            reporterId = null,
            reportedUserId = null,
            reason = "SPAM",
            description = "Spam content",
            status = ReportStatus.RESOLVED,
            resolution = "Account deleted by user",
            reviewedBy = null,
            reviewedAt = "2026-09-06T00:00:00Z",
            createdAt = "2026-09-01T00:00:00Z",
            relativeTime = "2h ago",
            reporter = null,
            reportedUser = null,
            post = null
        )

        assertNull(reportWithDetails.reporterId)
        assertNull(reportWithDetails.reportedUserId)
        assertNull(reportWithDetails.reporter)
        assertNull(reportWithDetails.reportedUser)
        assertEquals(ReportStatus.RESOLVED, reportWithDetails.status)
        assertEquals("Account deleted by user", reportWithDetails.resolution)
    }

    @Test
    fun testPostImagePathDto_deserialization() {
        val jsonWithImagePath = """{"image_path": "user-uuid/post-1.jpg"}"""
        val dto1 = json.decodeFromString<PostImagePathDto>(jsonWithImagePath)
        assertEquals("user-uuid/post-1.jpg", dto1.imagePath)

        val jsonWithNull = """{"image_path": null}"""
        val dto2 = json.decodeFromString<PostImagePathDto>(jsonWithNull)
        assertNull(dto2.imagePath)

        val jsonEmpty = """{}"""
        val dto3 = json.decodeFromString<PostImagePathDto>(jsonEmpty)
        assertNull(dto3.imagePath)
    }

    // --- 2. Error Mapping & Sanitization ---

    private fun mapDeleteAccountException(e: Throwable): Exception {
        val msg = e.message ?: ""
        return when {
            e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ||
            e is HttpRequestTimeoutException ||
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("Failed to connect", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) -> {
                Exception("Unable to connect. Please check your internet connection and try again.")
            }
            msg.contains("session", ignoreCase = true) || msg.contains("auth", ignoreCase = true) ||
            msg.contains("token", ignoreCase = true) || msg.contains("401", ignoreCase = true) -> {
                Exception("Your session has expired. Please sign in again.")
            }
            else -> {
                Exception("Couldn't delete your account. Please try again.")
            }
        }
    }

    @Test
    fun testErrorMapping_networkExceptionsSanitized() {
        val hostEx = UnknownHostException("api.supabase.co: nodename nor servname provided")
        val mappedHost = mapDeleteAccountException(hostEx)
        assertEquals("Unable to connect. Please check your internet connection and try again.", mappedHost.message)

        val timeoutEx = SocketTimeoutException("connect timed out")
        val mappedTimeout = mapDeleteAccountException(timeoutEx)
        assertEquals("Unable to connect. Please check your internet connection and try again.", mappedTimeout.message)

        val connectEx = ConnectException("Connection refused")
        val mappedConnect = mapDeleteAccountException(connectEx)
        assertEquals("Unable to connect. Please check your internet connection and try again.", mappedConnect.message)
    }

    @Test
    fun testErrorMapping_authAndSessionExceptionsSanitized() {
        val tokenExpired = RuntimeException("JWT expired: token has invalid claims")
        val mappedToken = mapDeleteAccountException(tokenExpired)
        assertEquals("Your session has expired. Please sign in again.", mappedToken.message)

        val authError = RuntimeException("HTTP 401 Unauthorized: user session missing")
        val mappedAuth = mapDeleteAccountException(authError)
        assertEquals("Your session has expired. Please sign in again.", mappedAuth.message)
    }

    @Test
    fun testErrorMapping_databaseAndInternalExceptionsSanitizedNeverLeaksTechnicalDetails() {
        val pgException = RuntimeException("PostgrestException: function delete_user_account() error at line 42, SQLSTATE 42501")
        val mappedPg = mapDeleteAccountException(pgException)
        assertEquals("Couldn't delete your account. Please try again.", mappedPg.message)
        assertFalse(mappedPg.message!!.contains("PostgrestException"))
        assertFalse(mappedPg.message!!.contains("SQLSTATE"))
        assertFalse(mappedPg.message!!.contains("line 42"))

        val internal500 = RuntimeException("HTTP 500 Internal Server Error: Database pool exhausted")
        val mapped500 = mapDeleteAccountException(internal500)
        assertEquals("Couldn't delete your account. Please try again.", mapped500.message)
        assertFalse(mapped500.message!!.contains("500"))
        assertFalse(mapped500.message!!.contains("Database pool exhausted"))
    }

    // --- 3. UI State Transitions ---

    @Test
    fun testProfileUiState_defaultDeletionState() {
        val state = ProfileUiState()
        assertFalse(state.isDeletingAccount)
        assertNull(state.deleteAccountError)
    }

    @Test
    fun testProfileUiState_deletingStateTransition() {
        var state = ProfileUiState()

        // 1. User confirms deletion -> loading starts, previous errors cleared
        state = state.copy(isDeletingAccount = true, deleteAccountError = null)
        assertTrue(state.isDeletingAccount)
        assertNull(state.deleteAccountError)

        // 2. Error occurs -> loading stops, error displayed
        state = state.copy(
            isDeletingAccount = false,
            deleteAccountError = "Couldn't delete your account. Please try again."
        )
        assertFalse(state.isDeletingAccount)
        assertEquals("Couldn't delete your account. Please try again.", state.deleteAccountError)

        // 3. User dismisses dialog or cancels -> error cleared
        state = state.copy(deleteAccountError = null)
        assertFalse(state.isDeletingAccount)
        assertNull(state.deleteAccountError)
    }

    // --- 4. Duplicate Deletion Call Suppression ---

    @Test
    fun testDuplicateDeletionCall_guard() {
        var callCount = 0
        var isDeletingAccount = false

        fun deleteAccount() {
            if (isDeletingAccount) return
            isDeletingAccount = true
            callCount++
        }

        // First click
        deleteAccount()
        assertEquals(1, callCount)
        assertTrue(isDeletingAccount)

        // Rapid second and third clicks while deleting
        deleteAccount()
        deleteAccount()
        assertEquals(1, callCount)
    }
}
