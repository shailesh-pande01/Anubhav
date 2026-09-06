package com.example.anubhav.presentation.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmEmptyState
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmOutlinedButton
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.presentation.components.PostItem
import com.example.anubhav.presentation.report.ReportPostDialog
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onNavigateToEditPost: (String) -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var reportingPost by remember { mutableStateOf<PostWithAuthor?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile(null) // null = current user
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMorePosts && !state.isLoadingMorePosts) {
            viewModel.loadNextUserPostsPage()
        }
    }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Profile",
                actions = {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Account options",
                                tint = MaterialTheme.calmTextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Log out", color = MaterialTheme.calmTextPrimary) },
                                onClick = {
                                    showOptionsMenu = false
                                    showLogoutDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.calmTextSecondary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteAccountDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading && state.profile == null) {
            CalmLoadingIndicator(modifier = Modifier.fillMaxSize())
        } else {
            val profile = state.profile
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Profile Header Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarImage(
                                imageUrl = profile?.profileImageUrl,
                                name = profile?.displayName ?: "User",
                                size = 72.dp
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = profile?.displayName ?: "Your Profile",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 20.sp
                                    ),
                                    color = MaterialTheme.calmTextPrimary
                                )
                                if (!profile?.username.isNullOrBlank()) {
                                    Text(
                                        text = "@${profile?.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.calmTextSecondary
                                    )
                                }
                                if (!profile?.location.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📍 ${profile?.location}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.calmTextSecondary
                                    )
                                }
                            }
                        }

                        // Bio
                        if (!profile?.bio.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = profile?.bio ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.calmTextPrimary
                            )
                        }

                        // Currently working on
                        if (!profile?.currentlyWorkingOn.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Currently working on",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile?.currentlyWorkingOn ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextPrimary
                            )
                        }

                        // Things I've done
                        if (!profile?.thingsIveDone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Things I've done",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profile?.thingsIveDone ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = MaterialTheme.calmTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Admin Dashboard shortcut (visible strictly to verified administrators)
                        if (state.isAdmin) {
                            CalmButton(
                                text = "Admin Dashboard",
                                onClick = onNavigateToAdminDashboard,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Edit Profile action
                        CalmOutlinedButton(
                            text = "Edit Profile",
                            onClick = onNavigateToEditProfile,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
                    }
                }

                // Posts List
                if (state.error != null && state.posts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.error ?: "Couldn't load posts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.calmTextSecondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CalmButton(
                                text = "Try again",
                                onClick = { viewModel.loadProfile(null) },
                                modifier = Modifier.fillMaxWidth(0.5f)
                            )
                        }
                    }
                } else if (state.posts.isEmpty() && !state.isLoading) {
                    item {
                        CalmEmptyState(
                            title = "No posts yet.",
                            subtitle = "Share something meaningful you worked on or explored.",
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                } else {
                    items(
                        items = state.posts,
                        key = { it.id }
                    ) { post ->
                        PostItem(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post) },
                            onProfileClick = onNavigateToUserProfile,
                            onEditClick = { onNavigateToEditPost(post.id) },
                            onDeleteClick = { viewModel.deletePost(post) },
                            onReportClick = { reportingPost = post }
                        )
                    }
                }

                if (state.isLoadingMorePosts) {
                    item {
                        CalmLoadingIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text("Log out?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.calmTextPrimary)
            },
            text = {
                Text("Are you sure you want to log out of Anubhav?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.calmTextSecondary)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut(onLogout)
                    }
                ) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = MaterialTheme.calmTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isDeletingAccount) {
                    showDeleteAccountDialog = false
                    viewModel.clearDeleteAccountError()
                }
            },
            title = {
                Text("Delete account?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.calmTextPrimary)
            },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete your account, profile, posts, and associated data. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.calmTextSecondary
                    )
                    if (state.deleteAccountError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.deleteAccountError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(
                            onSuccess = {
                                showDeleteAccountDialog = false
                                onLogout()
                            }
                        )
                    },
                    enabled = !state.isDeletingAccount
                ) {
                    if (state.isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Delete account",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.clearDeleteAccountError()
                    },
                    enabled = !state.isDeletingAccount
                ) {
                    Text("Cancel", color = MaterialTheme.calmTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }

    reportingPost?.let { targetPost ->
        ReportPostDialog(
            postId = targetPost.id,
            reportedUserId = targetPost.userId,
            onDismiss = { reportingPost = null },
            onReportSubmitted = { reportingPost = null }
        )
    }
}
