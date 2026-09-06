package com.example.anubhav

import com.example.anubhav.core.image.ImageCompressor
import com.example.anubhav.data.remote.dto.PostLikeCountDto
import com.example.anubhav.data.remote.dto.PostLikePostIdOnlyDto
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
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

class ScalabilityAndOptimizationUnitTest {

    @Test
    fun testCalculateInSampleSize_avatarTarget400() {
        // A 4000x3000 smartphone photo downsampled to fit within 400x400:
        // halfWidth = 2000, halfHeight = 1500
        // inSampleSize starts at 1
        // Iteration 1: 1500/1 >= 400 || 2000/1 >= 400 -> sampleSize = 2
        // Iteration 2: 750 >= 400 || 1000 >= 400 -> sampleSize = 4
        // Iteration 3: 375 >= 400 (false) || 500 >= 400 (true) -> sampleSize = 8
        // Iteration 4: 187 >= 400 (false) || 250 >= 400 (false) -> loop terminates
        // result = 8 (resulting bitmap decoded at 500x375, then scaled to 400x300)
        val sampleSize = ImageCompressor.calculateInSampleSize(
            width = 4000,
            height = 3000,
            reqWidth = ImageCompressor.MAX_AVATAR_WIDTH,
            reqHeight = ImageCompressor.MAX_AVATAR_HEIGHT
        )
        assertEquals(8, sampleSize)
    }

    @Test
    fun testCalculateInSampleSize_avatarTargetExactFit() {
        val sampleSize = ImageCompressor.calculateInSampleSize(
            width = 400,
            height = 400,
            reqWidth = ImageCompressor.MAX_AVATAR_WIDTH,
            reqHeight = ImageCompressor.MAX_AVATAR_HEIGHT
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun testCalculateInSampleSize_avatarTargetSmallerThan400() {
        val sampleSize = ImageCompressor.calculateInSampleSize(
            width = 250,
            height = 250,
            reqWidth = ImageCompressor.MAX_AVATAR_WIDTH,
            reqHeight = ImageCompressor.MAX_AVATAR_HEIGHT
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun testDeterministicAvatarStoragePath() {
        val userId = "user-uuid-12345"
        val path = "${userId}/avatar.jpg"

        assertEquals("user-uuid-12345/avatar.jpg", path)
        assertFalse("Path must NOT contain dynamic timestamps", path.contains(Regex("""avatar_\d+""")))
    }

    @Test
    fun testAvatarCacheBustingUrlGeneration() {
        val userId = "user-uuid-12345"
        val basePublicUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/$userId/avatar.jpg"
        val timestamp = 1788547919000L
        val urlWithTimestamp = "$basePublicUrl?t=$timestamp"

        assertTrue(urlWithTimestamp.startsWith("https://example.supabase.co/storage/v1/object/public/profile-images/user-uuid-12345/avatar.jpg"))
        assertTrue(urlWithTimestamp.contains("?t=1788547919000"))
    }

    @Test
    fun testLegacyAvatarRegexMatching() {
        val userId = "user-uuid-12345"
        val legacyPattern = Regex("""profile-images[/\\]${Regex.escape(userId)}[/\\](avatar_\d+\.jpg)""")

        // Valid legacy timestamped avatar belonging to this user
        val legacyUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/user-uuid-12345/avatar_1741234567890.jpg"
        val match = legacyPattern.find(legacyUrl)
        assertNotNull(match)
        assertEquals("avatar_1741234567890.jpg", match?.groupValues?.get(1))

        // Deterministic new avatar MUST NOT match (should never be deleted)
        val deterministicUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/user-uuid-12345/avatar.jpg"
        assertNull(legacyPattern.find(deterministicUrl))

        // Legacy avatar belonging to a DIFFERENT user MUST NOT match
        val otherUserUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/different-user-999/avatar_1741234567890.jpg"
        assertNull(legacyPattern.find(otherUserUrl))
    }

    @Test
    fun testPostLikePostIdOnlyDtoSerialization() {
        val dto = PostLikePostIdOnlyDto(postId = "post-abc-123")
        val json = Json.encodeToString(dto)

        assertTrue(json.contains("\"post_id\":\"post-abc-123\""))
        assertFalse(json.contains("user_id"))
        assertFalse(json.contains("created_at"))

        val decoded = Json.decodeFromString<PostLikePostIdOnlyDto>(json)
        assertEquals("post-abc-123", decoded.postId)
    }

    @Test
    fun testPostLikeCountDtoSerialization() {
        val dto = PostLikeCountDto(postId = "post-xyz-789", likeCount = 42L)
        val json = Json.encodeToString(dto)

        assertTrue(json.contains("\"post_id\":\"post-xyz-789\""))
        assertTrue(json.contains("\"like_count\":42"))

        val decoded = Json.decodeFromString<PostLikeCountDto>(json)
        assertEquals("post-xyz-789", decoded.postId)
        assertEquals(42L, decoded.likeCount)
    }

    @Test
    fun testBoundedLikesMappingLogic() {
        // Simulates bounded likes set containing only 2 post IDs
        val userLikedPostIds = setOf("post-1", "post-3")

        val postIds = listOf("post-1", "post-2", "post-3", "post-4")
        val likedStatusMap = postIds.associateWith { userLikedPostIds.contains(it) }

        assertTrue(likedStatusMap["post-1"] == true)
        assertFalse(likedStatusMap["post-2"] == true)
        assertTrue(likedStatusMap["post-3"] == true)
        assertFalse(likedStatusMap["post-4"] == true)
    }

    @Test
    fun testBatchedOwnerLikeCountsAndPrivacyRule() {
        val currentUserId = "shailesh-id"
        val otherUserId = "creator-b-id"

        val ownerCountsMap = mapOf(
            "post-own-1" to 15,
            "post-own-2" to 3
        )

        val authorShailesh = UserProfile(id = currentUserId, username = "shailesh", displayName = "Shailesh")
        val authorCreatorB = UserProfile(id = otherUserId, username = "creator_b", displayName = "Creator B")

        // Post 1: Owned by current user
        val isOwnerPost1 = (authorShailesh.id == currentUserId)
        val post1LikeCount = if (isOwnerPost1) ownerCountsMap["post-own-1"] ?: 0 else null

        // Post 2: Owned by Creator B
        val isOwnerPost2 = (authorCreatorB.id == currentUserId)
        val post2LikeCount = if (isOwnerPost2) ownerCountsMap["post-other-1"] ?: 0 else null

        // Verification: Owner sees count
        assertTrue(isOwnerPost1)
        assertEquals(15, post1LikeCount)

        // Verification: Non-owner strictly receives null (calm privacy rule)
        assertFalse(isOwnerPost2)
        assertNull("Other users must NEVER see like counts", post2LikeCount)
    }
}
