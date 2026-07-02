package com.guardian.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkOverlayManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    @Volatile
    var isShowing = false
        private set
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showOverlay() {
        if (isShowing) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("NetworkOverlayManager", "SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        mainHandler.post { createAndShowOverlay() }
    }

    private fun createAndShowOverlay() {
        synchronized(this) {
            if (isShowing) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000")) // Black background for reconnecting
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)

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
        }

        val titleText = TextView(context).apply {
            text = "Reconnecting..."
            setTextColor(Color.WHITE)
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subtitleText = TextView(context).apply {
            text = "Securing VPN tunnel. Please wait."
            setTextColor(Color.LTGRAY)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }

        layout.addView(titleText)
        layout.addView(subtitleText)

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
            Log.d("NetworkOverlayManager", "Network disconnect overlay shown.")
        } catch (e: Exception) {
            Log.e("NetworkOverlayManager", "Failed to show network overlay", e)
            isShowing = false
        }
        }
    }

    fun removeOverlay() {
        if (!isShowing) return
        mainHandler.post {
            synchronized(this) {
                if (!isShowing) return@post
                try {
                    overlayView?.let { windowManager?.removeView(it) }
                } catch (e: Exception) {
                    Log.e("NetworkOverlayManager", "Failed to remove network overlay", e)
                } finally {
                    overlayView = null
                    isShowing = false
                    Log.d("NetworkOverlayManager", "Network disconnect overlay removed.")
                }
            }
        }
    }
}
