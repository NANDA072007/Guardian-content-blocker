package com.guardian.app.walls.wall1

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [VpnController] that manages service start/stop intents
 * and tracks the active [VpnState] state machine.
 */
@Singleton
class VpnControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : VpnController {

    private val _status = MutableStateFlow(VpnState.STOPPED)

    override fun start() {
        if (_status.value == VpnState.RUNNING || _status.value == VpnState.STARTING) return
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START_VPN
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            updateStatus(VpnState.STARTING)
        } catch (e: Exception) {
            updateStatus(VpnState.ERROR)
        }
    }

    override fun stop() {
        if (_status.value == VpnState.STOPPED || _status.value == VpnState.STOPPING) return
        val intent = Intent(context, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_STOP_VPN
        }
        try {
            context.startService(intent)
            updateStatus(VpnState.STOPPING)
        } catch (e: Exception) {
            updateStatus(VpnState.ERROR)
        }
    }


    override fun restart() {
        stop()
        start()
    }

    override fun status(): StateFlow<VpnState> = _status

    override fun updateStatus(state: VpnState) {
        _status.value = state
    }
}
