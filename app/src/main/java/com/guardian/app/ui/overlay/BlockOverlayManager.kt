package com.guardian.app.ui.overlay

import android.accessibilityservice.AccessibilityService
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockOverlayManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    @Volatile private var accessibilityService: AccessibilityService? = null

    fun setAccessibilityService(service: AccessibilityService) {
        this.accessibilityService = service
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    @Volatile
    private var isShowing = false
    private var countDownTimer: CountDownTimer? = null
    private var lastShowTimeMs = 0L

    private val overlayThread = android.os.HandlerThread("BlockOverlayWorker", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val overlayHandler = Handler(overlayThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var timerText: TextView
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var instructionText: TextView

    companion object {
        private const val COOLDOWN_TIMER_MS = 45_000L
        private const val REINFORCEMENT_TIMER_MS = 3_000L
        private const val DEBOUNCE_MS = 2000L
    }

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayHandler.post { preInflateOverlay() }
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

        overlayHandler.post {
            synchronized(this) {
                if (!isShowing) {
                    try {
                        if (overlayView == null) {
                            preInflateOverlay()
                        }
                        // Reset to block phase
                        titleText.text = "DANGER ZONE"
                        titleText.setTextColor(Color.parseColor("#EF4444"))
                        subtitleText.text = "Guardian has blocked this content.\nTake a breath. You are stronger than this impulse."
                        subtitleText.setTextColor(Color.WHITE)
                        instructionText.text = "This screen will close automatically.\nYou will be redirected to your home screen."
                        instructionText.setTextColor(Color.parseColor("#FFCDD2"))
                        timerText.setTextColor(Color.WHITE)

                        if (overlayView?.parent == null) {
                            windowManager?.addView(overlayView, getLayoutParams())
                        }
                        overlayView?.visibility = View.VISIBLE
                        isShowing = true

                        goHome()

                        mainHandler.post { startBlockTimer() }
                        Log.d("BlockOverlayManager", "Overlay shown. Cooldown: ${COOLDOWN_TIMER_MS / 1000}s")
                    } catch (e: Exception) {
                        Log.e("BlockOverlayManager", "Failed to show overlay", e)
                        isShowing = false
                    }
                }
            }
        }
    }

    private fun getLayoutParams(): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && accessibilityService != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            flags,
            PixelFormat.OPAQUE
        )
    }

    private fun preInflateOverlay() {
        if (overlayView != null) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60F172A"))
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            visibility = View.GONE

            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_HOME,
                        KeyEvent.KEYCODE_APP_SWITCH,
                        KeyEvent.KEYCODE_VOLUME_UP,
                        KeyEvent.KEYCODE_VOLUME_DOWN -> true
                        else -> false
                    }
                } else {
                    false
                }
            }
            setOnTouchListener { _, _ -> true }
        }

        titleText = TextView(context).apply {
            text = "DANGER ZONE"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        subtitleText = TextView(context).apply {
            text = "Guardian has blocked this content.\nTake a breath. You are stronger than this impulse."
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        instructionText = TextView(context).apply {
            text = "This screen will close automatically.\nYou will be redirected to your home screen."
            setTextColor(Color.parseColor("#FFCDD2"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }

        timerText = TextView(context).apply {
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
        overlayView = layout
    }

    private fun startBlockTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(COOLDOWN_TIMER_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerText.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                showReinforcement()
            }
        }.start()
    }

    private fun showReinforcement() {
        overlayHandler.post {
            mainHandler.post {
                titleText.text = "YOU DID IT!"
                titleText.setTextColor(Color.parseColor("#4CAF50"))
                subtitleText.text = "+1 Strength"
                subtitleText.setTextColor(Color.parseColor("#A5D6A7"))
                instructionText.text = "Keep going. Every win makes you stronger."
                instructionText.setTextColor(Color.WHITE)
                timerText.text = "✓"
                timerText.setTextColor(Color.parseColor("#4CAF50"))
                timerText.textSize = 72f

                countDownTimer?.cancel()
                countDownTimer = object : CountDownTimer(REINFORCEMENT_TIMER_MS, REINFORCEMENT_TIMER_MS) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        goHome()
                        removeOverlay()
                    }
                }.start()
            }
        }
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

    fun removeOverlay() {
        if (!isShowing) return
        overlayHandler.post {
            synchronized(this) {
                if (!isShowing) return@post
                try {
                    overlayView?.let {
                        if (it.parent != null) {
                            windowManager?.removeView(it)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BlockOverlayManager", "Failed to remove overlay", e)
                } finally {
                    isShowing = false
                    mainHandler.post {
                        countDownTimer?.cancel()
                        countDownTimer = null
                    }
                }
            }
        }
    }
}
