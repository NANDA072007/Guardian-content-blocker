package com.guardian.app.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationManager @Inject constructor(
    private val config: ConfigurationManager
) {

    fun getMasterKeyHash(): String? = config.getMasterKeyHash()
    fun setMasterKeyHash(hash: String) = config.setMasterKeyHash(hash)

    fun getTrustedPersonContact(): String? = config.getTrustedPersonContact()
    fun setTrustedPersonContact(contact: String) = config.setTrustedPersonContact(contact)

    fun getUninstallUnlockTime(): Long = config.getUninstallUnlockTime()
    fun setUninstallUnlockTime(timeMs: Long) = config.setUninstallUnlockTime(timeMs)

    fun verifyMasterKey(key: String): Boolean {
        val storedHash = getMasterKeyHash() ?: return false
        val inputHash = com.guardian.app.util.CryptoUtils.sha256(key.toByteArray())
        return storedHash == inputHash
    }
}

