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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.anubhav.domain.model.AdminPostSummary
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmEmptyState
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPostsScreen(
    onBack: () -> Unit,
    viewModel: AdminPostsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var postToRemove by remember { mutableStateOf<AdminPostSummary?>(null) }
    var removeReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Posts",
                subtitle = "Post Content Management",
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
            // Search Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CalmTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = "Search post text...",
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.calmTextTertiary
                        )
                    }
                )
            }

            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All Posts", "MOST_REPORTED" to "Most Reported", "REMOVED" to "Removed Posts").forEach { (filterKey, label) ->
                    FilterChip(
                        selected = state.selectedFilter == filterKey,
                        onClick = { viewModel.onFilterChanged(filterKey) },
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
                    state.isLoading && state.posts.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CalmLoadingIndicator()
                        }
                    }

                    state.posts.isEmpty() -> {
                        CalmEmptyState(
                            title = "No posts found",
                            subtitle = "Try adjusting your search query or filter.",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = state.posts,
                                key = { it.post.id }
                            ) { postSummary ->
                                AdminPostCard(
                                    postSummary = postSummary,
                                    onRemoveClick = {
                                        removeReason = ""
                                        postToRemove = postSummary
                                    }
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

    if (postToRemove != null) {
        ActionDialog(
            title = "Remove Post?",
            message = "This post will be permanently removed from Anubhav and its storage media pruned.",
            confirmLabel = "Remove",
            isDestructive = true,
            reasonValue = removeReason,
            onReasonChange = { removeReason = it },
            onConfirm = {
                val target = postToRemove
                postToRemove = null
                if (target != null) {
                    viewModel.removePost(target.post.id, target.post.userId, removeReason, target.post.imagePath)
                }
            },
            onDismiss = { postToRemove = null }
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
fun AdminPostCard(
    postSummary: AdminPostSummary,
    onRemoveClick: () -> Unit
) {
    val post = postSummary.post
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.calmBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Author & Status/Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarImage(
                        imageUrl = post.author.profileImageUrl,
                        name = post.author.displayName,
                        size = 36.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = post.author.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.calmTextPrimary
                        )
                        Text(
                            text = "@${post.author.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.calmTextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (postSummary.reportsCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${postSummary.reportsCount} Reports",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (post.isRemoved) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REMOVED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.calmTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Post content
            if (post.content.isNotBlank()) {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Post Image thumbnail
            if (!post.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Post thumbnail",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = post.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.calmTextTertiary
                )

                if (!post.isRemoved) {
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Post",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
