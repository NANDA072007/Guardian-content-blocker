package com.guardian.app.walls.wall2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.guardian.app.walls.wall2.core.ProtectionOrchestrator
import com.guardian.app.walls.wall2.event.SentryState
import com.guardian.app.walls.wall2.health.AccessibilityHealthEngine
import com.guardian.app.walls.wall2.health.RestrictedSettingsHelper
import com.guardian.app.walls.wall2.health.SelfTestRoutine
import com.guardian.app.BuildConfig
import com.guardian.app.ui.overlay.BlockOverlayManager
import com.guardian.app.walls.wall2.router.AccessibilityEventRouter
import java.util.concurrent.TimeUnit

@dagger.hilt.android.AndroidEntryPoint
class AccessibilitySentry : AccessibilityService() {

    @javax.inject.Inject lateinit var router: AccessibilityEventRouter
    @javax.inject.Inject lateinit var orchestrator: ProtectionOrchestrator
    @javax.inject.Inject lateinit var healthEngine: AccessibilityHealthEngine
    @javax.inject.Inject lateinit var selfTestRoutine: SelfTestRoutine
    @javax.inject.Inject lateinit var restrictedSettingsHelper: RestrictedSettingsHelper
    @javax.inject.Inject lateinit var blockOverlayManager: BlockOverlayManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GuardianAccessibilityChannel"
        private const val NOTIFICATION_ID = 1002
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private lateinit var backgroundHandler: Handler
    private var selfTestHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("AccessibilityWorker", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        backgroundHandler = Handler(thread.looper)

        val selfTestThread = HandlerThread("SelfTestWorker", Process.THREAD_PRIORITY_LOWEST)
        selfTestThread.start()
        selfTestHandler = Handler(selfTestThread.looper)

        orchestrator.updateSentryState(SentryState.STARTING)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && restrictedSettingsHelper.isRestrictedSettingsBlocking()) {
            Log.w("AccessibilitySentry", "Restricted settings may block accessibility. Providing guidance.")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        orchestrator.updateSentryState(SentryState.PERMISSION_GRANTED)
        healthEngine.onServiceConnected()

        blockOverlayManager.setAccessibilityService(this)

        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100L
        }

        startPeriodicSelfTest()
        orchestrator.updateSentryState(SentryState.RUNNING)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Accessibility Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Guardian's accessibility monitor alive"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Guardian is monitoring")
            .setContentText("Accessibility service is active and protecting you.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (BuildConfig.DEBUG) {
            Log.v("GuardianA11y", "Event: type=${event.eventType} pkg=${event.packageName} source=${event.source != null} text=${event.text}")
        }

        healthEngine.onEvent(
            eventType = event.eventType,
            packageName = event.packageName?.toString(),
            hasSource = event.source != null,
            hasText = event.text != null && !event.text.isNullOrEmpty()
        )
        selfTestRoutine.feedEvent(event)

        backgroundHandler.post {
            try {
                val protectionEvent = router.routeEvent(event)
                if (protectionEvent != null) {
                    mainHandler.post {
                        orchestrator.onProtectionEvent(protectionEvent)
                    }
                }
            } catch (e: Exception) {
                Log.e("AccessibilitySentry", "Error processing event", e)
            }
        }
    }

    override fun onInterrupt() {
        healthEngine.onInterrupt()
    }

    override fun onDestroy() {
        super.onDestroy()
        selfTestHandler?.looper?.quitSafely()
        try {
            (backgroundHandler.looper as? android.os.HandlerThread)?.quitSafely()
        } catch (_: Exception) {}

        healthEngine.onServiceDisconnected()
        orchestrator.updateSentryState(SentryState.STOPPING)

        try {
            Log.d("AccessibilitySentry", "onDestroy — self-resurrection")
            val restartIntent = Intent(applicationContext, AccessibilitySentry::class.java)
            applicationContext.startService(restartIntent)
        } catch (e: Exception) {
            Log.w("AccessibilitySentry", "Resurrection failed: ${e.message}")
        }

        try {
            val recoveryWork = OneTimeWorkRequestBuilder<com.guardian.app.workers.AccessibilityRecoveryWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(recoveryWork)
        } catch (_: Exception) {}

        orchestrator.updateSentryState(SentryState.DISABLED)
    }

    private fun startPeriodicSelfTest() {
        selfTestHandler?.post(object : Runnable {
            override fun run() {
                try {
                    selfTestRoutine.feedRootNode(rootInActiveWindow)
                    val details = selfTestRoutine.runDiagnostics()
                    healthEngine.onSelfTest(
                        passed = details.recentEventsReceived && details.activeWindowReadable,
                        details = details
                    )
                } catch (_: Exception) {}
                selfTestHandler?.postDelayed(this, 30_000L)
            }
        })
    }
}
