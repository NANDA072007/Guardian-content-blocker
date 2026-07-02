# Guardian: PROJECT.md

**CRITICAL AGENT INSTRUCTION: The AI agent MUST read this file before starting any update, task, or implementation on this project.**

Version: 1.0

Type: Source of Truth — Feed this to any AI before every session

Rule: If it is not in this document, the AI must ask before assuming

1. PROJECT IDENTITY
App Name: Guardian

Package Name: com.guardian.app

Type: Native Android Application

Purpose: A system-level content blocking and digital commitment device for adults who want to break compulsive pornography and digital addiction habits

Target Platform: Android only (API 26 minimum, API 34 target)

Architecture Style: Service-Oriented Architecture with a React Native UI layer

Build Status: Built, testing phase, approaching Play Store submission

2. THE PROBLEM THIS APP SOLVES
People who struggle with compulsive content consumption already know they want to stop. The problem is not awareness. The problem is the gap between the rational self who makes decisions at 9 PM and the impulsive self who opens a browser at 1 AM.
Existing blockers fail because they are too easy to disable. A motivated user can turn off a standard content blocker in under 60 seconds.
Guardian's answer is to replace willpower with architecture. Four simultaneous layers of Android system-level protection make bypassing genuinely difficult. The 20-second block screen gives the rational brain time to override the impulse. A trusted person holding the master password adds social accountability that makes bypass psychologically costly.

3. TECH STACK — COMPLETE AND FINAL
Do not suggest alternatives to any item in this list without explicit instruction.
Core Language: Kotlin (all Android services, business logic, data layer)

UI Layer: React Native (all screens — dashboard, onboarding, block screen, emergency mode, settings)

ML Runtime: TensorFlow Lite with quantized MobileNetV2 model

Local Database: Room (Jetpack) on top of SQLite

Encrypted Storage: EncryptedSharedPreferences (Jetpack Security)

Background Tasks: WorkManager (Jetpack) for periodic tasks

Dependency Injection: Hilt (Dagger)

Networking: OkHttp (for blocklist OTA updates only)

Cloud Auth: Firebase Anonymous Authentication

Cloud Database: Firestore (accountability summaries only)

Push Notifications: Firebase Cloud Messaging

Bloom Filter: Guava Android library

React State: Zustand

React Navigation: React Navigation v6

React SVG: react-native-svg

React Sheets: @gorhom/bottom-sheet

React Haptics: react-native-haptic-feedback
Not used and must not be suggested:

Python (not in the app — only used in offline data pipeline scripts)
Java (Kotlin only)
Flutter (previous version — completely abandoned)
WireGuard (rejected by architecture team)
Any paid API in the core blocking path
Mixpanel, Amplitude, Adjust, or any third-party analytics SDK
Firebase Analytics (not used — zero telemetry policy)


4. FOLDER STRUCTURE — EXACT
android/
  app/
    src/
      main/
        kotlin/com/guardian/app/
          core/
            GuardianCoreService.kt
            ServiceRestartReceiver.kt
            GuardianAlarmReceiver.kt
            GuardianJobService.kt
            OemCompatibilityManager.kt
          walls/
            wall1/
              DnsVpnService.kt
              GuardianBlockingPipeline.kt
              DomainBloomFilter.kt
              CompactBlocklist.kt
              DomainPatternMatcher.kt
              BlocklistSeeder.kt
            wall2/
              AccessibilitySentryService.kt
              BlockOverlayManager.kt
            wall3/
              VisionSentryService.kt
              TFLiteClassifier.kt
            wall4/
              GuardianAdminReceiver.kt
              DevicePolicyController.kt
          data/
            db/
              GuardianDatabase.kt
              entities/
                StreakLog.kt
                BlockEvent.kt
                DomainBlocklist.kt
              dao/
                StreakDao.kt
                BlockEventDao.kt
                DomainBlocklistDao.kt
            prefs/
              SecurePrefsManager.kt
            firebase/
              FirebaseManager.kt
          engine/
            StreakEngine.kt
            RelapseDetector.kt
            SharedKeyManager.kt
            CooloffEngine.kt
          workers/
            DailyStreakWorker.kt
            BlocklistUpdateWorker.kt
            CloudSyncWorker.kt
            CooloffExpiryWorker.kt
            DangerZoneNotificationWorker.kt
          bridge/
            GuardianBridgeModule.kt
            GuardianBridgePackage.kt
            GuardianEventEmitter.kt
          di/
            AppModule.kt
            DatabaseModule.kt
            EngineModule.kt
          broadcast/
            GuardianBroadcastActions.kt
        res/
          xml/
            accessibility_service_config.xml
            device_admin.xml
            network_security_config.xml
          assets/
            bloom_filter.bin
            blocklist.bin
            domain_classifier.tflite
            nsfw_model.tflite

