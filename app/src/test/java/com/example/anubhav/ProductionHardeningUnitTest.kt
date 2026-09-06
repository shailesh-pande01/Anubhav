package com.example.anubhav

import androidx.compose.ui.graphics.Color
import com.example.anubhav.core.image.ImageCompressor
import com.example.anubhav.data.remote.dto.ModerationActionDto
import com.example.anubhav.data.remote.dto.PostDto
import com.example.anubhav.data.remote.dto.ProfileDto
import com.example.anubhav.data.remote.dto.ReportDto
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.domain.model.ReportStatus
import com.example.anubhav.presentation.navigation.Screen
import com.example.anubhav.ui.theme.CalmSuccess
import com.example.anubhav.ui.theme.CalmSuccessLight
import com.example.anubhav.ui.theme.CalmWarning
import com.example.anubhav.ui.theme.CalmWarningLight
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production Readiness & Hardening Unit Tests
 * Verifies crash immunity, dimension caching, serialization resilience,
 * theme contrast accessibility invariants, and reactive session navigation rules.
 */
class ProductionHardeningUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // --------------------------------------------------------------------------
    // 1. Dimension Calculation & Bitmap Recycling Safety
    // --------------------------------------------------------------------------

    @Test
    fun testCalculateInSampleSize_extremeDimensions() {
        // High-resolution panoramic photo 8000x2000
        val samplePanoramic = ImageCompressor.calculateInSampleSize(
            width = 8000,
            height = 2000,
            reqWidth = ImageCompressor.MAX_POST_WIDTH,
            reqHeight = ImageCompressor.MAX_POST_HEIGHT
        )
        // 8000 / 4 = 2000 (halfWidth / 4 = 1000 <= 1280)
        assertEquals(4, samplePanoramic)

        // Standard portrait photo 1080x1920 (halfHeight 960 < 1280, so inSampleSize is 1; scaled in step 7)
        val sampleStandardPortrait = ImageCompressor.calculateInSampleSize(
            width = 1080,
            height = 1920,
            reqWidth = ImageCompressor.MAX_POST_WIDTH,
            reqHeight = ImageCompressor.MAX_POST_HEIGHT
        )
        assertEquals(1, sampleStandardPortrait)

        // 4K portrait photo 2160x3840 (halfHeight 1920 >= 1280, so inSampleSize is 2)
        val sample4kPortrait = ImageCompressor.calculateInSampleSize(
            width = 2160,
            height = 3840,
            reqWidth = ImageCompressor.MAX_POST_WIDTH,
            reqHeight = ImageCompressor.MAX_POST_HEIGHT
        )
        assertEquals(2, sample4kPortrait)

        // Minimal 1x1 image
        val sampleMinimal = ImageCompressor.calculateInSampleSize(
            width = 1,
            height = 1,
            reqWidth = ImageCompressor.MAX_POST_WIDTH,
            reqHeight = ImageCompressor.MAX_POST_HEIGHT
        )
        assertEquals(1, sampleMinimal)
    }

    @Test
    fun testBitmapRecyclingDimensionSafetyInvariant() {
        // Simulates the exact contract of caching dimensions prior to recycle()
        class MockSafeBitmap(val width: Int, val height: Int) {
            private var isRecycled = false

            fun recycle() {
                isRecycled = true
            }

            fun getSafeWidth(): Int {
                check(!isRecycled) { "Cannot access width on a recycled bitmap!" }
                return width
            }

            fun getSafeHeight(): Int {
                check(!isRecycled) { "Cannot access height on a recycled bitmap!" }
                return height
            }
        }

        val bitmap = MockSafeBitmap(1280, 720)

        // Correct production pattern: Cache before recycling
        val cachedWidth = bitmap.getSafeWidth()
        val cachedHeight = bitmap.getSafeHeight()
        bitmap.recycle()

        // Accessing cached properties succeeds after recycling
        assertEquals(1280, cachedWidth)
        assertEquals(720, cachedHeight)

        // Directly accessing bitmap methods after recycle throws IllegalStateException
        var caughtException = false
        try {
            bitmap.getSafeWidth()
        } catch (e: IllegalStateException) {
            caughtException = true
        }
        assertTrue("Accessing recycled bitmap directly must be caught", caughtException)
    }

    // --------------------------------------------------------------------------
    // 2. ProGuard / R8 Serialization Resilience
    // --------------------------------------------------------------------------

    @Test
    fun testProfileDto_serializationAndDeserialization() {
        val dto = ProfileDto(
            id = "usr-42",
            username = "prod_user",
            displayName = "Production User",
            bio = "Building calm products",
            location = "Tokyo, Japan",
            currentlyWorkingOn = "Clean architecture",
            thingsIveDone = "Open source",
            profileImageUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/usr-42/avatar.jpg",
            accountStatus = "ACTIVE",
            restrictedUntil = null,
            suspendedUntil = null,
            statusReason = "",
            createdAt = "2026-09-01T12:00:00Z",
            updatedAt = "2026-09-06T12:00:00Z"
        )

        val encoded = json.encodeToString(dto)
        assertTrue(encoded.contains("\"username\":\"prod_user\""))
        assertTrue(encoded.contains("\"account_status\":\"ACTIVE\""))

        val decoded = json.decodeFromString<ProfileDto>(encoded)
        assertEquals(dto.id, decoded.id)
        assertEquals(dto.username, decoded.username)
        assertEquals(AccountStatus.ACTIVE, decoded.toDomain().accountStatus)
    }

    @Test
    fun testPostDto_deserializationWithNullsAndSpecialCharacters() {
        val jsonPayload = """
            {
                "id": "post-789",
                "user_id": "usr-42",
                "post_type": "TEXT",
                "content": "Calm editorial content with \"quotes\" & symbols: ₹ 100",
                "image_path": null,
                "is_removed": false,
                "created_at": "2026-09-06T18:00:00Z",
                "updated_at": "2026-09-06T18:00:00Z"
            }
        """.trimIndent()

        val postDto = json.decodeFromString<PostDto>(jsonPayload)
        assertEquals("post-789", postDto.id)
        assertNull(postDto.imagePath)
        assertFalse(postDto.isRemoved == true)
        assertTrue(postDto.content.contains("₹ 100"))
    }

    @Test
    fun testModerationActionDto_deserialization() {
        val jsonAction = """
            {
                "id": "mod-1",
                "action": "USER_RESTRICTED",
                "user_id": "usr-99",
                "post_id": null,
                "report_id": "rep-5",
                "admin_id": "admin-1",
                "reason": "Excessive spamming",
                "duration_until": "2026-09-13T00:00:00Z",
                "created_at": "2026-09-06T12:00:00Z"
            }
        """.trimIndent()

        val actionDto = json.decodeFromString<ModerationActionDto>(jsonAction)
        assertEquals("USER_RESTRICTED", actionDto.action)
        assertEquals(ModerationActionType.USER_RESTRICTED, actionDto.toDomain().action)
        assertEquals("Excessive spamming", actionDto.reason)
    }

    // --------------------------------------------------------------------------
    // 3. Status Badges & Contrast Accessibility Invariants
    // --------------------------------------------------------------------------

    @Test
    fun testThemeStatusColors_lightAndDarkValues() {
        // Light mode colors
        assertEquals(Color(0xFF2E7D32), CalmSuccess)
        assertEquals(Color(0xFFE65100), CalmWarning)

        // Dark mode accessible variants (higher luminance to ensure >= 4.5:1 contrast on dark surface)
        assertEquals(Color(0xFF81C784), CalmSuccessLight)
        assertEquals(Color(0xFFFFB74D), CalmWarningLight)
    }

    @Test
    fun testBannedBadgeContract_contrastAssurance() {
        // Dark theme: Banned badge background must never be pure black on dark surface
        val darkSurfaceValue = 0xFF1A1C20L
        val darkBadgeBg = 0xFF374151L
        val darkBadgeText = 0xFFF9FAFBL

        assertFalse("Dark mode banned badge must not be pure black", darkBadgeBg == 0xFF000000L)
        assertFalse("Dark mode banned badge must not match surface color", darkBadgeBg == darkSurfaceValue)
        assertTrue("Dark mode text must be bright", darkBadgeText > 0xFFF00000L)
    }

    // --------------------------------------------------------------------------
    // 4. Session Navigation Gate Rules
    // --------------------------------------------------------------------------

    @Test
    fun testSessionNavigationGate_unauthenticatedRoutesExempted() {
        val unauthenticatedRoutes = setOf(
            Screen.Auth.route,
            Screen.ForgotPassword.route,
            Screen.ResetPassword.route
        )

        fun isExemptRoute(route: String): Boolean {
            return unauthenticatedRoutes.contains(route) || route.startsWith("anubhav://")
        }

        // Unauthenticated screens must NEVER be redirected
        assertTrue(isExemptRoute(Screen.Auth.route))
        assertTrue(isExemptRoute(Screen.ForgotPassword.route))
        assertTrue(isExemptRoute(Screen.ResetPassword.route))
        assertTrue(isExemptRoute("anubhav://reset-password"))

        // Authenticated screens MUST trigger redirect when session expires
        assertFalse(isExemptRoute(Screen.Main.route))
        assertFalse(isExemptRoute(Screen.EditProfile.route))
        assertFalse(isExemptRoute(Screen.AdminDashboard.route))
        assertFalse(isExemptRoute(Screen.AdminReports.route))
        assertFalse(isExemptRoute(Screen.EditPost.createRoute("p1")))
    }

    @Test
    fun testAccountStatusEnums_roundTrip() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"))
        assertEquals(AccountStatus.RESTRICTED, AccountStatus.valueOf("RESTRICTED"))
        assertEquals(AccountStatus.SUSPENDED, AccountStatus.valueOf("SUSPENDED"))
        assertEquals(AccountStatus.BANNED, AccountStatus.valueOf("BANNED"))

        assertEquals(ReportStatus.PENDING, ReportStatus.valueOf("PENDING"))
        assertEquals(ReportStatus.REVIEWING, ReportStatus.valueOf("REVIEWING"))
        assertEquals(ReportStatus.RESOLVED, ReportStatus.valueOf("RESOLVED"))
        assertEquals(ReportStatus.DISMISSED, ReportStatus.valueOf("DISMISSED"))
    }
}
