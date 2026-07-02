package com.guardian.app.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardian.app.walls.wall1.ConnectionMonitor
import com.guardian.app.walls.wall1.VpnController
import com.guardian.app.walls.wall1.VpnState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates execution lifecycles and states for both Wall 1 (VPN) and Wall 2 (Accessibility).
 * Subscribes to connection monitoring changes and exposes the unified [VpnState] to the application dashboard.
 */
@Singleton
class ProtectionOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: ConfigurationManager,
    private val vpnController: VpnController,
    private val connectionMonitor: ConnectionMonitor
) {
    private val _protectionState = MutableStateFlow(VpnState.STOPPED)
    val protectionState: StateFlow<VpnState> = _protectionState.asStateFlow()

    private val connectionListener = object : ConnectionMonitor.Listener {
        override fun onConnectionLost() {
            _protectionState.value = VpnState.NETWORK_LOST
            vpnController.updateStatus(VpnState.NETWORK_LOST)
            
            // Broadcast intent to show block/reconnect overlay
            val intent = Intent(context, GuardianCoreService::class.java).apply {
                action = GuardianCoreService.ACTION_SHOW_BLOCK_OVERLAY
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onConnectionValidated() {
            if (_protectionState.value == VpnState.NETWORK_LOST || _protectionState.value == VpnState.RECONNECTING) {
                _protectionState.value = VpnState.RUNNING
                vpnController.updateStatus(VpnState.RUNNING)
                vpnController.start()
            }
        }

        override fun onCaptivePortalDetected() {
            // Suspend VPN temporarily to let the captive portal login pass
            _protectionState.value = VpnState.RECONNECTING
            vpnController.updateStatus(VpnState.RECONNECTING)
            vpnController.stop()
        }
    }

    fun startProtection() {
        if (_protectionState.value == VpnState.RUNNING || _protectionState.value == VpnState.STARTING) return
        config.setWall1Enabled(true)
        _protectionState.value = VpnState.STARTING
        vpnController.updateStatus(VpnState.STARTING)

        // Ensure the core foreground service is running
        val coreIntent = Intent(context, GuardianCoreService::class.java).apply {
            action = GuardianCoreService.ACTION_START_CORE
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(coreIntent)
            } else {
                context.startService(coreIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Bind connection monitor
        connectionMonitor.startMonitoring(connectionListener)

        // Initialize VPN tunnel
        vpnController.start()
        _protectionState.value = VpnState.RUNNING
        vpnController.updateStatus(VpnState.RUNNING)
    }

    fun stopProtection() {
        if (_protectionState.value == VpnState.STOPPED || _protectionState.value == VpnState.STOPPING) return
        config.setWall1Enabled(false)
        _protectionState.value = VpnState.STOPPING
        vpnController.updateStatus(VpnState.STOPPING)

        connectionMonitor.stopMonitoring()
        vpnController.stop()

        _protectionState.value = VpnState.STOPPED
        vpnController.updateStatus(VpnState.STOPPED)
    }


    fun updateState(state: VpnState) {
        _protectionState.value = state
        vpnController.updateStatus(state)
    }
}