src/ (React Native)
  screens/
    onboarding/
      ContractScreen.tsx
      AccessibilityPermissionScreen.tsx
      DeviceAdminScreen.tsx
      TrustedPersonSetupScreen.tsx
      PasswordShareScreen.tsx
      OnboardingCompleteScreen.tsx
      OnboardingNavigator.tsx
    dashboard/
      DashboardScreen.tsx
    block/
      BlockScreen.tsx
    emergency/
      EmergencyScreen.tsx
    settings/
      SettingsScreen.tsx
      UnlockRequestScreen.tsx
      WeeklyReportScreen.tsx
  stores/
    guardianStore.ts
    onboardingStore.ts
  design/
    tokens.ts
    components.ts
    animations.ts
    haptics.ts
  components/
    AnimatedComponents.tsx
    SvgIcons.tsx
    WallStatusPill.tsx
    MissionCard.tsx
    StreakRing.tsx
    BreathingCircle.tsx
    CircularTimer.tsx
  hooks/
    useGuardianBridge.ts
    useBlockTimer.ts
    useStepTransition.ts
  native/
    GuardianNativeModule.ts
  data/
    missions.ts

5. THE FOUR WALLS — WHAT EACH ONE DOES
Wall 1: DNS/VPN Engine

Creates a local VPN loopback using Android VpnService API. Intercepts all DNS queries on the device. Checks each domain against a local blocklist using a Bloom Filter first (sub-millisecond), then a compact hash table, then pattern matching. Blocked domains resolve to 0.0.0.0. No traffic is routed to any external server. The VPN is entirely local. Target lookup speed is under 30ms for known domains.
Wall 2: Accessibility Sentry

Uses Android AccessibilityService to monitor URL bars in all major browsers in real time. When a blocked domain is detected in the address bar, fires a block overlay within 200ms. Also monitors Settings navigation and executes GLOBAL_ACTION_HOME if the user attempts to navigate to Accessibility Settings, Device Admin, or App Info for Guardian. Self-protection fires on TYPE_WINDOW_STATE_CHANGED events only.
Wall 3: ML Vision Sentry

Captures the device screen every 7-10 seconds using MediaProjection API. Only activates when a browser or flagged app is in the foreground — completely idle otherwise. Downscales capture to 224x224 pixels and runs TFLite MobileNetV2 inference. If adult content confidence score exceeds 0.85, fires block overlay. Every captured image is recycled from memory immediately after inference. Nothing is ever written to storage or transmitted over network.
Wall 4: Device Admin (Policy Controller)

Uses DevicePolicyManager to prevent uninstallation and Force Stop without the master password. Monitors its own AccessibilityService status every 60 seconds. If external tampering is detected, logs the event and alerts the trusted person. Uses GuardianAdminReceiver extending DeviceAdminReceiver.

6. GUARDIAN CORE SERVICE
GuardianCoreService is the orchestration heartbeat. It is a ForegroundService.
Rules that must never be violated:

onStartCommand always returns START_STICKY
startForeground is called in onCreate directly, never inside a coroutine
onDestroy sends ACTION_RESTART_SERVICE broadcast BEFORE calling super.onDestroy
serviceScope is CoroutineScope(Dispatchers.IO + SupervisorJob())
serviceScope.cancel() is called in onDestroy
No static Context references anywhere in the service or any companion object
Watchdog coroutine runs every 30 seconds, wrapped entirely in try/catch

The watchdog does five things on every tick:

Checks if AccessibilitySentryService is running
Checks if DnsVpnService is running
If any wall is inactive: logs event, attempts restart, sends wall status broadcast
Checks for burst events (3 blocks in 10 minutes) and fires burst alert if found
Updates foreground notification with current streak day


