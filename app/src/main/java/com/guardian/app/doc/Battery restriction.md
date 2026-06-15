# Guardian: Beating Android Battery Restrictions
**The problem nobody talks about honestly**

---

## First — Understand What You're Actually Fighting

Everyone tells you the same thing:
"Use ForegroundService. Use START_STICKY. Use WorkManager."

You did all of that. Guardian still dies.

Here's why: **those solutions fight Android. Your real enemy is not Android.**

Your real enemy is the phone manufacturer.

```
Google builds Android
        ↓
Samsung / Xiaomi / Realme take Android
        ↓
They add their own battery management layer ON TOP
        ↓
This layer operates BELOW Android's service system
        ↓
It kills processes at kernel level
        ↓
Android never even knows it happened
        ↓
START_STICKY fires — but too late
        ↓
Your ForegroundService restarts — OEM kills it again
        ↓
Infinite loop. Guardian stays dead.
```

The setting reverting back to Battery Saver automatically?
That is the OEM firmware scanning your app every 30 minutes,
deciding it is "inactive" (because it has no visible UI activity),
and silently resetting your permission.

This is not a bug you can fix with better Kotlin.
This is a war you fight with a multi-weapon strategy.

---

## The Thinking Nobody Does

Everyone's approach:
```
Problem: App gets killed
Solution: Make app harder to kill
Result: OEM finds another way to kill it
```

The different approach:
```
Problem: App gets killed
Solution 1: Make it hard to kill (defense)
Solution 2: Make it instant to resurrect (offense)
Solution 3: Make the user an ally (psychology)
Solution 4: Use system APIs the OEM cannot touch (nuclear)
```

Guardian doesn't need to be unkillable.
It needs to be **immediately resurrectable with zero user action.**

If Guardian dies and comes back in 3 seconds — the user never knows.
That is functionally identical to never dying.

---

## The 6-Weapon Arsenal

---

### Weapon 1 — Battery Optimization Exemption (Request In-App)

Most developers tell users to go find this manually in settings.
That is wrong. You request it programmatically.

```kotlin
// GuardianCoreService.kt

fun requestBatteryOptimizationExemption(context: Context) {
    val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
    
    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
        // This opens an Android SYSTEM dialog
        // The user taps ONE button
        // This registers at a level BELOW OEM battery management
        // OEM cannot auto-revert a system-level exemption
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
```

**Why this works when manual settings don't:**
Manual path: User sets → OEM firmware scans → OEM reverts
Programmatic path: System registers exemption → stored at OS level → OEM cannot touch it

Add this to your onboarding as Step 5.
It is a single system dialog. One tap. Permanent.

---

### Weapon 2 — AlarmManager Chain (The Nuclear Option)

WorkManager can be killed.
ForegroundService can be killed.
AlarmManager with setExactAndAllowWhileIdle cannot be silently killed.

It is registered at the kernel alarm subsystem.
The OEM battery manager does not have access to cancel it
without root-level intervention.

```kotlin
// GuardianCoreService.kt

companion object {
    const val ALARM_ACTION = "com.guardian.ALARM_HEARTBEAT"
    const val ALARM_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
}

fun scheduleAlarmChain(context: Context) {
    val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
    
    val intent = Intent(context, GuardianAlarmReceiver::class.java).apply {
        action = ALARM_ACTION
    }
    
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    // setExactAndAllowWhileIdle = fires even in Doze mode
    // This is the only alarm type that survives deep sleep
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.ELAPSED_REALTIME_WOKEN,
        SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS,
        pendingIntent
    )
}
```

```kotlin
// GuardianAlarmReceiver.kt
// This receiver does two things:
// 1. Restarts Guardian if dead
// 2. Reschedules the next alarm (the chain)

class GuardianAlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GuardianCoreService.ALARM_ACTION) {
            
            // Step 1: Restart if dead
            if (!GuardianCoreService.isRunning(context)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, GuardianCoreService::class.java)
                )
            }
            
            // Step 2: Schedule the next alarm
            // This is the chain — each alarm schedules the next one
            // The only way to break the chain is to cancel the PendingIntent
            // OEM cannot do this without the app's permission
            GuardianCoreService.scheduleAlarmChain(context)
        }
    }
}
```

Register in AndroidManifest.xml:
```xml
<receiver
    android:name=".core.GuardianAlarmReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.guardian.ALARM_HEARTBEAT" />
    </intent-filter>
</receiver>
```

