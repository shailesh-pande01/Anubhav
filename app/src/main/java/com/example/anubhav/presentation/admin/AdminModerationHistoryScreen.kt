package com.example.anubhav.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.domain.model.ModerationAction
import com.example.anubhav.domain.model.ModerationActionType
import com.example.anubhav.presentation.components.CalmEmptyState
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmSuccess
import com.example.anubhav.ui.theme.calmWarning
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationHistoryScreen(
    onBack: () -> Unit,
    viewModel: AdminModerationHistoryViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Audit Log",
                subtitle = "Moderation History",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Action Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Actions",
                    "WARNING" to "Warnings",
                    "POST_REMOVED" to "Posts Removed",
                    "USER_RESTRICTED" to "Restricted",
                    "USER_SUSPENDED" to "Suspended",
                    "USER_BANNED" to "Banned",
                    "REPORT_DISMISSED" to "Dismissed"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = state.selectedActionFilter == key,
                        onClick = { viewModel.onFilterChanged(key) },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading && state.actions.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CalmLoadingIndicator()
                        }
                    }

                    state.actions.isEmpty() -> {
                        CalmEmptyState(
                            title = "No audit records found",
                            subtitle = "Administrative actions will appear here.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.actions,
                                key = { it.id }
                            ) { action ->
                                ModerationActionCard(action = action)
                            }
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModerationActionCard(action: ModerationAction) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Action Badge & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModerationActionBadge(actionType = action.action)
                Text(
                    text = action.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.calmTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Admin & Target
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Admin: ",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextSecondary
                )
                Text(
                    text = action.adminDisplayName ?: "Admin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.calmTextPrimary
                )
                if (!action.targetUserDisplayName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Target: ",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.calmTextSecondary
                    )
                    Text(
                        text = action.targetUserDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.calmTextPrimary
                    )
                }
            }

            // Reason
            if (action.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reason: ${action.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.calmTextPrimary
                )
            }

            // Duration
            if (!action.durationUntil.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Until: ${action.durationUntil.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.calmTextTertiary
                )
            }
        }
    }
}

@Composable
fun ModerationActionBadge(actionType: ModerationActionType) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val successColor = MaterialTheme.calmSuccess
    val warningColor = MaterialTheme.calmWarning
    val (bgColor, textColor) = when (actionType) {
        ModerationActionType.WARNING -> warningColor.copy(alpha = 0.18f) to warningColor
        ModerationActionType.POST_REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f) to MaterialTheme.colorScheme.error
        ModerationActionType.USER_RESTRICTED -> warningColor.copy(alpha = 0.18f) to warningColor
        ModerationActionType.USER_SUSPENDED -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f) to MaterialTheme.colorScheme.error
        ModerationActionType.USER_BANNED -> if (isDark) {
            androidx.compose.ui.graphics.Color(0xFF374151) to androidx.compose.ui.graphics.Color(0xFFF9FAFB)
        } else {
            androidx.compose.ui.graphics.Color(0xFF1E2022) to androidx.compose.ui.graphics.Color.White
        }
        ModerationActionType.REPORT_DISMISSED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.calmTextSecondary
        ModerationActionType.STATUS_RESET -> successColor.copy(alpha = 0.18f) to successColor
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = actionType.displayLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}
