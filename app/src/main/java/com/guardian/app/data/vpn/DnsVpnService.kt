package com.guardian.app.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat

class DnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
                // Use CleanBrowsing Adult Filter DNS Servers
                .addDnsServer("185.228.168.10")
                .addDnsServer("185.228.169.11")
                // Dummy routing: route a local IP to the VPN so the VPN connects,
                // but let the system route actual internet (including DNS) via physical network.
                // This forces Android to use our DNS without us needing to process all packets.
                .addAddress("10.0.0.2", 32)
                .addRoute("10.0.0.2", 32)
                .establish()

            // Register network change listener for instant reconnection
            registerNetworkCallback()

            Log.d("DnsVpnService", "VPN started successfully")
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to start VPN", e)
        }
    }

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("DnsVpnService", "Network available — checking VPN state")
                    // If VPN interface died during network switch, reconnect
                    if (vpnInterface == null) {
                        Log.d("DnsVpnService", "VPN interface is null, reconnecting...")
                        reconnectVpn()
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.d("DnsVpnService", "Network lost — VPN will reconnect when network returns")
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to register network callback", e)
        }
    }

    private fun reconnectVpn() {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        try {
            vpnInterface = Builder()
                .setSession("Guardian DNS")
                .addDnsServer("185.228.168.10")
                .addDnsServer("185.228.169.11")
                .addAddress("10.0.0.2", 32)
                .addRoute("10.0.0.2", 32)
                .establish()
            Log.d("DnsVpnService", "VPN reconnected successfully")
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to reconnect VPN", e)
        }
    }

    // Called when another VPN app takes over — Guardian's VPN is forcibly revoked
    override fun onRevoke() {
        Log.w("DnsVpnService", "VPN REVOKED — another app took over VPN!")
        vpnInterface = null

        // Show a high-priority warning notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Protection Disabled!")
            .setContentText("Another VPN app disabled Guardian's DNS protection. Open Guardian to fix.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1004, notification)

        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Guardian VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "VPN service for domain blocking"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Guardian is Active")
        .setContentText("Stay focused. Stay strong. Your journey is protected.")
        .setSmallIcon(android.R.drawable.ic_secure)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun stopVpn() {
        try {
            // Unregister network callback
            networkCallback?.let {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
            networkCallback = null

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
        try {
            networkCallback?.let {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
    }
}
