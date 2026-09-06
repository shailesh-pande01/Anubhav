package com.example.anubhav.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmOutlinedButton
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun AdminUserDetailScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: AdminUserDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadUser(userId)
    }

    var showWarnDialog by remember { mutableStateOf(false) }
    var showRestrictDialog by remember { mutableStateOf(false) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    var actionReason by remember { mutableStateOf("") }
    var selectedDurationDays by remember { mutableIntStateOf(7) }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "User Moderation",
                subtitle = state.userDetail?.profile?.let { "@${it.username}" },
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CalmLoadingIndicator()
            }
        } else {
            val detail = state.userDetail
            if (detail == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "User not found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextSecondary
                    )
                }
                return@Scaffold
            }

            val profile = detail.profile

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Section 1: User Profile Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        imageUrl = profile.profileImageUrl,
                        name = profile.displayName,
                        size = 64.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                            color = MaterialTheme.calmTextPrimary
                        )
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.calmTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        UserStatusBadge(status = profile.accountStatus)
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: User Statistics Grid
                Text(
                    text = "Account Statistics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(title = "Posts", value = "${detail.postCount}", modifier = Modifier.weight(1f))
                    StatCard(
                        title = "Reports Recv.",
                        value = "${detail.reportsReceivedCount}",
                        badgeColor = if (detail.reportsReceivedCount > 0) MaterialTheme.colorScheme.error else null,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Warnings",
                        value = "${detail.warningsCount}",
                        badgeColor = if (detail.warningsCount > 0) MaterialTheme.colorScheme.error else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(title = "Removed Posts", value = "${detail.removedPostsCount}", modifier = Modifier.weight(1f))
                    StatCard(title = "Suspensions", value = "${detail.suspensionsCount}", modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Direct Moderation Actions
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (profile.accountStatus != AccountStatus.ACTIVE) {
                    CalmButton(
                        text = "Reset Status to Active (Unban / Unsuspend)",
                        onClick = {
                            actionReason = ""
                            showResetDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                CalmOutlinedButton(
                    text = "Warn User",
                    onClick = {
                        actionReason = ""
                        showWarnDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                CalmOutlinedButton(
                    text = "Restrict User (Temporary)",
                    onClick = {
                        actionReason = ""
                        selectedDurationDays = 7
                        showRestrictDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                CalmOutlinedButton(
                    text = "Suspend User (Temporary)",
                    onClick = {
                        actionReason = ""
                        selectedDurationDays = 7
                        showSuspendDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                CalmButton(
                    text = "Permanently Ban User",
                    onClick = {
                        actionReason = ""
                        showBanDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Section 4: Moderation Audit History Timeline for this user
                if (detail.moderationHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Moderation History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.calmTextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    detail.moderationHistory.forEach { action ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = action.action.displayLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = action.createdAt.take(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.calmTextTertiary
                                    )
                                }
                                if (action.reason.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = action.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.calmTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Dialogs
    if (showWarnDialog) {
        ActionDialog(
            title = "Warn User?",
            message = "A formal warning will be logged for this user.",
            confirmLabel = "Issue Warning",
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showWarnDialog = false
                viewModel.warnUser(actionReason)
            },
            onDismiss = { showWarnDialog = false }
        )
    }

    if (showRestrictDialog) {
        DurationActionDialog(
            title = "Restrict User",
            message = "User cannot create posts or like while restricted.",
            confirmLabel = "Restrict",
            durations = listOf(1 to "24 hours", 3 to "3 days", 7 to "7 days", 30 to "30 days"),
            selectedDuration = selectedDurationDays,
            onSelectDuration = { selectedDurationDays = it },
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showRestrictDialog = false
                viewModel.restrictUser(selectedDurationDays, actionReason)
            },
            onDismiss = { showRestrictDialog = false }
        )
    }

    if (showSuspendDialog) {
        DurationActionDialog(
            title = "Suspend User",
            message = "User will be locked out of the app until suspension ends.",
            confirmLabel = "Suspend",
            durations = listOf(1 to "1 day", 7 to "7 days", 30 to "30 days"),
            selectedDuration = selectedDurationDays,
            onSelectDuration = { selectedDurationDays = it },
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showSuspendDialog = false
                viewModel.suspendUser(selectedDurationDays, actionReason)
            },
            onDismiss = { showSuspendDialog = false }
        )
    }

    if (showBanDialog) {
        ActionDialog(
            title = "Permanently Ban User?",
            message = "User will be permanently banned from Anubhav.",
            confirmLabel = "Permanently Ban",
            isDestructive = true,
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showBanDialog = false
                viewModel.banUser(actionReason)
            },
            onDismiss = { showBanDialog = false }
        )
    }

    if (showResetDialog) {
        ActionDialog(
            title = "Reset to Active?",
            message = "Remove any active suspension or ban and restore normal access for this user.",
            confirmLabel = "Restore Active Status",
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showResetDialog = false
                viewModel.resetToActive(actionReason)
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (state.actionSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearActionMessage() },
            title = { Text("Success", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.calmTextPrimary) },
            text = { Text(state.actionSuccessMessage ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.calmTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearActionMessage() }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