7. DATA ARCHITECTURE
Room Database name: guardian_db
Table: streak_log

Columns: id (PK autoincrement), date (String ISO format YYYY-MM-DD), isClean (Boolean), blockCount (Int)

Purpose: One row per day. Tracks whether each day was clean or had a relapse.
Table: block_events

Columns: id (PK autoincrement), timestamp (Long unix millis), wallSource (String), triggerType (String), hourOfDay (Int)

Purpose: Every block event logged here. wallSource values: WALL_1, WALL_2, WALL_3, CORE. triggerType values: DNS, URL, VISUAL, SETTINGS_BYPASS, SERVICE_DESTROYED, WALL_INACTIVE, BATTERY_EXEMPTION_REVOKED

Critical rule: No URL string, domain name, or any user content is ever stored in this table. Only the source and type of the block event.
Table: domain_blocklist

Columns: domainHash (String PK indexed), category (String), addedAt (Long)

Purpose: Stores SHA-256 hashes of blocked domains. Never stores domain strings in plaintext.
EncryptedSharedPreferences file: guardian_secure_prefs

Keys: master_key_hash, trusted_person_contact, trusted_person_name, setup_complete, hard_lock_active, cooloff_end_timestamp, vpn_active, device_admin_active, firebase_uid, last_restart_reason, trusted_person_fcm_token, danger_zone_enabled
Firestore path: /artifacts/{appId}/public/data/{userId}/

Documents: weekly_summary, alert_log

What is stored: Only pre-aggregated counts (streak day, block count, mission count). Never URLs, domains, names, or any identifying information.

8. BROADCAST ACTIONS
All internal communication uses LocalBroadcastManager.
ACTION_SHOW_BLOCK_OVERLAY = com.guardian.SHOW_BLOCK_OVERLAY

Sent by: Any wall when content is detected

Received by: GuardianCoreService routes to React Native via bridge

Extras: EXTRA_WALL_SOURCE (String), EXTRA_TRIGGER_TYPE (String), EXTRA_STREAK_DAY (Int)
ACTION_DISMISS_OVERLAY = com.guardian.DISMISS_OVERLAY

Sent by: React Native UI after block timer completes

Received by: GuardianCoreService
ACTION_STREAK_UPDATE = com.guardian.STREAK_UPDATE

Sent by: StreakEngine after any streak change

Received by: React Native via GuardianEventEmitter

Extras: EXTRA_STREAK_COUNT (Int), EXTRA_IS_CLEAN (Boolean)
ACTION_BURST_ALERT = com.guardian.BURST_ALERT

Sent by: GuardianCoreService watchdog when 3 blocks in 10 minutes detected

Received by: CloudSyncWorker enqueue
ACTION_RESTART_SERVICE = com.guardian.RESTART_SERVICE

Sent by: Every service in its onDestroy, before super.onDestroy

Received by: ServiceRestartReceiver
ACTION_WALL_STATUS_CHANGED = com.guardian.WALL_STATUS_CHANGED

Sent by: Any wall on status change

Received by: GuardianCoreService and React Native via bridge

Extras: EXTRA_WALL_NUMBER (Int), EXTRA_IS_ACTIVE (Boolean)

9. NATIVE BRIDGE — ALL EXPOSED METHODS
These are the exact methods GuardianBridgeModule exposes to React Native.

No other methods exist. Do not invent additional bridge methods.
requestAccessibilityPermission() → Promise<Boolean>

requestDeviceAdminPermission() → Promise<Boolean>

requestBatteryOptimizationExemption() → Promise<Boolean>

generateAndSetupSharedKey() → Promise<String> (returns plaintext password once only)

confirmPasswordShared() → Promise<void>

isWallActive(wallNumber: Int) → Promise<Boolean>

getCurrentStreak() → Promise<Int>

getBlocksToday() → Promise<Int>

requestUnlock() → Promise<String> (JSON: status, remainingMs)

getCooloffStatus() → Promise<String> (JSON: isActive, remainingMs)

setBlockScreenActive(active: Boolean) → Promise<void>

verifyMasterPassword(password: String) → Promise<Boolean>

completeMission(missionId: String) → Promise<void>

sendTestAlert() → Promise<void>

