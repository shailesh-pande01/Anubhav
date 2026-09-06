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
    data object EditPost : Screen("edit_post/{postId}") {
        fun createRoute(postId: String): String = "edit_post/$postId"
    }

    // Admin Navigation
    data object AdminDashboard : Screen("admin_dashboard")
    data object AdminReports : Screen("admin_reports")
    data object AdminReportDetail : Screen("admin_report_detail/{reportId}") {
        fun createRoute(reportId: String): String = "admin_report_detail/$reportId"
    }
    data object AdminUsers : Screen("admin_users")
    data object AdminUserDetail : Screen("admin_user_detail/{userId}") {
        fun createRoute(userId: String): String = "admin_user_detail/$userId"
    }
    data object AdminPosts : Screen("admin_posts")
    data object AdminModerationHistory : Screen("admin_moderation_history")
}

enum class BottomTab {
    POSTS,
    CREATE,
    PROFILE
}
