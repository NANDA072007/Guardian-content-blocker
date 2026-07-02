package com.guardian.app.core

import com.guardian.app.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CooldownTimerManager @Inject constructor(
    private val config: ConfigurationManager,
    private val timeProvider: TimeProvider
) {

    fun getFailedAttempts(): Int = config.getFailedAttempts()

    fun incrementFailedAttempts(): Int {
        val count = getFailedAttempts() + 1
        config.setFailedAttempts(count)
        return count
    }

    fun resetFailedAttempts() {
        config.setFailedAttempts(0)
        config.setCooloffEndTime(0L)
        config.setCooloffStartUptime(0L)
        config.setCooloffDuration(0L)
    }

    fun setCooloff(durationMs: Long) {
        val endWallTime = timeProvider.currentTimeMillis() + durationMs
        val startUptime = timeProvider.elapsedRealtime()
        config.setCooloffEndTime(endWallTime)
        config.setCooloffStartUptime(startUptime)
        config.setCooloffDuration(durationMs)
    }

    fun getRemainingCooloffMs(): Long {
        val endWallTime = config.getCooloffEndTime()
        val startUptime = config.getCooloffStartUptime()
        val durationMs = config.getCooloffDuration()

        if (endWallTime == 0L || startUptime == 0L || durationMs == 0L) return 0L

        val currentWallTime = timeProvider.currentTimeMillis()
        val currentUptime = timeProvider.elapsedRealtime()

        val uptimePassed = currentUptime - startUptime
        val uptimeRemaining = durationMs - uptimePassed
        val wallTimeRemaining = endWallTime - currentWallTime

        return maxOf(0L, maxOf(uptimeRemaining, wallTimeRemaining))
    }

    fun setProtectionPaused(pauseUntilMs: Long) {
        config.setProtectionPausedUntil(pauseUntilMs)
    }

    fun isProtectionPaused(): Boolean {
        val pauseUntil = config.getProtectionPausedUntil()
        if (pauseUntil == 0L) return false
        if (timeProvider.currentTimeMillis() < pauseUntil) return true
        config.setProtectionPausedUntil(0L)
        return false
    }

    fun getRemainingPauseMs(): Long {
        val pauseUntil = config.getProtectionPausedUntil()
        if (pauseUntil == 0L) return 0L
        val remaining = pauseUntil - timeProvider.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
}
