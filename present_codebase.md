# Guardian - Complete Codebase Presentation

## Overview

Guardian is an Android accountability app designed for people in recovery from pornography addiction. It uses aggressive system-level architecture to create a "digital fortress" that prevents the user from bypassing protections, even during moments of weakness. The app cannot be easily uninstalled or disabled.

**Package:** `com.guardian.app`
**Tech Stack:** Kotlin, Jetpack Compose, Hilt (DI), Room (Database), WorkManager, VPN API, Accessibility Services, Device Admin

---

## Architecture

The app is built around a **multi-layered defense system** called "Walls." Each Wall operates independently, so even if one is defeated, the others remain active.

```
┌─────────────────────────────────────────────┐
│                   UI Layer                   │
│  Compose Screens → ViewModels → Repository  │
├─────────────────────────────────────────────┤
│               Defense Walls                 │
│  Wall 1: DNS/VPN Content Filter            │
│  Wall 2: Accessibility Content Scanner     │
│  Wall 4: Device Admin (Anti-Uninstall)     │
├─────────────────────────────────────────────┤
│            Survival Architecture            │
│  CoreService + Watchdog + AlarmManager +    │
│  JobService + Boot Receiver + Resurrector   │
├─────────────────────────────────────────────┤
│               Data Layer                    │
│  Room DB + EncryptedSharedPrefs + Crypto    │
└─────────────────────────────────────────────┘
```

---

## File Structure (52 files)

```
app/src/main/
├── AndroidManifest.xml
├── java/com/guardian/app/
│   ├── GuardianApplication.kt              (89 lines)
│   ├── MainActivity.kt                     (59 lines)
│   │
│   ├── core/                               [Survival & Security]
│   │   ├── GuardianCoreService.kt          (218 lines)
│   │   ├── ServiceResurrector.kt           (203 lines)
│   │   ├── AccessibilityWatchdog.kt        (154 lines)
│   │   ├── GuardianJobService.kt           (71 lines)
│   │   ├── GuardianAlarmReceiver.kt        (65 lines)
│   │   ├── SecurityManager.kt             (155 lines)
│   │   ├── OemCompatibilityManager.kt     (169 lines)
│   │   └── ServiceRestartReceiver.kt       (25 lines)
│   │
│   ├── walls/                              [Content Blocking]
│   │   ├── wall1/
│   │   │   ├── DnsVpnService.kt            (238 lines)
│   │   │   └── TunPacketHandler.kt         (327 lines)
│   │   └── wall2/
│   │       └── AccessibilitySentry.kt      (377 lines)
│   │
│   ├── engine/
│   │   └── BlocklistEngine.kt              (62 lines)
│   │
│   ├── data/                               [Database & Repository]
│   │   ├── db/
│   │   │   ├── GuardianDatabase.kt         (66 lines)
│   │   │   ├── DatabaseHelper.kt           (55 lines)
│   │   │   ├── entities/
│   │   │   │   ├── DomainBlocklist.kt      (12 lines)
│   │   │   │   ├── BlockEvent.kt           (14 lines)
│   │   │   │   └── StreakLog.kt            (13 lines)
│   │   │   └── dao/
│   │   │       ├── DomainBlocklistDao.kt   (19 lines)
│   │   │       ├── BlockEventDao.kt        (21 lines)
│   │   │       └── StreakDao.kt            (19 lines)
│   │   └── repository/
│   │       └── GuardianRepository.kt       (33 lines)
│   │
│   ├── di/                                 [Dependency Injection]
│   │   ├── AppModule.kt                    (23 lines)
│   │   └── DatabaseModule.kt               (21 lines)
│   │
│   ├── broadcast/                          [Broadcast Receivers]
│   │   ├── GuardianDeviceAdminReceiver.kt  (24 lines)
│   │   └── AdbReceiver.kt                 (34 lines)
│   │
│   ├── workers/                            [Background Workers]
│   │   ├── WatchdogWorker.kt               (95 lines)
│   │   ├── BlocklistUpdateWorker.kt        (75 lines)
│   │   └── AccessibilityRecoveryWorker.kt  (109 lines)
│   │
│   ├── manager/
│   │   └── GuardianManager.kt             (20 lines)
│   │
│   ├── util/
│   │   ├── CryptoUtils.kt                 (22 lines)
│   │   └── Constants.kt                   (6 lines)
│   │
│   └── ui/                                 [User Interface]
│       ├── navigation/
│       │   └── AppNavigation.kt            (52 lines)
│       ├── screens/
│       │   ├── SplashScreen.kt             (57 lines)
│       │   ├── OnboardingScreen.kt         (411 lines)
│       │   ├── DashboardScreen.kt          (714 lines)
│       │   ├── SettingsScreen.kt           (118 lines)
│       │   ├── TrustedPersonScreen.kt      (354 lines)
│       │   ├── EmergencyScreen.kt          (234 lines)
│       │   ├── PrivacyPolicyScreen.kt      (59 lines)
│       │   └── CustomerServiceScreen.kt    (173 lines)
│       ├── viewmodel/
│       │   ├── DashboardViewModel.kt       (77 lines)
│       │   └── OnboardingViewModel.kt      (45 lines)
│       ├── overlay/
│       │   ├── BlockOverlayManager.kt      (270 lines)
│       │   └── NetworkOverlayManager.kt    (137 lines)
│       └── theme/
│           ├── Color.kt                    (17 lines)
│           ├── Theme.kt                    (58 lines)
│           ├── Type.kt                     (41 lines)
│           └── NeumorphismCard.kt          (84 lines)
│
├── res/
│   ├── xml/
│   │   ├── accessibility_service_config.xml
│   │   ├── device_admin.xml
│   │   ├── data_extraction_rules.xml
│   │   └── backup_rules.xml
│   └── values/
│       ├── strings.xml
│       ├── colors.xml
│       ├── colors_icon.xml
│       └── themes.xml
```

