package com.example.anubhav.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.example.anubhav.domain.model.AccountStatus
import com.example.anubhav.domain.model.AdminUserSummary
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmEmptyState
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmSuccess
import com.example.anubhav.ui.theme.calmWarning
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: AdminUsersViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Users",
                subtitle = "User Moderation & Status",
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
            // Search Input
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CalmTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = "Search by username or name...",
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.calmTextTertiary
                        )
                    }
                )
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL", "ACTIVE", "RESTRICTED", "SUSPENDED", "BANNED").forEach { status ->
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { viewModel.onStatusFilterChanged(status) },
                        label = { Text(status.lowercase().replaceFirstChar { it.uppercase() }) }
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
                    state.isLoading && state.users.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CalmLoadingIndicator()
                        }
                    }

                    state.users.isEmpty() -> {
                        CalmEmptyState(
                            title = "No users found",
                            subtitle = if (state.searchQuery.isNotBlank()) "No users matching '${state.searchQuery}'." else "No users in this category.",
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
                                items = state.users,
                                key = { it.profile.id }
                            ) { userSummary ->
                                UserSummaryCard(
                                    userSummary = userSummary,
                                    onClick = { onUserClick(userSummary.profile.id) }
                                )
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
fun UserSummaryCard(
    userSummary: AdminUserSummary,
    onClick: () -> Unit
) {
    val profile = userSummary.profile
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                imageUrl = profile.profileImageUrl,
                name = profile.displayName,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.calmTextPrimary
                )
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.calmTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${userSummary.postCount} posts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.calmTextTertiary
                    )
                    if (userSummary.reportsReceivedCount > 0) {
                        Text(
                            text = "${userSummary.reportsReceivedCount} reports",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Status Badge
            UserStatusBadge(status = profile.accountStatus)
        }
    }
}

@Composable
fun UserStatusBadge(status: AccountStatus) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val successColor = MaterialTheme.calmSuccess
    val warningColor = MaterialTheme.calmWarning
    val (bgColor, textColor) = when (status) {
        AccountStatus.ACTIVE -> successColor.copy(alpha = 0.18f) to successColor
        AccountStatus.RESTRICTED -> warningColor.copy(alpha = 0.18f) to warningColor
        AccountStatus.SUSPENDED -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f) to MaterialTheme.colorScheme.error
        AccountStatus.BANNED -> if (isDark) {
            androidx.compose.ui.graphics.Color(0xFF374151) to androidx.compose.ui.graphics.Color(0xFFF9FAFB)
        } else {
            androidx.compose.ui.graphics.Color(0xFF1E2022) to androidx.compose.ui.graphics.Color.White
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}
