# Guardian: Architecture Design Document (ADD)
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## 1. System Overview

Guardian is a **native Android application** built on a Service-Oriented Architecture (SOA). Its core defensive capabilities run as independent, persistent background services coordinated by a central orchestration component called the **Guardian Core**.

The application is split into two distinct layers:
- **The Engine (Kotlin):** All system-level services, ML inference, VPN, and data logic. This layer owns the device.
- **The Interface (React Native):** Dashboard, onboarding, block screen, and emergency mode UI. This layer communicates with the Engine via a local broadcast/bridge pattern.

The fundamental design constraint is **persistence**. Every architectural decision is made through the lens of: *"What happens if Android tries to kill this process?"* The answer must always be: *"It comes back."*

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        USER INTERFACE                        │
│              (React Native — Dashboard, Onboarding)          │
└────────────────────────┬────────────────────────────────────┘
                         │ LocalBroadcastManager / Intent Bridge
┌────────────────────────▼────────────────────────────────────┐
│                     GUARDIAN CORE                            │
│            (Kotlin ForegroundService — Orchestrator)         │
│   START_STICKY │ BOOT_COMPLETED │ Watchdog Thread            │
└──┬─────────────┬──────────────┬──────────────┬─────────────┘
   │             │              │              │
   ▼             ▼              ▼              ▼
┌──────┐    ┌────────┐    ┌─────────┐    ┌──────────┐
│WALL 1│    │WALL 2  │    │WALL 3   │    │WALL 4    │
│DNS/  │    │Access- │    │Vision   │    │Device    │
│VPN   │    │ibility │    │Sentry   │    │Policy    │
│Engine│    │Sentry  │    │(ML)     │    │Controller│
└──┬───┘    └───┬────┘    └────┬────┘    └──────────┘
   │            │              │
   ▼            ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                     DATA LAYER                               │
│   Room DB │ EncryptedSharedPreferences │ Firebase (Cloud)    │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Component Specifications

### 3.1 Guardian Core (Orchestrator)

| Property | Value |
|---|---|
| **Type** | Android `ForegroundService` |
| **Class** | `GuardianCoreService.kt` |
| **Priority** | `FOREGROUND_SERVICE_TYPE_DATA_SYNC` |
| **Notification** | Persistent, non-dismissible |
| **Restart Strategy** | `START_STICKY` in `onStartCommand` |
| **Boot Recovery** | `BroadcastReceiver` on `BOOT_COMPLETED` + `QUICKBOOT_POWERON` |
| **Watchdog** | Coroutine-based heartbeat every 30 seconds; verifies all Walls are active |

**Responsibilities:**
- Instantiate and monitor Wall 1, 2, and 3 services
- Receive broadcasts from Walls and route to Block Overlay
- Manage app lifecycle state (setup, active, locked, cooloff)
- Interface with the React Native UI layer via `LocalBroadcastManager`

**Persistence Contract:**
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Always START_STICKY. Never START_NOT_STICKY.
    return START_STICKY
}

override fun onDestroy() {
    super.onDestroy()
    // Self-resurrect via broadcast
    val broadcastIntent = Intent("com.guardian.RESTART_SERVICE")
    sendBroadcast(broadcastIntent)
}
```

---

### 3.2 Wall 1: DNS/VPN Engine

| Property | Value |
|---|---|
| **Type** | Android `VpnService` |
| **Class** | `DnsVpnService.kt` |
| **Interface** | Local TUN interface — no remote server |
| **Blocklist** | SQLite table of SHA-256 hashed domains (100,000+ entries) |
| **Resolution** | Blocked domains resolve to `0.0.0.0` |
| **Lookup Speed** | Target < 30ms (indexed SQLite query) |
| **Update Mechanism** | `WorkManager` weekly OTA pull via OkHttp |

**Data Flow:**
```
App Makes DNS Request
        │
        ▼
TUN Interface Intercepts
        │
        ▼
Hash domain string → SHA-256
        │
        ▼
SQLite Lookup (indexed)
        │
   ┌────┴────┐
   │         │
MATCH     NO MATCH
   │         │
