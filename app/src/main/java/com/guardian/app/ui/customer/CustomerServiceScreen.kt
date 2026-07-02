package com.guardian.app.ui.customer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.core.rememberRestrictedSettingsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerServiceScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var report by remember { mutableStateOf(CustomerReport()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submitResult by remember { mutableStateOf<CustomerReportSender.Result?>(null) }
    var events by remember { mutableStateOf<List<String>>(emptyList()) }
    var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
    var showDeviceInfo by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("customer_prefs", Context.MODE_PRIVATE) }
    var lastSubmitTime by remember { mutableStateOf(prefs.getLong("last_submit", 0L)) }
    var remainingCooldown by remember { mutableStateOf(0L) }

    val restrictedHelper = rememberRestrictedSettingsHelper()

    LaunchedEffect(Unit) {
        val di = withContext(Dispatchers.IO) {
            DeviceInfoCollector.collect(context, restrictedHelper)
        }
        deviceInfo = di
        report = report.copy(deviceInfo = di)
    }

    LaunchedEffect(lastSubmitTime) {
        remainingCooldown = Validation.remainingCooldown(lastSubmitTime)
        while (remainingCooldown > 0) {
            delay(1000L)
            remainingCooldown = Validation.remainingCooldown(lastSubmitTime)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Service", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ── Category Picker ──
            Text("Category", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Category.entries.forEach { category ->
                    val selected = report.category == category
                    FilterChip(
                        selected = selected,
                        onClick = { report = report.copy(category = category) },
                        label = { Text(category.displayName, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Severity (only for Bug) ──
            if (report.category is Category.Bug) {
                Text("Severity", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Severity.entries.forEach { severity ->
                        val selected = report.severity == severity
                        val color = when (severity) {
                            Severity.CRITICAL -> Color(0xFFE53935)
                            Severity.MAJOR -> Color(0xFFFB8C00)
                            Severity.MINOR -> Color(0xFF43A047)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) color.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) color else color.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { report = report.copy(severity = severity) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                severity.displayName,
                                color = if (selected) color else color.copy(alpha = 0.7f),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Conditional Fields ──
            when (report.category) {
                is Category.Bug -> {
                    BugReportFields(report, validationErrors) { report = it }
                }
                is Category.Feature -> {
                    FeatureRequestFields(report, validationErrors) { report = it }
                }
                is Category.Suggestion -> {
                    SuggestionFields(report, validationErrors) { report = it }
                }
                is Category.Other -> {
                    OtherFields(report, validationErrors) { report = it }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Auto-collected Info ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeviceInfo = !showDeviceInfo },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Auto-collected Diagnostics",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (showDeviceInfo) "▲" else "▼",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showDeviceInfo && deviceInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            deviceInfo!!.toFormattedString(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Submit Button ──
            Button(
                onClick = {
                    val validation = Validation.validate(report)
                    validationErrors = validation.errors
                    if (!validation.valid) return@Button
                    if (remainingCooldown > 0) {
                        Toast.makeText(context, "Please wait ${remainingCooldown / 1000}s before sending again", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    submitResult = null
                    coroutineScope.launch(Dispatchers.IO) {
                        val result = CustomerReportSender.send(report)
                        withContext(Dispatchers.Main) {
                            isSubmitting = false
                            submitResult = result
                            if (result is CustomerReportSender.Result.Success) {
                                lastSubmitTime = System.currentTimeMillis()
                                prefs.edit().putLong("last_submit", lastSubmitTime).apply()
                                report = CustomerReport(deviceInfo = deviceInfo)
                                validationErrors = emptyMap()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSubmitting && remainingCooldown == 0L
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── Cooldown ──
            if (remainingCooldown > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cooldown: ${remainingCooldown / 1000}s remaining",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Result Feedback ──
            if (submitResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                when (val result = submitResult) {
                    is CustomerReportSender.Result.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1B5E20).copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Report Submitted — ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())}",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                    is CustomerReportSender.Result.Failure -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFB71C1C).copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Failed: ${result.reason}",
                                    color = Color(0xFFEF9A9A),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = {
                                    submitResult = null
                                    isSubmitting = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val retry = CustomerReportSender.send(report)
                                        withContext(Dispatchers.Main) {
                                            isSubmitting = false
                                            submitResult = retry
                                        }
                                    }
                                }) {
                                    Text("Retry", color = Color(0xFFEF9A9A))
                                }
                            }
                        }
                    }
                    null -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Copy Diagnostics ──
            OutlinedButton(
                onClick = {
                    val text = deviceInfo?.toFormattedString().orEmpty()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Guardian Diagnostics", text))
                    Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Diagnostics", fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Sub-Composables ──

@Composable
private fun BugReportFields(
    report: CustomerReport,
    errors: Map<String, String>,
    onUpdate: (CustomerReport) -> Unit
) {
    OutlinedTextField(
        value = report.description,
        onValueChange = { onUpdate(report.copy(description = it)) },
        label = { Text("Describe the bug *") },
        placeholder = { Text("What went wrong?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        maxLines = 5,
        isError = "description" in errors,
        supportingText = if ("description" in errors) {{ Text(errors["description"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = report.stepsToReproduce,
        onValueChange = { onUpdate(report.copy(stepsToReproduce = it)) },
        label = { Text("Steps to Reproduce *") },
        placeholder = { Text("1. Open app\n2. Go to...\n3. See error") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        maxLines = 6,
        isError = "stepsToReproduce" in errors,
        supportingText = if ("stepsToReproduce" in errors) {{ Text(errors["stepsToReproduce"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = report.expectedBehavior,
        onValueChange = { onUpdate(report.copy(expectedBehavior = it)) },
        label = { Text("Expected Behavior *") },
        placeholder = { Text("What should have happened?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        maxLines = 4,
        isError = "expectedBehavior" in errors,
        supportingText = if ("expectedBehavior" in errors) {{ Text(errors["expectedBehavior"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = report.actualBehavior,
        onValueChange = { onUpdate(report.copy(actualBehavior = it)) },
        label = { Text("Actual Behavior *") },
        placeholder = { Text("What actually happened?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        maxLines = 4,
        isError = "actualBehavior" in errors,
        supportingText = if ("actualBehavior" in errors) {{ Text(errors["actualBehavior"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun FeatureRequestFields(
    report: CustomerReport,
    errors: Map<String, String>,
    onUpdate: (CustomerReport) -> Unit
) {
    OutlinedTextField(
        value = report.description,
        onValueChange = { onUpdate(report.copy(description = it)) },
        label = { Text("Describe the feature *") },
        placeholder = { Text("What feature would you like to see?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        maxLines = 6,
        isError = "description" in errors,
        supportingText = if ("description" in errors) {{ Text(errors["description"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun SuggestionFields(
    report: CustomerReport,
    errors: Map<String, String>,
    onUpdate: (CustomerReport) -> Unit
) {
    OutlinedTextField(
        value = report.description,
        onValueChange = { onUpdate(report.copy(description = it)) },
        label = { Text("Your suggestion *") },
        placeholder = { Text("How can we improve Guardian?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        maxLines = 6,
        isError = "description" in errors,
        supportingText = if ("description" in errors) {{ Text(errors["description"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun OtherFields(
    report: CustomerReport,
    errors: Map<String, String>,
    onUpdate: (CustomerReport) -> Unit
) {
    OutlinedTextField(
        value = report.description,
        onValueChange = { onUpdate(report.copy(description = it)) },
        label = { Text("Your message *") },
        placeholder = { Text("How can we help you?") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        maxLines = 6,
        isError = "description" in errors,
        supportingText = if ("description" in errors) {{ Text(errors["description"]!!, color = MaterialTheme.colorScheme.error) }} else null,
        shape = MaterialTheme.shapes.medium
    )
}
