package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import android.content.Intent
import android.app.Activity
import android.provider.Settings as AndroidSettings
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.guardian.app.core.rememberSecurityManager
import com.guardian.app.domain.PauseProtectionUseCase
import com.guardian.app.walls.wall1.DnsVpnService
import com.guardian.app.broadcast.GuardianDeviceAdminReceiver
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guardian.app.core.rememberRestrictedSettingsHelper
import com.guardian.app.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTrustedPerson: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToCustomerService: () -> Unit,
    viewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    
    var isWall1Enabled by remember { mutableStateOf(viewModel.isWall1Enabled()) }

    var isWall2Enabled by remember { mutableStateOf(viewModel.isWall2Enabled()) }
    var isWall4Enabled by remember { mutableStateOf(dpm.isAdminActive(componentName)) }
    var showOemDialog by remember { mutableStateOf(false) }
    var isAccessibilityRunning by remember { mutableStateOf(isAccessibilityServiceRunning(context)) }
    var isOemAcknowledged by remember { mutableStateOf(viewModel.isOemOptimizationAcknowledged()) }
    val restrictedHelper = rememberRestrictedSettingsHelper()
    var isRestrictedBlocking by remember { mutableStateOf(restrictedHelper.isRestrictedSettingsBlocking()) }
    
    val currentStreak by viewModel.currentStreak.collectAsState()
    
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    var isIgnoringBattery by remember { mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isWall1Enabled = viewModel.isWall1Enabled()
                isWall2Enabled = viewModel.isWall2Enabled()
                isWall4Enabled = dpm.isAdminActive(componentName)
                isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                isAccessibilityRunning = isAccessibilityServiceRunning(context)
                isRestrictedBlocking = restrictedHelper.isRestrictedSettingsBlocking()
                
                // Fetch the streak each time the dashboard comes to the foreground
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Check if the 24-hour cooloff timer has FINISHED, meaning the app is now unlocked
    val currentTime = System.currentTimeMillis()
    val unlockTime = viewModel.getUninstallUnlockTime()
    val isUnlockAvailable = (unlockTime > 0L) && (currentTime > unlockTime)

    // Helper to handle locked toggles
    val handleLockedToggle = {
        Toast.makeText(context, "Access Denied: Enter your Guardian Code to unlock.", Toast.LENGTH_SHORT).show()
        onNavigateToTrustedPerson()
    }
    
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
            isWall1Enabled = true
        } else {
            viewModel.setWall1Enabled(false)
            isWall1Enabled = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "GUARDIAN", 
                        color = MaterialTheme.colorScheme.onBackground, 
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = 4.sp
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCustomerService,
                icon = { Icon(Icons.Default.SupportAgent, contentDescription = "Customer Service") },
                text = { Text("Support", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
                targetValue = if (isFullyArmed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.tertiary.copy(alpha = redAlpha),
                animationSpec = tween(500),
                label = "shieldColor"
            )
            val shieldBorderColor by animateColorAsState(
                targetValue = if (isFullyArmed) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                animationSpec = tween(500),
                label = "shieldBorderColor"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(shieldColor)
                    .border(1.dp, shieldBorderColor, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isFullyArmed,
                    transitionSpec = {
                        scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "shieldText"
                ) { armed ->
                    if (armed) {
                        Text("SYSTEM ARMED", color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    } else {
                        Text("DEFENSES COMPROMISED", color = MaterialTheme.colorScheme.tertiary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Gamified Header Stats Area
            val milestoneTitle = when {
                currentStreak >= 90 -> "GODLIKE"
                currentStreak >= 30 -> "UNSTOPPABLE"
                currentStreak >= 14 -> "ON FIRE"
                currentStreak >= 7 -> "SOLID"
                currentStreak >= 3 -> "MOMENTUM"
                else -> "STREAK"
            }
            
            val milestoneColor = when {
                currentStreak >= 90 -> Color(0xFFE040FB) // Neon Purple
                currentStreak >= 30 -> Color(0xFF00E676) // Neon Green
                currentStreak >= 14 -> Color(0xFFFF3D00) // Fire Orange
                currentStreak >= 7 -> Color(0xFFFFC107)  // Amber
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                milestoneColor.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    )
                    .border(2.dp, milestoneColor.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Background Track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    strokeWidth = 14.dp
                )
                
                // Animated Progress Ring
                val targetProgress = (currentStreak % 30f) / 30f
                val animatedProgress by animateFloatAsState(
                    targetValue = if (targetProgress == 0f && currentStreak > 0) 1f else targetProgress.coerceAtLeast(0.02f),
                    animationSpec = tween(1500, easing = FastOutSlowInEasing),
                    label = "streakRing"
                )
                
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    color = milestoneColor,
                    strokeWidth = 14.dp
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = milestoneTitle, 
                        color = milestoneColor, 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$currentStreak", 
                        color = MaterialTheme.colorScheme.onBackground, 
                        fontSize = 56.sp, 
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "DAYS CLEAN", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Protection Toggles
            Text(
                text = "Protections",
                color = MaterialTheme.colorScheme.onBackground,
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
                            viewModel.startVpn()
                            isWall1Enabled = true
                        }
                    } else {
                        if (!viewModel.checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            viewModel.stopVpn()
                            isWall1Enabled = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProtectionRow(
                title = "Wall 2 (Accessibility)",
                subtitle = "Detects typed adult keywords & blocks settings changes",
                isChecked = isWall2Enabled,
                isLocked = isWall2Enabled && !isUnlockAvailable,
                statusActive = if (isWall2Enabled) isAccessibilityRunning else null,
                onCheckedChange = { checked -> 
                    if (checked) {
                        val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                        viewModel.setWall2Enabled(true)
                        viewModel.relockApp()
                        isWall2Enabled = true
                    } else {
                        if (!viewModel.checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            viewModel.setWall2Enabled(false)
                            isWall2Enabled = false
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
                        viewModel.relockApp()
                    } else {
                        if (!viewModel.checkIsUnlocked()) {
                            handleLockedToggle()
                        } else {
                            // If unlock is available, they can remove it. But standard Android flow requires
                            // removing device admin from settings anyway. We navigate them to settings or Trusted Person.
                            onNavigateToTrustedPerson() 
                        }
                    }
                }
            )


            Spacer(modifier = Modifier.height(24.dp))

            // --- PAUSE PROTECTION (Master Key gated) ---
            val securityManager = rememberSecurityManager()
            val pauseUseCase = remember {
                val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.guardian.app.core.SecurityManagerEntryPoint::class.java
                )
                ep.pauseProtectionUseCase()
            }
            var showPinDialog by remember { mutableStateOf(false) }
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf("") }
            var isPaused by remember { mutableStateOf(pauseUseCase.isPaused()) }
            var remainingPauseMs by remember { mutableStateOf(pauseUseCase.getRemainingMs()) }

            // Update pause state every second
            LaunchedEffect(isPaused) {
                while (isPaused) {
                    kotlinx.coroutines.delay(1000L)
                    remainingPauseMs = pauseUseCase.getRemainingMs()
                    if (remainingPauseMs <= 0) {
                        isPaused = false
                    }
                }
            }

            if (isPaused) {
                val minutes = (remainingPauseMs / 60000).toInt()
                val seconds = ((remainingPauseMs % 60000) / 1000).toInt()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF3E0))
                        .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "Protection Paused",
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Settings access unlocked for ${minutes}m ${seconds}s",
                            color = Color(0xFFBF360C),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = { showPinDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Pause Protection (5 min)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Guardian Code Dialog
            if (showPinDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showPinDialog = false
                        pinInput = ""
                        pinError = ""
                    },
                    title = { Text("Enter Guardian Code", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("To pause protection for 5 minutes, enter the Guardian Code shown during setup.", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 9) pinInput = it.uppercase() },
                                label = { Text("Guardian Code (e.g. ABCD-1234)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                                )
                            )
                            if (pinError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(pinError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (!securityManager.verifyMasterKey(pinInput)) {
                                pinError = "Incorrect Guardian Code."
                            } else {
                                pauseUseCase.execute()
                                isPaused = true
                                remainingPauseMs = PauseProtectionUseCase.PAUSE_DURATION_MS
                                showPinDialog = false
                                pinInput = ""
                                pinError = ""
                                Toast.makeText(context, "Protection paused for 5 minutes.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Confirm", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPinDialog = false
                            pinInput = ""
                            pinError = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showOemDialog) {
                AlertDialog(
                    onDismissRequest = { showOemDialog = false },
                    title = { Text("AutoStart Enabled?", color = MaterialTheme.colorScheme.onSurface) },
                    text = { Text("Did you successfully turn ON AutoStart and Lock the app in your Recents menu?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setOemOptimizationAcknowledged(true)
                            isOemAcknowledged = true
                            showOemDialog = false
                        }) {
                            Text("Yes, it's ON", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOemDialog = false }) {
                            Text("Not yet", color = MaterialTheme.colorScheme.tertiary)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), // Red for urgency
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val displayName = manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    Text("Enable $displayName AutoStart (Required)", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Bug 6 fix: Overlay permission request uses aliased AndroidSettings
            if (!AndroidSettings.canDrawOverlays(context)) {
                Button(
                    onClick = {
                        val intent = Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
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

            if (isRestrictedBlocking) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A237E).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF5C6BC0).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "⚠ Restricted Settings Detected",
                            color = Color(0xFF7986CB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Android 13+ is blocking Guardian's accessibility service because it was installed outside the Play Store.",
                            color = Color(0xFF9FA8DA),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                context.startActivity(restrictedHelper.openRestrictedSettings())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3949AB)
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Allow Restricted Settings", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Settings → Apps → Guardian → ⋮ → Allow restricted settings",
                            color = Color(0xFF7986CB),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Emergency Mode Button
            Button(
                onClick = onNavigateToEmergency,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
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
    statusActive: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val lockColor = if (isLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val shadowDark = if (isDark) Color(0xFF060B17) else Color(0xFFCBD5E1)
    val shadowLight = if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(20.dp)
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 8.dp, y = 8.dp)
                .shadow(10.dp, shape, clip = false, ambientColor = shadowDark, spotColor = shadowDark)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = (-3).dp, y = (-3).dp)
                .shadow(4.dp, shape, clip = false, ambientColor = shadowLight, spotColor = shadowLight)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .drawBehind {
                    val r = 20.dp.toPx()
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.3f),
                        topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                        size = size.copy(width = size.width - 1.dp.toPx(), height = size.height - 1.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                    )
                }
                .background(surfaceColor)
                .clickable { 
                    onCheckedChange(!isChecked) 
                }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusActive != null) {
                    val dotColor = if (statusActive) Color(0xFF4CAF50) else Color(0xFFEF4444)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = null, // The Row's clickable handles all interactions
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = lockColor,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface
            )
        )
    }
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

private fun isAccessibilityServiceRunning(context: android.content.Context): Boolean {
    val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
    val targetServiceName = "com.guardian.app.walls.wall2.AccessibilitySentry"
    return enabledServices.any { it.resolveInfo.serviceInfo.name == targetServiceName }
}
