package com.guardian.app.walls.wall2.event

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventDebouncer @Inject constructor() {
    private val lastEventTime = HashMap<String, Long>()

    fun shouldProcess(category: String, debounceMs: Long = 2000L): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastEventTime[category] ?: 0L
        return if (now - lastTime >= debounceMs) {
            lastEventTime[category] = now
            true
        } else {
            false
        }
    }

    fun reset(category: String) {
        lastEventTime.remove(category)
    }

    fun clear() {
        lastEventTime.clear()
    }

    fun remainingMs(category: String): Long {
        val now = System.currentTimeMillis()
        val lastTime = lastEventTime[category] ?: 0L
        return (lastTime + 2000L) - now
    }
}
