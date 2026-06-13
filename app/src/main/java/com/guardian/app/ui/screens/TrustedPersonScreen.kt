package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.guardian.app.data.SecurityManager
import com.guardian.app.util.CryptoUtils
import kotlinx.coroutines.delay
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import com.guardian.app.data.policy.GuardianDeviceAdminReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedPersonScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    
    var disableCode by remember { mutableStateOf("") }
    
    // Check if cooloff is active
    val currentUnlockTime = securityManager.getUninstallUnlockTime()
    var isCooloffActive by remember { mutableStateOf(currentUnlockTime > System.currentTimeMillis()) }
    var timeRemainingMs by remember { mutableStateOf(maxOf(0L, currentUnlockTime - System.currentTimeMillis())) }
    
    // Setup State
    var isSetupMode by remember { mutableStateOf(securityManager.getTrustedPersonContact().isNullOrEmpty()) }
    var phoneNumber by remember { mutableStateOf("") }
    
    var showUnlockDialog by remember { mutableStateOf(false) }

    // Timer to update UI
    LaunchedEffect(isCooloffActive) {
        while(isCooloffActive) {
            val remain = securityManager.getUninstallUnlockTime() - System.currentTimeMillis()
            if (remain <= 0) {
                isCooloffActive = false
                timeRemainingMs = 0L
                showUnlockDialog = true
            } else {
                timeRemainingMs = remain
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
                        android.util.Log.e("TrustedPersonScreen", "Failed to remove admin", e)
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

    fun hashString(input: String): String = CryptoUtils.sha256(input.toByteArray())

        var showWrongCodeToast by remember { mutableStateOf(false) }
        if (showWrongCodeToast) {
            android.widget.Toast.makeText(context, "Wrong code. Ask your Trusted Person for the correct code.", android.widget.Toast.LENGTH_SHORT).show()
            showWrongCodeToast = false
        }

        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trusted Person", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("<", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
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
            
            if (isSetupMode) {
                // SETUP MODE UI
                Text(
                    text = "Accountability Setup",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter your Trusted Person's phone number. We will generate a secure code and send it to them. You will NOT be able to view this code again.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4FC3F7),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF4FC3F7),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            val generatedCode = CryptoUtils.generateUnlockCode()
                            securityManager.setTrustedPersonContact(phoneNumber)
                            securityManager.setMasterKeyHash(hashString(generatedCode))
                            
                            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:$phoneNumber")
                                putExtra("sms_body", "I am using Guardian for my recovery. You are my Trusted Person. My emergency unlock code is: $generatedCode. Do not give this back to me unless we talk first.")
                            }
                            context.startActivity(smsIntent)
                            isSetupMode = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Share Code", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

            } else {
                // EXISTING DISABLE MODE UI
                Text(
                    text = "Unlock Guardian",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = disableCode,
                    onValueChange = { disableCode = it },
                    label = { Text("Emergency Code from Partner") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF4CAF50),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = lambda@{
                        val enteredHash = hashString(disableCode)
                        val storedHash = securityManager.getMasterKeyHash()

                        if (storedHash == null || !CryptoUtils.constantTimeEquals(enteredHash.toByteArray(), storedHash.toByteArray())) {
                            showWrongCodeToast = true
                            return@lambda
                        }
                        if (!isCooloffActive) {
                            val newUnlockTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                            securityManager.setUninstallUnlockTime(newUnlockTime)
                            isCooloffActive = true

                            val partnerNumber = securityManager.getTrustedPersonContact()
                            if (!partnerNumber.isNullOrEmpty()) {
                                val alertIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$partnerNumber")
                                    putExtra("sms_body", "I just requested to disable Guardian. The 24-hour cooloff has started. Please check in with me.")
                                }
                                context.startActivity(alertIntent)
                            }
                        }
                    },
                    enabled = !isCooloffActive && disableCode.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isCooloffActive) "Cooloff Active" else "Initiate 24h Disable",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (isCooloffActive) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val hours = (timeRemainingMs / (1000 * 60 * 60)) % 24
                    val minutes = (timeRemainingMs / (1000 * 60)) % 60
                    val seconds = (timeRemainingMs / 1000) % 60
                    
                    Text(
                        text = "Guardian will unlock in: ${String.format("%02d:%02d:%02d", hours, minutes, seconds)}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