**Total Kotlin code: ~5,600 lines across 46 source files**

---

## What We Built - Complete Feature Breakdown

### 1. Wall 1: DNS/VPN Content Filter (`walls/wall1/`)

**Purpose:** Blocks adult domains at the network level before any content loads.

**How it works:**
- Creates a local VPN tunnel using Android's `VpnService` API
- Routes only DNS traffic (UDP port 53) through the tunnel — NOT all internet traffic (split tunneling)
- Intercepts raw DNS query packets from the TUN interface
- Extracts the domain name from each DNS packet
- Hashes the domain with SHA-256 and checks against a Room database of blocked domains
- If blocked: responds with `NXDOMAIN` (domain not found) — the site simply doesn't load
- If allowed: forwards the DNS query to Google's DNS server (8.8.8.8) and relays the response back
- Logs every block event to the database for streak tracking

**Anti-Bypass Features:**
- Routes traffic to ALL known DoH/DoT server IPs through the TUN (Cloudflare 1.1.1.1, Quad9 9.9.9.9, OpenDNS, AdGuard, Mullvad, NextDNS, etc.)
- Detects TCP SYN packets to known DoH servers on ports 443/853 and sends TCP RST to kill the connection
- This prevents users from bypassing the DNS filter by switching to encrypted DNS

**Key Classes:**
- `DnsVpnService.kt` — VPN lifecycle, notification, network callback, kill-switch
- `TunPacketHandler.kt` — Raw packet processing, DNS parsing, NXDOMAIN generation, TCP RST, LRU cache (1000 entries)

**Network Resilience:**
- `ConnectivityManager.NetworkCallback` monitors network state
- On network loss: closes VPN interface, shows "Reconnecting..." overlay
- On captive portal detection (hotel WiFi login): suspends VPN to let user log in
- On network validated: rebuilds VPN tunnel
- `onRevoke()`: detects when another VPN app takes over, shows high-priority notification

### 2. Wall 2: Accessibility Content Scanner (`walls/wall2/`)

**Purpose:** Detects adult content typed in ANY app (including incognito browsers) and protects Guardian settings from being changed.

