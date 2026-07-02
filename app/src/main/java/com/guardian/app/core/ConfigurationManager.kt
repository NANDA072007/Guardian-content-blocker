package com.guardian.app.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConfigurationManager — Unified single source of truth for persistent settings and states.
 * Accesses EncryptedStateStore to manage security credentials, active walls, and policy variables.
 */
@Singleton
class ConfigurationManager @Inject constructor(
    private val store: EncryptedStateStore
) {
    fun isSetupComplete(): Boolean = store.getBoolean("setup_complete", false)
    fun setSetupComplete(isComplete: Boolean) = store.putBoolean("setup_complete", isComplete)

    fun isWall1Enabled(): Boolean = store.getBoolean("wall_1_enabled", false)
    fun setWall1Enabled(enabled: Boolean) = store.putBoolean("wall_1_enabled", enabled)

    fun isWall2Enabled(): Boolean = store.getBoolean("wall_2_enabled", false)
    fun setWall2Enabled(enabled: Boolean) = store.putBoolean("wall_2_enabled", enabled)

    fun isOemOptimizationAcknowledged(): Boolean = store.getBoolean("oem_optimization_acknowledged", false)
    fun setOemOptimizationAcknowledged(acknowledged: Boolean) = store.putBoolean("oem_optimization_acknowledged", acknowledged)

    fun getMasterKeyHash(): String? = store.getString("master_key_hash")
    fun setMasterKeyHash(hash: String) = store.putString("master_key_hash", hash)

    fun getTrustedPersonContact(): String? = store.getString("trusted_person_contact")
    fun setTrustedPersonContact(contact: String) = store.putString("trusted_person_contact", contact)

    fun getUninstallUnlockTime(): Long = store.getLong("uninstall_unlock_time", 0L)
    fun setUninstallUnlockTime(timeMs: Long) = store.putLong("uninstall_unlock_time", timeMs)

    fun getFailedAttempts(): Int = store.getInt("failed_unlock_attempts", 0)
    fun setFailedAttempts(attempts: Int) = store.putInt("failed_unlock_attempts", attempts)

    fun getCooloffEndTime(): Long = store.getLong("cooloff_end_time", 0L)
    fun setCooloffEndTime(timeMs: Long) = store.putLong("cooloff_end_time", timeMs)

    fun getCooloffStartUptime(): Long = store.getLong("cooloff_start_uptime", 0L)
    fun setCooloffStartUptime(timeMs: Long) = store.putLong("cooloff_start_uptime", timeMs)

    fun getCooloffDuration(): Long = store.getLong("cooloff_duration", 0L)
    fun setCooloffDuration(durationMs: Long) = store.putLong("cooloff_duration", durationMs)

    fun getProtectionPausedUntil(): Long = store.getLong("protection_paused_until", 0L)
    fun setProtectionPausedUntil(timeMs: Long) = store.putLong("protection_paused_until", timeMs)
}
