# Guardian: Engineering Standards & Coding Protocol
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## 1. The Prime Directive

Guardian is a system-level Android application. The rules that apply to a standard consumer app — "ship fast, fix later," "it works on my device," "we'll optimize in v2" — do not apply here.

Every service we write runs continuously in the background on a user's personal device. A memory leak is not a code smell — it is a ticking timer toward the OS killing our app and a relapse. A performance regression is not a minor issue — it drains the user's battery and makes Guardian an irritant instead of a tool.

**The standard is: production-grade, senior-level code. No exceptions. No placeholders. No "// TODO: implement this later."**

If it ships, it must be complete, tested, and hardened.

---

## 2. Technology Stack & Approved Libraries

### 2.1 Approved Stack

| Layer | Technology | Version Policy |
|---|---|---|
| **Core Language** | Kotlin | Latest stable |
| **UI Layer** | React Native | Latest LTS |
| **ML Runtime** | TensorFlow Lite | Latest stable |
| **Local Database** | Room (Jetpack) | Latest stable |
| **Encrypted Storage** | EncryptedSharedPreferences (Jetpack Security) | Latest stable |
| **Background Tasks** | WorkManager (Jetpack) | Latest stable |
| **Dependency Injection** | Hilt (Dagger) | Latest stable |
| **Networking** | OkHttp | Latest stable |
| **Cloud** | Firebase (Auth, Firestore, FCM) | Latest stable |

### 2.2 Prohibited Libraries

The following categories of libraries are explicitly prohibited. Any suggestion — from an AI or a human — to add these must be rejected:

| Category | Reason |
|---|---|
| Any third-party analytics SDK (Mixpanel, Amplitude, Adjust, etc.) | They collect and transmit user data independently. Zero-telemetry is non-negotiable. |
| Any paid API in the core blocking path | Violates the zero-rupee constraint. Cloud goes down; Guardian must not. |
| Any library with known ad-tracking | Conflicts with Guardian's privacy contract with the user. |
| Reflection-heavy libraries on service classes | Risk of ProGuard stripping and service crashes on release builds. |

### 2.3 Adding a New Library

Before adding any new library, answer all four questions:

1. Is it in the Android Jetpack suite or an explicitly approved open-source library?
2. Does it require any network permission or transmit any data?
3. Does it add > 5MB to the APK?
4. Does it conflict with ProGuard/R8 rules for our service classes?

If any answer is "yes" or "unsure," open a documented decision entry before proceeding.

---

## 3. Android Service Standards

All Guardian background services must follow these non-negotiable patterns.

### 3.1 The Persistence Contract

Every service that extends `Service` or `ForegroundService` must implement the full persistence pattern:

```kotlin
// AI-Generated: [Module Name] — Persistence Contract
// This pattern ensures the service survives OS kill events and device reboots.

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // MANDATORY: Always START_STICKY. Never START_NOT_STICKY or START_REDELIVER_INTENT
    // unless the intent payload is critical for restart.
    return START_STICKY
}

override fun onDestroy() {
    super.onDestroy()
    // Self-resurrect immediately via broadcast
    val restartIntent = Intent(GUARDIAN_RESTART_ACTION)
    sendBroadcast(restartIntent)
}
```

**Restart Receiver must be registered in AndroidManifest.xml:**
```xml
<receiver android:name=".receivers.ServiceRestartReceiver"
          android:exported="false">
    <intent-filter>
        <action android:name="com.guardian.RESTART_SERVICE" />
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

### 3.2 Memory Management Rules

Memory leaks cause the OS Low Memory Killer to terminate Guardian. These rules are mandatory:

**Rule 1 — No Static Context References:**
```kotlin
// WRONG — Context leak. OS will kill this.
companion object {
    var appContext: Context? = null
}

// CORRECT — Use WeakReference if you must hold context
class MyService : Service() {
    private val contextRef = WeakReference(this)
}
```

**Rule 2 — Coroutine Scope Tied to Lifecycle:**
```kotlin
// WRONG — GlobalScope leaks beyond service lifecycle
GlobalScope.launch { doWork() }

