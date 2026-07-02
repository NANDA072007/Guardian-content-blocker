package com.guardian.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.guardian.app.ui.theme.NeumorphismCard
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guardian.app.core.rememberSecurityManager
import com.guardian.app.core.OemCompatibilityManager
import com.guardian.app.broadcast.GuardianDeviceAdminReceiver
import com.guardian.app.ui.viewmodel.OnboardingViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val securityManager = rememberSecurityManager()
    var currentStep by remember { mutableStateOf(0) }
    var guardianCode by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A1A), Color(0xFF121212))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(500)
                ) + fadeIn(animationSpec = tween(500)) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(500)
                ) + fadeOut(animationSpec = tween(500))
            },
            label = "OnboardingTransition"
        ) { step ->
            when (step) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> BatteryPermissionStep(resumeTrigger = resumeTrigger, onNext = { currentStep = 2 })
                2 -> OverlayPermissionStep(resumeTrigger = resumeTrigger, onNext = { currentStep = 3 })
                3 -> AccessibilityPermissionStep(resumeTrigger = resumeTrigger, onNext = { currentStep = 4 })
                4 -> VpnPermissionStep(resumeTrigger = resumeTrigger, viewModel = viewModel, onNext = { currentStep = 5 })
                5 -> DeviceAdminPermissionStep(resumeTrigger = resumeTrigger, onNext = { currentStep = 6 })
                6 -> {
                    if (guardianCode.isEmpty()) {
                        guardianCode = viewModel.generateGuardianCode()
                    }
                    GuardianCodeStep(
                        code = guardianCode,
                        onComplete = {
                            viewModel.completeSetup()
                            onFinish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NeumorphismCardWrapper(content: @Composable () -> Unit) {
    NeumorphismCard(
        modifier = Modifier.padding(32.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        content()
    }
}

@Composable
fun StepIndicator(current: Int, total: Int = 6) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        for (i in 0 until total) {
            Box(
                modifier = Modifier
                    .size(if (i == current) 22.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (i <= current) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.15f)
                    )
            )
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeumorphismCardWrapper {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome to", color = Color.LightGray, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("GUARDIAN", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "This is not a generic blocker. Guardian relies on aggressive system-level architecture and human accountability. It cannot be easily uninstalled.",
                    color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("I Understand", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionStep(
    stepNumber: Int,
    stepTitle: String,
    description: String,
    instructions: @Composable (() -> Unit)? = null,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(isGranted) {
        if (isGranted) {
            delay(600)
            onNext()
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NeumorphismCardWrapper {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepIndicator(current = stepNumber - 1)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Step $stepNumber of 6", color = Color(0xFF4FC3F7), fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stepTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(description, color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                if (instructions != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    instructions()
                }
                Spacer(modifier = Modifier.height(32.dp))
                if (isGranted) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF4CAF50),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Granted — continuing...", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Button(
                        onClick = onGrant,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stepTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var isExempted by remember { mutableStateOf(OemCompatibilityManager.isBatteryOptimizationExempted(context)) }

    LaunchedEffect(resumeTrigger) {
        isExempted = OemCompatibilityManager.isBatteryOptimizationExempted(context)
    }

    PermissionStep(
        stepNumber = 1,
        stepTitle = "Power Protection",
        description = "Your phone's power settings can silently disable Guardian. We need battery optimization exemption to keep protection active at all times.",
        instructions = {
            Text(
                OemCompatibilityManager.getManualInstructions(),
                color = Color(0xFF4FC3F7),
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(16.dp)
            )
        },
        isGranted = isExempted,
        onGrant = {
            OemCompatibilityManager.requestSystemExemption(context)
            OemCompatibilityManager.getDeepLinkIntent()?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(it) } catch (_: Exception) {}
            }
        },
        onNext = onNext
    )
}

@Composable
fun OverlayPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LaunchedEffect(resumeTrigger) { isGranted = Settings.canDrawOverlays(context) }

    PermissionStep(
        stepNumber = 2,
        stepTitle = "Overlay Access",
        description = "Guardian needs to display the DANGER ZONE screen over other apps when you attempt to access blocked content. This is the visual barrier that stops you.",
        isGranted = isGranted,
        onGrant = {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        },
        onNext = onNext
    )
}

@Composable
fun AccessibilityPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current

    fun isAccessibilityServiceRunning(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val targetServiceName = "com.guardian.app.walls.wall2.AccessibilitySentry"
        return enabledServices.any { it.resolveInfo.serviceInfo.name == targetServiceName }
    }

    var isGranted by remember { mutableStateOf(isAccessibilityServiceRunning()) }

    LaunchedEffect(resumeTrigger) { isGranted = isAccessibilityServiceRunning() }

    PermissionStep(
        stepNumber = 3,
        stepTitle = "Accessibility Service",
        description = "Guardian uses Accessibility to detect adult content you type in any app — including incognito browsers, private tabs, and chat apps. It also locks Guardian's own settings from being turned off.",
        instructions = {
            Column {
                Text(
                    "Settings → Accessibility → Downloaded apps → Guardian → Toggle ON",
                    color = Color(0xFF4FC3F7),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "If you don't see Guardian, tap the three-dot menu and select 'Show system services'.",
                    color = Color(0xFF9FA8DA),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        isGranted = isGranted,
        onGrant = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        },
        onNext = onNext
    )
}

@Composable
fun VpnPermissionStep(resumeTrigger: Int, viewModel: OnboardingViewModel, onNext: () -> Unit) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(viewModel.isWall1Enabled()) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
            isGranted = true
        }
    }

    LaunchedEffect(resumeTrigger) { isGranted = viewModel.isWall1Enabled() }

    PermissionStep(
        stepNumber = 4,
        stepTitle = "DNS Shield (VPN)",
        description = "Guardian uses a local VPN to intercept DNS queries and block millions of adult domains at the network level. This works in every browser and every app that uses the system DNS.",
        isGranted = isGranted,
        onGrant = {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnLauncher.launch(vpnIntent)
            } else {
                viewModel.startVpn()
                isGranted = true
            }
        },
        onNext = onNext
    )
}

@Composable
fun DeviceAdminPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    var isGranted by remember { mutableStateOf(dpm.isAdminActive(componentName)) }

    LaunchedEffect(resumeTrigger) { isGranted = dpm.isAdminActive(componentName) }

    PermissionStep(
        stepNumber = 5,
        stepTitle = "Device Administrator",
        description = "Once active, Guardian cannot be uninstalled or force-stopped without your Guardian Code and a 24-hour cooloff. This is the lock that makes the app unbypassable.",
        isGranted = isGranted,
        onGrant = {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian requires Device Admin to prevent uninstallation.")
            }
            context.startActivity(intent)
        },
        onNext = onNext
    )
}

@Composable
fun GuardianCodeStep(
    code: String,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeumorphismCardWrapper {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepIndicator(current = 6)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Step 6 of 6", color = Color(0xFF4FC3F7), fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your Guardian Code", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This code is required to pause protection or start the 24-hour uninstall cooloff. Save it somewhere safe. You cannot recover it if lost.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A237E).copy(alpha = 0.3f))
                        .border(1.dp, Color(0xFF5C6BC0).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = code,
                        color = Color(0xFF7986CB),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Guardian Code", code))
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Copy Code", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("I've Saved My Code — Start", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
