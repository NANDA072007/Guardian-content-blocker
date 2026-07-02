package com.guardian.app.domain

import com.guardian.app.core.CooldownTimerManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PauseProtectionUseCase @Inject constructor(
    private val cooldownTimer: CooldownTimerManager
) {
    companion object {
        const val PAUSE_DURATION_MS = 5 * 60 * 1000L
    }

    fun execute(): Long {
        val pauseUntil = System.currentTimeMillis() + PAUSE_DURATION_MS
        cooldownTimer.setProtectionPaused(pauseUntil)
        return PAUSE_DURATION_MS
    }

    fun isPaused(): Boolean = cooldownTimer.isProtectionPaused()

    fun getRemainingMs(): Long = cooldownTimer.getRemainingPauseMs()
}
