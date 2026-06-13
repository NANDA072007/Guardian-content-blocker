package com.guardian.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Privacy Policy",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "1. Local Data Storage\n" +
                       "Guardian is designed for absolute privacy. All your data, including your streak, your Trusted Person's contact information, and your app preferences, are stored strictly locally on your device. We do not use external databases or cloud servers to store your personal information.\n\n" +
                       "2. VPN Service\n" +
                       "The DNS/VPN wall operates entirely on your local device. It acts as a local filter to block adult domains. It does not transmit your browsing history to us or any third parties. It is a 'dummy' VPN that routes traffic locally for filtering purposes only.\n\n" +
                       "3. Accessibility Service\n" +
                       "The Accessibility Service is used exclusively to read the screen for specific forbidden keywords or URLs in your browser, and to prevent the unauthorized uninstallation of the app. We do not keylog, record, or transmit your screen data anywhere.\n\n" +
                       "4. No Tracking\n" +
                       "We do not track your location, use analytics software, or sell your data. Guardian is a tool for your recovery, not a tool for data collection.",
                color = Color.LightGray,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}
