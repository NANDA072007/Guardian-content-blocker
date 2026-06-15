package com.guardian.app.data.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ServiceRestartReceiver", "Broadcast Received: ${intent.action}")
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "com.guardian.RESTART_SERVICE" ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val serviceIntent = Intent(context, GuardianCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Kickstart the resurrection chains
            GuardianCoreService.scheduleAlarmChain(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                GuardianJobService.scheduleJob(context)
            }
        }
    }
}
