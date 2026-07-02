package com.guardian.app.walls.wall2.adapter

import com.guardian.app.walls.wall2.event.ProtectionEvent

interface OverlayController {
    fun showBlock(event: ProtectionEvent, strategy: OverlayStrategy)
    fun dismissBlock()
    fun getQueueDepth(): Int
}

enum class OverlayStrategy {
    Replace,
    Queue,
    Merge,
    IgnoreLowerPriority
}
