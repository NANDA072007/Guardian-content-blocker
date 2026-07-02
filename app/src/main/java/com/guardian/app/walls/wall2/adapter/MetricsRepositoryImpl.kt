package com.guardian.app.walls.wall2.adapter

import com.guardian.app.walls.wall2.event.Decision
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsRepositoryImpl @Inject constructor() : MetricsRepository {

    private val processingTimes = ConcurrentLinkedDeque<Long>()
    private val traversalTimes = ConcurrentLinkedDeque<Long>()
    private val traversalNodes = ConcurrentLinkedDeque<Int>()
    private val traversalDepth = ConcurrentLinkedDeque<Int>()
    private val overlayLatencies = ConcurrentLinkedDeque<Long>()

    @Volatile private var droppedEvents = 0L
    @Volatile private var recoveryCount = 0L
    @Volatile private var cacheHits = 0L
    @Volatile private var cacheMisses = 0L
    @Volatile private var falsePositives = 0L
    @Volatile private var maxQueueDepth = 0
    @Volatile private var lastMemorySnapshot = 0L

    override fun recordEventProcessingTime(ms: Long) {
        processingTimes.addLast(ms)
        if (processingTimes.size > 1000) processingTimes.removeFirst()
    }

    override fun recordOverlayLatency(ms: Long) {
        overlayLatencies.addLast(ms)
        if (overlayLatencies.size > 100) overlayLatencies.removeFirst()
    }

    override fun recordTreeTraversal(nodes: Int, depth: Int, ms: Long) {
        traversalTimes.addLast(ms)
        traversalNodes.addLast(nodes)
        traversalDepth.addLast(depth)
        if (traversalTimes.size > 1000) traversalTimes.removeFirst()
        if (traversalNodes.size > 1000) traversalNodes.removeFirst()
        if (traversalDepth.size > 1000) traversalDepth.removeFirst()
    }

    override fun recordDroppedEvent(category: String) { droppedEvents++ }
    override fun recordRecovery() { recoveryCount++ }
    override fun recordCacheHitRate(hits: Long, misses: Long) {
        cacheHits += hits
        cacheMisses += misses
    }

    override fun recordDecision(decision: Decision) {
        if (decision is Decision.Observe) falsePositives++
    }

    override fun snapshot(): MetricsSnapshot {
        val now = Runtime.getRuntime()
        lastMemorySnapshot = (now.totalMemory() - now.freeMemory()) / (1024 * 1024)

        val sortedTimes = processingTimes.toList().sorted()
        val p95 = if (sortedTimes.isEmpty()) 0.0
            else sortedTimes[(sortedTimes.size * 0.95).toInt().coerceIn(0, sortedTimes.size - 1)].toDouble()

        return MetricsSnapshot(
            avgEventLatencyMs = averageLong(processingTimes),
            p95EventLatencyMs = p95,
            avgTreeTraversalMs = averageLong(traversalTimes),
            avgTreeNodes = averageInt(traversalNodes),
            avgTreeDepth = averageInt(traversalDepth),
            maxQueueDepth = maxQueueDepth,
            avgOverlayDurationMs = averageLong(overlayLatencies),
            droppedEvents = droppedEvents,
            recoveryCount = recoveryCount,
            cacheHitRate = if (cacheHits + cacheMisses > 0) cacheHits.toDouble() / (cacheHits + cacheMisses) else 0.0,
            falsePositiveCount = falsePositives,
            memoryUsageMb = lastMemorySnapshot
        )
    }

    private fun averageLong(deque: ConcurrentLinkedDeque<Long>): Double {
        if (deque.isEmpty()) return 0.0
        return deque.sum().toDouble() / deque.size
    }

    private fun averageInt(deque: ConcurrentLinkedDeque<Int>): Double {
        if (deque.isEmpty()) return 0.0
        return deque.sum().toDouble() / deque.size
    }
}