Call `scheduleAlarmChain(context)` in:
- GuardianCoreService.onCreate()
- ServiceRestartReceiver.onReceive() (after BOOT_COMPLETED)
- GuardianAlarmReceiver.onReceive() itself (the chain)

Now even if Guardian is killed:
```
OEM kills Guardian at 2:47 AM
        ↓
Next alarm fires at 3:02 AM (15 minutes max gap)
        ↓
GuardianAlarmReceiver.onReceive() fires
        ↓
Detects GuardianCoreService is not running
        ↓
Starts GuardianCoreService
        ↓
Schedules next alarm for 3:17 AM
        ↓
User at 2:48 AM sees a block screen
User at 3:03 AM sees nothing because Guardian is back
```

15-minute maximum gap. Acceptable. Better than staying dead all night.

---

### Weapon 3 — Partial WakeLock (Startup Shield)

The gap between Guardian being killed and the AlarmManager
firing is the dangerous window.

A WakeLock on startup keeps the CPU alive long enough for
all services to register properly before the OEM can intervene:

```kotlin
// GuardianCoreService.kt

private var startupWakeLock: PowerManager.WakeLock? = null

override fun onCreate() {
    super.onCreate()
    
    // Acquire wakelock FIRST before anything else
    // 30 seconds — enough time for all walls to initialize
    acquireStartupWakeLock()
    
    // Now do everything else
    startForeground(NOTIFICATION_ID, buildNotification())
    initializeWatchdog()
    startAllWalls()
    scheduleAlarmChain(this)
    
    // WakeLock released after initialization
    // or automatically after 30 seconds
}

private fun acquireStartupWakeLock() {
    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
    startupWakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "Guardian::StartupLock"
    ).apply {
        acquire(30 * 1000L) // 30 seconds maximum
    }
}

override fun onDestroy() {
    startupWakeLock?.let { if (it.isHeld) it.release() }
    serviceScope.cancel()
    val restartIntent = Intent(GuardianBroadcastActions.ACTION_RESTART_SERVICE)
    sendBroadcast(restartIntent)
    super.onDestroy()
}
```

**Important:** Always set a timeout on WakeLocks (the second parameter
to acquire()). A WakeLock with no timeout that is never released will
drain the battery completely. 30 seconds is enough for startup.

---

### Weapon 4 — JobService Fallback (For Doze Mode)

Android's Doze mode is different from OEM battery management.
Doze is legitimate Android behavior that suspends app execution
when the screen is off and the device hasn't moved.

JobService bypasses Doze with Android's permission:

```kotlin
// core/GuardianJobService.kt

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class GuardianJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // Check if GuardianCoreService is running
        if (!GuardianCoreService.isRunning(applicationContext)) {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, GuardianCoreService::class.java)
            )
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
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) 
                               as JobScheduler
            
            val jobInfo = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, GuardianJobService::class.java)
            )
                .setPeriodic(15 * 60 * 1000L) // 15 minutes
                .setPersisted(true) // Survives reboot
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // No network needed
                .build()
            
            jobScheduler.schedule(jobInfo)
        }
    }
}
```

Declare in manifest:
```xml
<service
    android:name=".core.GuardianJobService"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:exported="true" />
```

Call `GuardianJobService.scheduleJob(context)` in:
- GuardianCoreService.onCreate()
- ServiceRestartReceiver (BOOT_COMPLETED)

Now you have THREE resurrection mechanisms running simultaneously:
- AlarmManager chain (15 min)
- JobService (15 min, offset)
- WorkManager (15 min, offset)

The three timers are unlikely to all be reset at the same moment.
At least one will fire and resurrect Guardian.

---

### Weapon 5 — OEM-Specific Detection + Auto-Instructions

The setting keeps reverting? Make Guardian detect when it has reverted
and guide the user to fix it — without requiring them to know where to look.

