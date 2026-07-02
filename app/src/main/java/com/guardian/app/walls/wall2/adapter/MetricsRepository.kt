package com.guardian.app.walls.wall2.adapter

import com.guardian.app.walls.wall2.event.Decision

interface MetricsRepository {
    fun recordEventProcessingTime(ms: Long)
    fun recordOverlayLatency(ms: Long)
    fun recordTreeTraversal(nodes: Int, depth: Int, ms: Long)
    fun recordDroppedEvent(category: String)
    fun recordRecovery()
    fun recordCacheHitRate(hits: Long, misses: Long)
    fun recordDecision(decision: Decision)
    fun snapshot(): MetricsSnapshot
}

data class MetricsSnapshot(
    val avgEventLatencyMs: Double = 0.0,
    val p95EventLatencyMs: Double = 0.0,
    val avgTreeTraversalMs: Double = 0.0,
    val avgTreeNodes: Double = 0.0,
    val avgTreeDepth: Double = 0.0,
    val maxQueueDepth: Int = 0,
    val avgOverlayDurationMs: Double = 0.0,
    val droppedEvents: Long = 0,
    val recoveryCount: Long = 0,
    val cacheHitRate: Double = 0.0,
    val falsePositiveCount: Long = 0,
    val memoryUsageMb: Long = 0
)
