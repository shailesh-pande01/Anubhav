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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Forgot Password",
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            if (state.isResetEmailSent) {
                // Dedicated Confirmation State (Requirement 29)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Check your email",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.calmTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "We've sent a password reset link to your email address.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.calmTextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Open the email and tap the reset link to create a new password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextSecondary
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    CalmButton(
                        text = "Back to login",
                        onClick = {
                            viewModel.onDismissResetEmailSent()
                            onBack()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { viewModel.sendPasswordReset() },
                            enabled = !state.isSendingReset
                        ) {
                            Text(
                                text = if (state.isSendingReset) "Resending..." else "Resend email",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }
            } else {
                // Input Form State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Forgot password?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.calmTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter your email and we'll send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextSecondary
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    CalmTextField(
                        value = state.resetEmail,
                        onValueChange = viewModel::onResetEmailChange,
                        label = "Email",
                        placeholder = "Enter your email"
                    )

                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    CalmButton(
                        text = "Send reset link",
                        onClick = { viewModel.sendPasswordReset() },
                        isLoading = state.isSendingReset,
                        enabled = !state.isSendingReset
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = onBack) {
                            Text(
                                text = "Back to login",
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
