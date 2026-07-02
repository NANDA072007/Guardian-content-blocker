package com.guardian.app

import android.app.Application
import com.guardian.app.core.ServiceResurrector
import com.guardian.app.core.ServiceResurrectorEntryPoint
import com.guardian.app.domain.InitializeBlocklistUseCase
import com.guardian.app.workers.BlocklistUpdateWorker
import com.guardian.app.workers.WatchdogWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@dagger.hilt.android.HiltAndroidApp
class GuardianApplication : Application() {

    @Inject lateinit var initializeBlocklist: InitializeBlocklistUseCase

    override fun onCreate() {
        super.onCreate()
        
        initializeBlocklist.execute()
        
        scheduleWatchdog()
        scheduleWeeklyUpdates()
        scheduleResurrectionChain()
    }

    /**
     * WorkManager watchdog — 15-minute periodic check.
     * This is the final fallback. AlarmManager and JobService are primary.
     */
    private fun scheduleWatchdog() {
        val watchdogRequest = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WatchdogWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogRequest
        )
    }

    /**
     * Weekly blocklist OTA update — 7-day periodic with network constraint.
     */
    private fun scheduleWeeklyUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyBlocklistUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    /**
     * Schedule AlarmManager + JobService resurrection chain.
     * These survive Doze mode and OEM battery managers.
     * Idempotent — safe to call on every app start.
     */
    private fun scheduleResurrectionChain() {
        val entryPoint = EntryPointAccessors.fromApplication(this, ServiceResurrectorEntryPoint::class.java)
        val resurrector = entryPoint.serviceResurrector()
        resurrector.scheduleAlarmChain(this)
        resurrector.scheduleJobService(this)
    }
}
