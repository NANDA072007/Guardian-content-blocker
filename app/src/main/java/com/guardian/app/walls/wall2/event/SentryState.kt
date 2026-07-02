package com.guardian.app.walls.wall2.event

enum class SentryState {
    DISABLED,
    PERMISSION_GRANTED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    RESTARTING,
    ERROR,
    RECOVERING
}
