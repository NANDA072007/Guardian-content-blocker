package com.guardian.app.domain

import com.guardian.app.core.AuthenticationManager
import com.guardian.app.core.CooldownTimerManager
import com.guardian.app.util.CryptoUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InitiateCooloffUseCase @Inject constructor(
    private val auth: AuthenticationManager,
    private val cooldownTimer: CooldownTimerManager
) {
    companion object {
        const val COOLDOWN_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    sealed class Result {
        data object Success : Result()
        data object WrongCode : Result()
        data class PenaltyActive(val penaltyMinutes: Long) : Result()
        data object AlreadyCoolingOff : Result()
    }

    fun execute(enteredCode: String): Result {
        val storedHash = auth.getMasterKeyHash() ?: return Result.WrongCode
        val enteredHash = CryptoUtils.sha256(enteredCode.toByteArray())

        if (!CryptoUtils.constantTimeEquals(enteredHash.toByteArray(), storedHash.toByteArray())) {
            val fails = cooldownTimer.incrementFailedAttempts()
            if (fails >= 5) {
                val penaltyMinutes = when {
                    fails == 5 -> 1L
                    fails == 6 -> 5L
                    else -> 30L
                }
                cooldownTimer.setCooloff(penaltyMinutes * 60 * 1000)
                return Result.PenaltyActive(penaltyMinutes)
            }
            return Result.WrongCode
        }

        cooldownTimer.resetFailedAttempts()

        if (isCooloffActive()) {
            return Result.AlreadyCoolingOff
        }

        val newUnlockTime = System.currentTimeMillis() + COOLDOWN_DURATION_MS
        auth.setUninstallUnlockTime(newUnlockTime)
        return Result.Success
    }

    fun isCooloffActive(): Boolean {
        val unlockTime = auth.getUninstallUnlockTime()
        return unlockTime > 0L && System.currentTimeMillis() < unlockTime
    }

    fun getRemainingMs(): Long {
        val unlockTime = auth.getUninstallUnlockTime()
        if (unlockTime == 0L) return 0L
        return maxOf(0L, unlockTime - System.currentTimeMillis())
    }

    fun getTrustedPersonContact(): String? = auth.getTrustedPersonContact()
}
