package com.guardian.app.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START_VPN = "com.guardian.app.START_VPN"
        const val ACTION_STOP_VPN = "com.guardian.app.STOP_VPN"
        private const val CHANNEL_ID = "GuardianVpnChannel"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> startVpn()
            ACTION_STOP_VPN -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            vpnInterface = Builder()
                .setSession("Guardian DNS")
                // Dummy routing to establish VPN icon without breaking internet.
                // Actual heavy-lifting is done by AccessibilitySentry for Wall 2/3.
                .addAddress("10.0.0.2", 32)
                .addRoute("10.0.0.2", 32)
                .establish()

        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to start VPN", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Guardian VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "VPN service for domain blocking"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Guardian DNS Active")
        .setContentText("Blocking adult domains at network level")
        .setSmallIcon(android.R.drawable.ic_secure)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
