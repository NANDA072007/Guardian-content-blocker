package com.guardian.app.data.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class GuardianCoreService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var blockOverlayManager: com.guardian.app.ui.overlay.BlockOverlayManager? = null
    private var startupWakeLock: android.os.PowerManager.WakeLock? = null
    private val blockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SHOW_BLOCK_OVERLAY) {
                Log.d("GuardianCoreService", "Block trigger received. Showing overlay.")
                blockOverlayManager?.showOverlay()
            }
        }
    }
    
    companion object {
        const val CHANNEL_ID = "GuardianCoreChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_CORE = "com.guardian.app.START_CORE"
        const val ACTION_SHOW_BLOCK_OVERLAY = "com.guardian.app.ACTION_SHOW_BLOCK_OVERLAY"
        const val ALARM_ACTION = "com.guardian.ALARM_HEARTBEAT"
        const val ALARM_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
        
        fun scheduleAlarmChain(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java).apply {
                action = ALARM_ACTION
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Fallback for Android 14+ if exact alarm permission is denied
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
                    pendingIntent
                )
            }
        }
        
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (GuardianCoreService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        acquireStartupWakeLock()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        blockOverlayManager = com.guardian.app.ui.overlay.BlockOverlayManager(this)
        
        val filter = IntentFilter(ACTION_SHOW_BLOCK_OVERLAY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.registerReceiver(
                this, blockReceiver, filter, Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(blockReceiver, filter)
        }
        
        startWatchdog()
        scheduleAlarmChain(this)
        GuardianJobService.scheduleJob(this)
    }

    private fun acquireStartupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        startupWakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "Guardian::StartupLock"
        ).apply {
            acquire(30 * 1000L) // 30 seconds maximum
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GuardianCoreService", "Core Service Started")
        // Always START_STICKY to resurrect
        return START_STICKY
    }



    private fun startWatchdog() {
        val securityManager = com.guardian.app.data.SecurityManager(applicationContext)
        
        scope.launch {
            while (isActive) {
                // Keep VPN alive
                if (securityManager.isWall1Enabled()) {
                    val vpnIntent = android.net.VpnService.prepare(applicationContext)
                    if (vpnIntent == null) {
                        val intent = Intent(applicationContext, com.guardian.app.data.vpn.DnsVpnService::class.java)
                        intent.action = com.guardian.app.data.vpn.DnsVpnService.ACTION_START_VPN
                        startService(intent)
                    }
                }
                
                // Silently re-request battery exemption in background (no UI popup)
                // This uses the PowerManager whitelist API which doesn't show any dialog
                // The real protection comes from AlarmManager + JobService regardless
                try {
                    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        Log.d("GuardianCoreService", "Battery exemption not active. Resurrection system is handling it.")
                    }
                } catch (e: Exception) {
                    // Silently ignore
                }
                
                // Re-arm resurrection weapons every cycle
                scheduleAlarmChain(applicationContext)
                GuardianJobService.scheduleJob(applicationContext)
                
                delay(60_000)
            }
        }
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
            .setSmallIcon(android.R.drawable.ic_secure) // Using standard icon temporarily
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("GuardianCoreService", "App removed from recents! Resurrecting...")
        val serviceIntent = Intent(this, GuardianCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        startupWakeLock?.let { if (it.isHeld) it.release() }
        job.cancel()
        try {
            unregisterReceiver(blockReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Log.d("GuardianCoreService", "Core Service Destroyed - Attempting resurrection")

        val serviceIntent = Intent(this, GuardianCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
