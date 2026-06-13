package com.guardian.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView


class BlockOverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false
    private var countDownTimer: CountDownTimer? = null
    private var lastShowTimeMs = 0L
    private var backPressCount = 0
    private var backPressResetTimer: CountDownTimer? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showOverlay() {
        if (isShowing) return

        val now = System.currentTimeMillis()
        if (now - lastShowTimeMs < 2000) return
        lastShowTimeMs = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("BlockOverlayManager", "SYSTEM_ALERT_WINDOW permission not granted")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        createAndShowOverlay()
    }

    private fun createAndShowOverlay() {
        // Build the layout programmatically for maximum efficiency and minimum memory
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D32F2F")) // Deep Red
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            
            // Consume back button by default; 5 rapid presses = emergency dismiss
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                    backPressCount++
                    backPressResetTimer?.cancel()
                    if (backPressCount >= 5) {
                        backPressCount = 0
                        removeOverlay()
                    } else {
                        backPressResetTimer = object : CountDownTimer(1500, 1500) {
                            override fun onTick(millisUntilFinished: Long) {}
                            override fun onFinish() { backPressCount = 0 }
                        }.start()
                    }
                    true
                } else {
                    false
                }
            }
        }

        val titleText = TextView(context).apply {
            text = "DANGER ZONE"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleText = TextView(context).apply {
            text = "Guardian has blocked this content.\nTake a breath. You are stronger than this impulse."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 64)
        }

        val timerText = TextView(context).apply {
            text = "20"
            setTextColor(Color.WHITE)
            textSize = 72f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        layout.addView(timerText)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(layout, layoutParams)
            overlayView = layout
            isShowing = true
            startTimer(timerText)
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
        }
    }

    private fun startTimer(timerTextView: TextView) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerTextView.text = seconds.toString()
            }

            override fun onFinish() {
                removeOverlay()
                // Force user to home screen as an extra safety measure after timer
                val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
        }.start()
    }

    private fun removeOverlay() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isShowing = false
            countDownTimer?.cancel()
            countDownTimer = null
        }
    }
}
