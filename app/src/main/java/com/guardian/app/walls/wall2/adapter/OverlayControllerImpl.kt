package com.guardian.app.walls.wall2.adapter

import android.content.Context
import android.util.Log
import com.guardian.app.ui.overlay.BlockOverlayManager
import com.guardian.app.walls.wall2.event.ProtectionEvent
import com.guardian.app.walls.wall2.event.Decision
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockOverlayManager: BlockOverlayManager
) : OverlayController {

    private data class QueuedOverlay(
        val event: ProtectionEvent,
        val strategy: OverlayStrategy
    )

    private val queue = mutableListOf<QueuedOverlay>()
    @Volatile private var isShowing = false

    override fun showBlock(event: ProtectionEvent, strategy: OverlayStrategy) {
        when (strategy) {
            OverlayStrategy.Replace -> {
                queue.clear()
                queue.add(QueuedOverlay(event, strategy))
                showNext()
            }
            OverlayStrategy.Queue -> {
                val priority = priorityOf(event)
                val insertIndex = queue.indexOfFirst { priorityOf(it.event) < priority }
                if (insertIndex >= 0) {
                    queue.add(insertIndex, QueuedOverlay(event, strategy))
                } else {
                    queue.add(QueuedOverlay(event, strategy))
                }
                if (!isShowing) showNext()
            }
            OverlayStrategy.Merge -> {
                val existing = queue.firstOrNull()
                if (existing != null) {
                    queue[0] = existing
                } else {
                    queue.add(QueuedOverlay(event, strategy))
                }
                if (!isShowing) showNext()
            }
            OverlayStrategy.IgnoreLowerPriority -> {
                val currentPriority = if (queue.isNotEmpty()) priorityOf(queue.first().event) else 0
                if (priorityOf(event) > currentPriority) {
                    queue.clear()
                    queue.add(QueuedOverlay(event, strategy))
                    showNext()
                }
            }
        }
    }

    override fun dismissBlock() {
        isShowing = false
        queue.removeFirstOrNull()
        if (queue.isNotEmpty()) showNext()
    }

    override fun getQueueDepth(): Int = queue.size

    private fun showNext() {
        if (queue.isEmpty()) return
        isShowing = true
        Log.d("OverlayController", "Showing block for: ${queue.first().event.source}")
        blockOverlayManager.showOverlay()
    }

    private fun priorityOf(event: ProtectionEvent): Int = when (event.severity) {
        com.guardian.app.walls.wall2.event.Severity.CRITICAL -> 100
        com.guardian.app.walls.wall2.event.Severity.HIGH -> 75
        com.guardian.app.walls.wall2.event.Severity.MEDIUM -> 50
        com.guardian.app.walls.wall2.event.Severity.LOW -> 25
        com.guardian.app.walls.wall2.event.Severity.INFO -> 10
    }
}
