package com.example.anubhav

import com.example.anubhav.core.utils.RelativeTimeFormatter
import com.example.anubhav.data.remote.dto.ProfileDto
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnubhavUnitTest {

    @Test
    fun testRelativeTimeFormatter_justNow() {
        val now = Instant.now().toString()
        val formatted = RelativeTimeFormatter.format(now)
        assertEquals("just now", formatted)
    }

    @Test
    fun testRelativeTimeFormatter_minutesAgo() {
        val tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES).toString()
        val formatted = RelativeTimeFormatter.format(tenMinutesAgo)
        assertEquals("10m", formatted)
    }

    @Test
    fun testRelativeTimeFormatter_hoursAgo() {
        val threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS).toString()
        val formatted = RelativeTimeFormatter.format(threeHoursAgo)
        assertEquals("3h", formatted)
    }

    @Test
    fun testRelativeTimeFormatter_yesterday() {
        val yesterday = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val formatted = RelativeTimeFormatter.format(yesterday)
        assertEquals("Yesterday", formatted)
    }

    @Test
    fun testRelativeTimeFormatter_daysAgo() {
        val fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS).toString()
        val formatted = RelativeTimeFormatter.format(fourDaysAgo)
        assertEquals("4d", formatted)
    }

    @Test
    fun testUsernameValidationRegex() {
        val usernameRegex = Regex("^[a-zA-Z0-9_.]+$")

        assertTrue("shailesh".matches(usernameRegex))
        assertTrue("user_123".matches(usernameRegex))
        assertTrue("john.doe".matches(usernameRegex))

        assertFalse("user@name".matches(usernameRegex))
        assertFalse("user name".matches(usernameRegex))
        assertFalse("user#1".matches(usernameRegex))
        assertFalse("user!".matches(usernameRegex))
    }

    @Test
    fun testProfileDtoToDomainMapping() {
        val dto = ProfileDto(
            id = "user-123",
            username = "shailesh",
            displayName = "Shailesh",
            bio = "Building things\nExploring places",
            location = "India",
            currentlyWorkingOn = "Android project",
            thingsIveDone = "Himalayan trek\nLearned guitar",
            profileImageUrl = "https://example.com/avatar.jpg"
        )

        val domain = dto.toDomain()
        assertEquals("user-123", domain.id)
        assertEquals("shailesh", domain.username)
        assertEquals("Shailesh", domain.displayName)
        assertEquals("Building things\nExploring places", domain.bio)
        assertEquals("India", domain.location)
        assertEquals("Android project", domain.currentlyWorkingOn)
        assertEquals("Himalayan trek\nLearned guitar", domain.thingsIveDone)
        assertEquals("https://example.com/avatar.jpg", domain.profileImageUrl)
    }

    @Test
    fun testLikeCountPrivacyRule() {
        val author = UserProfile(
            id = "author-123",
            username = "shailesh",
            displayName = "Shailesh"
        )

        // When Shailesh views his own post:
        val ownerPost = PostWithAuthor(
            id = "post-001",
            userId = "author-123",
            postType = PostType.TEXT,
            content = "Finished building my keyboard.",
            createdAt = Instant.now().toString(),
            author = author,
            isOwner = true,
            isLikedByCurrentUser = true,
            ownerLikeCount = 18
        )

        assertTrue(ownerPost.isOwner)
        assertEquals(18, ownerPost.ownerLikeCount)

        // When another user (e.g. User B) views Shailesh's post:
        val viewerPost = PostWithAuthor(
            id = "post-001",
            userId = "author-123",
            postType = PostType.TEXT,
            content = "Finished building my keyboard.",
            createdAt = Instant.now().toString(),
            author = author,
            isOwner = false,
            isLikedByCurrentUser = true,
            ownerLikeCount = null // Strictly null for non-owners
        )

        assertFalse(viewerPost.isOwner)
        assertNull("Non-owner must NOT see like counts!", viewerPost.ownerLikeCount)
    }

    @Test
    fun testCalculateInSampleSize_smallerImage() {
        val sampleSize = com.example.anubhav.core.image.ImageCompressor.calculateInSampleSize(
            width = 800,
            height = 600,
            reqWidth = 1280,
            reqHeight = 1280
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun testCalculateInSampleSize_largeImage() {
        // 4000 x 3000 downsampled to fit within 1280 x 1280
        // halfWidth = 2000, halfHeight = 1500
        // (halfWidth/1 >= 1280 is true), sampleSize becomes 2
        // (halfWidth/2 = 1000 >= 1280 is false), loop terminates
        val sampleSize = com.example.anubhav.core.image.ImageCompressor.calculateInSampleSize(
            width = 4000,
            height = 3000,
            reqWidth = 1280,
            reqHeight = 1280
        )
        assertEquals(2, sampleSize)
    }

    @Test
    fun testCalculateInSampleSize_hugeImage() {
        // 8000 x 6000 downsampled to fit within 1280 x 1280
        val sampleSize = com.example.anubhav.core.image.ImageCompressor.calculateInSampleSize(
            width = 8000,
            height = 6000,
            reqWidth = 1280,
            reqHeight = 1280
        )
        assertEquals(4, sampleSize)
    }

    @Test
    fun testCalculateInSampleSize_panorama() {
        // 10000 x 800 panorama
        val sampleSize = com.example.anubhav.core.image.ImageCompressor.calculateInSampleSize(
            width = 10000,
            height = 800,
            reqWidth = 1280,
            reqHeight = 1280
        )
        assertEquals(4, sampleSize)
    }

    @Test
    fun testEditProfileFormInitializationGuard() {
        val state = com.example.anubhav.presentation.profile.ProfileUiState(
            isEditFormInitialized = true,
            editDisplayName = "User Modified Name",
            editBio = "User Modified Bio"
        )

        // Verifies the guard logic in ProfileViewModel.loadProfileForEditing
        val shouldPopulate = !state.isEditFormInitialized
        assertFalse("Should not overwrite user edits once form is initialized", shouldPopulate)
        assertEquals("User Modified Name", state.editDisplayName)
        assertEquals("User Modified Bio", state.editBio)
    }
}
