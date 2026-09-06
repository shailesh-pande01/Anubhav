package com.example.anubhav.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.anubhav.presentation.components.AvatarImage
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.components.CalmOutlinedButton
import com.example.anubhav.presentation.components.CalmTextField
import com.example.anubhav.presentation.components.CalmTopBar
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onProfileSaved: () -> Unit,
    onAccountDeleted: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProfileForEditing()
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onAvatarSelected(uri)
    }

    Scaffold(
        topBar = {
            CalmTopBar(
                title = "Edit Profile",
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
        if (state.isEditLoading && state.profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CalmLoadingIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile photo editor
            Box(
                modifier = Modifier
                    .clickable(enabled = !state.isSavingProfile) {
                        avatarPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AvatarImage(
                    imageUrl = state.pendingAvatarUri?.toString() ?: state.editProfileImageUrl ?: state.profile?.profileImageUrl,
                    name = state.editDisplayName.ifBlank { "User" },
                    size = 84.dp
                )

                if (state.isSavingProfile && state.pendingAvatarUri != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = {
                    avatarPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !state.isSavingProfile
            ) {
                Text(
                    text = "Change photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.calmTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display Name
            CalmTextField(
                value = state.editDisplayName,
                onValueChange = viewModel::onEditDisplayNameChange,
                label = "Display Name",
                placeholder = "Enter your name"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Username
            CalmTextField(
                value = state.editUsername,
                onValueChange = viewModel::onEditUsernameChange,
                label = "Username",
                placeholder = "Choose a username"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Location
            CalmTextField(
                value = state.editLocation,
                onValueChange = viewModel::onEditLocationChange,
                label = "Location",
                placeholder = "Enter your location"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Currently working on
            CalmTextField(
                value = state.editCurrentlyWorkingOn,
                onValueChange = viewModel::onEditCurrentlyWorkingOnChange,
                label = "Currently working on",
                placeholder = "e.g. Learning pottery, writing a book..."
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Things I've done
            CalmTextField(
                value = state.editThingsIveDone,
                onValueChange = viewModel::onEditThingsIveDoneChange,
                label = "Things I've done",
                placeholder = "e.g. Ran a marathon, learned guitar, built a bookshelf...",
                singleLine = false,
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bio
            CalmTextField(
                value = state.editBio,
                onValueChange = viewModel::onEditBioChange,
                label = "Bio",
                placeholder = "A brief bio about yourself...",
                singleLine = false,
                minLines = 2,
                maxLines = 4
            )

            if (state.editError != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = state.editError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            CalmButton(
                text = "Save",
                onClick = { viewModel.saveProfile(context, onProfileSaved) },
                enabled = !state.isSavingProfile && !state.isDeletingAccount,
                isLoading = state.isSavingProfile
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Account",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.calmTextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Permanently delete your account and all associated data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.calmTextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            CalmOutlinedButton(
                text = "Delete Account",
                onClick = { showDeleteAccountDialog = true },
                enabled = !state.isSavingProfile && !state.isDeletingAccount,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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
                                onAccountDeleted()
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
}
