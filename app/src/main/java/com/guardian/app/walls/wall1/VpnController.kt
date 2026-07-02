package com.guardian.app.walls.wall1

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface contract for controlling the state and observing the status of the VPN service.
 */
interface VpnController {
    fun start()
    fun stop()
    fun restart()
    fun status(): StateFlow<VpnState>
    fun updateStatus(state: VpnState)
}
