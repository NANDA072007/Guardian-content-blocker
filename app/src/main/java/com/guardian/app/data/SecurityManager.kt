package com.guardian.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurityManager(context: Context) {

    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "guardian_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isSetupComplete(): Boolean {
        return sharedPreferences.getBoolean("setup_complete", false)
    }

    fun setSetupComplete(isComplete: Boolean) {
        sharedPreferences.edit().putBoolean("setup_complete", isComplete).apply()
    }

    fun getMasterKeyHash(): String? {
        return sharedPreferences.getString("master_key_hash", null)
    }

    fun setMasterKeyHash(hash: String) {
        sharedPreferences.edit().putString("master_key_hash", hash).apply()
    }

    fun getTrustedPersonContact(): String? {
        return sharedPreferences.getString("trusted_person_contact", null)
    }

    fun setTrustedPersonContact(contact: String) {
        sharedPreferences.edit().putString("trusted_person_contact", contact).apply()
    }

    fun getUninstallUnlockTime(): Long {
        return sharedPreferences.getLong("uninstall_unlock_time", 0L)
    }

    fun setUninstallUnlockTime(timeMs: Long) {
        sharedPreferences.edit().putLong("uninstall_unlock_time", timeMs).apply()
    }

    fun isWall1Enabled(): Boolean {
        return sharedPreferences.getBoolean("wall_1_enabled", false)
    }

    fun setWall1Enabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("wall_1_enabled", enabled).apply()
    }

    fun isWall2Enabled(): Boolean {
        return sharedPreferences.getBoolean("wall_2_enabled", false)
    }

    fun setWall2Enabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("wall_2_enabled", enabled).apply()
    }
    fun isOemOptimizationAcknowledged(): Boolean {
        return sharedPreferences.getBoolean("oem_optimization_acknowledged", false)
    }

    fun setOemOptimizationAcknowledged(acknowledged: Boolean) {
        sharedPreferences.edit().putBoolean("oem_optimization_acknowledged", acknowledged).apply()
    }
}
