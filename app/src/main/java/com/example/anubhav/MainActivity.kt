package com.example.anubhav

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.anubhav.core.supabase.SupabaseClientProvider
import com.example.anubhav.data.repository.AuthRepository
import com.example.anubhav.presentation.navigation.AppNavigation
import com.example.anubhav.ui.theme.AnubhavTheme
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)
        enableEdgeToEdge()
        setContent {
            AnubhavTheme {
                AppNavigation(authRepository = authRepository)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            val handled = authRepository.handleRecoveryIntent(intent.data)
            if (!handled) {
                // Safe fallback for other potential deep links (e.g. OAuth)
                runCatching {
                    SupabaseClientProvider.client.handleDeeplinks(intent)
                }
            }
        }
    }
}