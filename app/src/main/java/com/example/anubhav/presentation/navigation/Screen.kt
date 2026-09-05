package com.example.anubhav.presentation.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object ForgotPassword : Screen("forgot_password")
    data object ResetPassword : Screen("reset_password")
    data object Main : Screen("main")
    data object EditProfile : Screen("edit_profile")
    data object UserProfile : Screen("user_profile/{userId}") {
        fun createRoute(userId: String): String = "user_profile/$userId"
    }
}

enum class BottomTab {
    POSTS,
    CREATE,
    PROFILE
}