getTrustedPersonName() → Promise<String>

resetStreak() → Promise<void>

getWeeklyStats() → Promise<String> (JSON: cleanDays, totalBlocks, missionsCompleted)

getHourlyDistribution() → Promise<String> (JSON array: 24 hourly block counts)
Events sent FROM Kotlin TO React Native:

GuardianStreakUpdate → {streakDay: number, isClean: boolean}

GuardianWallStatusChange → {wallNumber: number, isActive: boolean}

GuardianBlockEvent → {wallSource: string, triggerType: string}

10. SECURITY RULES — NON-NEGOTIABLE
Password hashing:

SHA-256 with random salt. Format stored: "BASE64_SALT:BASE64_HASH"

Verification uses MessageDigest.isEqual() only. Never String.equals(). Never == operator.

The plaintext password exists only as a local variable inside generateMasterPassword().

It is returned to the caller once and never stored in any field, preference, or log.
No domain strings in logs or database:

Every domain is hashed before any lookup or storage operation.

No Log.d, Log.i, or Timber call may contain a domain name, URL, or user-typed text.
No screenshot persistence:

Every Bitmap captured by VisionSentryService is recycled in a finally block.

No image is written to external storage, internal storage, or gallery.

No image is transmitted over any network connection.
Encrypted storage only:

All sensitive data (password hash, trusted person contact, cooloff timestamp) is stored in EncryptedSharedPreferences only. Plain SharedPreferences is forbidden for any Guardian-sensitive key.
Firebase is anonymous only:

Firebase UID type must be anonymous. No email, phone, or social login.

Firestore documents contain only event counts and timestamps. Never PII.

11. PERFORMANCE BUDGETS — HARD LIMITS
These are not targets. These are requirements. Any code that violates these must be rewritten.
Wall 1 DNS lookup latency: p95 under 30ms

Wall 2 block screen appearance: p95 under 200ms from AccessibilityEvent

Wall 3 ML inference: p95 under 500ms from capture to decision

Service restart after kill: under 3 seconds

Reboot recovery: under 5 seconds from BOOT_COMPLETED to all walls active

Background RAM footprint: under 100MB PSS

Battery consumption: under 5% of total battery per 24-hour cycle

App cold start: under 3 seconds TTID

12. BLOCKING PIPELINE — EXACT ORDER
Every domain goes through these layers in this exact order. No exceptions.
Layer 1: Bloom Filter check (sub-millisecond)

If bloom filter returns definitely-not-blocked → ALLOW immediately

If bloom filter returns maybe-blocked → continue to Layer 2
Layer 2: Pattern matching (under 1ms)

Checks domain against regex patterns (.xxx, .porn, ^sex., ^porn., obfuscation patterns)

If pattern matches → BLOCK with source PATTERN
Layer 3: Compact hash table (under 1ms)

Checks domain hash against in-memory HashSet loaded from blocklist.bin

If match found → BLOCK with source BLOCKLIST
Layer 4: ML domain name classifier (under 5ms, runs on uncertain domains only)

Classifies domain name string using TFLite model

If confidence above 0.85 → BLOCK with source ML_DOMAIN

Otherwise → ALLOW

13. BUSINESS RULES
Streak rules:

A streak increments by 1 for each 24-hour period with zero relapse events.

A relapse is defined as 3 or more block events within any 10-minute window.

On relapse: streak resets to 0, trusted person receives FCM alert within 60 seconds.

Streak is recalculated by querying the database backward from today. It is not stored as a counter.
Cooloff rules:

Default cooloff duration: 24 hours exactly.

Cooloff cannot be cancelled without entering the master password.

Cooloff persists through device reboots (stored in EncryptedSharedPreferences).

Cooloff uses network time (SNTP pool.ntp.org) when available.

If system clock differs from network time by more than 60 seconds: use network time.

Cooloff end timestamp is verified to be actually in the past before clearing.
Master password rules:

Generated as exactly 12 characters, alphanumeric, guaranteed at least 1 uppercase, 1 lowercase, 1 digit.

Uses SecureRandom — never Random or kotlin.random.Random.

After the user shares it via WhatsApp or SMS and confirms: plaintext is never accessible again.

Only the hash is stored. The app cannot recover or display the password after setup.
Block event rules:

