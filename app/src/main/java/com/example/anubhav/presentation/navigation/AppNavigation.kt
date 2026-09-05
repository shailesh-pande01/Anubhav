package com.example.anubhav.presentation.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.presentation.auth.AuthScreen
import com.example.anubhav.presentation.auth.ForgotPasswordScreen
import com.example.anubhav.presentation.auth.ResetPasswordScreen
import com.example.anubhav.presentation.components.CalmLoadingIndicator
import com.example.anubhav.presentation.create.CreatePostScreen
import com.example.anubhav.presentation.feed.FeedScreen
import com.example.anubhav.presentation.profile.EditProfileScreen
import com.example.anubhav.presentation.profile.ProfileScreen
import com.example.anubhav.presentation.profile.ProfileViewModel
import com.example.anubhav.presentation.profile.UserProfileScreen
import com.example.anubhav.ui.theme.calmBorderSubtle
import com.example.anubhav.ui.theme.calmTextPrimary
import com.example.anubhav.ui.theme.calmTextTertiary
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AppNavigation(
    authRepository: AuthRepository = remember { AuthRepository() }
) {
    val sessionStatus by authRepository.sessionStatus.collectAsState()

    // While Supabase is restoring the session from local preferences, display calm loading state
    if (sessionStatus is SessionStatus.Initializing) {
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

    val navController = rememberNavController()
    val startDestination = remember {
        if (sessionStatus is SessionStatus.Authenticated) Screen.Main.route else Screen.Auth.route
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
                onBack = { navController.popBackStack() }
            )
        }

        // Reset password flow with deep linking
        composable(
            route = Screen.ResetPassword.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "anubhav://reset-password.*" },
                navDeepLink { uriPattern = "anubhav://reset-password" }
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
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
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

        // Edit current user's profile - scoped to Screen.Main.route to share ProfileViewModel instance
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
                viewModel = profileViewModel
            )
        }
    }
}

@Composable
fun MainScaffold(
    onNavigateToUserProfile: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
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
                            onNavigateToProfile = onNavigateToUserProfile
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
