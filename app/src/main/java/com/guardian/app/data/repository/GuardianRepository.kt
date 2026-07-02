package com.guardian.app.data.repository

import android.content.Context
import com.guardian.app.core.SecurityManager
import com.guardian.app.data.db.GuardianDatabase
import com.guardian.app.data.db.entities.DomainBlocklist
import com.guardian.app.data.db.entities.BlockEvent
import com.guardian.app.util.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuardianRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager
) {
    private val db by lazy { GuardianDatabase.getInstance(context) }

    // Synchronous methods for performance-critical paths (VPN packet processing)
    fun isDomainBlockedSync(hash: String): Boolean = db.domainDao().isDomainBlocked(hash) > 0
    fun logBlockEventSync(wallSource: String) {
        db.blockEventDao().logBlockEvent(BlockEvent(timestamp = System.currentTimeMillis(), wallSource = wallSource))
    }

    // Coroutine-safe suspend methods
    suspend fun isDomainBlocked(hash: String): Boolean = withContext(Dispatchers.IO) {
        db.domainDao().isDomainBlocked(hash) > 0
    }

    suspend fun getAllDomainHashes(): List<String> = withContext(Dispatchers.IO) {
        db.domainDao().getAllDomainHashes()
    }

    suspend fun insertDomain(hash: String, category: String) = withContext(Dispatchers.IO) {
        db.domainDao().insertDomain(DomainBlocklist(hash, category))
    }

    suspend fun logBlockEvent(wallSource: String) = withContext(Dispatchers.IO) {
        db.blockEventDao().logBlockEvent(BlockEvent(timestamp = System.currentTimeMillis(), wallSource = wallSource))
    }

    suspend fun getDaysCleanStreak(): Int = withContext(Dispatchers.IO) {
        val lastRelapseTimestamp = db.blockEventDao().getLastRelapseTimestamp() ?: 0L
        val baselineTime = if (lastRelapseTimestamp == 0L) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.firstInstallTime
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } else {
            lastRelapseTimestamp
        }
        val days = db.blockEventDao().getDaysSince(baselineTime)
        days + 1
    }

    suspend fun insertDomains(hashes: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        db.domainDao().insertDomains(hashes.map { DomainBlocklist(it.first, it.second) })
    }

    fun getSecurityManager(): SecurityManager = securityManager
}
