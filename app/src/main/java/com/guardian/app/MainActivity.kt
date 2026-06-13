package com.guardian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import android.os.Build
import com.guardian.app.data.core.GuardianCoreService
import com.guardian.app.ui.navigation.AppNavigation
import com.guardian.app.ui.theme.GuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuardianTheme {
                AppNavigation()
            }
        }
        
        startGuardianCore()
    }

    private fun startGuardianCore() {
        val serviceIntent = Intent(this, GuardianCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