Every block event is logged to block_events table with timestamp and wall source.

No domain name is stored in any log entry.

Block events older than 90 days are purged by a scheduled cleanup task.

Block event count is the only data synced to Firestore — not the events themselves.
Mission rules:

3 missions are shown per day.

Missions are selected deterministically by date (same 3 missions all day).

Mission categories: breathing, movement, journaling, social.

Completion state is stored locally. Not synced to cloud.
Freemium rules:

Free tier: Wall 1 (DNS) and Wall 2 (Accessibility). Basic streak. Emergency Mode.

Pro tier ($4.99/month): Wall 3 (ML Vision). Wall 4 (Device Admin). Trusted Person Alerts. Weekly dashboard. Full missions.

Pro status is verified against a subscription record. Implementation TBD for launch — current build assumes Pro for testing.

14. CODING STANDARDS — MANDATORY
Memory rules (Android services):

No static Context references. Use WeakReference if Context must be held.

All Bitmaps recycled in finally blocks. No exceptions to this rule.

Coroutine scopes use CoroutineScope(Dispatchers.IO + SupervisorJob()).

serviceScope.cancel() called in every service's onDestroy().

No GlobalScope anywhere in the codebase.
Thread rules:

All database operations run on Dispatchers.IO.

All accessibility event processing runs on Dispatchers.IO — never the accessibility thread.

All bridge method promises resolve on the correct thread (UiThreadUtil for UI, coroutine IO for database).
AI-generated code rule:

Every block of AI-generated code is marked with a comment:

// AI-Generated: [description] | Reviewed: [yes/no] | Logic Stress Test: [PASSED/PENDING]
Definition of Done:

A task is done only when:

The persistence test passes (service restarts within 3 seconds of force kill)

The bypass test passes (no wall can be disabled without master password)

No new memory leaks in Android Profiler

Battery consumption unchanged from baseline

All new permissions have justification comments in Manifest
Forbidden patterns:

Thread.sleep() for synchronization — use coroutines, IdlingResource, or awaitility

Magic numbers — all thresholds must be named constants

Placeholder comments in critical paths — use TODO() Kotlin function (throws NotImplementedError)

Single-row inserts for bulk operations — always batch at 1000 rows minimum

15. OEM COMPATIBILITY NOTES
Samsung (OneUI 5+):

Auto optimization daily must be disabled programmatically or via user instruction.

Requires three separate battery settings — not one.

GuardianJobService scheduled as fallback.
Xiaomi/Redmi (MIUI 12+):

Autostart permission in Security App is mandatory — without it Guardian dies regardless of battery settings.

MIUI memory cleaner daemon (mcd) resets battery settings every 30 minutes.

Must use adb shell dumpsys deviceidle whitelist during development.
OnePlus (OxygenOS):

Smart Boost feature periodically clears background apps.

RAM Boost setting must be OFF for Guardian to survive.
Realme/OPPO (ColorOS):

Intelligent Control auto-freezes apps after 3 days of no visible user interaction.

App Quick Freeze list must not contain Guardian.
Universal fix applied in code:

AlarmManager chain using setExactAndAllowWhileIdle registers at kernel alarm level.

OEM battery managers cannot cancel registered alarms without root access.

This creates a 15-minute maximum resurrection gap regardless of OEM.

Battery optimization exemption requested programmatically in onboarding using

Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.

16. WHAT THE AI MUST NEVER DO
Do not suggest Firebase Analytics — Guardian uses zero-telemetry policy.

Do not suggest any paid API in the blocking pipeline — all blocking works offline.

Do not use GlobalScope in any service or coroutine.

Do not store any URL, domain name, or user-typed content in any log or database.

Do not write any image to disk during Wall 3 operation.

Do not use String.equals() or == for password comparison — use MessageDigest.isEqual().

Do not suggest WireGuard VPN chaining — explicitly rejected by project decision.

Do not suggest a Core/Sentry app split for Play Store compliance — rejected.

Do not suggest changing the 24-hour cooloff to 72 hours — rejected.

Do not add features not in this document without explicit instruction from the founder.

Do not use plain SharedPreferences for any sensitive key.

Do not call startForeground() inside a coroutine — call directly in onCreate().

Do not return anything other than START_STICKY from any service's onStartCommand().
