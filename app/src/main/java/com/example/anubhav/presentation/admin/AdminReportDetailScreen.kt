package com.example.anubhav.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmOutlinedButton
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun AdminReportDetailScreen(
    reportId: String,
    onBack: () -> Unit,
    viewModel: AdminReportDetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(reportId) {
        viewModel.loadReport(reportId)
    }

    var showDismissDialog by remember { mutableStateOf(false) }
    var showRemovePostDialog by remember { mutableStateOf(false) }
    var showWarnDialog by remember { mutableStateOf(false) }
    var showRestrictDialog by remember { mutableStateOf(false) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }

    var actionReason by remember { mutableStateOf("") }
    var selectedDurationDays by remember { mutableIntStateOf(7) }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Review Report",
                subtitle = "Report #${reportId.take(8).uppercase()}",
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
            val report = state.report
            if (report == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "Report not found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextSecondary
                    )
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Section 1: Report Information Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reason: ${report.reason}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error
                            )
                            ReportStatusBadge(status = report.status)
                        }

                        if (report.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Description:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = report.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextPrimary
                            )
                        }

                        if (!report.resolution.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Resolution: ${report.resolution}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.calmTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Reported ${report.relativeTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.calmTextTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Reported Post Preview
                Text(
                    text = "Reported Content",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val post = report.post
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (post != null) {
                            if (!post.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = post.imageUrl,
                                    contentDescription = "Reported image",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (post.content.isNotBlank()) {
                                Text(
                                    text = post.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.calmTextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Text(
                                text = "Posted ${post.relativeTime} by @${post.author.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.calmTextTertiary
                            )

                            if (post.isRemoved) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⚠️ This post has already been removed (${post.removalReason ?: "Removed"})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = "Post no longer exists or was deleted by the author.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: Reported User & Reporter
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Reported User
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Reported User",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AvatarImage(
                                imageUrl = report.reportedUser?.profileImageUrl,
                                name = report.reportedUser?.displayName ?: "User",
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.reportedUser?.displayName ?: "User",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.calmTextPrimary
                            )
                            Text(
                                text = "@${report.reportedUser?.username ?: "user"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Status: ${report.reportedUser?.accountStatus?.name ?: "ACTIVE"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (report.reportedUser?.accountStatus?.name != "ACTIVE") MaterialTheme.colorScheme.error else MaterialTheme.calmTextPrimary
                            )
                            val userDetail = state.reportedUserDetail
                            if (userDetail != null) {
                                Text(
                                    text = "Reports received: ${userDetail.reportsReceivedCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.calmTextSecondary
                                )
                                Text(
                                    text = "Warnings: ${userDetail.warningsCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.calmTextSecondary
                                )
                            }
                        }
                    }

                    // Reporter
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Reporter",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AvatarImage(
                                imageUrl = report.reporter?.profileImageUrl,
                                name = report.reporter?.displayName ?: "Reporter",
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = report.reporter?.displayName ?: "Reporter",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.calmTextPrimary
                            )
                            Text(
                                text = "@${report.reporter?.username ?: "reporter"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }

                // Section 4: Previous Reports History Context
                if (state.previousReportsOnPost.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "🚨 Context: This post has received ${state.previousReportsOnPost.size} reports",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val grouped = state.previousReportsOnPost.groupingBy { it.reason }.eachCount()
                            val summaryStr = grouped.entries.joinToString(" • ") { "${it.key}: ${it.value}" }
                            Text(
                                text = summaryStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Section 5: Moderation Action Buttons
                Text(
                    text = "Moderation Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action 1: Dismiss Report
                CalmOutlinedButton(
                    text = "Dismiss Report",
                    onClick = {
                        actionReason = ""
                        showDismissDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action 2: Remove Post
                if (post != null && !post.isRemoved) {
                    CalmButton(
                        text = "Remove Post",
                        onClick = {
                            actionReason = ""
                            showRemovePostDialog = true
                        },
                        enabled = !state.isActionLoading,
                        isLoading = state.isActionLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Action 3: Warn User
                CalmOutlinedButton(
                    text = "Warn User",
                    onClick = {
                        actionReason = ""
                        showWarnDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action 4: Restrict User
                CalmOutlinedButton(
                    text = "Restrict User...",
                    onClick = {
                        actionReason = ""
                        selectedDurationDays = 7
                        showRestrictDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action 5: Suspend User
                CalmOutlinedButton(
                    text = "Suspend User...",
                    onClick = {
                        actionReason = ""
                        selectedDurationDays = 7
                        showSuspendDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action 6: Ban User
                CalmButton(
                    text = "Permanently Ban User",
                    onClick = {
                        actionReason = ""
                        showBanDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Confirmation / Action Dialogs

    // 1. Dismiss Dialog
    if (showDismissDialog) {
        ActionDialog(
            title = "Dismiss Report?",
            message = "This report will be closed as dismissed.",
            confirmLabel = "Dismiss",
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showDismissDialog = false
                viewModel.dismissReport(actionReason)
            },
            onDismiss = { showDismissDialog = false }
        )
    }

    // 2. Remove Post Dialog
    if (showRemovePostDialog) {
        ActionDialog(
            title = "Remove Post?",
            message = "This post will be removed from Anubhav and its storage media pruned.",
            confirmLabel = "Remove Post",
            isDestructive = true,
            reasonValue = actionReason,
            onReasonChange = { actionReason = it },
            onConfirm = {
                showRemovePostDialog = false
                viewModel.removePost(actionReason)
            },
            onDismiss = { showRemovePostDialog = false }
        )
    }

    // 3. Warn User Dialog
    if (showWarnDialog) {
        ActionDialog(
            title = "Warn User?",
            message = "A formal warning will be logged on the user's account.",
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

    // 4. Restrict User Dialog
    if (showRestrictDialog) {
        DurationActionDialog(
            title = "Restrict User",
            message = "The user will be temporarily restricted from creating posts or liking.",
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

    // 5. Suspend User Dialog
    if (showSuspendDialog) {
        DurationActionDialog(
            title = "Suspend User",
            message = "The user will be locked out of the application until suspension expires.",
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

    // 6. Ban User Dialog
    if (showBanDialog) {
        ActionDialog(
            title = "Permanently Ban User?",
            message = "This user will be permanently banned from Anubhav. Their account will remain available for moderation and audit records.",
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

    // Action Success Notification Dialog
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

    // Action Error Notification Dialog
    if (state.actionErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearActionErrorMessage() },
            title = { Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) },
            text = { Text(state.actionErrorMessage ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.calmTextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearActionErrorMessage() }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
fun ActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    reasonValue: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.calmTextPrimary)
        },
        text = {
            Column {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.calmTextSecondary)
                Spacer(modifier = Modifier.height(14.dp))
                CalmTextField(
                    value = reasonValue,
                    onValueChange = onReasonChange,
                    label = "Reason (optional)",
                    placeholder = "Specify rationale for audit history...",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.calmTextSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun DurationActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    durations: List<Pair<Int, String>>,
    selectedDuration: Int,
    onSelectDuration: (Int) -> Unit,
    reasonValue: String,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.calmTextPrimary)
        },
        text = {
            Column {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.calmTextSecondary)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Duration:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.calmTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durations.forEach { (days, label) ->
                        val isSelected = selectedDuration == days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelectDuration(days) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.calmTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                CalmTextField(
                    value = reasonValue,
                    onValueChange = onReasonChange,
                    label = "Reason",
                    placeholder = "Specify rationale...",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = MaterialTheme.calmTextSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    )
}
