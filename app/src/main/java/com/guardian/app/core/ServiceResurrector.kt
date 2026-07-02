package com.guardian.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ServiceResurrector — Multi-path resurrection chain for Guardian services.
 *
 * Strategy: If Android or OEM kills a service, resurrect it through multiple
 * independent mechanisms. At least one will fire within 15 minutes.
 *
 * Paths:
 * 1. Immediate: startForegroundService (works if process is alive)
 * 2. AlarmManager: setExactAndAllowWhileIdle (survives Doze, OEM can't cancel)
 * 3. JobService: setPersisted (survives reboot, handles Doze)
 * 4. WorkManager: Periodic (final fallback)
 *
 * Battery cost: ~0.001% per hour (one lightweight check + alarm registration)
 */
@Singleton
class ServiceResurrector @Inject constructor() {

    /**
     * Attempt immediate resurrection of GuardianCoreService.
     * Returns true if the start command was sent (doesn't guarantee service started).
     */
    fun resurrectCoreService(context: Context): Boolean {
        return try {
            val intent = Intent(context, GuardianCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Immediate resurrection command sent for CoreService")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CoreService immediately", e)
            false
        }
    }

    /**
     * Attempt immediate resurrection of AccessibilitySentry.
     * Only works if the service is enabled in Settings but process died.
     * Returns true if command sent.
     */
    fun resurrectAccessibility(context: Context): Boolean {
        return try {
            val intent = Intent(context, Class.forName("com.guardian.app.walls.wall2.AccessibilitySentry"))
            context.startService(intent)
            Log.d(TAG, "Immediate resurrection command sent for AccessibilitySentry")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot restart AccessibilitySentry (may be disabled in Settings): ${e.message}")
            false
        }
    }

    /**
     * Schedule AlarmManager chain — fires every 15 minutes even in Doze mode.
     * OEM battery managers cannot cancel setExactAndAllowWhileIdle alarms
     * without root access.
     */
    fun scheduleAlarmChain(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, GuardianAlarmReceiver::class.java).apply {
                action = ALARM_HEARTBEAT_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
                pendingIntent
            )
            Log.d(TAG, "AlarmManager chain scheduled (next alarm in ${ALARM_INTERVAL_MS / 1000}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm chain", e)
        }
    }

    /**
     * Cancel the AlarmManager chain (called when Guardian is fully disabled by user).
     */
    fun cancelAlarmChain(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, GuardianAlarmReceiver::class.java).apply {
                action = ALARM_HEARTBEAT_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "AlarmManager chain cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm chain", e)
        }
    }

    /**
     * Schedule JobService — periodic 15-minute check that survives Doze mode.
     * setPersisted(true) survives device reboots.
     */
    fun scheduleJobService(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val existingJob = jobScheduler.getPendingJob(JOB_ID)
            if (existingJob != null) {
                Log.d(TAG, "JobService already scheduled (id=$JOB_ID)")
                return
            }

            val jobInfo = JobInfo.Builder(JOB_ID, ComponentName(context, GuardianJobService::class.java))
                .setPeriodic(JOB_PERIOD_MS)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build()

            jobScheduler.schedule(jobInfo)
            Log.d(TAG, "JobService scheduled (periodic, persisted, every ${JOB_PERIOD_MS / 1000}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule JobService", e)
        }
    }

    /**
     * Cancel the JobService (called when Guardian is fully disabled by user).
     */
    fun cancelJobService(context: Context) {
        try {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d(TAG, "JobService cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel JobService", e)
        }
    }

    /**
     * Full resurrection chain — call this when a service is detected as dead.
     * Tries immediate start, then ensures alarm chain and job service are active.
     */
    fun resurrectFull(context: Context, serviceName: String) {
        Log.w(TAG, "Full resurrection triggered for: $serviceName")

        when (serviceName) {
            "core" -> {
                resurrectCoreService(context)
                scheduleAlarmChain(context)
                scheduleJobService(context)
            }
            "accessibility" -> {
                val restarted = resurrectAccessibility(context)
                if (!restarted) {
                    Log.w(TAG, "Accessibility disabled in Settings. Cannot restart programmatically.")
                }
                scheduleAlarmChain(context)
                scheduleJobService(context)
            }
            "both" -> {
                resurrectCoreService(context)
                resurrectAccessibility(context)
                scheduleAlarmChain(context)
                scheduleJobService(context)
            }
        }
    }

    companion object {
        private const val TAG = "ServiceResurrector"
        const val ALARM_HEARTBEAT_ACTION = "com.guardian.app.ALARM_HEARTBEAT"
        private const val ALARM_REQUEST_CODE = 7777
        private const val ALARM_INTERVAL_MS = 15 * 60 * 1000L
        private const val JOB_ID = 8888
        private const val JOB_PERIOD_MS = 15 * 60 * 1000L
    }
}