Resolve   Pass through
0.0.0.0   normally
```

**Key Constraints:**
- No DNS query content is ever logged. Only block event count is recorded.
- The VPN does not route traffic to any remote server. It is a local loopback only.
- VpnService must handle `onRevoke()` — if the user manually revokes VPN permission, Wall 1 must alert Guardian Core and Wall 2 assumes full responsibility.

---

### 3.3 Wall 2: Accessibility Sentry

| Property | Value |
|---|---|
| **Type** | Android `AccessibilityService` |
| **Class** | `AccessibilitySentryService.kt` |
| **Monitor Scope** | All packages (browser URL bars, search fields, Settings navigation) |
| **Interception Speed** | Target < 200ms from URL entry to Block Screen |
| **Self-Protection** | Blocks navigation to Accessibility, Device Admin, and App Info settings |

**Detection Logic:**
```
AccessibilityEvent Received
        │
        ▼
Is event TYPE_WINDOW_STATE_CHANGED or TYPE_VIEW_TEXT_CHANGED?
        │
   ┌────┴────────────────────────────────┐
   │                                     │
Browser Package?                  Settings Package?
   │                                     │
Extract URL from                  Check sub-page name
address bar node                         │
   │                              Matches Accessibility/
   ▼                              Device Admin/App Info?
Hash URL → Check                         │
blocklist                          Yes → GLOBAL_ACTION_HOME
   │                               + Send alert to Core
MATCH → Trigger
Block Overlay
```

**Monitored Packages (Phase 1):**
- `com.android.chrome`
- `org.mozilla.firefox`
- `com.sec.android.app.sbrowser` (Samsung)
- `com.opera.browser`
- `com.android.settings` (self-protection)
- `com.miui.securitycenter` (Xiaomi-specific)

**Self-Protection Rule:** If the active window is `com.android.settings` and the sub-window title contains any of `["Accessibility", "Device Admin", "App Info", "Guardian"]`, immediately execute `GLOBAL_ACTION_HOME` and send `ACTION_SHOW_BLOCK_OVERLAY` broadcast.

---

### 3.4 Wall 3: Vision Sentry (ML Engine)

| Property | Value |
|---|---|
| **Type** | Background service using `MediaProjection` |
| **Class** | `VisionSentryService.kt` |
| **Capture Interval** | Every 7–10 seconds (state-aware — only when browser is foreground) |
| **Capture Resolution** | 540×960 (downscaled from device resolution for performance) |
| **ML Model** | TensorFlow Lite — Quantized MobileNetV2 (NSFW detection) |
| **Confidence Threshold** | > 0.85 for `adult_content` class |
| **Inference Latency** | Target < 500ms on mid-range device (Snapdragon 680 baseline) |
| **Memory Policy** | Image bitmap cleared from memory immediately after inference |

**State-Aware Activation Logic:**
```
Every 5 seconds: Check foreground package
        │
Is foreground package in browser/flagged list?
   ┌────┴────┐
  YES       NO
   │         │
Activate   Idle mode
Capture    (no capture,
Loop       minimal CPU)
```

**Inference Flow:**
```
VirtualDisplay → Bitmap Buffer
        │
Downscale to 224×224 (MobileNetV2 input)
        │
TFLite Interpreter.run()
        │
Parse output tensor
        │
adult_content confidence > 0.85?
   ┌────┴────┐
  YES       NO
   │         │
Broadcast  Clear bitmap
BLOCK_     from memory
OVERLAY    Continue loop
   │
