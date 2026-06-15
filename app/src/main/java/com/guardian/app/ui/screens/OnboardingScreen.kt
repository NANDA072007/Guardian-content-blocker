package com.guardian.app.ui.screens

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.guardian.app.data.SecurityManager
import com.guardian.app.data.core.OemCompatibilityManager
import com.guardian.app.data.policy.GuardianDeviceAdminReceiver
import com.guardian.app.data.vpn.DnsVpnService
import com.guardian.app.util.CryptoUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    var currentStep by remember { mutableStateOf(0) }
    var phoneNumber by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    // State triggers to re-evaluate permissions on resume
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

    fun isValidPhone(number: String): Boolean = Patterns.PHONE.matcher(number).matches()

    // Dynamic animated background
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
                3 -> AccessibilityPermissionStep(resumeTrigger = resumeTrigger, securityManager = securityManager, onNext = { currentStep = 4 })
                4 -> VpnPermissionStep(resumeTrigger = resumeTrigger, securityManager = securityManager, onNext = { currentStep = 5 })
                5 -> DeviceAdminPermissionStep(resumeTrigger = resumeTrigger, onNext = { currentStep = 6 })
                6 -> PartnerSetupStep(
                    phoneNumber = phoneNumber,
                    onPhoneChange = { phoneNumber = it },
                    onComplete = lambda@{
                        if (!isValidPhone(phoneNumber)) return@lambda
                        val generatedCode = CryptoUtils.generateUnlockCode()
                        securityManager.setTrustedPersonContact(phoneNumber)
                        securityManager.setMasterKeyHash(CryptoUtils.sha256(generatedCode.toByteArray()))
                        securityManager.setSetupComplete(true)
                        
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:$phoneNumber")
                            putExtra("sms_body", "I am using Guardian for my recovery. You are my Trusted Person. My emergency unlock code is: $generatedCode. Do not give this back to me unless we talk first.")
                        }
                        context.startActivity(smsIntent)
                        onFinish()
                    }
                )
            }
        }
    }
}

@Composable
fun GlassmorphismCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(32.dp)
    ) {
        content()
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassmorphismCard {
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
fun BatteryPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var isExempted by remember { mutableStateOf(OemCompatibilityManager.isBatteryOptimizationExempted(context)) }
    
    LaunchedEffect(resumeTrigger) {
        isExempted = OemCompatibilityManager.isBatteryOptimizationExempted(context)
    }
    LaunchedEffect(Unit) {
        while (!isExempted) {
            delay(1000)
            isExempted = OemCompatibilityManager.isBatteryOptimizationExempted(context)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 1: Power", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your phone's power settings can silently disable Guardian. We need one permission to prevent this.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text(OemCompatibilityManager.getManualInstructions(), color = Color(0xFF4FC3F7), fontSize = 12.sp, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(16.dp))
                Spacer(modifier = Modifier.height(32.dp))
                if (isExempted) {
                    Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Protected! Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            OemCompatibilityManager.requestSystemExemption(context)
                            OemCompatibilityManager.getDeepLinkIntent()?.let { 
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(it) } catch (e: Exception) {}
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Protect Guardian's Power", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onNext) { Text("Skip - I'll do this later", color = Color.Gray, fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    LaunchedEffect(resumeTrigger) { isGranted = Settings.canDrawOverlays(context) }
    LaunchedEffect(Unit) {
        while (!isGranted) {
            delay(1000)
            isGranted = Settings.canDrawOverlays(context)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 2: Overlay", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Guardian needs to display the DANGER ZONE screen over other apps when you attempt to access blocked content.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(32.dp))
                if (isGranted) {
                    Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Granted! Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Grant Overlay Access", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityPermissionStep(resumeTrigger: Int, securityManager: SecurityManager, onNext: () -> Unit) {
    val context = LocalContext.current

    // Bug 3 fix: Check real system state via AccessibilityManager, not a SharedPreferences flag
    fun isAccessibilityServiceRunning(): Boolean {
        val am = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val targetServiceName = "com.guardian.app.data.accessibility.AccessibilitySentry"
        return enabledServices.any { it.resolveInfo.serviceInfo.name == targetServiceName }
    }

    var isGranted by remember { mutableStateOf(isAccessibilityServiceRunning()) }
    
    LaunchedEffect(resumeTrigger) { isGranted = isAccessibilityServiceRunning() }
    LaunchedEffect(Unit) {
        while (!isGranted) {
            delay(1000)
            isGranted = isAccessibilityServiceRunning()
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 3: Scanner", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Guardian uses Accessibility Services to scan browser URLs and screen content for adult keywords. Without this, browsers cannot be filtered.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Locate 'Guardian' under Downloaded Apps and turn it ON.", color = Color(0xFF4FC3F7), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                if (isGranted) {
                    Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Active! Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Enable Accessibility", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VpnPermissionStep(resumeTrigger: Int, securityManager: SecurityManager, onNext: () -> Unit) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(securityManager.isWall1Enabled()) }
    
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, DnsVpnService::class.java)
            intent.action = DnsVpnService.ACTION_START_VPN
            context.startService(intent)
            securityManager.setWall1Enabled(true)
            isGranted = true
        }
    }

    LaunchedEffect(resumeTrigger) { isGranted = securityManager.isWall1Enabled() }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 4: Network", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Guardian uses a local VPN to route DNS traffic through an adult-content filter. This blocks millions of adult domains natively.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(32.dp))
                if (isGranted) {
                    Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Filtering ON! Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnLauncher.launch(vpnIntent)
                            } else {
                                val intent = Intent(context, DnsVpnService::class.java)
                                intent.action = DnsVpnService.ACTION_START_VPN
                                context.startService(intent)
                                securityManager.setWall1Enabled(true)
                                isGranted = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Activate DNS Shield", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceAdminPermissionStep(resumeTrigger: Int, onNext: () -> Unit) {
    val context = LocalContext.current
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    var isGranted by remember { mutableStateOf(dpm.isAdminActive(componentName)) }
    
    LaunchedEffect(resumeTrigger) { isGranted = dpm.isAdminActive(componentName) }
    LaunchedEffect(Unit) {
        while (!isGranted) {
            delay(1000)
            isGranted = dpm.isAdminActive(componentName)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Step 5: Lock Down", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("To prevent easy uninstallation, Guardian requires Device Administrator rights. Once active, the app cannot be deleted without unlocking it.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(32.dp))
                if (isGranted) {
                    Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Locked! Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Guardian requires Device Admin to prevent uninstallation.")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Lock Guardian", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PartnerSetupStep(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassmorphismCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Final Step: Partner", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Guardian requires a Trusted Person. Enter their phone number. We will generate a cryptographic lock and send them the key via SMS.", color = Color.LightGray, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone Number") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF4FC3F7),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { if (phoneNumber.isNotEmpty()) onComplete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = phoneNumber.isNotEmpty()
                ) {
                    Text("Secure & Share Key", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
