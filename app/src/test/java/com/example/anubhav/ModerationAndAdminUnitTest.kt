package com.example.anubhav

import com.example.anubhav.data.remote.dto.AdminDashboardStatsDto
import com.example.anubhav.data.remote.dto.CreateReportDto
import com.example.anubhav.data.remote.dto.ModerationActionDto
import com.example.anubhav.data.remote.dto.PostSoftDeleteDto
import com.example.anubhav.data.remote.dto.PostUpdateDto
import com.example.anubhav.data.remote.dto.PostWithProfileDto
import com.example.anubhav.data.remote.dto.ProfileStatusUpdateDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.data.remote.dto.UpdateReportStatusDto
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminRole
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.ReportReason
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.domain.model.ReportWithDetails
import com.example.anubhav.domain.model.UserProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ModerationAndAdminUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAccountStatusValues() {
        assertEquals("active", AccountStatus.ACTIVE.name.lowercase())
        assertEquals("restricted", AccountStatus.RESTRICTED.name.lowercase())
        assertEquals("suspended", AccountStatus.SUSPENDED.name.lowercase())
        assertEquals("banned", AccountStatus.BANNED.name.lowercase())

        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromString("ACTIVE"))
        assertEquals(AccountStatus.RESTRICTED, AccountStatus.fromString("restricted"))
        assertEquals(AccountStatus.SUSPENDED, AccountStatus.fromString("SUSPENDED"))
        assertEquals(AccountStatus.BANNED, AccountStatus.fromString("banned"))
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromString("unknown_val"))
    }

    @Test
    fun testReportReasonDisplayNames() {
        assertEquals("Spam", ReportReason.SPAM.displayName)
        assertEquals("Harassment", ReportReason.HARASSMENT.displayName)
        assertEquals("Inappropriate content", ReportReason.INAPPROPRIATE.displayName)
        assertEquals("Hate speech", ReportReason.HATE_SPEECH.displayName)
        assertEquals("Violence", ReportReason.VIOLENCE.displayName)
        assertEquals("Other", ReportReason.OTHER.displayName)

        assertEquals(ReportReason.SPAM, ReportReason.fromString("spam"))
        assertEquals(ReportReason.HARASSMENT, ReportReason.fromString("harassment"))
        assertEquals(ReportReason.OTHER, ReportReason.fromString("random_reason"))
    }

    @Test
    fun testReportStatusFromString() {
        assertEquals(ReportStatus.PENDING, ReportStatus.fromString("PENDING"))
        assertEquals(ReportStatus.REVIEWING, ReportStatus.fromString("reviewing"))
        assertEquals(ReportStatus.RESOLVED, ReportStatus.fromString("resolved"))
        assertEquals(ReportStatus.DISMISSED, ReportStatus.fromString("dismissed"))
        assertEquals(ReportStatus.PENDING, ReportStatus.fromString("invalid"))
    }

    @Test
    fun testModerationActionTypeDisplayLabels() {
        assertEquals("Warning", ModerationActionType.WARNING.displayLabel)
        assertEquals("Post Removed", ModerationActionType.POST_REMOVED.displayLabel)
        assertEquals("User Restricted", ModerationActionType.USER_RESTRICTED.displayLabel)
        assertEquals("User Suspended", ModerationActionType.USER_SUSPENDED.displayLabel)
        assertEquals("User Banned", ModerationActionType.USER_BANNED.displayLabel)
        assertEquals("Report Dismissed", ModerationActionType.REPORT_DISMISSED.displayLabel)
        assertEquals("Status Reset", ModerationActionType.STATUS_RESET.displayLabel)

        assertEquals(ModerationActionType.WARNING, ModerationActionType.fromString("warning"))
        assertEquals(ModerationActionType.USER_BANNED, ModerationActionType.fromString("user_banned"))
    }

    @Test
    fun testReportDtoToDomainMapping() {
        val dto = ReportDto(
            id = "rep-101",
            postId = "post-999",
            reporterId = "user-1",
            reportedUserId = "author-2",
            reason = "spam",
            description = "Promotional spam link repeatedly posted",
            status = "pending",
            resolution = null,
            reviewedBy = null,
            reviewedAt = null,
            createdAt = Instant.now().toString()
        )

        val domain = dto.toDomain()
        assertEquals("rep-101", domain.id)
        assertEquals("post-999", domain.postId)
        assertEquals("user-1", domain.reporterId)
        assertEquals("author-2", domain.reportedUserId)
        assertEquals("spam", domain.reason)
        assertEquals("Promotional spam link repeatedly posted", domain.description)
        assertEquals(ReportStatus.PENDING, domain.status)
        assertNull(domain.reviewedBy)
        assertNull(domain.reviewedAt)
        assertNull(domain.resolution)
    }

    @Test
    fun testReportDtoUnknownStatusFallback() {
        val dto = ReportDto(
            id = "rep-102",
            postId = "post-999",
            reporterId = "user-1",
            reportedUserId = "author-2",
            reason = "spam",
            description = "",
            status = "unrecognized_status_code",
            createdAt = Instant.now().toString()
        )

        val domain = dto.toDomain()
        assertEquals(ReportStatus.PENDING, domain.status)
    }

    @Test
    fun testReportWithDetails() {
        val author = UserProfile(
            id = "author-2",
            username = "author_user",
            displayName = "Author User"
        )
        val post = PostWithAuthor(
            id = "post-999",
            userId = "author-2",
            postType = PostType.TEXT,
            content = "My cool post content",
            createdAt = Instant.now().toString(),
            author = author
        )
        val reporter = UserProfile(
            id = "user-1",
            username = "alice",
            displayName = "Alice"
        )

        val details = ReportWithDetails(
            id = "rep-103",
            postId = "post-999",
            reporterId = "user-1",
            reportedUserId = "author-2",
            reason = "harassment",
            description = "Harassing comments",
            status = ReportStatus.RESOLVED,
            reporter = reporter,
            reportedUser = author,
            post = post
        )

        assertEquals("rep-103", details.id)
        assertEquals("harassment", details.reason)
        assertEquals(ReportStatus.RESOLVED, details.status)
        assertNotNull(details.post)
        assertEquals("post-999", details.post?.id)
        assertEquals("My cool post content", details.post?.content)
        assertNotNull(details.reporter)
        assertEquals("alice", details.reporter?.username)
        assertNotNull(details.reportedUser)
        assertEquals("author_user", details.reportedUser?.username)
    }

    @Test
    fun testModerationActionDtoToDomainMapping() {
        val dto = ModerationActionDto(
            id = "act-1",
            adminId = "admin-1",
            action = "user_suspended",
            userId = "user-2",
            postId = null,
            reportId = "rep-1",
            reason = "Severe harassment in posts",
            durationUntil = "2026-09-13T12:00:00Z",
            createdAt = Instant.now().toString()
        )

        val domain = dto.toDomain()
        assertEquals("act-1", domain.id)
        assertEquals("admin-1", domain.adminId)
        assertEquals(ModerationActionType.USER_SUSPENDED, domain.action)
        assertEquals("Severe harassment in posts", domain.reason)
        assertEquals("2026-09-13T12:00:00Z", domain.durationUntil)
    }

    @Test
    fun testAdminDashboardStatsDtoMapping() {
        val dto = AdminDashboardStatsDto(
            totalUsers = 530,
            totalPosts = 1250,
            pendingReports = 14,
            resolvedReports = 42,
            bannedUsers = 1,
            restrictedUsers = 4,
            suspendedUsers = 2,
            postsRemoved = 15
        )

        val domain = dto.toDomain()
        assertEquals(530L, domain.totalUsers)
        assertEquals(1250L, domain.totalPosts)
        assertEquals(14L, domain.pendingReports)
        assertEquals(42L, domain.resolvedReports)
        assertEquals(1L, domain.bannedUsers)
        assertEquals(4L, domain.restrictedUsers)
        assertEquals(2L, domain.suspendedUsers)
        assertEquals(15L, domain.postsRemoved)
    }

    @Test
    fun testDtoJsonSerialization() {
        val postUpdate = PostUpdateDto(
            content = "Updated post content",
            postType = "TEXT",
            imagePath = null
        )
        val postUpdateJson = json.encodeToString(postUpdate)
        assertTrue(postUpdateJson.contains("Updated post content"))
        assertTrue(postUpdateJson.contains("post_type"))

        val postSoftDelete = PostSoftDeleteDto(
            isRemoved = true,
            removedAt = "2026-09-06T12:00:00Z",
            removedBy = "admin-uuid-1",
            removalReason = "Violates community guidelines"
        )
        val postSoftDeleteJson = json.encodeToString(postSoftDelete)
        assertTrue(postSoftDeleteJson.contains("is_removed"))
        assertTrue(postSoftDeleteJson.contains("admin-uuid-1"))
        assertTrue(postSoftDeleteJson.contains("Violates community guidelines"))

        val createReport = CreateReportDto(
            postId = "post-456",
            reporterId = "reporter-123",
            reportedUserId = "target-789",
            reason = "hate_speech",
            description = "Offensive slurs"
        )
        val createReportJson = json.encodeToString(createReport)
        assertTrue(createReportJson.contains("reporter_id"))
        assertTrue(createReportJson.contains("reported_user_id"))
        assertTrue(createReportJson.contains("hate_speech"))
        assertTrue(createReportJson.contains("Offensive slurs"))

        val updateReportStatus = UpdateReportStatusDto(
            status = "RESOLVED",
            resolution = "Post removed by admin",
            reviewedBy = "admin-1",
            reviewedAt = "2026-09-06T12:30:00Z"
        )
        val updateReportStatusJson = json.encodeToString(updateReportStatus)
        assertTrue(updateReportStatusJson.contains("RESOLVED"))
        assertTrue(updateReportStatusJson.contains("Post removed by admin"))

        val profileStatusUpdate = ProfileStatusUpdateDto(
            accountStatus = "SUSPENDED",
            suspendedUntil = "2026-09-13T12:00:00Z",
            restrictedUntil = null,
            statusReason = "Multiple violations"
        )
        val profileStatusUpdateJson = json.encodeToString(profileStatusUpdate)
        assertTrue(profileStatusUpdateJson.contains("SUSPENDED"))
        assertTrue(profileStatusUpdateJson.contains("suspended_until"))
        assertTrue(profileStatusUpdateJson.contains("Multiple violations"))
    }

    @Test
    fun testUserPermissionsRule() {
        val currentUserId = "user-123"
        val postOwnerId = "user-123"
        val otherUserId = "user-456"

        val isOwner = currentUserId == postOwnerId
        assertTrue("Owner should have permission to edit and delete", isOwner)

        val isNonOwner = otherUserId == postOwnerId
        assertFalse("Non-owner should not have permission to edit or delete", isNonOwner)
    }

    @Test
    fun testUserProfileAccountStatusChecks() {
        val activeUser = UserProfile(
            id = "u1",
            username = "active_user",
            displayName = "Active User",
            accountStatus = AccountStatus.ACTIVE
        )
        assertFalse(activeUser.isTemporarilyRestricted())
        assertFalse(activeUser.isTemporarilySuspended())
        assertFalse(activeUser.isPermanentlyBanned())

        val bannedUser = UserProfile(
            id = "u2",
            username = "banned_user",
            displayName = "Banned User",
            accountStatus = AccountStatus.BANNED
        )
        assertTrue(bannedUser.isPermanentlyBanned())

        val futureTime = Instant.now().plus(7, ChronoUnit.DAYS).toString()
        val pastTime = Instant.now().minus(1, ChronoUnit.DAYS).toString()

        val activeRestrictedUser = UserProfile(
            id = "u3",
            username = "restricted_user",
            displayName = "Restricted User",
            accountStatus = AccountStatus.RESTRICTED,
            restrictedUntil = futureTime
        )
        assertTrue(activeRestrictedUser.isTemporarilyRestricted())

        val expiredRestrictedUser = UserProfile(
            id = "u4",
            username = "expired_restricted_user",
            displayName = "Expired Restricted User",
            accountStatus = AccountStatus.RESTRICTED,
            restrictedUntil = pastTime
        )
        assertFalse(expiredRestrictedUser.isTemporarilyRestricted())

        val activeSuspendedUser = UserProfile(
            id = "u5",
            username = "suspended_user",
            displayName = "Suspended User",
            accountStatus = AccountStatus.SUSPENDED,
            suspendedUntil = futureTime
        )
        assertTrue(activeSuspendedUser.isTemporarilySuspended())

        val expiredSuspendedUser = UserProfile(
            id = "u6",
            username = "expired_suspended_user",
            displayName = "Expired Suspended User",
            accountStatus = AccountStatus.SUSPENDED,
            suspendedUntil = pastTime
        )
        assertFalse(expiredSuspendedUser.isTemporarilySuspended())
    }

    @Test
    fun testAdminRoleEnum() {
        assertEquals(AdminRole.SUPER_ADMIN, AdminRole.fromString("SUPER_ADMIN"))
        assertEquals(AdminRole.MODERATOR, AdminRole.fromString("MODERATOR"))
        assertEquals(AdminRole.MODERATOR, AdminRole.fromString("unknown"))
    }

    @Test
    fun testPostWithProfileDto_nullIsRemoved() {
        val jsonString = """{"id":"p1","user_id":"u1","post_type":"TEXT","content":"test","created_at":"2026-09-01","is_removed":null}"""
        val dto = json.decodeFromString<PostWithProfileDto>(jsonString)
        assertEquals("p1", dto.id)
        assertNull(dto.isRemoved)
        assertFalse(dto.isRemoved == true)
    }

    @Test
    fun testPostWithProfileDto_missingIsRemoved() {
        val jsonString = """{"id":"p2","user_id":"u1","post_type":"TEXT","content":"legacy post","created_at":"2026-09-01"}"""
        val dto = json.decodeFromString<PostWithProfileDto>(jsonString)
        assertEquals("p2", dto.id)
        assertEquals(false, dto.isRemoved)
        assertFalse(dto.isRemoved == true)
    }

    @Test
    fun testPostWithProfileDto_falseIsRemoved() {
        val jsonString = """{"id":"p3","user_id":"u1","post_type":"TEXT","content":"active post","created_at":"2026-09-01","is_removed":false}"""
        val dto = json.decodeFromString<PostWithProfileDto>(jsonString)
        assertEquals("p3", dto.id)
        assertEquals(false, dto.isRemoved)
        assertFalse(dto.isRemoved == true)
    }

    @Test
    fun testPostWithProfileDto_trueIsRemoved() {
        val jsonString = """{"id":"p4","user_id":"u1","post_type":"TEXT","content":"removed post","created_at":"2026-09-01","is_removed":true}"""
        val dto = json.decodeFromString<PostWithProfileDto>(jsonString)
        assertEquals("p4", dto.id)
        assertEquals(true, dto.isRemoved)
        assertTrue(dto.isRemoved == true)
    }

    @Test
    fun testLegacyPostVisibilityFilterRule() {
        val legacyPostNull = PostWithProfileDto(id = "1", userId = "u1", content = "Legacy 1", isRemoved = null)
        val legacyPostDefault = PostWithProfileDto(id = "2", userId = "u1", content = "Legacy 2")
        val normalPostActive = PostWithProfileDto(id = "3", userId = "u1", content = "Active 3", isRemoved = false)
        val moderatedPostRemoved = PostWithProfileDto(id = "4", userId = "u1", content = "Removed 4", isRemoved = true)

        val allPosts = listOf(legacyPostNull, legacyPostDefault, normalPostActive, moderatedPostRemoved)

        // The visibility filter rule: isRemoved != true
        val visiblePosts = allPosts.filter { it.isRemoved != true }

        assertEquals(3, visiblePosts.size)
        assertTrue(visiblePosts.any { it.id == "1" }) // legacy with null is visible
        assertTrue(visiblePosts.any { it.id == "2" }) // legacy default is visible
        assertTrue(visiblePosts.any { it.id == "3" }) // normal active is visible
        assertFalse(visiblePosts.any { it.id == "4" }) // removed is hidden
    }

    @Test
    fun testCoalesceLogicSimulation() {
        fun coalesceIsRemoved(isRemoved: Boolean?): Boolean = isRemoved ?: false

        assertFalse("null should coalesce to false (visible)", coalesceIsRemoved(null))
        assertFalse("false should stay false (visible)", coalesceIsRemoved(false))
        assertTrue("true should stay true (hidden)", coalesceIsRemoved(true))
    }
}
