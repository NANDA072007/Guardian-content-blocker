package com.guardian.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.guardian.app.core.rememberSecurityManager
import com.guardian.app.core.rememberProtectionOrchestrator
import com.guardian.app.ui.screens.DashboardScreen
import com.guardian.app.ui.screens.OnboardingScreen
import com.guardian.app.ui.screens.SplashScreen
import com.guardian.app.ui.screens.SettingsScreen
import com.guardian.app.ui.screens.TrustedPersonScreen
import com.guardian.app.ui.screens.EmergencyScreen
import com.guardian.app.ui.screens.PrivacyPolicyScreen
import com.guardian.app.ui.customer.CustomerServiceScreen
import com.guardian.app.ui.screens.DiagnosticsScreen

@Composable
fun AppNavigation() {
    val securityManager = rememberSecurityManager()
    
    // Determine start screen based on setup status
    val startDest = if (securityManager.isSetupComplete()) "dashboard" else "onboarding"
    var currentScreen by rememberSaveable { mutableStateOf("splash") }

    when (currentScreen) {
        "splash" -> SplashScreen(onNavigateToDashboard = { currentScreen = startDest })
        "onboarding" -> OnboardingScreen(onFinish = { currentScreen = "dashboard" })
        "dashboard" -> DashboardScreen(
            onNavigateToSettings = { currentScreen = "settings" },
            onNavigateToTrustedPerson = { currentScreen = "trusted_person" },
            onNavigateToEmergency = { currentScreen = "emergency" },
            onNavigateToCustomerService = { currentScreen = "customer_service" }
        )
        "settings" -> SettingsScreen(
            onNavigateBack = { currentScreen = "dashboard" },
            onNavigateToTrustedPerson = { currentScreen = "trusted_person" },
            onNavigateToPrivacyPolicy = { currentScreen = "privacy_policy" },
            onNavigateToDiagnostics = { currentScreen = "diagnostics" }
        )
        "diagnostics" -> {
            val orchestrator = rememberProtectionOrchestrator()
            DiagnosticsScreen(
                onNavigateBack = { currentScreen = "settings" },
                orchestrator = orchestrator
            )
        }
        "trusted_person" -> TrustedPersonScreen(
            onNavigateBack = { currentScreen = "dashboard" }
        )
        "emergency" -> EmergencyScreen(
            onNavigateBack = { currentScreen = "dashboard" }
        )
        "privacy_policy" -> PrivacyPolicyScreen(
            onNavigateBack = { currentScreen = "settings" }
        )
        "customer_service" -> CustomerServiceScreen(
            onNavigateBack = { currentScreen = "dashboard" }
        )
    }
}
