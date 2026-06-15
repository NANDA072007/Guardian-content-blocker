package com.guardian.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
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
    @Volatile
    private var isShowing = false
    private var countDownTimer: CountDownTimer? = null
    private var lastShowTimeMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val COOLDOWN_TIMER_MS = 45_000L // 45 seconds
        private const val DEBOUNCE_MS = 2000L
    }

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showOverlay() {
        if (isShowing) return

        val now = System.currentTimeMillis()
        if (now - lastShowTimeMs < DEBOUNCE_MS) return
        lastShowTimeMs = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("BlockOverlayManager", "SYSTEM_ALERT_WINDOW permission not granted")
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("BlockOverlayManager", "Failed to request overlay permission", e)
            }
            return
        }

        // Always create overlay on the main thread to prevent crashes
        mainHandler.post { createAndShowOverlay() }
    }

    private fun createAndShowOverlay() {
        // Double-check on main thread (race condition guard)
        if (isShowing) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D32F2F"))
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)

            // Consume ALL key events — back button, recent apps, volume keys
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_HOME,
                        KeyEvent.KEYCODE_APP_SWITCH,
                        KeyEvent.KEYCODE_VOLUME_UP,
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            // Consume all escape attempts silently
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        }

        // Title
        val titleText = TextView(context).apply {
            text = "DANGER ZONE"
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // Motivational subtitle
        val subtitleText = TextView(context).apply {
            text = "Guardian has blocked this content.\nTake a breath. You are stronger than this impulse."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        // Instruction text
        val instructionText = TextView(context).apply {
            text = "This screen will close automatically.\nYou will be redirected to your home screen."
            setTextColor(Color.parseColor("#FFCDD2"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        // Countdown timer display
        val timerText = TextView(context).apply {
            text = (COOLDOWN_TIMER_MS / 1000).toString()
            setTextColor(Color.WHITE)
            textSize = 72f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        layout.addView(titleText)
        layout.addView(subtitleText)
        layout.addView(instructionText)
        layout.addView(timerText)

        // Production-grade window flags:
        // - TYPE_APPLICATION_OVERLAY: draws over all apps
        // - FLAG_LAYOUT_IN_SCREEN: fills the entire screen including status/nav bar area
        // - FLAG_KEEP_SCREEN_ON: prevents screen from turning off during cooldown
        // - FLAG_NOT_FOCUSABLE is intentionally ABSENT so we capture key events
        // - FLAG_NOT_TOUCH_MODAL is intentionally ABSENT so touches don't pass through
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.OPAQUE
        )

        try {
            windowManager?.addView(layout, layoutParams)
            overlayView = layout
            isShowing = true

            // Force user to home screen immediately so the blocked app loses focus
            goHome()

            startTimer(timerText)
            Log.d("BlockOverlayManager", "Overlay shown. Cooldown: ${COOLDOWN_TIMER_MS / 1000}s")
        } catch (e: Exception) {
            Log.e("BlockOverlayManager", "Failed to show overlay", e)
            isShowing = false
        }
    }

    private fun startTimer(timerTextView: TextView) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(COOLDOWN_TIMER_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerTextView.text = seconds.toString()
            }

            override fun onFinish() {
                goHome()
                removeOverlay()
            }
        }.start()
    }

    private fun goHome() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e("BlockOverlayManager", "Failed to go home", e)
        }
    }

    private fun removeOverlay() {
        if (!isShowing) return
        mainHandler.post {
            try {
                overlayView?.let { windowManager?.removeView(it) }
            } catch (e: Exception) {
                Log.e("BlockOverlayManager", "Failed to remove overlay", e)
            } finally {
                overlayView = null
                isShowing = false
                countDownTimer?.cancel()
                countDownTimer = null
            }
        }
    }
}
