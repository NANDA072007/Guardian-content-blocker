package com.guardian.app.ui.screens

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
import com.guardian.app.domain.InitiateCooloffUseCase
import kotlinx.coroutines.delay
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.widget.Toast
import com.guardian.app.broadcast.GuardianDeviceAdminReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedPersonScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val cooloffUseCase = remember {
        val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.guardian.app.core.SecurityManagerEntryPoint::class.java
        )
        ep.initiateCooloffUseCase()
    }
    var guardianCode by remember { mutableStateOf("") }
    var isCooloffActive by remember { mutableStateOf(cooloffUseCase.isCooloffActive()) }
    var timeRemainingMs by remember { mutableStateOf(cooloffUseCase.getRemainingMs()) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    val initialPenaltyMs = cooloffUseCase.getRemainingMs()
    var isPenaltyActive by remember { mutableStateOf(false) }
    var penaltyTimeMs by remember { mutableStateOf(0L) }

    LaunchedEffect(isCooloffActive, isPenaltyActive) {
        while (isCooloffActive || isPenaltyActive) {
            if (isCooloffActive) {
                timeRemainingMs = cooloffUseCase.getRemainingMs()
                if (timeRemainingMs <= 0) {
                    isCooloffActive = false
                    showUnlockDialog = true
                }
            }
            if (isPenaltyActive) {
                penaltyTimeMs = cooloffUseCase.getRemainingMs()
                if (penaltyTimeMs <= 0) {
                    isPenaltyActive = false
                }
            }
            delay(1000)
        }
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("Unlock Guardian?", color = Color.White) },
            text = { Text("The 24-hour cooloff has expired. You can now disable protections. Remove Device Admin to allow uninstallation?", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = {
                    showUnlockDialog = false
                    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
                    try {
                        dpm.removeActiveAdmin(componentName)
                    } catch (e: Exception) {
                        android.util.Log.e("UnlockScreen", "Failed to remove admin", e)
                    }
                }) {
                    Text("Remove Admin", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("Keep Locked", color = Color(0xFF4CAF50))
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock Guardian", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0A0A1A)
                )
            )
        },
        containerColor = Color(0xFF0A0A1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0A1A), Color(0xFF121212))
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Unlock Guardian",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter your Guardian Code to start a 24-hour cooloff. After that, you can disable protections.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = guardianCode,
                onValueChange = { if (it.length <= 9) guardianCode = it.uppercase() },
                label = { Text("Guardian Code") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Color(0xFF4CAF50),
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    val result = cooloffUseCase.execute(guardianCode)
                    when (result) {
                        is InitiateCooloffUseCase.Result.WrongCode -> {
                            Toast.makeText(context, "Wrong Guardian Code.", Toast.LENGTH_SHORT).show()
                        }
                        is InitiateCooloffUseCase.Result.PenaltyActive -> {
                            isPenaltyActive = true
                            Toast.makeText(context, "Too many wrong attempts. Locked for ${result.penaltyMinutes} minutes.", Toast.LENGTH_LONG).show()
                        }
                        is InitiateCooloffUseCase.Result.AlreadyCoolingOff -> {}
                        is InitiateCooloffUseCase.Result.Success -> {
                            isCooloffActive = true
                        }
                    }
                },
                enabled = !isCooloffActive && !isPenaltyActive && guardianCode.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isPenaltyActive) "Too many attempts" else if (isCooloffActive) "Cooloff Active" else "Initiate 24h Cooloff",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCooloffActive || isPenaltyActive) Color.Gray else Color.White
                )
            }

            if (isPenaltyActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Too many wrong codes. Try again in ${penaltyTimeMs / 1000}s",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
            }

            if (isCooloffActive) {
                Spacer(modifier = Modifier.height(32.dp))
                val hours = (timeRemainingMs / (1000 * 60 * 60)) % 24
                val minutes = (timeRemainingMs / (1000 * 60)) % 60
                val seconds = (timeRemainingMs / 1000) % 60

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFD32F2F).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFD32F2F).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "COOLOFF ACTIVE",
                            color = Color(0xFFEF5350),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )
                    }
                }
            }
        }
    }
}
