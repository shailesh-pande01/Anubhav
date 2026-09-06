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

    @Test
    fun testParseEmailFromRpcData() {
        // Quoted JSON scalar from PostgREST
        assertEquals("spande3518@gmail.com", com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("\"spande3518@gmail.com\""))
        assertEquals("user@example.com", com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("  \"user@example.com\"  "))

        // Unquoted scalar string
        assertEquals("spande3518@gmail.com", com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("spande3518@gmail.com"))

        // Non-existent user returning JSON null or empty
        assertEquals(null, com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("null"))
        assertEquals(null, com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("  null  "))
        assertEquals(null, com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData("\"\""))
        assertEquals(null, com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData(""))
        assertEquals(null, com.example.anubhav.data.repository.AuthRepository.parseEmailFromRpcData(null))
    }

    @Test
    fun testAuthViewModelIsEmailIdentifier() {
        assertTrue(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("user@example.com"))
        assertTrue(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("  shailesh@gmail.com  "))
        assertTrue(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("alex.artisan@studio.design"))

        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("shailesh"))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("  SHAILESH  "))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("alex_99"))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("artisan.maker"))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("user@"))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("@domain.com"))
        assertFalse(com.example.anubhav.presentation.auth.AuthViewModel.isEmailIdentifier("user@domain"))
    }

    @Test
    fun testMapAuthException_networkErrors() {
        val ioException = java.io.IOException("Failed to connect to host")
        val mapped = com.example.anubhav.data.repository.AuthRepository.mapAuthException(ioException, isUsernameLogin = true)
        assertEquals("Couldn't connect. Please check your internet connection and try again.", mapped.message)

        val timeoutException = Exception("connect timed out")
        val mappedTimeout = com.example.anubhav.data.repository.AuthRepository.mapAuthException(timeoutException, isUsernameLogin = false)
        assertEquals("Couldn't connect. Please check your internet connection and try again.", mappedTimeout.message)
    }

    @Test
    fun testMapAuthException_invalidCredentials() {
        val credException = Exception("invalid login credentials")
        // Username login should say "Incorrect password. Please try again."
        val usernameMapped = com.example.anubhav.data.repository.AuthRepository.mapAuthException(credException, isUsernameLogin = true)
        assertEquals("Incorrect password. Please try again.", usernameMapped.message)

        // Email login should say "Invalid email or password. Please try again."
        val emailMapped = com.example.anubhav.data.repository.AuthRepository.mapAuthException(credException, isUsernameLogin = false)
        assertEquals("Invalid email or password. Please try again.", emailMapped.message)
    }

    @Test
    fun testMapAuthException_unexpectedErrorSanitization() {
        val rawException = Exception("PGRST301: JWT expired or malformed internal token details")
        val mapped = com.example.anubhav.data.repository.AuthRepository.mapAuthException(rawException, isUsernameLogin = true)
        assertEquals("Something went wrong. Please try again.", mapped.message)
    }

    @Test
    fun testParseUriParameters_validFragment() {
        val fragment = "access_token=fake_access_token_jwt&expires_in=3600&refresh_token=fake_refresh_token&token_type=bearer&type=recovery"
        val params = com.example.anubhav.data.repository.AuthRepository.parseUriParameters(fragment)

        assertEquals("fake_access_token_jwt", params["access_token"])
        assertEquals("fake_refresh_token", params["refresh_token"])
        assertEquals("3600", params["expires_in"])
        assertEquals("bearer", params["token_type"])
        assertEquals("recovery", params["type"])
    }

    @Test
    fun testParseUriParameters_errorFragmentWithUrlEncoding() {
        val errorFragment = "error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"
        val params = com.example.anubhav.data.repository.AuthRepository.parseUriParameters(errorFragment)

        assertEquals("access_denied", params["error"])
        assertEquals("otp_expired", params["error_code"])
        assertEquals("Email link is invalid or has expired", params["error_description"])
    }

    @Test
    fun testParseUriParameters_emptyAndNull() {
        assertTrue(com.example.anubhav.data.repository.AuthRepository.parseUriParameters(null).isEmpty())
        assertTrue(com.example.anubhav.data.repository.AuthRepository.parseUriParameters("").isEmpty())
        assertTrue(com.example.anubhav.data.repository.AuthRepository.parseUriParameters("   ").isEmpty())
    }

    @Test
    fun testPasswordValidation_strictRules() {
        fun validateNewPassword(newPass: String, confirmPass: String): String? {
            if (newPass.isBlank()) return "Password cannot be empty."
            if (confirmPass.isBlank()) return "Please confirm your new password."
            if (newPass.length < 6) return "Password must be at least 6 characters."
            if (newPass != confirmPass) return "Passwords do not match."
            return null
        }

        // Empty password
        assertEquals("Password cannot be empty.", validateNewPassword("", "123456"))
        assertEquals("Password cannot be empty.", validateNewPassword("   ", "123456"))

        // Empty confirm password
        assertEquals("Please confirm your new password.", validateNewPassword("validPass123", ""))
        assertEquals("Please confirm your new password.", validateNewPassword("validPass123", "   "))

        // Too short
        assertEquals("Password must be at least 6 characters.", validateNewPassword("abc", "abc"))
        assertEquals("Password must be at least 6 characters.", validateNewPassword("12345", "12345"))

        // Mismatched
        assertEquals("Passwords do not match.", validateNewPassword("newSecretPassword", "differentSecretPassword"))

        // Valid
        assertEquals(null, validateNewPassword("newSecretPassword", "newSecretPassword"))
    }

    @Test
    fun testMapAuthException_recoveryErrors() {
        val expiredOtpEx = Exception("otp_expired: Email link is invalid or has expired")
        val mappedOtp = com.example.anubhav.data.repository.AuthRepository.mapAuthException(expiredOtpEx)
        assertEquals("This reset link has expired. Please request a new one.", mappedOtp.message)

        val expiredTokenEx = Exception("token has expired")
        val mappedToken = com.example.anubhav.data.repository.AuthRepository.mapAuthException(expiredTokenEx)
        assertEquals("This reset link has expired. Please request a new one.", mappedToken.message)

        val badJwtEx = Exception("bad_jwt: token is invalid")
        val mappedJwt = com.example.anubhav.data.repository.AuthRepository.mapAuthException(badJwtEx)
        assertEquals("This reset link is no longer valid. Please request a new one.", mappedJwt.message)

        val weakPassEx = Exception("weak_password: Password should be at least 6 characters")
        val mappedWeak = com.example.anubhav.data.repository.AuthRepository.mapAuthException(weakPassEx)
        assertEquals("Password must be at least 6 characters.", mappedWeak.message)
    }

    @Test
    fun testRecoveryStatusStateHierarchy() {
        val idle: com.example.anubhav.data.repository.RecoveryStatus = com.example.anubhav.data.repository.RecoveryStatus.Idle
        assertEquals(com.example.anubhav.data.repository.RecoveryStatus.Idle, idle)

        val verifying: com.example.anubhav.data.repository.RecoveryStatus = com.example.anubhav.data.repository.RecoveryStatus.Verifying
        assertEquals(com.example.anubhav.data.repository.RecoveryStatus.Verifying, verifying)

        val ready: com.example.anubhav.data.repository.RecoveryStatus = com.example.anubhav.data.repository.RecoveryStatus.Ready("user@example.com")
        assertTrue(ready is com.example.anubhav.data.repository.RecoveryStatus.Ready)
        assertEquals("user@example.com", (ready as com.example.anubhav.data.repository.RecoveryStatus.Ready).email)

        val expiredError: com.example.anubhav.data.repository.RecoveryStatus = com.example.anubhav.data.repository.RecoveryStatus.Error(
            isExpired = true,
            message = "This password reset link has expired. Please request a new reset link."
        )
        assertTrue(expiredError is com.example.anubhav.data.repository.RecoveryStatus.Error)
        assertTrue((expiredError as com.example.anubhav.data.repository.RecoveryStatus.Error).isExpired)

        val invalidError: com.example.anubhav.data.repository.RecoveryStatus = com.example.anubhav.data.repository.RecoveryStatus.Error(
            isExpired = false,
            message = "This reset link is no longer valid. Please request a new one."
        )
        assertFalse((invalidError as com.example.anubhav.data.repository.RecoveryStatus.Error).isExpired)
    }
}
