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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        blockOverlayManager = com.guardian.app.ui.overlay.BlockOverlayManager(this)
        
        val filter = IntentFilter(ACTION_SHOW_BLOCK_OVERLAY)
        androidx.core.content.ContextCompat.registerReceiver(
            this, blockReceiver, filter, Context.RECEIVER_NOT_EXPORTED
        )
        
        startWatchdog()
        scheduleResurrectionAlarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GuardianCoreService", "Core Service Started")
        // Always START_STICKY to resurrect
        return START_STICKY
    }

    private fun scheduleResurrectionAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, WatchdogReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Minimum inexact repeating interval is 15 minutes (AlarmManager.INTERVAL_FIFTEEN_MINUTES)
        // Android batches this with other system wake-ups to save battery.
        alarmManager.setInexactRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + android.app.AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            android.app.AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent
        )
        Log.d("GuardianCoreService", "Scheduled hardware resurrection alarm.")
    }

    private fun startWatchdog() {
        val securityManager = com.guardian.app.data.SecurityManager(applicationContext)
        scope.launch {
            while (isActive) {
                if (securityManager.isWall1Enabled()) {
                    val vpnIntent = android.net.VpnService.prepare(applicationContext)
                    if (vpnIntent == null) {
                        val intent = Intent(applicationContext, com.guardian.app.data.vpn.DnsVpnService::class.java)
                        intent.action = com.guardian.app.data.vpn.DnsVpnService.ACTION_START_VPN
                        startService(intent)
                    }
                }
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