// CORRECT — Scope tied to service; cancelled on onDestroy()
private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel() // Clean up all coroutines
}
```

**Rule 3 — ML Bitmap Cleanup (Wall 3 — Mandatory):**
```kotlin
// Every inference cycle must follow this pattern exactly.
var bitmap: Bitmap? = null
try {
    bitmap = captureScreen()
    val result = runInference(bitmap)
    handleResult(result)
} finally {
    // MANDATORY: Always recycle, even on exception
    bitmap?.recycle()
    bitmap = null
}
```

### 3.3 Battery Optimization Rules

**Rule 1 — State-Aware Execution (Wall 3):**
Wall 3 (ML Vision Sentry) must only run inference when a browser or flagged app is in the foreground. Implement a foreground package checker before every capture cycle:

```kotlin
private fun isBrowserForegrounded(): Boolean {
    val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
    val foregroundPackage = am.runningAppProcesses
        ?.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
        ?.processName
    return MONITORED_PACKAGES.contains(foregroundPackage)
}
```

**Rule 2 — Idle State in Event Loops:**
All event processing loops must include an explicit idle state with a minimum sleep interval:

```kotlin
// Wall 3 capture loop — always include delay between cycles
while (isActive) {
    if (isBrowserForegrounded()) {
        captureAndAnalyze()
    }
    delay(7_000) // Minimum 7-second rest between cycles
}
```

**Rule 3 — WorkManager for Deferred Tasks:**
Any task that does not need to run immediately (blocklist updates, weekly report sync) must use WorkManager, not a running coroutine or Timer:

```kotlin
val blocklistUpdate = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(7, TimeUnit.DAYS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "blocklist_update",
    ExistingPeriodicWorkPolicy.KEEP,
    blocklistUpdate
)
```

---

## 4. Security Coding Standards

### 4.1 Encrypted Storage Usage

All sensitive data must use `EncryptedSharedPreferences`. Plain `SharedPreferences` is forbidden for any Guardian-sensitive value:

```kotlin
// CORRECT — All sensitive Guardian data stored here
private val securePrefs: SharedPreferences by lazy {
    EncryptedSharedPreferences.create(
        context,
        "guardian_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
```

### 4.2 Master Password Handling

- The master password plaintext must never be stored anywhere after the initial sharing flow.
- Store only the BCrypt hash.
- Comparison must always be hash-to-hash, never plaintext-to-hash.
- If BCrypt is unavailable in the target SDK version, use SHA-256 with a stored salt minimum.

### 4.3 AndroidManifest Permission Rules

Every permission added to `AndroidManifest.xml` requires a written justification comment directly above it:

```xml
<!-- PERMISSION JUSTIFICATION: BIND_VPN_SERVICE
     Required for Wall 1 (DNS Engine). Creates a local TUN interface for
     DNS interception. Does NOT route traffic to any remote server. -->
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

<!-- PERMISSION JUSTIFICATION: RECEIVE_BOOT_COMPLETED
     Required for reboot recovery. Triggers GuardianCoreService restart
     within 5 seconds of BOOT_COMPLETED broadcast. -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Any permission without a justification comment is rejected at code review.

---

## 5. Code Documentation Standard

### 5.1 File Header (All Service Classes)

Every Kotlin service file must begin with this header:

```kotlin
/**
 * [Class Name]
 *
 * Wall: [WALL_1 / WALL_2 / WALL_3 / WALL_4 / CORE]
 * Phase: [1 / 2 / 3 / 4]
 * Status: [Active / In Development / Deprecated]
 *
 * Responsibility:
 * [One paragraph describing exactly what this class does and nothing else.]
 *
 * Persistence Strategy:
 * [How this class survives OS kill events and reboots.]
 *
 * Memory Contract:
 * [What this class allocates and how/when it releases it.]
 *
 * Known Limitations:
 * [Any device-specific behaviors or edge cases the next developer must know.]
 */
```

### 5.2 AI-Generated Code Marking

Any block of code primarily generated by an AI assistant must be marked:

```kotlin
// AI-Generated: [Brief description of what this block does]
// Reviewed by: Nanda | Date: [date]
// Logic Stress Test: [PASSED / PENDING]
```

This is not for shame — it is for traceability. If a bug appears in an AI-generated block, the marking helps pinpoint it immediately.

---

## 6. Wall-Specific Implementation Checklists

### Wall 1 Checklist (DNS/VPN)
- [ ] `VpnService.prepare()` called before establishing interface
- [ ] TUN interface binds only to localhost — no remote server routing
- [ ] SQLite blocklist table has an index on `domain_hash` column
- [ ] DNS lookup instrumented with latency timer (target p95 < 30ms)
- [ ] `onRevoke()` implemented — alerts Guardian Core and logs bypass event
- [ ] No domain string written to any log or database

### Wall 2 Checklist (Accessibility)
- [ ] Service declared with `android:accessibilityFeedbackType="feedbackGeneric"`
- [ ] `com.android.settings` in monitored package list
- [ ] Settings sub-page check implemented before `GLOBAL_ACTION_HOME`
- [ ] Event processing runs on background thread — never blocks the accessibility thread
- [ ] All 5 monitored browsers verified on test device

### Wall 3 Checklist (ML Vision)
- [ ] `isBrowserForegrounded()` check runs before every capture cycle
- [ ] Bitmap recycled in `finally` block — no exceptions
- [ ] Capture resolution downscaled to 540×960 before inference
- [ ] TFLite model input normalized to [0, 1] float range
- [ ] Confidence threshold set to 0.85 in a named constant, not a magic number
- [ ] `MediaProjection` token refreshed correctly after revocation

### Wall 4 Checklist (Device Admin)
- [ ] `DeviceAdminReceiver` correctly handles `onDisableRequested` — prompts password
- [ ] `DevicePolicyManager.setUninstallBlocked()` called on setup completion
- [ ] AccessibilityService status check runs every 60 seconds
- [ ] Tamper detection event logged to Room DB with timestamp

---

## 7. Testing Protocol

Every feature must pass three tests before it is considered done:

### The Persistence Test
*"Does it survive being killed?"*
1. Enable the feature
2. Execute: `adb shell am kill com.guardian`
3. Wait 10 seconds
4. Verify: Feature is fully active again

### The Bypass Test
*"Can the user disable this without the password?"*
1. Enable the feature
2. Attempt to disable via: Settings, ADB, Task Killer, Force Stop, Clear Data
3. Verify: All attempts are blocked or logged; no feature is disabled without password

### The Battery Test
*"Does it respect the 5% budget?"*
1. Enable all Walls
2. Run Android Battery Historian for 4 hours of normal device usage
3. Verify: Guardian's attributed battery usage ≤ 5%

---

## 8. Definition of Done

A task is **Done** — not "code complete," not "works on my device" — when:

- [ ] All Wall-specific checklist items are checked
- [ ] Persistence Test passed
- [ ] Bypass Test passed
- [ ] No new memory leaks (Android Profiler — Memory tab)
- [ ] Battery consumption unchanged from baseline (< 5% total)
- [ ] All new permissions have justification comments in Manifest
- [ ] AI-generated code blocks are marked and reviewed
- [ ] Class header documentation is complete
- [ ] Architecture design document updated if behavior deviates from ADD