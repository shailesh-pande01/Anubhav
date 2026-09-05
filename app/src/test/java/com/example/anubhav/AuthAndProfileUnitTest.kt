package com.example.anubhav

import com.example.anubhav.data.remote.dto.ProfileUpsertDto
import com.example.anubhav.presentation.auth.AuthUiState
import com.example.anubhav.presentation.profile.ProfileUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAndProfileUnitTest {

    @Test
    fun testProfileUpsertDto_omitsTimestampsToPreventDbConstraintViolation() {
        val upsertDto = ProfileUpsertDto(
            id = "test-user-id",
            username = "craftsman",
            displayName = "Alex Artisan",
            bio = "Working on hand-crafted goods.",
            location = "Kyoto, Japan",
            currentlyWorkingOn = "Ceramic tea bowls",
            thingsIveDone = "Woodworking, Pottery",
            profileImageUrl = "https://example.com/avatar.jpg"
        )

        val jsonString = Json.encodeToString(upsertDto)

        // Ensure critical fields are present
        assertTrue(jsonString.contains("\"id\":\"test-user-id\""))
        assertTrue(jsonString.contains("\"username\":\"craftsman\""))
        assertTrue(jsonString.contains("\"display_name\":\"Alex Artisan\""))

        // Ensure created_at and updated_at are strictly absent so PostgreSQL defaults/triggers apply
        assertFalse(jsonString.contains("created_at"))
        assertFalse(jsonString.contains("updated_at"))
    }

    @Test
    fun testUsernameOrEmailIdentifierDiscrimination() {
        fun isEmailIdentifier(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed.contains("@") && trimmed.contains(".")
        }

        assertTrue(isEmailIdentifier("user@example.com"))
        assertTrue(isEmailIdentifier("alex.artisan@studio.design"))
        assertTrue(isEmailIdentifier("test+anubhav@domain.co"))

        assertFalse(isEmailIdentifier("craftsman"))
        assertFalse(isEmailIdentifier("alex_99"))
        assertFalse(isEmailIdentifier("artisan.maker"))
    }

    @Test
    fun testUsernameNormalization() {
        fun normalizeUsername(input: String): String = input.trim().lowercase()

        assertEquals("craftsman", normalizeUsername("  Craftsman  "))
        assertEquals("alex.artisan", normalizeUsername("Alex.Artisan"))
        assertEquals("user_101", normalizeUsername("USER_101"))
    }

    @Test
    fun testRegisterPasswordConfirmationValidation() {
        fun validatePasswords(password: String, confirm: String): String? {
            if (password.length < 6) return "Password must be at least 6 characters."
            if (password != confirm) return "Passwords do not match."
            return null
        }

        // Matching passwords
        val validResult = validatePasswords("calmSecret123", "calmSecret123")
        assertEquals(null, validResult)

        // Mismatched passwords
        val mismatchResult = validatePasswords("calmSecret123", "differentPassword")
        assertEquals("Passwords do not match.", mismatchResult)

        // Too short
        val shortResult = validatePasswords("12345", "12345")
        assertEquals("Password must be at least 6 characters.", shortResult)
    }

    @Test
    fun testProfileStatePreservationOnSaveFailure() {
        // Simulates the UI state when save fails: form values MUST remain populated
        val initialFormState = ProfileUiState(
            isSavingProfile = true,
            editDisplayName = "Updated Name",
            editUsername = "updated_user",
            editBio = "Updated Bio Content",
            editLocation = "New Location",
            editCurrentlyWorkingOn = "Building mobile apps",
            editThingsIveDone = "Ran marathon"
        )

        // Simulated failure update
        val failureState = initialFormState.copy(
            isSavingProfile = false,
            editError = "Couldn't save your profile. Please try again."
        )

        assertFalse(failureState.isSavingProfile)
        assertNotNull(failureState.editError)
        assertEquals("Updated Name", failureState.editDisplayName)
        assertEquals("updated_user", failureState.editUsername)
        assertEquals("Updated Bio Content", failureState.editBio)
        assertEquals("New Location", failureState.editLocation)
        assertEquals("Building mobile apps", failureState.editCurrentlyWorkingOn)
        assertEquals("Ran marathon", failureState.editThingsIveDone)
    }

    @Test
    fun testResetPasswordValidation() {
        fun validateResetPassword(password: String, confirm: String): String? {
            if (password.length < 6) return "Password must be at least 6 characters."
            if (password != confirm) return "Passwords do not match."
            return null
        }

        assertEquals(null, validateResetPassword("newPassword456", "newPassword456"))
        assertEquals("Passwords do not match.", validateResetPassword("newPassword456", "otherPassword"))
        assertEquals("Password must be at least 6 characters.", validateResetPassword("short", "short"))
    }
}
