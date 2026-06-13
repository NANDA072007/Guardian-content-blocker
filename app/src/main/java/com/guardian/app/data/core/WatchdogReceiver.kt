package com.guardian.app.data.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.guardian.app.data.SecurityManager
import com.guardian.app.data.vpn.DnsVpnService

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("WatchdogReceiver", "Hardware Alarm triggered. Checking Guardian health...")
        val securityManager = SecurityManager(context)

        // Resurrect Core Service if any walls are meant to be active
        if (securityManager.isWall1Enabled() || securityManager.isWall2Enabled()) {
            val coreIntent = Intent(context, GuardianCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(coreIntent)
                } catch (e: Exception) {
                    Log.e("WatchdogReceiver", "Failed to start Core Service", e)
                }
            } else {
                context.startService(coreIntent)
            }
        }

        // Resurrect VPN if Wall 1 is meant to be active
        if (securityManager.isWall1Enabled()) {
            val vpnIntent = android.net.VpnService.prepare(context)
            if (vpnIntent == null) {
                val intentVpn = Intent(context, DnsVpnService::class.java)
                intentVpn.action = DnsVpnService.ACTION_START_VPN
                try {
                    context.startService(intentVpn)
                } catch (e: Exception) {
                    Log.e("WatchdogReceiver", "Failed to start VPN Service", e)
                }
            }
        }
    }
}
