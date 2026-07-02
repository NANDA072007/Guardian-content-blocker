package com.guardian.app.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.BuildConfig
import com.guardian.app.broadcast.GuardianDeviceAdminReceiver
import com.guardian.app.core.ProtectionOrchestrator
import com.guardian.app.core.rememberRestrictedSettingsHelper
import com.guardian.app.walls.wall1.VpnState
import kotlinx.coroutines.delay
import android.os.SystemClock
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    orchestrator: ProtectionOrchestrator
) {
    val protectionState by orchestrator.protectionState.collectAsState()
    val context = LocalContext.current
    val restrictedHelper = rememberRestrictedSettingsHelper()

    var usedMemory by remember { mutableStateOf(0L) }
    var totalMemory by remember { mutableStateOf(0L) }
    var maxMemory by remember { mutableStateOf(0L) }

    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager

    val a11yRunning = remember {
        am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            ?.any { it.resolveInfo.serviceInfo.name.contains("AccessibilitySentry") } ?: false
    }
    val adminActive = dpm.isAdminActive(componentName)
    val batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    val restrictedBlocking = restrictedHelper.isRestrictedSettingsBlocking()
    val overlayGranted = Settings.canDrawOverlays(context)

    LaunchedEffect(Unit) {
        while (true) {
            val runtime = Runtime.getRuntime()
            totalMemory = runtime.totalMemory()
            usedMemory = runtime.totalMemory() - runtime.freeMemory()
            maxMemory = runtime.maxMemory()
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A1A))
            )
        },
        containerColor = Color(0xFF0A0A1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0A1A), Color(0xFF121212))))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Card 1: Protection Status
            DiagnosticsCard(title = "Protection Status") {
                ProtectionStatusRow("VPN", protectionState == VpnState.RUNNING, protectionState.name)
                Spacer(modifier = Modifier.height(8.dp))
                ProtectionStatusRow("Accessibility", a11yRunning, if (a11yRunning) "Active" else "Stopped")
                Spacer(modifier = Modifier.height(8.dp))
                ProtectionStatusRow("Device Admin", adminActive, if (adminActive) "Enabled" else "Disabled")
                Spacer(modifier = Modifier.height(8.dp))
                ProtectionStatusRow("Battery Optimization", batteryIgnored, if (batteryIgnored) "Ignored" else "Not Ignored")
                Spacer(modifier = Modifier.height(8.dp))
                ProtectionStatusRow("Restricted Settings", !restrictedBlocking, if (restrictedBlocking) "Blocking" else "Not Blocking")
                Spacer(modifier = Modifier.height(8.dp))
                ProtectionStatusRow("Overlay Permission", overlayGranted, if (overlayGranted) "Granted" else "Missing")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: App Info
            DiagnosticsCard(title = "App Info") {
                InfoRow("Version", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: JVM Memory
            DiagnosticsCard(title = "JVM Heap") {
                val usedMb = usedMemory / (1024.0 * 1024.0)
                val totalMb = totalMemory / (1024.0 * 1024.0)
                val maxMb = maxMemory / (1024.0 * 1024.0)
                val progress = if (maxMemory > 0) usedMemory.toFloat() / maxMemory.toFloat() else 0f

                InfoRow("Used", "%.1f MB".format(Locale.US, usedMb))
                InfoRow("Allocated", "%.1f MB".format(Locale.US, totalMb))
                InfoRow("Max", "%.1f MB".format(Locale.US, maxMb))
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (progress > 0.8f) Color(0xFFE53935) else Color(0xFF00E676),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                InfoRow("Saturation", "%.1f%%".format(Locale.US, progress * 100))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 4: Permissions
            DiagnosticsCard(title = "Permissions") {
                PermissionRow("SYSTEM_ALERT_WINDOW", overlayGranted)
                PermissionRow("BIND_ACCESSIBILITY_SERVICE", a11yRunning)
                PermissionRow("BIND_DEVICE_ADMIN", adminActive)
                PermissionRow("POST_NOTIFICATIONS", Build.VERSION.SDK_INT < 33 || (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled())
                PermissionRow("BIND_VPN_SERVICE", protectionState == VpnState.RUNNING)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 5: Connection Monitor
            DiagnosticsCard(title = "Connection Monitor") {
                Text(
                    "Network Loss Debounce: 1200ms",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Status lost triggers are debounced to prevent reconnect loops during quick transitions.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Copy Diagnostics Button
            Button(
                onClick = {
                    val text = buildDiagnosticsText(context, protectionState, a11yRunning, adminActive, batteryIgnored, restrictedBlocking, overlayGranted)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Guardian Diagnostics", text))
                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Diagnostics", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProtectionStatusRow(label: String, ok: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (ok) Color(0xFF4CAF50) else Color(0xFFE53935))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(detail, color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun PermissionRow(name: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            if (granted) "✅" else "❌",
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            if (granted) "Granted" else "Missing",
            color = if (granted) Color(0xFF4CAF50) else Color(0xFFE53935),
            fontSize = 12.sp
        )
    }
}

@Composable
fun DiagnosticsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF4FC3F7),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

private fun buildDiagnosticsText(
    context: Context,
    state: VpnState,
    a11yRunning: Boolean,
    adminActive: Boolean,
    batteryIgnored: Boolean,
    restrictedBlocking: Boolean,
    overlayGranted: Boolean
): String {
    return buildString {
        appendLine("=== Guardian Diagnostics ===")
        appendLine()
        appendLine("App: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("--- Protection ---")
        appendLine("VPN: ${state.name}")
        appendLine("Accessibility: ${if (a11yRunning) "Running" else "Stopped"}")
        appendLine("Device Admin: ${if (adminActive) "Enabled" else "Disabled"}")
        appendLine("Battery Optimization: ${if (batteryIgnored) "Ignored" else "Not Ignored"}")
        appendLine("Restricted Settings: ${if (restrictedBlocking) "Blocking" else "Not Blocking"}")
        appendLine("Overlay Permission: ${if (overlayGranted) "Granted" else "Missing"}")
        appendLine()
        appendLine("--- Runtime ---")
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val maxMb = runtime.maxMemory() / (1024.0 * 1024.0)
        appendLine("Heap: ${"%.1f".format(usedMb)} MB / ${"%.1f".format(maxMb)} MB")
        appendLine("Uptime: ${SystemClock.elapsedRealtime() / 60000} min")
    }
}
