package com.guardian.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun EmergencyScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    DisposableEffect(activity) {
        if (activity != null) {
            val window = activity.window
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            // Hide status and navigation bars for maximum immersion
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Prevent screen from sleeping while breathing
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            onDispose {
                // Restore the device to normal when leaving the screen
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } else {
            onDispose { }
        }
    }
    
    // 0 = Inhale (4s), 1 = Hold (7s), 2 = Exhale (8s)
    var phase by remember { mutableStateOf(0) }
    
    // Animation scale target
    var targetScale by remember { mutableStateOf(0.5f) }
    var animationDuration by remember { mutableStateOf(4000) }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing),
        label = "BreathingAnimation"
    )

    LaunchedEffect(Unit) {
        while (true) {
            // Phase 0: Inhale 4s
            phase = 0
            animationDuration = 4000
            targetScale = 1.0f
            delay(4000)

            // Phase 1: Hold 7s
            phase = 1
            animationDuration = 7000
            targetScale = 1.0f // stays same
            delay(7000)

            // Phase 2: Exhale 8s
            phase = 2
            animationDuration = 8000
            targetScale = 0.5f
            delay(8000)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0A1A),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Emergency Sanctuary", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", color = Color.White, fontSize = 24.sp)
                    }
                }
            )
        }
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Text(
                text = "Do not fight the urge.\nJust breathe through it.",
                color = Color.LightGray,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier.padding(bottom = 64.dp)
            )

            // The Breathing Circle
            Box(
                modifier = Modifier
                    .size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                // The animated expanding/contracting bubble
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .graphicsLayer {
                            shadowElevation = 20.dp.toPx()
                            shape = CircleShape
                            ambientShadowColor = Color(0xFF4FC3F7)
                            spotShadowColor = Color(0xFF4FC3F7)
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF4FC3F7).copy(alpha = 0.5f), Color(0xFF0288D1).copy(alpha = 0.2f))
                            ), 
                            CircleShape
                        )
                )
                
                // The solid inner bubble
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF29B6F6), Color(0xFF0288D1))
                            ), 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (phase) {
                            0 -> "INHALE\n(4s)"
                            1 -> "HOLD\n(7s)"
                            else -> "EXHALE\n(8s)"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            // Grounding Instruction
            Text(
                text = "ACTION REQUIRED:\nStand up, leave this room, and get a glass of cold water right now.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

        }
    }
}