```kotlin
// core/OemCompatibilityManager.kt

object OemCompatibilityManager {
    
    private val manufacturer = Build.MANUFACTURER.lowercase()
    
    fun getDeepLinkIntent(): Intent? {
        return when {
            // Samsung: Opens directly to Guardian's battery settings
            manufacturer.contains("samsung") -> Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
            
            // Xiaomi: Opens MIUI autostart settings
            manufacturer.contains("xiaomi") || 
            manufacturer.contains("redmi") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            
            // OnePlus
            manufacturer.contains("oneplus") -> Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
            
            // Realme/OPPO
            manufacturer.contains("realme") || 
            manufacturer.contains("oppo") -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            
            // Huawei
            manufacturer.contains("huawei") || 
            manufacturer.contains("honor") -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            
            // Vivo
            manufacturer.contains("vivo") -> Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            
            // Stock Android or unknown
            else -> null
        }
    }
    
    fun getManualInstructions(): String {
        return when {
            manufacturer.contains("samsung") -> """
                Samsung requires 3 separate steps:
                
                1. Settings → Device Care → Battery
                   → tap ⋮ menu → Automation
                   → Turn OFF "Auto optimize daily"
                
                2. Settings → Battery → Background usage limits
                   → Find Guardian → tap → "Never sleeping"
                
                3. Settings → Apps → Guardian → Battery
                   → Select "Unrestricted"
                   
                All 3 must be done. One is not enough.
            """.trimIndent()
            
            manufacturer.contains("xiaomi") || 
            manufacturer.contains("redmi") -> """
                Xiaomi has 4 separate places. Do all 4:
                
                1. Security App → Permissions → Autostart
                   → Find Guardian → Enable
                   (This is the most important one)
                
                2. Settings → Apps → Manage apps → Guardian
                   → Battery Saver → No restrictions
                
                3. Settings → Battery & Performance
                   → Choose apps → Guardian → No restrictions
                
                4. Security App → Battery → Power Saving
                   → Guardian → No restrictions
            """.trimIndent()
            
            manufacturer.contains("oneplus") -> """
                OnePlus requires 2 steps:
                
                1. Settings → Battery
                   → Battery Optimization
                   → Find Guardian → Don't optimize
                
                2. Settings → Additional Settings
                   → Battery → RAM Boost → OFF
                   (This stops Smart Boost from clearing Guardian)
            """.trimIndent()
            
            manufacturer.contains("realme") || 
            manufacturer.contains("oppo") -> """
                Realme/OPPO requires 3 steps:
                
                1. Settings → Battery → App Quick Freeze
                   → Make sure Guardian is NOT listed here
                
                2. Settings → Battery → More
                   → Intelligent Control → OFF
                
                3. Settings → Apps → Guardian → Battery
                   → Allow background activity
            """.trimIndent()
            
            else -> """
                1. Settings → Apps → Guardian → Battery
                   → Unrestricted or No restrictions
                
                2. Settings → Battery → Battery Optimization
                   → Guardian → Don't optimize
            """.trimIndent()
        }
    }
    
    fun isBatteryOptimizationExempted(context: Context): Boolean {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    // Request the system-level exemption programmatically
    // This survives OEM resets — manual settings do not
    fun requestSystemExemption(context: Context) {
        if (!isBatteryOptimizationExempted(context)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}
```

---

### Weapon 6 — Watchdog Self-Monitoring + User Alert

The smartest weapon: Guardian monitors its OWN battery
exemption status and notifies the user when it has been reverted.

```kotlin
// Add to GuardianCoreService watchdog coroutine

private fun checkBatteryExemptionStatus() {
    val isExempted = OemCompatibilityManager.isBatteryOptimizationExempted(this)
    
    if (!isExempted) {
        // OEM has reverted our exemption
        // Notify the user immediately
        showBatteryExemptionRevokedNotification()
        
        // Log this as a tamper event
        serviceScope.launch(Dispatchers.IO) {
            blockEventDao.insert(
                BlockEvent(
                    timestamp = System.currentTimeMillis(),
                    wallSource = WallSource.CORE,
                    triggerType = TriggerType.BATTERY_EXEMPTION_REVOKED,
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                )
            )
        }
        
        // Alert Trusted Person if this keeps happening
        // (repeated revocations = someone actively trying to bypass Guardian)
    }
}

private fun showBatteryExemptionRevokedNotification() {
    val notification = NotificationCompat.Builder(this, "guardian_alerts")
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle("Guardian: Action Required")
        .setContentText("Your phone reset Guardian's battery permission. Tap to fix.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, BatteryPermissionActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
    
    NotificationManagerCompat.from(this).notify(BATTERY_ALERT_ID, notification)
}
```

---

## The Onboarding Screen You Must Add

