package com.guardian.app.walls.wall2.health

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundAppChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "FgAppChecker"

    fun getForegroundPackage(): String? {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager == null) {
                Log.w(tag, "UsageStatsManager not available — no usage stats permission")
                return null
            }

            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 5000,
                now
            )

            if (stats.isNullOrEmpty()) return null

            stats
                .filter { it.lastTimeUsed > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .firstOrNull()
                ?.packageName
        } catch (e: SecurityException) {
            Log.w(tag, "Usage stats permission not granted: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(tag, "Failed to get foreground package: ${e.message}")
            null
        }
    }

    fun checkAgainstAccessibility(accessibilityPackage: String?): HealthSignal.ForegroundApp {
        val usageStatsPackage = getForegroundPackage()
        val match = accessibilityPackage != null && usageStatsPackage != null &&
                accessibilityPackage == usageStatsPackage

        return HealthSignal.ForegroundApp(
            timestamp = System.currentTimeMillis(),
            accessibilityPackage = accessibilityPackage,
            usageStatsPackage = usageStatsPackage,
            match = match
        )
    }
}