**How it works:**
- Uses Android's Accessibility Service to listen for `TYPE_VIEW_TEXT_CHANGED` and `TYPE_WINDOW_STATE_CHANGED` events
- Runs on a background thread (`HandlerThread` with `THREAD_PRIORITY_BACKGROUND`) to avoid blocking the UI

**Content Detection (3 methods):**

1. **Typed Text Scanning** — When text is typed in any app:
   - Checks against 75+ substring blocked words (porn sites, explicit terms)
   - Checks against 30+ word-boundary blocked words (short words like "sex", "porn")
   - Applies leetspeak normalization (0→o, 1→i, 3→e, 4→a, 5→s, 7→t, $→s, @→a, etc.)
   - Detects and hashes URLs pasted into text fields, checks against domain blocklist

2. **Browser Page Scanning** — When a browser window opens:
   - Traverses the entire accessibility tree (depth-limited to 10 levels)
   - Short-circuit search: returns `true` immediately when any blocked word is found in any node's text or content description
   - No StringBuilder allocation — direct tree traversal for performance
   - Monitors 16 browser packages (Chrome, Firefox, Brave, DuckDuckGo, Opera, Edge, Samsung Browser, Kiwi, Vivaldi, Ecosia, Tor, UC, Mi Browser)

3. **Settings Protection** — When a settings screen opens:
   - Detects if the user is trying to disable Guardian (checks for "accessibility", "use service", "device admin", "deactivate", "uninstall")
   - If Guardian is visible on a dangerous screen: presses Back button automatically
   - Shows toast: "Protection is active. Enter your PIN in the Guardian app to make changes."
   - Respects 5-minute pause window (when parent has entered PIN)
   - Monitors 7 OEM settings packages

**Anti-Tampering:**
- Debounce system: 2-second cooldown per package to avoid scanning the same screen repeatedly
- When destroyed: attempts self-restart via `startService()`, schedules `AccessibilityRecoveryWorker` to check in 30 seconds

### 3. Wall 4: Device Admin (Anti-Uninstall)

**Purpose:** Prevents the app from being uninstalled through normal Android settings.

**How it works:**
- Registers as a Device Administrator via `DevicePolicyManager`
- Uses `force-lock` policy (minimal permissions requested)
- When active, the "Uninstall" button is grayed out in Android settings
- To uninstall, user must first deactivate Device Admin — which triggers the 24-hour cooloff timer

### 4. Survival Architecture (`core/`)

**Purpose:** Keep Guardian alive even when the OS or OEM battery managers try to kill it.

**4 Independent Resurrection Paths:**

| Path | Mechanism | Interval | Survives |
|------|-----------|----------|----------|
| 1 | Immediate `startForegroundService` | Instant | Process alive |
| 2 | `AlarmManager.setExactAndAllowWhileIdle` | 15 min | Doze, OEM kills |
| 3 | `JobService.setPersisted(true)` | 15 min | Doze, Reboot |
| 4 | `WorkManager` periodic | 15 min | App killed |

**GuardianCoreService (218 lines):**
- Foreground service with persistent notification ("Guardian is Active")
- `START_STICKY` — OS automatically restarts if killed
- Acquires 30-second WakeLock on startup to ensure initialization completes
- Registers broadcast receiver for block overlay triggers
- On `onDestroy()`: sends restart command BEFORE calling `super.onDestroy()`
- On `onTaskRemoved()`: immediately resurrects
- Integrates `AccessibilityWatchdog` coroutine monitor

**ServiceResurrector (203 lines):**
- `resurrectCoreService()` — immediate foreground service start
- `resurrectAccessibility()` — attempts to restart AccessibilitySentry
- `scheduleAlarmChain()` — `setExactAndAllowWhileIdle` with `ELAPSED_REALTIME_WAKEUP`
- `scheduleJobService()` — periodic with `setPersisted(true)`, no network/charging requirements
- `resurrectFull()` — orchestrates all paths for "core", "accessibility", or "both"
- `cancelAlarmChain()` / `cancelJobService()` — for clean disable

