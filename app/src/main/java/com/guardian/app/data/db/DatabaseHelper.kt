package com.guardian.app.data.db

import android.content.Context
import java.util.Calendar
import com.guardian.app.data.db.entities.DomainBlocklist
import com.guardian.app.data.db.entities.BlockEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseHelper @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    private val db = GuardianDatabase.getInstance(context)

    fun isDomainBlocked(hash: String): Boolean {
        return db.domainDao().isDomainBlocked(hash) > 0
    }

    fun insertDomain(hash: String, category: String) {
        db.domainDao().insertDomain(DomainBlocklist(hash, category))
    }

    fun logBlockEvent(wallSource: String) {
        db.blockEventDao().logBlockEvent(BlockEvent(timestamp = System.currentTimeMillis(), wallSource = wallSource))
    }

    fun getDaysCleanStreak(): Int {
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

        // Use the native SQLite date difference calculation for true midnight-to-midnight accuracy
        val days = db.blockEventDao().getDaysSince(baselineTime)
        
        // Base streak starts at Day 1
        return days + 1
    }
}
