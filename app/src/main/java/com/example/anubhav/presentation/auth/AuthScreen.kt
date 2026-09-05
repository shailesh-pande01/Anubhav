package com.example.anubhav.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // App Brand & Philosophy
                Text(
                    text = "Anubhav",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Live it. Share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary
                )

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = if (state.isLoginMode) "Welcome back" else "Create your profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (state.isLoginMode) {
                        "Sign in to see what creators and explorers are doing."
                    } else {
                        "Share meaningful activities, projects, and experiences."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.calmTextTertiary
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Email or Username
                CalmTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = if (state.isLoginMode) "Email or username" else "Email",
                    placeholder = if (state.isLoginMode) "Enter your email or username" else "Enter your email"
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password
                CalmTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    placeholder = if (state.isLoginMode) "Enter your password" else "Create a password",
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

                // Forgot Password link (Login mode)
                if (state.isLoginMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "Forgot password?",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.calmTextSecondary,
                            modifier = Modifier
                                .clickable { onNavigateToForgotPassword() }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                    }
                }

                // Additional registration fields
                if (!state.isLoginMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        label = "Confirm Password",
                        placeholder = "Confirm your password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayNameChange,
                        label = "Display Name",
                        placeholder = "Enter your name"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = "Username",
                        placeholder = "Choose a username"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.location,
                        onValueChange = viewModel::onLocationChange,
                        label = "Location",
                        placeholder = "Enter your location"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.currentlyWorkingOn,
                        onValueChange = viewModel::onCurrentlyWorkingOnChange,
                        label = "Currently working on",
                        placeholder = "e.g. Learning pottery, writing a book..."
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.thingsIveDone,
                        onValueChange = viewModel::onThingsIveDoneChange,
                        label = "Things I've done",
                        placeholder = "e.g. Ran a marathon, learned guitar, built a bookshelf...",
                        singleLine = false,
                        minLines = 3
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    CalmTextField(
                        value = state.bio,
                        onValueChange = viewModel::onBioChange,
                        label = "Short Bio",
                        placeholder = "A brief bio about yourself...",
                        singleLine = false,
                        minLines = 2
                    )
                }

                // Error message
                if (state.error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Submit Button
                CalmButton(
                    text = if (state.isLoginMode) "Log in" else "Create account",
                    onClick = { viewModel.submit(onAuthSuccess) },
                    isLoading = state.isLoading
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Toggle Mode
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isLoginMode) {
                            "New to Anubhav? Create account"
                        } else {
                            "Already have an account? Log in"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.calmTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable { viewModel.toggleMode() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