Clear bitmap from memory
```

**Privacy Contract:** No captured bitmap is ever written to disk, gallery, or transmitted over the network. The buffer exists only in-memory for the duration of inference.

---

### 3.5 Wall 4: Policy Controller

| Property | Value |
|---|---|
| **Type** | `DevicePolicyManager` + `DeviceAdminReceiver` |
| **Class** | `GuardianAdminReceiver.kt` |
| **Capability** | Prevents uninstallation and data clearing without master password |
| **Anti-Tamper** | Monitors its own AccessibilityService status every 60 seconds |

**Responsibilities:**
- Request Device Administrator privileges during onboarding
- Block standard uninstallation via system App Info page
- If AccessibilityService is disabled externally (via ADB), Wall 1 (VPN) is notified to remain active as secondary failsafe
- Detect and alert on ADB connection events (Phase 4)

---

## 4. Data Architecture

### 4.1 Local Storage

**EncryptedSharedPreferences** (`guardian_secure_prefs`)

| Key | Type | Description |
|---|---|---|
| `master_key_hash` | String | BCrypt hash of the master password |
| `trusted_person_contact` | String | Encrypted phone/WhatsApp of Trusted Person |
| `setup_complete` | Boolean | Whether onboarding is finished |
| `hard_lock_active` | Boolean | Whether 24-hour cooloff is in progress |
| `cooloff_end_timestamp` | Long | Unix timestamp for cooloff expiry |

**Room Database** (`guardian_db`)

*Table: `streak_log`*
| Column | Type | Description |
|---|---|---|
| `id` | Int (PK) | Auto-increment |
| `date` | String | ISO date string |
| `is_clean` | Boolean | No relapse that day |
| `block_count` | Int | Number of blocks triggered |

*Table: `block_events`*
| Column | Type | Description |
|---|---|---|
| `id` | Int (PK) | Auto-increment |
| `timestamp` | Long | Unix timestamp |
| `wall_source` | String | `WALL_1`, `WALL_2`, `WALL_3` |
| `trigger_type` | String | `DNS`, `URL`, `VISUAL` |
| `hour_of_day` | Int | For danger zone analytics |

*Table: `domain_blocklist`*
| Column | Type | Description |
|---|---|---|
| `domain_hash` | String (PK, Indexed) | SHA-256 of domain string |
| `category` | String | `adult`, `gambling`, etc. |

### 4.2 Cloud Storage (Firebase — Accountability Only)

**Firebase Anonymous Auth:** One anonymous UID per device. Never linked to PII.

**Firestore Path:** `/artifacts/{appId}/public/data/{userId}/`

*Document: `weekly_summary`*
```json
{
  "streak_current": 14,
  "streak_longest": 22,
  "blocks_this_week": 3,
  "missions_completed": 5,
  "last_updated": "2026-01-15T00:00:00Z"
}
```

*Document: `alert_log`*
```json
{
  "event": "BURST_BLOCK",
  "count": 3,
  "window_minutes": 5,
  "timestamp": "2026-01-15T23:47:00Z"
}
```

**Cloud Functions Trigger:** On write to `alert_log` where `event == "BURST_BLOCK"`, dispatch FCM notification to Trusted Person's device token.

---

## 5. Inter-Component Communication

All internal communication uses `LocalBroadcastManager` to avoid IPC overhead and keep all signals within the app process.

| Broadcast Action | Sender | Receiver | Payload |
|---|---|---|---|
| `ACTION_SHOW_BLOCK_OVERLAY` | Any Wall | Guardian Core → UI | `wall_source`, `trigger_type` |
| `ACTION_DISMISS_OVERLAY` | UI (timer complete) | Guardian Core | — |
| `ACTION_STREAK_UPDATE` | Streak Engine | UI | `current_streak`, `is_clean` |
| `ACTION_BURST_ALERT` | Block Event Logger | Cloud Sync Worker | `block_count`, `timestamp` |
| `ACTION_RESTART_SERVICE` | Guardian Core `onDestroy` | RestartReceiver | — |

---

## 6. Security Architecture

| Threat | Mitigation |
|---|---|
| Force Stop via App Info | Wall 4 (DevicePolicyManager) prevents access to App Info for Guardian |
| ADB `am force-stop` | `BOOT_COMPLETED` receiver restarts within 3 seconds; Phase 4 adds ADB detection |
| Manual clock change (bypass cooloff) | WorkManager cross-checks system time against network time on every execution |
| Accessibility disabled via Settings | Wall 2 self-protection blocks Settings navigation; Wall 1 acts as failsafe |
| Safe Mode boot | Phase 4: Detect Safe Mode and restrict all functionality; alert Trusted Person |
| Data Clear via App Info | Wall 4 prevents "Clear Data" action via DevicePolicyManager |

---

## 7. Full Tech Stack

| Layer | Technology | Justification |
|---|---|---|
| **Core Services** | Kotlin | Native Android API — mandatory for VpnService, AccessibilityService, DevicePolicyManager |
| **UI** | React Native | Rapid UI development; communicates with Kotlin engine via bridge |
| **ML** | TensorFlow Lite (MobileNetV2) | On-device, no network, quantized for performance |
| **Local DB** | Room + SQLite | Jetpack-native, type-safe, no external dependency |
| **Encrypted Storage** | EncryptedSharedPreferences | Jetpack Security — hardware-backed keystore on supported devices |
| **Background Tasks** | WorkManager | Handles doze mode and battery optimization correctly |
| **Cloud Auth** | Firebase Anonymous Auth | Zero PII collection |
| **Cloud DB** | Firestore (Free Tier) | Accountability data only |
| **Notifications** | Firebase Cloud Messaging | Trusted Person alerts |
| **Networking** | OkHttp | Blocklist OTA updates |
| **Dependency Injection** | Hilt (Dagger) | Clean service instantiation across Kotlin layer |
