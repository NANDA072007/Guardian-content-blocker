package com.guardian.app.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.app.core.GuardianCoreService
import com.guardian.app.core.ServiceResurrectorEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that runs periodically to resurrect services if they are terminated.
 * Delegates VPN/DNS checks to [com.guardian.app.core.ProtectionOrchestrator].
 */
class WatchdogWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("WatchdogWorker", "Executing background watchdog check...")
        val entryPoint = EntryPointAccessors.fromApplication(context, ServiceResurrectorEntryPoint::class.java)
        val securityManager = entryPoint.securityManager()

        try {
            // 1. Keep VPN/DNS alive if Wall 1 is enabled
            if (securityManager.isWall1Enabled()) {
                val vpnIntent = android.net.VpnService.prepare(context)
                if (vpnIntent == null) {
                    val orchestrator = entryPoint.orchestrator()
                    withContext(Dispatchers.Main) {
                        orchestrator.startProtection()
                    }
                }
            }

            // 2. Ensure Core Service is running
            if (!GuardianCoreService.isRunning(context) && (securityManager.isWall1Enabled() || securityManager.isWall2Enabled())) {
                val serviceIntent = Intent(context, GuardianCoreService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            
            // 3. Accessibility health check
            if (securityManager.isWall2Enabled()) {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
                val isRunning = enabledServices.any { it.resolveInfo.serviceInfo.name == "com.guardian.app.walls.wall2.AccessibilitySentry" }
                
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (isRunning) {
                    nm.cancel(2001)
                } else {
                    Log.w("WatchdogWorker", "Accessibility service is not running. Showing recovery notification.")
                    showRecoveryNotification(nm)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WatchdogWorker", "Error during watchdog check", e)
            Result.retry()
        }
    }

    private fun showRecoveryNotification(nm: android.app.NotificationManager) {
        val channelId = "GuardianRecoveryChannel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Guardian Recovery", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when Guardian protection needs to be re-enabled" }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("Guardian protection interrupted")
            .setContentText("Tap to re-enable Guardian accessibility service.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(2001, notification)
    }
}
