package com.example.anubhav.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.ProfileRepository
import com.example.anubhav.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoginMode: Boolean = true,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val location: String = "",
    val currentlyWorkingOn: String = "",
    val thingsIveDone: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    // Password reset state
    val resetEmail: String = "",
    val isSendingReset: Boolean = false,
    val resetSentMessage: String? = null,
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val isUpdatingPassword: Boolean = false,
    val passwordUpdateSuccess: Boolean = false,
    val passwordUpdateError: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val profileRepository: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                error = null,
                password = "",
                confirmPassword = ""
            )
        }
    }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }
    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value, error = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value.trim().lowercase(), error = null) }
    fun onBioChange(value: String) = _uiState.update { it.copy(bio = value) }
    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }
    fun onCurrentlyWorkingOnChange(value: String) = _uiState.update { it.copy(currentlyWorkingOn = value) }
    fun onThingsIveDoneChange(value: String) = _uiState.update { it.copy(thingsIveDone = value) }

    fun onResetEmailChange(value: String) = _uiState.update { it.copy(resetEmail = value, error = null, resetSentMessage = null) }
    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, passwordUpdateError = null) }
    fun onConfirmNewPasswordChange(value: String) = _uiState.update { it.copy(confirmNewPassword = value, passwordUpdateError = null) }

    fun sendPasswordReset() {
        val state = _uiState.value
        val email = state.resetEmail.trim()

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingReset = true, error = null, resetSentMessage = null) }
            val result = authRepository.sendPasswordResetEmail(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSendingReset = false,
                            resetSentMessage = "Password reset email sent. Check your inbox."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSendingReset = false,
                            error = err.localizedMessage ?: "Failed to send reset email. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun updatePassword(onSuccess: () -> Unit) {
        val state = _uiState.value
        val newPassword = state.newPassword.trim()
        val confirm = state.confirmNewPassword.trim()

        if (newPassword.length < 6) {
            _uiState.update { it.copy(passwordUpdateError = "Password must be at least 6 characters.") }
            return
        }

        if (newPassword != confirm) {
            _uiState.update { it.copy(passwordUpdateError = "Passwords do not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPassword = true, passwordUpdateError = null) }
            val result = authRepository.updatePassword(newPassword)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            passwordUpdateSuccess = true,
                            newPassword = "",
                            confirmNewPassword = ""
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isUpdatingPassword = false,
                            passwordUpdateError = err.localizedMessage ?: "Failed to update password. Please try again."
                        )
                    }
                }
            )
        }
    }

    fun resetPasswordState() {
        _uiState.update {
            it.copy(
                resetEmail = "",
                isSendingReset = false,
                resetSentMessage = null,
                newPassword = "",
                confirmNewPassword = "",
                isUpdatingPassword = false,
                passwordUpdateSuccess = false,
                passwordUpdateError = null
            )
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLoading) return

        val identifier = state.email.trim()
        val password = state.password.trim()

        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters.") }
            return
        }

        if (state.isLoginMode) {
            if (identifier.isBlank()) {
                _uiState.update { it.copy(error = "Please enter your email or username.") }
                return
            }
            val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches() || identifier.contains("@")
            if (!isEmail && (identifier.length < 3 || identifier.length > 30 || !identifier.matches(Regex("^[a-zA-Z0-9_.]+$")))) {
                _uiState.update { it.copy(error = "Please enter a valid email address or username.") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val result = if (isEmail) {
                    authRepository.signIn(identifier, password)
                } else {
                    authRepository.signInWithUsername(identifier, password)
                }
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                        onSuccess()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.localizedMessage ?: "Login failed. Please check your credentials."
                            )
                        }
                    }
                )
            }
        } else {
            // Register mode
            val email = identifier
            if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _uiState.update { it.copy(error = "Please enter a valid email address.") }
                return
            }

            val confirmPassword = state.confirmPassword.trim()
            if (password != confirmPassword) {
                _uiState.update { it.copy(error = "Passwords do not match.") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val displayName = state.displayName.trim()
                val username = state.username.trim().lowercase()

                if (displayName.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "Please enter your name.") }
                    return@launch
                }
                if (username.length < 3 || username.length > 30 || !username.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Username must be 3-30 characters with letters, numbers, '.', or '_'."
                        )
                    }
                    return@launch
                }

                // Check username availability
                val availabilityResult = profileRepository.isUsernameAvailable(username)
                if (availabilityResult.isFailure || availabilityResult.getOrNull() == false) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Username '$username' is already taken. Please choose another."
                        )
                    }
                    return@launch
                }

                // Sign up with Supabase Auth
                val signUpResult = authRepository.signUp(email, password)
                signUpResult.fold(
                    onSuccess = { userId ->
                        // Create profile record
                        val newProfile = UserProfile(
                            id = userId,
                            username = username,
                            displayName = displayName,
                            bio = state.bio.trim(),
                            location = state.location.trim(),
                            currentlyWorkingOn = state.currentlyWorkingOn.trim(),
                            thingsIveDone = state.thingsIveDone.trim(),
                            profileImageUrl = null
                        )
                        val saveResult = profileRepository.saveProfile(newProfile)
                        saveResult.fold(
                            onSuccess = {
                                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                                onSuccess()
                            },
                            onFailure = { saveError ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = "Account created, but couldn't save profile: ${saveError.localizedMessage}"
                                    )
                                }
                            }
                        )
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.localizedMessage ?: "Registration failed. Please try again."
                            )
                        }
                    }
                )
            }
        }
    }
}
