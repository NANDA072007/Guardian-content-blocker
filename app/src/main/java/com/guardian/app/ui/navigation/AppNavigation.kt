package com.guardian.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.guardian.app.data.SecurityManager
import com.guardian.app.ui.screens.DashboardScreen
import com.guardian.app.ui.screens.OnboardingScreen
import com.guardian.app.ui.screens.SplashScreen
import com.guardian.app.ui.screens.SettingsScreen
import com.guardian.app.ui.screens.TrustedPersonScreen
import com.guardian.app.ui.screens.EmergencyScreen
import com.guardian.app.ui.screens.PrivacyPolicyScreen

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    
    // Determine start screen based on setup status
    val startDest = if (securityManager.isSetupComplete()) "dashboard" else "onboarding"
    var currentScreen by rememberSaveable { mutableStateOf("splash") }

    when (currentScreen) {
        "splash" -> SplashScreen(onNavigateToDashboard = { currentScreen = startDest })
        "onboarding" -> OnboardingScreen(onFinish = { currentScreen = "dashboard" })
        "dashboard" -> DashboardScreen(
            onNavigateToSettings = { currentScreen = "settings" },
            onNavigateToTrustedPerson = { currentScreen = "trusted_person" },
            onNavigateToEmergency = { currentScreen = "emergency" }
        )
        "settings" -> SettingsScreen(
            onNavigateBack = { currentScreen = "dashboard" },
            onNavigateToTrustedPerson = { currentScreen = "trusted_person" },
            onNavigateToPrivacyPolicy = { currentScreen = "privacy_policy" }
        )
        "trusted_person" -> TrustedPersonScreen(
            onNavigateBack = { currentScreen = "dashboard" }
        )
        "emergency" -> EmergencyScreen(
            onNavigateBack = { currentScreen = "dashboard" }
        )
        "privacy_policy" -> PrivacyPolicyScreen(
            onNavigateBack = { currentScreen = "settings" }
        )
    }
}