This is the most important UX change.
Currently you skip battery permission.
That is the mistake.

Add this as **Step 5 of onboarding** after Device Admin:

```
Screen: BatteryPermissionScreen

Header: "One last thing."

Body: "Your phone's power settings can silently disable Guardian.
       We need one permission to prevent this."

[Primary Button]: "Protect Guardian's Power"
→ Calls OemCompatibilityManager.requestSystemExemption()
→ System dialog appears (ONE tap for user)
→ On completion → proceed to next screen

[Show OEM-specific manual instructions below button]
→ OemCompatibilityManager.getManualInstructions()
→ Displayed as a collapsible "Also do this on [Samsung/Xiaomi]:" section

[Secondary]: "Skip — I'll do this later"
→ Allowed but shows warning:
  "Guardian may not work reliably overnight without this.
   You can fix this in Settings anytime."
```

---

## The ADB Commands For Your Testing Right Now

While you're debugging — use these to bypass all OEM restrictions instantly:

```bash
# The most important one — whitelist at kernel level
adb shell dumpsys deviceidle whitelist +com.guardian.app

# Disable all battery optimization for Guardian
adb shell cmd appops set com.guardian.app RUN_IN_BACKGROUND allow
adb shell cmd appops set com.guardian.app RUN_ANY_IN_BACKGROUND allow

# On MIUI — disable the auto-revert daemon
adb shell settings put global app_standby_enabled 0

# Verify Guardian is in the whitelist
adb shell dumpsys deviceidle whitelist
# Look for com.guardian.app in the output

# Check Guardian's current battery optimization status
adb shell dumpsys battery
adb shell dumpsys deviceidle | grep com.guardian

# Force Guardian to stay active regardless of battery state
adb shell settings put global app_standby_enabled 0
adb shell settings put global forced_app_standby_enabled 0

# Nuclear option during testing only
# (Do not ship with this — it bypasses ALL optimization)
adb shell settings put global always_finish_activities 0
```

---

## Implementation Order

Do these in this exact order:

```
Step 1 (Today — 10 minutes):
Add adb shell dumpsys deviceidle whitelist +com.guardian.app
Test Guardian overnight with this — verify it survives
This proves the problem is OEM battery management, not your code

Step 2 (Today — 30 minutes):
Add requestBatteryOptimizationExemption() to onboarding
This is the highest-impact single code change

Step 3 (Tomorrow — 2 hours):
Implement AlarmManager chain (Weapon 2)
This is the nuclear resurrection option

Step 4 (This week — 2 hours):
Implement OemCompatibilityManager with all OEM detection
Add battery permission screen to onboarding

Step 5 (This week — 1 hour):
Add watchdog self-monitoring for exemption revocation
Add the notification that tells user when setting reverts

Step 6 (Before Play Store — 1 hour):
Test overnight on Samsung, Xiaomi, and one more OEM
Verify Guardian survives on all three
```

---

## The Real Insight

Everyone builds a system-level app and then fights the OEM
trying to keep it alive.

The different thinking is this:

**Stop trying to be unkillable. Start being instantly resurrectable.**

A user who experiences Guardian dying at 1 AM and coming back at 1:15 AM
has essentially zero additional relapse risk compared to a user whose Guardian
never died.

The gap is 15 minutes. The gap is the crack in the wall.
15 minutes is not long enough for a complete relapse cycle.

So the goal is not immortality — it is **recovery speed under 15 minutes.**

AlarmManager gives you that.
The OEM cannot take that away.

Guardian doesn't need to win the war against OEM firmware.
It just needs to be alive again before it matters.

---

## Summary Table

| Weapon | What It Does | OEM Can Override? | Effort |
|---|---|---|---|
| Manual battery settings | Exempts app | YES — auto-reverts | None (user does it) |
| Programmatic exemption request | System-level whitelist | Harder to revert | 30 min |
| AlarmManager chain | Resurrects every 15 min | NO | 2 hours |
| WakeLock on startup | Survives startup kill window | NO | 30 min |
| JobService fallback | Survives Doze mode | NO | 1 hour |
| OEM-specific deep links | Guides user to right settings | N/A | 2 hours |
| Watchdog self-monitoring | Detects + alerts on revert | N/A | 1 hour |

**Use all 7. Not just one.**

The OEM has multiple weapons. You need multiple weapons too.