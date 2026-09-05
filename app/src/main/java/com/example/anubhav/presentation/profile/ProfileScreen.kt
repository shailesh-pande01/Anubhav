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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmEmptyState
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmOutlinedButton
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.presentation.components.PostItem
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile(null) // null = current user
    }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Profile",
                actions = {
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Log out",
                            tint = MaterialTheme.calmTextSecondary
                        )
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
                if (state.posts.isEmpty()) {
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
                            onDeleteClick = { viewModel.deletePost(post) }
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
}
