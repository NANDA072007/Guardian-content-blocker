package com.guardian.app.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.LifecycleService
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import javax.inject.Inject
import com.guardian.app.ui.overlay.BlockOverlayManager
import com.guardian.app.core.SecurityManager

/**
 * GuardianCoreService — The orchestration heartbeat of Guardian.
 *
 * Persistence contract:
 * - onStartCommand returns START_STICKY
 * - onDestroy sends restart broadcast BEFORE super.onDestroy
 * - Startup WakeLock keeps CPU alive during initialization
 * - AlarmManager chain fires every 15 minutes (survives Doze + OEM)
 * - JobService fires every 15 minutes (offset, survives reboot)
 * - AccessibilityWatchdog checks every 30 seconds
 *
 * This service MUST stay alive. Everything else depends on it.
 */
@dagger.hilt.android.AndroidEntryPoint
class GuardianCoreService : LifecycleService() {

    @Inject lateinit var resurrector: ServiceResurrector
    @Inject lateinit var blockOverlayManager: BlockOverlayManager
    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var orchestrator: ProtectionOrchestrator
    @Inject lateinit var healthEngine: com.guardian.app.walls.wall2.health.AccessibilityHealthEngine

    private var startupWakeLock: android.os.PowerManager.WakeLock? = null
    private var accessibilityWatchdog: AccessibilityWatchdog? = null
    
    private val blockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SHOW_BLOCK_OVERLAY) {
                Log.d(TAG, "Block trigger received. Showing overlay.")
                blockOverlayManager.showOverlay()
            }
        }
    }
    
    companion object {
        private const val TAG = "GuardianCoreService"
        const val CHANNEL_ID = "GuardianCoreChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_CORE = "com.guardian.app.START_CORE"
        const val ACTION_SHOW_BLOCK_OVERLAY = "com.guardian.app.ACTION_SHOW_BLOCK_OVERLAY"
        
        fun isRunning(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                manager.getRunningServices(Integer.MAX_VALUE).any {
                    it.service.className == GuardianCoreService::class.java.name
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — initializing Guardian Core")
        
        acquireStartupWakeLock()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Register block overlay broadcast receiver
        val filter = IntentFilter(ACTION_SHOW_BLOCK_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.registerReceiver(
                this, blockReceiver, filter, Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(blockReceiver, filter)
        }

        // Initialize resurrection chain
        initializeResurrectionChain()
        
        // Start accessibility watchdog
        initializeWatchdog()
    }

    /**
     * Initialize the multi-path resurrection chain:
     * 1. AlarmManager — fires every 15 min, survives Doze + OEM
     * 2. JobService — fires every 15 min (offset), survives reboot
     * These ensure GuardianCoreService stays alive even if OEM kills it.
     */
    private fun initializeResurrectionChain() {
        if (securityManager.isSetupComplete()) {
            resurrector.scheduleAlarmChain(this)
            resurrector.scheduleJobService(this)
            Log.d(TAG, "Resurrection chain initialized (AlarmManager + JobService)")
        }
    }

    /**
     * Start the accessibility watchdog — checks every 30 seconds.
     * If accessibility dies, triggers immediate resurrection.
     */
    private fun initializeWatchdog() {
        if (!securityManager.isWall2Enabled()) return

        accessibilityWatchdog = AccessibilityWatchdog(this, lifecycleScope, healthEngine)
        accessibilityWatchdog?.start(
            onAccessibilityDead = {
                Log.w(TAG, "Watchdog detected accessibility death. Resurrecting...")
                resurrector.resurrectAccessibility(this)
            },
            onCoreServiceDead = {
                Log.w(TAG, "Watchdog detected core service death. Resurrecting...")
                resurrector.resurrectCoreService(this)
            }
        )
        Log.d(TAG, "Accessibility watchdog started (30s interval)")
    }

    private fun acquireStartupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        startupWakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Guardian::StartupLock"
        ).apply {
            acquire(30 * 1000L) // 30 seconds maximum — enough for all initialization
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand — Core Service Started (flags=$flags, startId=$startId)")
        
        // Ensure resurrection chain is active (safety re-registration)
        if (securityManager.isSetupComplete()) {
            resurrector.scheduleAlarmChain(this)
        }

        // Start protection if enabled in settings
        if (securityManager.isWall1Enabled()) {
            orchestrator.startProtection()
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Guardian Core"
            val descriptionText = "Keeps Guardian protection active"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian is Active")
            .setContentText("Your device is currently protected.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved — App removed from recents. Resurrecting...")
        resurrector.resurrectCoreService(this)
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy — Core Service Destroyed. Resurrecting immediately...")
        
        // Stop watchdog before destruction
        accessibilityWatchdog?.stop()
        accessibilityWatchdog = null
        
        // Release wake lock
        startupWakeLock?.let { if (it.isHeld) it.release() }
        
        // Unregister receiver
        try {
            unregisterReceiver(blockReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }

        // Ensure alarm chain is active (safety net)
        // NOTE: No immediate self-restart here. That creates a crash-restart loop
        // that triggers MIUI's "App keeps stopping" detection. START_STICKY in
        // onStartCommand + AlarmManager + JobService handle persistence.
        resurrector.scheduleAlarmChain(this)

        super.onDestroy()
    }
}