**AccessibilityWatchdog (154 lines):**
- Coroutine-based monitor running in `GuardianCoreService`'s lifecycle scope
- Checks every 30 seconds:
  1. Is AccessibilitySentry enabled in Android Settings?
  2. Is the AccessibilitySentry process running?
  3. Is the CoreService process running?
- Battery cost: ~0.001%/hr (one binder call per 30s)
- Triggers resurrection callbacks when services are dead

**GuardianJobService (71 lines):**
- Android's official Doze-mode work scheduler
- `setPersisted(true)` — survives device reboot
- Checks if CoreService and AccessibilitySentry are alive, restarts if dead
- Re-schedules itself as safety net

**GuardianAlarmReceiver (65 lines):**
- Fires every 15 minutes via `setExactAndAllowWhileIdle`
- OEM battery managers cannot cancel these alarms without root
- Each fire: checks health, restarts dead services, schedules next alarm (chain continues)

**ServiceRestartReceiver (25 lines):**
- Listens for `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, and custom `com.guardian.RESTART_SERVICE`
- Starts `GuardianCoreService` on device boot

### 5. Security & Encryption (`core/SecurityManager.kt`)

**Encrypted Storage:**
- Uses `EncryptedSharedPreferences` with AES-256-GCM (keys) and AES-256-GCM (values)
- Master key stored in Android Keystore via `MasterKey.Builder`

**Stored State:**
- `setup_complete` — has user finished onboarding
- `master_key_hash` — SHA-256 hash of the 6-digit emergency code
- `trusted_person_contact` — phone number of accountability partner
- `uninstall_unlock_time` — timestamp when 24-hour cooloff expires
- `wall_1_enabled`, `wall_2_enabled` — protection toggle states
- `oem_optimization_acknowledged` — user acknowledged OEM battery instructions
- `failed_unlock_attempts` — rate limiting counter
- `cooloff_end_time`, `cooloff_start_uptime`, `cooloff_duration` — penalty timer (uses both wall clock and uptime to prevent time-change bypass)
- `protection_paused_until` — timestamp when 5-minute pause expires

**Rate Limiting & Anti-Cheat:**
- After 5 wrong codes: 1-minute lockout
- After 6 wrong codes: 5-minute lockout
- After 7+ wrong codes: 30-minute lockout
- Uses dual time tracking (wall clock + `elapsedRealtime()`) — prevents bypassing by changing device time
- Maximum of the two remaining times is used

**Master Key Verification:**
- Constant-time comparison using `MessageDigest.isEqual()` — prevents timing attacks

### 6. Accountability System

**Trusted Person Setup:**
- During onboarding, user enters their Trusted Person's phone number
- App generates a random 6-digit numeric code
- Code is SHA-256 hashed and stored encrypted
- Plain text code is sent via SMS to the Trusted Person
- User NEVER sees the code again after setup

**24-Hour Cooloff:**
- To disable Guardian, user must enter the Trusted Person's code
- Correct code starts a 24-hour countdown timer
- During countdown, user can see the remaining time but cannot disable protections
- When timer expires: Device Admin can be removed, app can be uninstalled
- SMS alert is sent to Trusted Person: "I just requested to disable Guardian. The 24-hour cooloff has started."

**5-Minute Pause:**
- Dashboard has a "Pause Protection" button
- Requires entering the 6-digit Trusted Person code
- Pauses settings protection for 5 minutes (allows changing accessibility/device admin settings)
- Does NOT pause content blocking (Wall 1 and Wall 2 content scanning remain active)
- Timer displayed in real-time on dashboard

### 7. OEM Battery Optimization Handling (`core/OemCompatibilityManager.kt`)

**Supported Manufacturers:**
- Samsung (3-step process: Auto-optimize off, Never sleeping, Unrestricted battery)
- Xiaomi/Redmi/POCO (4-step process: Autostart, Battery Saver, Battery & Performance, Power Saving)
- OnePlus (2-step: Don't optimize, RAM Boost off)
- Realme/OPPO (3-step: App Quick Freeze, Intelligent Control, Allow background)
- Huawei/Honor (Startup Manager)
- Vivo (BgStartUpManager)

**Features:**
- Deep link intents to manufacturer-specific battery settings
- Manual step-by-step instructions for each manufacturer
- `isBatteryOptimizationExempted()` — checks `PowerManager.isIgnoringBatteryOptimizations()`
- `requestSystemExemption()` — programmatic exemption request via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

### 8. Anti-Tampering (`broadcast/`)

**ADB/USB Detection (`AdbReceiver.kt`):**
- Listens for `USB_STATE` broadcasts
- When USB is connected AND ADB is enabled AND Guardian is set up: shows block overlay
- Prevents user from using ADB commands to bypass protections

**Device Admin Receiver (`GuardianDeviceAdminReceiver.kt`):**
- Logs admin enable/disable events
- Shows warning message when user tries to disable admin: "Disabling Guardian will stop all protections. Are you sure?"

### 9. Overlay System (`ui/overlay/`)

**Block Overlay (`BlockOverlayManager.kt`):**
- Full-screen overlay shown when blocked content is detected
- Dark navy background with "DANGER ZONE" title in red
- Motivational message: "Take a breath. You are stronger than this impulse."
- 45-second countdown timer
- After countdown: "YOU DID IT! +1 Strength" reinforcement screen for 3 seconds
- Blocks all hardware buttons (Back, Home, Recents, Volume)
- Redirects to home screen after display
- Debounce: 2-second cooldown between shows
- Pre-inflated on background thread for instant display
- Uses `TYPE_ACCESSIBILITY_OVERLAY` when inside AccessibilityService (no permission needed)

**Network Overlay (`NetworkOverlayManager.kt`):**
- Shown when network is lost (VPN tunnel broken)
- Black background with "Reconnecting... Securing VPN tunnel. Please wait."
- Same hardware button blocking as Block Overlay
- Automatically removed when network is restored

### 10. Database Layer (`data/`)

**Room Database (`GuardianDatabase.kt`):**
- 3 entities, version 2, destructive migration
- `allowMainThreadQueries()` (original architecture decision)
- Callback seeds 2 domains on first creation

**Entities:**
- `DomainBlocklist` — `domainHash` (SHA-256, PK), `category`, `addedAt`
- `BlockEvent` — `id` (auto), `timestamp`, `wallSource`, `triggerType`, `hourOfDay`
- `StreakLog` — `id` (auto), `date` (YYYY-MM-DD), `isClean`, `blockCount`

**DAOs:**
- `DomainBlocklistDao` — `isDomainBlocked(hash)`, `insertDomains(list)`, `insertDomain(domain)`
- `BlockEventDao` — `logBlockEvent(event)`, `getLastBlockTimestamp()`, `getLastRelapseTimestamp()` (detects 3+ blocks in 10 min), `getDaysSince(timestamp)` (SQLite julianday calculation)
- `StreakDao` — `insertStreak(streakLog)`, `getStreakByDate(date)`, `getTotalCleanDays()`

**Streak Calculation (`DatabaseHelper.kt`):**
- Baseline: time of last relapse (3+ blocks in 10 minutes) OR app install time
- Uses native SQLite `julianday()` for accurate midnight-to-midnight calculation
- Returns days + 1 (streak starts at Day 1)

**Blocklist Engine (`engine/BlocklistEngine.kt`):**
- On first launch, unpacks `domains.csv.gz` from assets
- GZIP-compressed CSV with `domain_hash,category` format
- Batch inserts every 10,000 records to keep memory low
- Sets `blocklist_initialized` flag in SharedPreferences

### 11. Background Workers (`workers/`)

**WatchdogWorker (15-minute periodic):**
- Ensures VPN is running if Wall 1 is enabled
- Ensures CoreService is running
- Checks accessibility health — shows recovery notification if dead
- Clears recovery notification if healthy

**BlocklistUpdateWorker (7-day periodic):**
- Downloads updated `domains.csv.gz` from remote URL
- Network constraint: only runs when connected
- Batch inserts new domains into Room

**AccessibilityRecoveryWorker (one-shot, 30-second delay):**
- Fires 30 seconds after `AccessibilitySentry.onDestroy()`
- If service auto-recovered: clears any recovery notification
- If truly dead: shows ONE notification with link to Accessibility Settings
- Not repeating, not nagging

### 12. User Interface (`ui/`)

**Navigation (`AppNavigation.kt`):**
- State-based navigation (no Jetpack Navigation library)
- Routes: splash → onboarding (or dashboard) → settings, trusted_person, emergency, privacy_policy, customer_service

**Splash Screen:**
- Dark background, green circle with "G", "GUARDIAN" text
- 2-second delay, then navigates to appropriate screen

**Onboarding (7 steps):**
1. Welcome — explains the app's aggressive nature
2. Battery Permission — OEM-specific instructions with deep links
3. Overlay Permission — `SYSTEM_ALERT_WINDOW`
4. Accessibility Permission — guides to Enable Accessibility
5. VPN Permission — `VpnService.prepare()`
6. Device Admin — `ACTION_ADD_DEVICE_ADMIN`
7. Partner Setup — phone number entry, generates code, sends SMS

**Dashboard:**
- Master shield UI: "SYSTEM ARMED" (green) or "DEFENSES COMPROMISED" (pulsing red)
- Gamified streak display with circular progress ring:
  - STREAK (0-2 days) → MOMENTUM (3-6) → SOLID (7-13) → ON FIRE (14-29) → UNSTOPPABLE (30-89) → GODLIKE (90+)
- Color-coded milestone ring (primary → amber → fire orange → neon green → neon purple)
- 3 protection toggles with neumorphism card design:
  - Wall 1 (DNS/VPN) — starts VPN service
  - Wall 2 (Accessibility) — opens accessibility settings
  - Wall 4 (Device Admin) — requests admin
- Each toggle is lockable (requires Trusted Person code to disable)
- "Pause Protection (5 min)" button — PIN-gated
- OEM AutoStart button (manufacturer-specific)
- Overlay permission request button
- "EMERGENCY MODE" button
- Support FAB → Customer Service

**Trusted Person Screen:**
- Setup Mode: phone number entry → generates code → sends SMS
- Existing Mode: code entry → 24-hour cooloff → countdown timer display
- Rate limiting with progressive lockout
- Wrong code toast

**Emergency Screen:**
- Full immersive mode (hides system bars, keeps screen on)
- Breathing exercise: 4s inhale → 7s hold → 8s exhale (repeating)
- Animated expanding/contracting blue circle
- Action instruction: "Stand up, leave this room, get a glass of cold water"
- "Call Trusted Person" button (dials the partner's number)

**Settings Screen:**
- Privacy Policy link
- Disable Guardian link (→ Trusted Person screen)

**Customer Service Screen:**
- Problem and suggestion text fields
- Submits to Google Forms via POST request
- Includes device info (OS version, manufacturer, model)

**Privacy Policy Screen:**
- Static text explaining local-only data storage, VPN behavior, Accessibility usage, no tracking

### 13. Theme & Design System

**Colors:**
- Dark: Deep Navy (#0F172A), Surface Dark (#1E293B), Neon Teal (#14B8A6), Cyber Green (#10B981), Alert Red (#EF4444)
- Light: Light Background (#F8FAFC), Surface White (#FFFFFF)

**Neumorphism Cards:**
- Dual shadow system (dark shadow + light highlight)
- Inner glow effect via radial gradient
- Subtle border stroke
- Works in both dark and light themes

**Typography:**
- SansSerif font family
- Display: 32sp Bold
- Title: 22sp SemiBold
- Body: 16sp Normal
- Label: 12sp Medium

### 14. Dependency Injection (`di/`)

**Hilt Modules:**
- `AppModule` — provides `GuardianRepository` as singleton
- `DatabaseModule` — provides `DatabaseHelper` as singleton

**Injection Points:**
- `@HiltAndroidApp` — `GuardianApplication`
- `@AndroidEntryPoint` — `MainActivity`, `GuardianCoreService`, `DnsVpnService`, `AccessibilitySentry`
- `@HiltViewModel` — `DashboardViewModel`, `OnboardingViewModel`
- `@Inject` — repository injection in ViewModels and AccessibilitySentry

### 15. Build Configuration

**Gradle:**
- AGP 9.2.1
- Kotlin 2.2.10
- KSP 2.2.10-2.0.2 (Room)
- Hilt 2.57.1 (Kapt)
- Compose BOM 2026.02.01
- Firebase BOM 33.1.2
- compileSdk 36, minSdk 24, targetSdk 36

**Permissions (12):**
- `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_VPN`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`
- `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`
- `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `ACCESS_NETWORK_STATE`

**Proguard:**
- Keep all `androidx.security.crypto.**` (encrypted prefs)
- Keep all `com.guardian.app.**` (no obfuscation for reflection-sensitive code)
- Keep Kotlin coroutines internals

**Data Extraction Rules:**
- Excludes `guardian_secure_prefs.xml` and `guardian_db` from cloud backup and device transfer

### 16. Cryptography (`util/CryptoUtils.kt`)

- `sha256(ByteArray)` — SHA-256 hash, returns hex string
- `generateMasterPassword()` — 6-digit random numeric code using `SecureRandom`
- `constantTimeEquals(ByteArray, ByteArray)` — timing-attack-safe comparison via `MessageDigest.isEqual()`

---

## What's NOT Built Yet (Future Work)

1. **Bloom Filter** — Memory-efficient probabilistic data structure for faster domain lookups (currently using Room DB)
2. **Cloud Accountability** — Firebase integration for remote streak reporting to Trusted Person
3. **ML/On-Device Content Analysis** — Screen content classification using on-device models
4. **Engine Architecture Extraction** — Modular engine system for pluggable blocking strategies
5. **React Native Bridge** — Cross-platform UI layer
6. **Comprehensive Test Suite** — Unit tests, integration tests, UI tests

---

## Key Design Decisions

1. **Survival First** — The app prioritizes staying alive over everything else. 4 independent resurrection paths ensure at least one fires within 15 minutes.

2. **Accountability Over Control** — The app doesn't just block content; it creates human accountability through the Trusted Person system and 24-hour cooloff.

3. **No Data Leaves the Device** — All data is stored locally in encrypted form. The only network call is the customer service feedback form (Google Forms) and optional blocklist updates.

4. **Dual Time Tracking** — Cooloff timers use both `System.currentTimeMillis()` and `SystemClock.elapsedRealtime()` to prevent bypassing by changing device time.

5. **Progressive Friction** — Wrong codes don't just fail; they trigger progressively longer lockouts (1min → 5min → 30min).

6. **OEM-Specific Handling** — Different Android manufacturers have different battery optimization systems. Guardian provides specific instructions and deep links for each.

7. **Overlay Pre-Inflation** — Block overlay views are inflated on a background thread during service creation, so they appear instantly when needed.

8. **No User-Dependent Survival** — The app doesn't rely on the user to re-enable services. It resurrects itself through AlarmManager, JobService, and WorkManager.

---

## Build Status

**Current Status:** BUILD SUCCESSFUL

**Resolved Issues:**
- KSP/Kapt/Hilt version mismatch (upgraded Hilt to 2.57.1)
- Missing SecurityManager import in AdbReceiver
- Windows path length limits (resolved with configuration)

**Remaining Warnings (non-blocking):**
- `getRunningServices()` deprecated (intentional usage)
- `FLAG_FULLSCREEN` deprecated
- `Icons.Filled.ArrowBack` deprecated (use AutoMirrored)
- `stopForeground(Boolean)` deprecated (use `STOP_FOREGROUND_DETACH`)
- Kotlin annotation target warnings
- AGP legacy variant API warnings
