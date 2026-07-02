package com.guardian.app.util

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface contract to retrieve current wall time and elapsed uptime.
 * Avoids direct static dependencies on [System.currentTimeMillis] and [SystemClock.elapsedRealtime].
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
}

@Singleton
class RealTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
