package com.example.anubhav.presentation.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.data.repository.RecoveryStatus
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun ResetPasswordScreen(
    onResetSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    onRequestNewLink: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val handleBackToLogin = {
        viewModel.clearRecoveryAndBackToLogin(onBackToLogin)
    }

    android.util.Log.i("ResetPasswordScreen", "Composing ResetPasswordScreen: recoveryStatus=${state.recoveryStatus}, success=${state.passwordUpdateSuccess}")

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Reset Password",
                navigationIcon = {
                    IconButton(onClick = handleBackToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.calmTextPrimary
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when {
                // 1. Verifying token state
                state.recoveryStatus is RecoveryStatus.Verifying -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CalmLoadingIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Verifying reset link...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }

                // 2. Error / Expired link state (Requirements 20 & 21)
                state.recoveryStatus is RecoveryStatus.Error -> {
                    val errorStatus = state.recoveryStatus as RecoveryStatus.Error
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (errorStatus.isExpired) "Reset link expired" else "Invalid reset link",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.calmTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = errorStatus.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.calmTextSecondary
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        CalmButton(
                            text = "Request new link",
                            onClick = {
                                viewModel.clearRecoveryAndBackToLogin(onRequestNewLink)
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = handleBackToLogin) {
                                Text(
                                    text = "Back to log in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.calmTextSecondary
                                )
                            }
                        }
                    }
                }

                // 3. Success state (Requirements 17 & 47)
                state.passwordUpdateSuccess -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Password updated",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.calmTextPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Your password has been changed successfully.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.calmTextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You can now log in with your new password.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.calmTextTertiary
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        CalmButton(
                            text = "Continue to login",
                            onClick = {
                                viewModel.clearRecoveryAndBackToLogin(onResetSuccess)
                            }
                        )
                    }
                }

                // 4. Password Entry Form (Requirement 12, 13, 14)
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Reset password",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.calmTextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Create a new password for your Anubhav account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.calmTextSecondary
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        CalmTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = "New password",
                            placeholder = "At least 6 characters",
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                        tint = MaterialTheme.calmTextTertiary
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        CalmTextField(
                            value = state.confirmNewPassword,
                            onValueChange = viewModel::onConfirmNewPasswordChange,
                            label = "Confirm password",
                            placeholder = "Confirm your new password",
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                        tint = MaterialTheme.calmTextTertiary
                                    )
                                }
                            }
                        )

                        if (state.passwordUpdateError != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = state.passwordUpdateError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        CalmButton(
                            text = if (state.isUpdatingPassword) "Updating password..." else "Update password",
                            onClick = { viewModel.updatePassword(onSuccess = {}) },
                            isLoading = state.isUpdatingPassword,
                            enabled = !state.isUpdatingPassword && !state.passwordUpdateSuccess
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = handleBackToLogin) {
                                Text(
                                    text = "Back to log in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.calmTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
