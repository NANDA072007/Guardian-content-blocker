package com.guardian.app.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.app.core.ServiceResurrectorEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-shot worker that fires 30 seconds after AccessibilitySentry.onDestroy().
 * 
 * Strategy (same as Regain/Bark):
 * 1. Android often auto-reconnects the accessibility service within seconds
 *    if the process is still alive (kept alive by GuardianCoreService foreground service).
 * 2. This worker checks 30s later. If the service is back → do nothing.
 * 3. If truly dead → show ONE single notification. Not repeating. Not nagging.
 */
class AccessibilityRecoveryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "GuardianRecoveryChannel"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryPoint = EntryPointAccessors.fromApplication(context, ServiceResurrectorEntryPoint::class.java)
        val securityManager = entryPoint.securityManager()

        // Only care if Wall 2 is supposed to be enabled
        if (!securityManager.isWall2Enabled()) return@withContext Result.success()

        // Check if accessibility service is currently running
        if (isAccessibilityServiceRunning()) {
            Log.d("AccessibilityRecoveryWorker", "Service auto-recovered. No action needed.")
            // Clear any previous recovery notification
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            return@withContext Result.success()
        }

        // Service is truly dead. Show ONE notification.
        Log.w("AccessibilityRecoveryWorker", "Accessibility service is dead. Showing recovery notification.")
        showRecoveryNotification()

        Result.success()
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            val targetServiceName = "com.guardian.app.walls.wall2.AccessibilitySentry"
            enabledServices.any { it.resolveInfo.serviceInfo.name == targetServiceName }
        } catch (e: Exception) {
            Log.e("AccessibilityRecoveryWorker", "Error checking accessibility", e)
            false
        }
    }

    private fun showRecoveryNotification() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Guardian Recovery",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when Guardian protection needs to be re-enabled"
            }
            nm.createNotificationChannel(channel)
        }

        // Tapping the notification opens Accessibility Settings directly
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Guardian protection interrupted")
            .setContentText("Tap to re-enable Guardian accessibility service.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false) // Not persistent — one tap dismisses it
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }
}
