package com.example.anubhav.presentation.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anubhav.data.repository.DuplicateReportException
import com.example.anubhav.data.repository.ReportRepository
import com.example.anubhav.domain.model.ReportReason
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ReportPostDialog(
    postId: String,
    reportedUserId: String,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit,
    reportRepository: ReportRepository = remember { ReportRepository() }
) {
    var selectedReason by remember { mutableStateOf(ReportReason.SPAM) }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (isSuccess) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Report Submitted",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.calmTextPrimary
                )
            },
            text = {
                Text(
                    text = "Thank you for helping keep Anubhav safe. Our moderation team will review this post.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    onReportSubmitted()
                }) {
                    Text("Done", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = "Report Post",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.calmTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Why are you reporting this post?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) {
                                selectedReason = reason
                                errorMessage = null
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = {
                                selectedReason = reason
                                errorMessage = null
                            },
                            enabled = !isSubmitting,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reason.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.calmTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CalmTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Additional details (optional)",
                    placeholder = "Provide context to help us understand...",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            CalmButton(
                text = "Submit Report",
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        errorMessage = null
                        val result = reportRepository.submitReport(
                            postId = postId,
                            reportedUserId = reportedUserId,
                            reason = selectedReason.displayName,
                            description = description
                        )
                        isSubmitting = false
                        result.fold(
                            onSuccess = {
                                isSuccess = true
                            },
                            onFailure = { err ->
                                if (err is DuplicateReportException) {
                                    errorMessage = err.message
                                } else {
                                    errorMessage = err.message ?: "Couldn't submit report. Please try again."
                                }
                            }
                        )
                    }
                },
                enabled = !isSubmitting,
                isLoading = isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = MaterialTheme.calmTextSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
