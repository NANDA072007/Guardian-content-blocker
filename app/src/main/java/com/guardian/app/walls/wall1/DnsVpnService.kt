package com.guardian.app.walls.wall1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardian.app.walls.wall1.pipeline.DnsParser
import com.guardian.app.walls.wall1.pipeline.PacketDecoder
import com.guardian.app.walls.wall1.pipeline.PacketPipeline
import com.guardian.app.walls.wall1.pipeline.ResponseWriter
import javax.inject.Inject

@dagger.hilt.android.AndroidEntryPoint
class DnsVpnService : VpnService() {

    @Inject lateinit var networkOverlayManager: com.guardian.app.ui.overlay.NetworkOverlayManager
    @Inject lateinit var repository: com.guardian.app.data.repository.GuardianRepository
    @Inject lateinit var vpnController: VpnController
    @Inject lateinit var packetDecoder: PacketDecoder
    @Inject lateinit var dnsParser: DnsParser
    @Inject lateinit var responseWriter: ResponseWriter
    @Inject lateinit var ruleEngine: com.guardian.app.walls.wall1.pipeline.RuleEngine

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetPipeline: PacketPipeline? = null

    companion object {
        const val ACTION_START_VPN = "com.guardian.app.START_VPN"
        const val ACTION_STOP_VPN = "com.guardian.app.STOP_VPN"
        private const val CHANNEL_ID = "GuardianVpnChannel"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_START_VPN) {
            startVpn()
        } else if (intent.action == ACTION_STOP_VPN) {
            stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            val builder = Builder()
                .setSession("Guardian DNS")
                .addDnsServer("8.8.8.8")
                .addAddress("10.0.0.2", 32)
                .addRoute("8.8.8.8", 32)
                // Route known DoH/DoT server IPs through TUN to block bypass attempts
                // Cloudflare
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                // Quad9
                .addRoute("9.9.9.9", 32)
                .addRoute("149.112.112.112", 32)
                // OpenDNS
                .addRoute("208.67.222.222", 32)
                .addRoute("208.67.220.220", 32)
                // CleanBrowsing
                .addRoute("185.228.168.10", 32)
                .addRoute("185.228.169.11", 32)
                // AdGuard
                .addRoute("94.140.14.14", 32)
                .addRoute("94.140.15.15", 32)
                // Comodo
                .addRoute("8.26.56.26", 32)
                .addRoute("8.20.247.20", 32)
                // NextDNS
                .addRoute("45.90.28.0", 32)
                .addRoute("45.90.30.0", 32)
                // Mullvad
                .addRoute("194.242.2.2", 32)
                .addRoute("194.242.2.3", 32)
                // DNS.SB
                .addRoute("185.222.222.222", 32)
                .addRoute("45.11.45.11", 32)

            vpnInterface = builder.establish()

            vpnInterface?.let { fd ->
                packetPipeline = PacketPipeline(
                    this, fd, { socket -> protect(socket) }, repository,
                    packetDecoder, dnsParser, responseWriter, ruleEngine
                )
                packetPipeline?.start()
            }

            vpnController.updateStatus(VpnState.RUNNING)
            Log.d("DnsVpnService", "VPN started successfully. DNS-Only Split Tunneling active.")
        } catch (e: Exception) {
            vpnController.updateStatus(VpnState.ERROR)
            Log.e("DnsVpnService", "Failed to start VPN", e)
        }
    }

    override fun onRevoke() {
        Log.w("DnsVpnService", "VPN REVOKED — another app took over VPN!")
        vpnInterface = null
        vpnController.updateStatus(VpnState.STOPPED)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Guardian Protection Disabled!")
            .setContentText("Another VPN app disabled Guardian's DNS protection. Open Guardian to fix.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1004, notification)

        networkOverlayManager.removeOverlay()
        stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("DnsVpnService", "App removed from recents! Resurrecting VPN...")
        val serviceIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_START_VPN
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Failed to resurrect VPN in onTaskRemoved", e)
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
        .setContentTitle("Guardian is Active")
        .setContentText("Stay focused. Stay strong. Your journey is protected.")
        .setSmallIcon(android.R.drawable.ic_secure)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun stopVpn() {
        try {
            packetPipeline?.stop()
            packetPipeline = null
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
        vpnController.updateStatus(VpnState.STOPPED)
        networkOverlayManager.removeOverlay()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        vpnController.updateStatus(VpnState.STOPPED)
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        super.onDestroy()
    }
}
