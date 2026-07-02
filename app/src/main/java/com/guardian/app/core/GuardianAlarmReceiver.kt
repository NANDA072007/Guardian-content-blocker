package com.guardian.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import android.util.Log
import dagger.hilt.android.EntryPointAccessors

/**
 * GuardianAlarmReceiver — AlarmManager chain resurrection.
 *
 * This receiver fires every 15 minutes via setExactAndAllowWhileIdle.
 * OEM battery managers cannot cancel these alarms without root access.
 *
 * On each fire:
 * 1. Check if GuardianCoreService is running
 * 2. If dead → restart it
 * 3. Schedule the next alarm (chain continues)
 */
class GuardianAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ServiceResurrector.ALARM_HEARTBEAT_ACTION) return

        val entryPoint = EntryPointAccessors.fromApplication(context, ServiceResurrectorEntryPoint::class.java)
        val resurrector = entryPoint.serviceResurrector()
        val securityManager = entryPoint.securityManager()

        Log.d(TAG, "Alarm heartbeat fired — checking Guardian health...")

        if (!GuardianCoreService.isRunning(context)) {
            Log.w(TAG, "CoreService is dead during alarm heartbeat. Resurrecting...")
            resurrector.resurrectCoreService(context)
        }

        if (securityManager.isWall2Enabled()) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val isRunning = enabledServices.any {
                it.resolveInfo.serviceInfo.name == "com.guardian.app.walls.wall2.AccessibilitySentry"
            }

            if (!isRunning) {
                Log.w(TAG, "Accessibility service not running during alarm heartbeat.")
                resurrector.resurrectAccessibility(context)
            }
        }

        resurrector.scheduleAlarmChain(context)
        Log.d(TAG, "Next alarm scheduled. Chain continues.")
    }

    companion object {
        private const val TAG = "GuardianAlarmReceiver"
    }
}
