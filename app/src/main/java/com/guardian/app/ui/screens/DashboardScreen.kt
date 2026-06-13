package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import android.content.Intent
import android.app.Activity
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.guardian.app.data.SecurityManager
import com.guardian.app.data.db.DatabaseHelper
import com.guardian.app.data.vpn.DnsVpnService
import com.guardian.app.data.policy.GuardianDeviceAdminReceiver
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTrustedPerson: () -> Unit,
    onNavigateToEmergency: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    
    var isWall1Enabled by remember { mutableStateOf(securityManager.isWall1Enabled()) }

    var isWall2Enabled by remember { mutableStateOf(securityManager.isWall2Enabled()) }
    var isWall4Enabled by remember { mutableStateOf(dpm.isAdminActive(componentName)) }
    var showOemDialog by remember { mutableStateOf(false) }
    var isOemAcknowledged by remember { mutableStateOf(securityManager.isOemOptimizationAcknowledged()) }
    var currentStreak by remember { mutableStateOf(0) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isWall1Enabled = securityManager.isWall1Enabled()
                isWall2Enabled = securityManager.isWall2Enabled()
                isWall4Enabled = dpm.isAdminActive(componentName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Check if the 24-hour cooloff timer has FINISHED, meaning the app is now unlocked
    val currentTime = System.currentTimeMillis()
    val unlockTime = securityManager.getUninstallUnlockTime()
    val isUnlockAvailable = (unlockTime > 0L) && (currentTime > unlockTime)

    // Helper to evaluate unlock status in real-time
    val checkIsUnlocked = {
        val uTime = securityManager.getUninstallUnlockTime()
        (uTime > 0L) && (System.currentTimeMillis() > uTime)
    }

    // Helper to handle locked toggles
    val handleLockedToggle = {
        Toast.makeText(context, "Access Denied: You must unlock via Trusted Person.", Toast.LENGTH_SHORT).show()
        onNavigateToTrustedPerson()
    }

    // Helper to re-lock the app when a wall is turned ON
    val relockApp = {
        securityManager.setUninstallUnlockTime(0L)
    }

    // Load streak on resume/composition
    LaunchedEffect(Unit) {
        currentStreak = dbHelper.getDaysCleanStreak()
    }
    
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, DnsVpnService::class.java)
            intent.action = DnsVpnService.ACTION_START_VPN
            context.startService(intent)
            isWall1Enabled = true
            securityManager.setWall1Enabled(true)
            relockApp()
        } else {
            isWall1Enabled = false
            securityManager.setWall1Enabled(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian Dashboard", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // --- MASTER SHIELD UI ---
            val isFullyArmed = isWall1Enabled && isWall2Enabled && isWall4Enabled
            
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val redAlpha by infiniteTransition.animateFloat(
                initialValue = 0.05f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            val shieldColor by animateColorAsState(
                targetValue = if (isFullyArmed) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFD32F2F).copy(alpha = redAlpha),
                animationSpec = tween(500),
                label = "shieldColor"
            )
            val shieldBorderColor by animateColorAsState(
                targetValue = if (isFullyArmed) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFEF5350).copy(alpha = 0.8f),
                animationSpec = tween(500),
                label = "shieldBorderColor"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(shieldColor)
                    .border(1.dp, shieldBorderColor, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isFullyArmed,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "shieldText"
                ) { armed ->
                    if (armed) {
                        Text("System Armed: Maximum Protection", color = Color(0xFF81C784), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Defenses Compromised", color = Color(0xFFEF5350), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Header Stats Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Current Streak", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Day $currentStreak", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Protection Toggles
            Text(
                text = "Protections",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            ProtectionRow(
                title = "Wall 1 (DNS/VPN)",
                subtitle = "Blocks adult domains at network level",
                isChecked = isWall1Enabled,
                isLocked = isWall1Enabled && !isUnlockAvailable,
                onCheckedChange = { checked -> 
                    if (checked) {
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnLauncher.launch(vpnIntent)
                        } else {
                            val intent = Intent(context, DnsVpnService::class.java)
                            intent.action = DnsVpnService.ACTION_START_VPN
                            context.startService(intent)
                            isWall1Enabled = true
                            securityManager.setWall1Enabled(true)
                            relockApp()
                        }
                    } else {
                        if (!checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            val intent = Intent(context, DnsVpnService::class.java)
                            intent.action = DnsVpnService.ACTION_STOP_VPN
                            context.startService(intent)
                            isWall1Enabled = false
                            securityManager.setWall1Enabled(false)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtectionRow(
                title = "Wall 2 (Accessibility)",
                subtitle = "Monitors browser URL entry",
                isChecked = isWall2Enabled,
                isLocked = isWall2Enabled && !isUnlockAvailable,
                onCheckedChange = { checked -> 
                    if (checked) {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                        isWall2Enabled = true
                        securityManager.setWall2Enabled(true)
                        relockApp()
                    } else {
                        if (!checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            isWall2Enabled = false
                            securityManager.setWall2Enabled(false)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtectionRow(
                title = "Wall 4 (Device Admin)",
                subtitle = "Prevents app uninstallation",
                isChecked = isWall4Enabled,
                isLocked = isWall4Enabled && !isUnlockAvailable,
                onCheckedChange = { checked ->
                    if (checked) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian requires Device Admin to prevent uninstallation.")
                        }
                        context.startActivity(intent)
                        relockApp()
                    } else {
                        if (!checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            // If unlock is available, they can remove it. But standard Android flow requires
                            // removing device admin from settings anyway. We navigate them to settings or Trusted Person.
                            onNavigateToTrustedPerson() 
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            
            // Request battery optimization exclusion
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                Button(
                    onClick = {
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Disable Battery Optimization", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showOemDialog) {
                AlertDialog(
                    onDismissRequest = { showOemDialog = false },
                    title = { Text("AutoStart Enabled?", color = Color.White) },
                    text = { Text("Did you successfully turn ON AutoStart and Lock the app in your Recents menu?", color = Color.Gray) },
                    confirmButton = {
                        TextButton(onClick = {
                            securityManager.setOemOptimizationAcknowledged(true)
                            isOemAcknowledged = true
                            showOemDialog = false
                        }) {
                            Text("Yes, it's ON", color = Color(0xFF4CAF50))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOemDialog = false }) {
                            Text("Not yet", color = Color(0xFFD32F2F))
                        }
                    },
                    containerColor = Color(0xFF1E1E1E)
                )
            }

            // Check for OEM specific AutoStart optimizations
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            val oemIntent = getOemAutoStartIntent(manufacturer)
            
            if (oemIntent != null && !isOemAcknowledged) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(oemIntent)
                            showOemDialog = true
                        } catch (e: Exception) {
                            // Fallback to app info
                            val fallbackIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            fallbackIntent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(fallbackIntent)
                            showOemDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), // Red for urgency
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val displayName = manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    Text("Enable $displayName AutoStart (Required)", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Overlay permission request
            if (!Settings.canDrawOverlays(context)) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)), // Warning Orange
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Grant Required Overlay Permission", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Emergency Mode Button
            Button(
                onClick = onNavigateToEmergency,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("!", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EMERGENCY MODE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProtectionRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    isLocked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val lockColor = if (isLocked) Color(0xFFD32F2F) else Color(0xFF4CAF50) // Red if locked (preventing off), Green if standard on
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable { 
                // Only allow click if we aren't locked into ON, or if we want to trigger the locked response
                onCheckedChange(!isChecked) 
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = null, // The Row's clickable handles all interactions
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = lockColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF333333)
            )
        )
    }
}

private fun getOemAutoStartIntent(manufacturer: String): Intent? {
    return when (manufacturer) {
        "xiaomi", "redmi", "poco" -> Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        }
        "oppo", "realme", "oneplus" -> Intent().apply {
            component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        }
        "vivo", "iqoo" -> Intent().apply {
            component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        }
        "huawei", "honor" -> Intent().apply {
            component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        }
        "samsung" -> Intent().apply {
            component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
        }
        else -> null
    }
}
