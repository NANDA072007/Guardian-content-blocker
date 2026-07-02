package com.guardian.app.walls.wall2.adapter

import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditTrailImpl @Inject constructor() : AuditTrail {

    private val entries = ConcurrentLinkedDeque<AuditEntry>()

    override fun append(entry: AuditEntry) {
        entries.addFirst(entry)
        if (entries.size > 500) entries.removeLast()
    }

    override fun recent(count: Int): List<AuditEntry> {
        return entries.take(count)
    }

    override fun clear() { entries.clear() }

    override fun count(): Int = entries.size
}
