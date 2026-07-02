package com.guardian.app.walls.wall2.adapter

import java.util.UUID

data class AuditEntry(
    val timestamp: Long,
    val eventId: UUID,
    val eventType: String,
    val decision: String,
    val source: String,
    val summary: String
)

interface AuditTrail {
    fun append(entry: AuditEntry)
    fun recent(count: Int): List<AuditEntry>
    fun clear()
    fun count(): Int
}
