package com.guardian.app.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val protectionState: ProtectionStateManager,
    private val cooldownTimer: CooldownTimerManager,
    private val auth: AuthenticationManager
) {
    fun isSetupComplete(): Boolean = protectionState.isSetupComplete()
    fun setSetupComplete(isComplete: Boolean) = protectionState.setSetupComplete(isComplete)

    fun getMasterKeyHash(): String? = auth.getMasterKeyHash()
    fun setMasterKeyHash(hash: String) = auth.setMasterKeyHash(hash)

    fun getTrustedPersonContact(): String? = auth.getTrustedPersonContact()
    fun setTrustedPersonContact(contact: String) = auth.setTrustedPersonContact(contact)

    fun getUninstallUnlockTime(): Long = auth.getUninstallUnlockTime()
    fun setUninstallUnlockTime(timeMs: Long) = auth.setUninstallUnlockTime(timeMs)

    fun isWall1Enabled(): Boolean = protectionState.isWall1Enabled()
    fun setWall1Enabled(enabled: Boolean) = protectionState.setWall1Enabled(enabled)

    fun isWall2Enabled(): Boolean = protectionState.isWall2Enabled()
    fun setWall2Enabled(enabled: Boolean) = protectionState.setWall2Enabled(enabled)

    fun isOemOptimizationAcknowledged(): Boolean = protectionState.isOemOptimizationAcknowledged()
    fun setOemOptimizationAcknowledged(acknowledged: Boolean) = protectionState.setOemOptimizationAcknowledged(acknowledged)

    fun getFailedAttempts(): Int = cooldownTimer.getFailedAttempts()
    fun incrementFailedAttempts(): Int = cooldownTimer.incrementFailedAttempts()
    fun resetFailedAttempts() = cooldownTimer.resetFailedAttempts()

    fun setCooloff(durationMs: Long) = cooldownTimer.setCooloff(durationMs)
    fun getRemainingCooloffMs(): Long = cooldownTimer.getRemainingCooloffMs()

    fun setProtectionPaused(pauseUntilMs: Long) = cooldownTimer.setProtectionPaused(pauseUntilMs)
    fun isProtectionPaused(): Boolean = cooldownTimer.isProtectionPaused()
    fun getRemainingPauseMs(): Long = cooldownTimer.getRemainingPauseMs()

    fun verifyMasterKey(key: String): Boolean = auth.verifyMasterKey(key)
}
