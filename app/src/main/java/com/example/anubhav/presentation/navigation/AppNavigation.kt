package com.example.anubhav.presentation.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.data.repository.ProfileRepository
import com.example.anubhav.data.repository.RecoveryStatus
import com.example.anubhav.domain.model.UserProfile
import com.example.anubhav.presentation.admin.AdminDashboardScreen
import com.example.anubhav.presentation.admin.AdminModerationHistoryScreen
import com.example.anubhav.presentation.admin.AdminPostsScreen
import com.example.anubhav.presentation.admin.AdminReportDetailScreen
import com.example.anubhav.presentation.admin.AdminReportsScreen
import com.example.anubhav.presentation.admin.AdminUserDetailScreen
import com.example.anubhav.presentation.admin.AdminUsersScreen
import com.example.anubhav.presentation.auth.AuthScreen
import com.example.anubhav.presentation.auth.ForgotPasswordScreen
import com.example.anubhav.presentation.auth.ResetPasswordScreen
import com.example.anubhav.presentation.components.CalmButton
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.create.CreatePostScreen
import com.example.anubhav.presentation.feed.FeedScreen
import com.example.anubhav.presentation.post.EditPostScreen
import com.example.anubhav.presentation.profile.EditProfileScreen
import com.example.anubhav.presentation.profile.ProfileScreen
import com.example.anubhav.presentation.profile.ProfileViewModel
import com.example.anubhav.presentation.profile.UserProfileScreen
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextSecondary
import com.example.anubhav.ui.theme.calmTextTertiary
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val sessionStatus by authRepository.sessionStatus.collectAsState()
    val recoveryStatus by authRepository.recoveryStatus.collectAsState()

    val currentUserId = authRepository.getCurrentUserId()
    var userProfile by remember(currentUserId) { mutableStateOf<UserProfile?>(null) }
    val profileRepo = remember { ProfileRepository() }

    LaunchedEffect(currentUserId, sessionStatus) {
        val uid = authRepository.getCurrentUserId()
        if (uid != null && sessionStatus is SessionStatus.Authenticated) {
            val p = profileRepo.getProfile(uid).getOrNull()
            userProfile = p
        } else {
            userProfile = null
        }
    }

    // 1. If currently verifying a recovery link, display calm verifying state
    if (recoveryStatus is RecoveryStatus.Verifying) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CalmLoadingIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Verifying reset link...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary
                )
            }
        }
        return
    }

    // 2. While Supabase is restoring the regular session from local preferences, display calm loading state
    if (sessionStatus is SessionStatus.Initializing && recoveryStatus is RecoveryStatus.Idle) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CalmLoadingIndicator()
        }
        return
    }

    // 3. User account moderation gate: If user is banned or suspended, lock out normal app access
    val profile = userProfile
    if (profile != null && (profile.isPermanentlyBanned() || profile.isTemporarilySuspended())) {
        val isBanned = profile.isPermanentlyBanned()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Account Restricted",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = if (isBanned) "Account Banned" else "Account Suspended",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.calmTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isBanned) {
                        "Your account has been permanently banned for violating Anubhav community standards."
                    } else {
                        "Your account has been suspended until ${profile.suspendedUntil?.take(10)}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.calmTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (profile.statusReason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Reason: ${profile.statusReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.calmTextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                CalmButton(
                    text = "Log Out",
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            authRepository.signOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.6f)
                )
            }
        }
        return
    }

    val navController = rememberNavController()

    // Automatically navigate to ResetPassword when a recovery deep link is resolved
    LaunchedEffect(recoveryStatus) {
        when (recoveryStatus) {
            is RecoveryStatus.Ready, is RecoveryStatus.Error -> {
                if (navController.currentDestination?.route != Screen.ResetPassword.route) {
                    navController.navigate(Screen.ResetPassword.route)
                }
            }
            else -> Unit
        }
    }

    // Reactive session lifecycle gate: kick unauthenticated users back to Auth screen if session ends
    LaunchedEffect(sessionStatus) {
        if (sessionStatus is SessionStatus.NotAuthenticated) {
            val currentRoute = navController.currentDestination?.route
            val isUnauthenticatedRoute = currentRoute == Screen.Auth.route ||
                    currentRoute == Screen.ForgotPassword.route ||
                    currentRoute == Screen.ResetPassword.route ||
                    currentRoute?.startsWith("anubhav://") == true
            if (!isUnauthenticatedRoute && currentRoute != null) {
                userProfile = null
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val startDestination = remember {
        if (recoveryStatus is RecoveryStatus.Ready || recoveryStatus is RecoveryStatus.Error) {
            Screen.ResetPassword.route
        } else if (sessionStatus is SessionStatus.Authenticated) {
            Screen.Main.route
        } else {
            Screen.Auth.route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Authentication flow
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }

        // Forgot password flow
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Reset password flow with deep linking
        composable(
            route = Screen.ResetPassword.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "anubhav://reset-password.*" },
                navDeepLink { uriPattern = "anubhav://reset-password" },
                navDeepLink { uriPattern = "anubhav://auth/reset-password.*" },
                navDeepLink { uriPattern = "anubhav://auth/reset-password" }
            )
        ) {
            ResetPasswordScreen(
                onResetSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackToLogin = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRequestNewLink = {
                    navController.navigate(Screen.ForgotPassword.route) {
                        popUpTo(Screen.Auth.route) { inclusive = false }
                    }
                }
            )
        }

        // Main app scaffold hosting 3 bottom tabs: Posts | + | Profile
        composable(Screen.Main.route) {
            MainScaffold(
                onNavigateToUserProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNavigateToEditPost = { postId ->
                    navController.navigate(Screen.EditPost.createRoute(postId))
                },
                onNavigateToAdminDashboard = {
                    navController.navigate(Screen.AdminDashboard.route)
                },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Edit Post screen
        composable(
            route = Screen.EditPost.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId").orEmpty()
            EditPostScreen(
                postId = postId,
                onBack = { navController.popBackStack() },
                onPostUpdated = { navController.popBackStack() }
            )
        }

        // View another user's profile
        composable(
            route = Screen.UserProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            UserProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() }
            )
        }

        // Edit current user's profile
        composable(Screen.EditProfile.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Main.route) }.getOrNull()
            }
            val profileViewModel: ProfileViewModel = if (parentEntry != null) {
                viewModel(parentEntry)
            } else {
                viewModel()
            }
            EditProfileScreen(
                onBack = {
                    if (navController.currentDestination?.route == Screen.EditProfile.route) {
                        navController.popBackStack()
                    }
                },
                onProfileSaved = {
                    if (navController.currentDestination?.route == Screen.EditProfile.route) {
                        navController.popBackStack()
                    }
                },
                onAccountDeleted = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = profileViewModel
            )
        }

        // Admin Dashboard
        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReports = { navController.navigate(Screen.AdminReports.route) },
                onNavigateToUsers = { navController.navigate(Screen.AdminUsers.route) },
                onNavigateToPosts = { navController.navigate(Screen.AdminPosts.route) },
                onNavigateToHistory = { navController.navigate(Screen.AdminModerationHistory.route) },
                onReviewReport = { reportId ->
                    navController.navigate(Screen.AdminReportDetail.createRoute(reportId))
                }
            )
        }

        // Admin Reports
        composable(Screen.AdminReports.route) {
            AdminReportsScreen(
                onBack = { navController.popBackStack() },
                onReportClick = { reportId ->
                    navController.navigate(Screen.AdminReportDetail.createRoute(reportId))
                }
            )
        }

        // Admin Report Detail / Review
        composable(
            route = Screen.AdminReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            AdminReportDetailScreen(
                reportId = reportId,
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Users List
        composable(Screen.AdminUsers.route) {
            AdminUsersScreen(
                onBack = { navController.popBackStack() },
                onUserClick = { userId ->
                    navController.navigate(Screen.AdminUserDetail.createRoute(userId))
                }
            )
        }

        // Admin User Detail
        composable(
            route = Screen.AdminUserDetail.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId").orEmpty()
            AdminUserDetailScreen(
                userId = userId,
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Posts Management
        composable(Screen.AdminPosts.route) {
            AdminPostsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Moderation History / Audit Log
        composable(Screen.AdminModerationHistory.route) {
            AdminModerationHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainScaffold(
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToEditPost: (String) -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BottomTab.POSTS) }

    Scaffold(
        bottomBar = {
            CalmBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedTab,
                label = "BottomTabCrossfade"
            ) { tab ->
                when (tab) {
                    BottomTab.POSTS -> {
                        FeedScreen(
                            onNavigateToProfile = onNavigateToUserProfile,
                            onNavigateToEditPost = onNavigateToEditPost
                        )
                    }

                    BottomTab.CREATE -> {
                        CreatePostScreen(
                            onPostCreated = {
                                selectedTab = BottomTab.POSTS
                            }
                        )
                    }

                    BottomTab.PROFILE -> {
                        ProfileScreen(
                            onNavigateToEditProfile = onNavigateToEditProfile,
                            onNavigateToEditPost = onNavigateToEditPost,
                            onNavigateToAdminDashboard = onNavigateToAdminDashboard,
                            onLogout = onLogout,
                            onNavigateToUserProfile = onNavigateToUserProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalmBottomBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.calmBorderSubtle)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Posts Tab (Home)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(BottomTab.POSTS) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTab == BottomTab.POSTS) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Posts",
                    tint = if (selectedTab == BottomTab.POSTS) MaterialTheme.calmTextPrimary else MaterialTheme.calmTextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 2. Create Tab (Center + Action)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(BottomTab.CREATE) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (selectedTab == BottomTab.CREATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 3. Profile Tab (Person)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabSelected(BottomTab.PROFILE) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTab == BottomTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile",
                    tint = if (selectedTab == BottomTab.PROFILE) MaterialTheme.calmTextPrimary else MaterialTheme.calmTextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
