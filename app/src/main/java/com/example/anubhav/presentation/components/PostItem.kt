package com.example.anubhav.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.anubhav.domain.model.PostType
import com.example.anubhav.domain.model.PostWithAuthor
import com.example.anubhav.ui.theme.LikeActive
import com.example.anubhav.ui.theme.LikeInactive
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary

@Composable
fun PostItem(
    post: PostWithAuthor,
    onLikeClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header: Profile Picture, Display Name, Relative Time, and Optional Overflow Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                imageUrl = post.author.profileImageUrl,
                name = post.author.displayName,
                size = 38.dp,
                onClick = { onProfileClick(post.userId) }
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onProfileClick(post.userId) }
            ) {
                Text(
                    text = post.author.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.calmTextPrimary
                )
                if (post.relativeTime.isNotBlank()) {
                    Text(
                        text = post.relativeTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.calmTextTertiary
                    )
                }
            }

            // Post Owner actions (delete)
            if (post.isOwner && onDeleteClick != null) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Post options",
                            tint = MaterialTheme.calmTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete post",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content
        when (post.postType) {
            PostType.TEXT -> {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 23.sp,
                        letterSpacing = 0.15.sp
                    ),
                    color = MaterialTheme.calmTextPrimary,
                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp)
                )
            }

            PostType.IMAGE -> {
                Column(modifier = Modifier.padding(start = 48.dp)) {
                    if (!post.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = post.imageUrl,
                            contentDescription = "Post image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 460.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }

                    if (post.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                            color = MaterialTheme.calmTextPrimary
                        )
                    }
                }
            }
        }

        // Footer: Calm Like Action (Owner sees count, others see ONLY the heart icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLikeClick() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (post.isLikedByCurrentUser) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = if (post.isLikedByCurrentUser) "Unlike" else "Like",
                    tint = if (post.isLikedByCurrentUser) LikeActive else LikeInactive,
                    modifier = Modifier.size(18.dp)
                )

                // STRICT PRODUCT PRIVACY RULE:
                // Only display like count if current user is the owner of this post.
                if (post.isOwner && post.ownerLikeCount != null) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${post.ownerLikeCount}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.calmTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.calmBorderSubtle
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete post?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.calmTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently delete this post.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteClick?.invoke()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.calmTextSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
