package com.guardian.app.data.core

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class GuardianJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("GuardianJobService", "JobService triggered for resurrection check.")
        
        // Check if GuardianCoreService is running
        if (!GuardianCoreService.isRunning(applicationContext)) {
            Log.d("GuardianJobService", "CoreService is dead! Resurrecting...")
            val serviceIntent = Intent(applicationContext, GuardianCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    ContextCompat.startForegroundService(applicationContext, serviceIntent)
                } catch (e: Exception) {
                    Log.e("GuardianJobService", "Failed to start CoreService", e)
                }
            } else {
                startService(serviceIntent)
            }
        }
        
        // Schedule next job
        scheduleJob(applicationContext)
        
        // Return false = job is complete synchronously
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true = reschedule if stopped early
        return true
    }

    companion object {
        private const val JOB_ID = 1001
        
        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, GuardianJobService::class.java)
            )
                .setMinimumLatency(15 * 60 * 1000L) // Wait at least 15 minutes
                .setOverrideDeadline(30 * 60 * 1000L) // Force execution within 30 minutes
                .setPersisted(true) // Survives reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // No network needed
                .build()
            
            try {
                jobScheduler.schedule(jobInfo)
            } catch (e: Exception) {
                Log.e("GuardianJobService", "Failed to schedule job", e)
            }
        }
    }
}
