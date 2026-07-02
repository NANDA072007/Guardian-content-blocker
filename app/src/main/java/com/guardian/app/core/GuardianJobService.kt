package com.guardian.app.core

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.EntryPointAccessors

/**
 * GuardianJobService — Doze mode resurrection fallback.
 *
 * Android's JobService is the official way to schedule work during Doze mode.
 * With setPersisted(true), this survives device reboots.
 *
 * This service fires every 15 minutes (offset from AlarmManager) and checks
 * if GuardianCoreService is alive. If dead, restarts it.
 */
class GuardianJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ServiceResurrectorEntryPoint::class.java)
        val resurrector = entryPoint.serviceResurrector()
        val securityManager = entryPoint.securityManager()

        Log.d(TAG, "JobService fired — checking Guardian health...")

        if (!GuardianCoreService.isRunning(applicationContext)) {
            Log.w(TAG, "CoreService is dead. Resurrecting...")
            val serviceIntent = Intent(applicationContext, GuardianCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
        }

        if (securityManager.isWall2Enabled()) {
            val am = applicationContext.getSystemService(ACCESSIBILITY_SERVICE)
                as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val isRunning = enabledServices.any {
                it.resolveInfo.serviceInfo.name == "com.guardian.app.walls.wall2.AccessibilitySentry"
            }

            if (!isRunning) {
                Log.w(TAG, "Accessibility service not running. Attempting restart...")
                resurrector.resurrectAccessibility(applicationContext)
            }
        }

        resurrector.scheduleJobService(applicationContext)

        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "JobService stopped early. Rescheduling...")
        return true
    }

    companion object {
        private const val TAG = "GuardianJobService"
    }
}
