package com.guardian.app.walls.wall1

/**
 * State machine representing the lifecycle of the VPN service.
 */
enum class VpnState {
    STOPPED,
    STARTING,
    RUNNING,
    NETWORK_LOST,
    RECONNECTING,
    STOPPING,
    ERROR
}
