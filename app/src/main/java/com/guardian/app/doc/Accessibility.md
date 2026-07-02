# Guardian: Accessibility Feature Specification & Engineering Blueprint
**Version:** 1.0  
**Status:** Under Design  
**Target Component:** Wall 2 (Accessibility Sentry — `com.guardian.app.walls.wall2`)  

---

## 1. Mission
Observe user interactions across supported apps and detect attempts to access protected content while minimizing performance impact.

> [!IMPORTANT]  
> The Accessibility Sentry is strictly an **observer**, not a blocker. It observes what is happening on screen and produces well-defined events. The Protection Engine (managed by Guardian Core and the Overlay Managers) makes the decisions on whether to block and how to handle triggers.

---

## 2. Functional Requirements
* **Start Automatically:** The service must bind automatically when the system boots and the accessibility permission is active.
* **Observe Accessibility Events:** Filter and process incoming system accessibility events (`AccessibilityEvent`).
* **Detect Supported Browsers:** Identify when a target browser package becomes the active foreground application.
* **Extract URLs:** Extract the active web address from browser URL inputs and address bars.
* **Detect Keywords:** Scan on-screen text nodes for blocked substrings and word boundary matches.
* **Detect App & Package Changes:** Detect when the user transitions to a new package (app switching).
* **Detect Permission Removal:** Spot attempts to disable the Accessibility Service or Device Admin inside Settings.
* **Notify Protection Engine:** Broadcast well-structured event payloads to the central orchestrator upon detection.
* **Resilience & Recovery:** Resume operation immediately after device reboots or process termination.
* **Graceful Lifecycle:** Support clean starting and stopping when authorized (e.g., when the correct PIN is provided).

---

## 3. Non-Functional Requirements
* **CPU Consumption:** Average CPU utilization must remain **< 3%** under normal operation.
* **RAM Footprint:** Heap memory allocated must remain **< 40MB**.
* **Zero UI Lag:** Traversal and scanning must not block the system's main UI thread (processing must complete on a background worker thread).
* **No ANRs:** Ensure no App Not Responding crashes occur, even during heavy scrolling on complex web pages.
* **OS Support:** Full compatibility with Android 10 through Android 16.
* **Recovery Time:** If the service is killed, it must recover and resume monitoring in **< 3 seconds**.
* **Processing Latency:** From event emission to classification and notification, total processing time must be **< 100ms**.

---

## 4. Constraints
* **Accessibility API Limitations:** Android UI nodes are updated asynchronously. If the system lags, `rootInActiveWindow` may return stale data.
* **OEM Customization (Xiaomi, Samsung):** Aggressive battery managers (MIUI/HyperOS, Device Care) kill background services. The service must be registered with foreground capabilities and work with system keep-alives.
* **Battery Restrictions:** Keeping the CPU active for scanning drains battery. Events must be debounced and rate-limited.
* **Privacy Controls:** Google Play Store enforces strict guidelines for Accessibility Services. The service must function 100% offline, never transmit on-screen content over the network, and ignore text fields flagged as passwords or sensitive inputs.
* **Window Isolation:** Split-screen or multi-window modes generate interleaved events from different packages. The service must track window focus indexes to identify which app is truly in the foreground.

---

## 5. Research Data: Android Accessibility Architecture
### How AccessibilityService Works
When registered, the Android system sends `AccessibilityEvent` packets to the service's `onAccessibilityEvent()` callback. The service queries the active window node hierarchy represented by a tree of `AccessibilityNodeInfo` objects.

### Event Triggers
Traversing the complete layout tree on every single change is extremely expensive. We use a targeted event filter strategy:
* `TYPE_WINDOW_STATE_CHANGED`: Triggers when the user switches applications, opens a menu, or opens a dialog. Excellent for low-frequency app boundary detection.
* `TYPE_WINDOW_CONTENT_CHANGED`: Triggers when content inside the window changes (scrolling, loading new views). **Critical for detecting URL updates and lazy-loaded web content.** Must be aggressively debounced.
* `TYPE_VIEW_TEXT_CHANGED`: Triggers when the user types or pastes text. Essential for scanning input fields.

### Traversal Cost vs. Node Count
Evaluating node traversal costs shows that calling `node.getChild(i)` recursively has an $O(N)$ time complexity where $N$ is the number of nodes in the layout tree.
* Small layouts (< 50 nodes): Traversal takes **< 2ms**.
* Massive web view scroll views (> 800 nodes): Traversal can exceed **50ms**.
* **Mitigation:** We implement a **short-circuit tree traversal** that stops scanning the moment a blocked keyword is found, and limit recursive depth to a maximum of 10 levels.

---

## 6. Threat Model & Anti-Bypass Matrix

| Threat Vector | Mechanism | Mitigation Strategy |
|:---|:---|:---|
| **Manual Service Disablement** | User navigates to Settings -> Accessibility and turns off the Guardian toggle. | Scan settings app packages. If the active screen is the Guardian Accessibility sub-screen and contains "deactivate" or "stop", perform `GLOBAL_ACTION_BACK` immediately. |
| **OEM Background Murder** | Aggressive task killers (like Xiaomi's MIUI) kill the process during screen-off. | Launch a weekly/daily watchdog via `WorkManager` and `AlarmManager` that verifies if the service is running. If dead, display a system notification prompting the user to re-enable it. |
| **Root Node Lockouts** | System dialogs or secure windows return `null` for `rootInActiveWindow`. | Cache the last known active package. If the root node is null but the package hasn't changed, treat it as a continuation of the same app state. |
| **Heavy Scroll Spam** | Rapid scrolling on lists causes thousands of `TYPE_WINDOW_CONTENT_CHANGED` events per second, causing CPU spikes. | Implement an active package-based debounce timer. Do not scan the same package more than once every **2000ms** unless a window state change occurs. |
| **Split Screen Bypass** | User puts browser in the bottom half and settings in the top half to disable permission. | Query all interactive windows using `getWindows()`. Identify the package of the currently focused window and block settings interaction. |

---

## 7. Component Architecture
To ensure clean separation of concerns, the Accessibility module is divided into modular, single-responsibility components:

```
                            ┌─────────────────────────────────┐
                            │    Android Accessibility API    │
                            └────────────────┬────────────────┘
                                             │ AccessibilityEvent
                                             ▼
                            ┌─────────────────────────────────┐
                            │     AccessibilitySentry (Wrap)  │
                            └────────────────┬────────────────┘
                                             │
                                             ▼
                            ┌─────────────────────────────────┐
                            │          Event Manager          │
                            └────────────────┬────────────────┘
                                             │
                                             ▼
                            ┌─────────────────────────────────┐
                            │          Event Filter           │
                            │   (Package Debounce & Timers)   │
                            └────────────────┬────────────────┘
                                             │ Cleaned Event
                                             ▼
     ┌───────────────────────────────────────┴──────────────────────────────────────┐
     │                                                                              │
     ▼                                                                              ▼
┌──────────────────────────────┐                                            ┌──────────────────────────────┐
│       Browser Detector       │                                            │         App Detector         │
└──────────────┬───────────────┘                                            └──────────────┬───────────────┘
               │ Matches Browser Pkg                                                       │ Matches Settings Pkg
               ▼                                                                           ▼
┌──────────────────────────────┐                                            ┌──────────────────────────────┐
│        URL Extractor         │                                            │      Settings Protection     │
│   (Traverses URL Bar nodes)  │                                            │  (Detects dangerous buttons) │
└──────────────┬───────────────┘                                            └──────────────┬───────────────┘
               │ URL Found                                                                 │ Threat Detected
               ▼                                                                           ▼
┌──────────────────────────────┐                                            ┌──────────────────────────────┐
│       Keyword Scanner        │                                            │       Recovery Manager       │
│  (Short-circuit text scan)   │                                            │ (Watchdog & Self-resurrect)  │
└──────────────┬───────────────┘                                            └──────────────┬───────────────┘
               │ Block Trigger                                                             │ Trigger Recovery
               └─────────────────────────────┬─────────────────────────────────────────────┘
                                             ▼
                            ┌─────────────────────────────────┐
                            │        Protection Engine        │
                            │  (Guardian Core / Overlay Mgr)  │
                            └─────────────────────────────────┘
```

---

## 8. Interfaces
All modules communicate through abstract interfaces, making them easy to unit-test and swap out:

```kotlin
interface AccessibilityObserver {
    fun start()
    fun stop()
    fun getStatus(): SentryState
}

interface UrlExtractor {
    fun extractUrl(rootNode: AccessibilityNodeInfo): String?
}

interface BrowserDetector {
    fun isSupportedBrowser(packageName: String): Boolean
    fun getBrowserUrlBarId(packageName: String): String?
}

interface KeywordDetector {
    fun containsBlockedKeyword(rootNode: AccessibilityNodeInfo): Boolean
}
```

---

## 9. Event Flow Sequence
The lifecycle of an incoming event traverses these processing layers:

```
[AccessibilityEvent] ──> [Event Manager] ──> [Event Filter] ──> [Browser Detector]
                                                                        │
                                                            Supported Browser Found?
                                                                 ┌──────┴──────┐
                                                                YES            NO
                                                                 │             │
                                                          [URL Extractor] [App Detector]
                                                                 │             │
                                                            URL Found?      Settings?
                                                          ┌──────┴──────┐      │
                                                         YES            NO     ▼
                                                          │             │  [Self-Protect]
                                                    [Hash Check]  [Text Scan]  │
                                                          │             │      ▼
                                                          ▼             ▼  [Trigger Block]
                                                    [Protection Engine (Decide Block)]
```

---

## 10. State Machine
The internal state of the observer is tracked via a strict state model rather than simple booleans:

```
    ┌───────────────┐  Permission Granted  ┌───────────────┐
    │   DISABLED    ├─────────────────────>│  PERMISSION   │
    └───────▲───────┘                      │    GRANTED    │
            │                              └───────┬───────┘
            │ Revoked                              │ Initialize HandlerThreads
            │                                      ▼
    ┌───────┴───────┐  Setup Incomplete    ┌───────────────┐
    │     ERROR     │<─────────────────────┤   STARTING    │
    └───────▲───────┘                      └───────┬───────┘
            │                                      │ Setup Complete
            │ System Failure                       ▼
    ┌───────┴───────┐  Deactivation Pin    ┌───────────────┐
    │  RECOVERING   │<─────────────────────┤    RUNNING    │
    └───────▲───────┘                      └───────┬───────┘
            │                                      │ Temporary Unlock
            │ Watchdog Trigger                     ▼
            └──────────────────────────────┤    PAUSED     │
                                           └───────────────┘
```

---

## 11. Detection Engine
The detection engine handles parsing and categorizing active screen elements:
* **Browser Navigation:** Matches browser package names, extracts text from address bar nodes (using known node IDs), and normalizes text into domains.
* **Input Typing:** Captures keystroke sequences in standard input fields. It runs a sliding-window character buffer to detect typed bypass attempts.
* **App Switch Detection:** Monitors package updates to block unauthorized settings menus.
* **Incognito Detection:** Identifies private browsing frames by scanning for specific strings (e.g., `"incognito"`, `"private browsing"`) or resource layout IDs unique to private tab menus.

---

## 12. Recovery System
To guard against system shutdowns, the Accessibility service runs a multi-stage recovery protocol:
1. **Self-Resurrection Command:** In `onDestroy()`, the service attempts to start itself immediately via an explicit background service intent.
2. **WorkManager Watchdog:** Schedules a one-shot `AccessibilityRecoveryWorker` delayed by 30 seconds. If the service is dead when the worker runs, the worker fires a high-importance user notification to guide them back to Settings to enable it.
3. **Heartbeat Thread:** The orchestrator service (`GuardianCoreService`) queries the accessibility state every 30 seconds. If deactivated, it restricts app access and prompts for remediation.

---

## 13. Instrumentation & Performance Metrics
We measure system health by exporting these metrics locally:
* `average_event_processing_time_ms`: Time taken to filter and scan nodes.
* `dropped_events_count`: Number of skipped events due to debouncing.
* `traversed_nodes_peak`: Peak number of layout elements processed in a single run.
* `recovery_trigger_count`: Number of times the watchdog resurrected the service.
* `cpu_load_percentage`: Current processor utilization of the Accessibility process.

---

## 14. Logging Policy
* **Allowed Logs:** Service connection states, target app transitions, security boundary triggers, and background resurrection logs.
* **Forbidden Logs:** Keystrokes, search queries, password field changes, URL query parameters containing PII, or clipboard contents.
* **Formatting:** All logs must run through a production formatter that strips parameter values unless `BuildConfig.DEBUG` is active.

---

## 15. Testing Plan
### Component Unit Tests
* **`BrowserDetectorTest`:** Validate browser package matching list.
* **`UrlExtractorTest`:** Provide mock `AccessibilityNodeInfo` trees and verify correct URL parsing.
* **`DebounceFilterTest`:** Confirm events from the same app are ignored if fired within the 2000ms cooldown window.

### Integration Tests
* **`AppSwitchToSettingsTest`:** Verify that navigating to the Settings app generates the correct protection event and trigger callback within 100ms.

### Stress Testing Checklist
* [ ] Scroll a 1000-item Reddit feed in Chrome. Verify CPU usage stays below 5%.
* [ ] Switch between Chrome, settings, and the home screen rapidly 50 times.
* [ ] Enter split-screen mode with Chrome and Settings running side-by-side. Verify both apps are tracked correctly.
* [ ] Force-stop the app process using Android Studio. Verify the recovery notification fires within 35 seconds.

---

## 16. Security & Privacy Review
* **Process Isolation:** The Accessibility service runs inside the secure application sandbox. No other third-party application can read our variables or access our scanned nodes.
* **Secure Cache:** Cached URL mappings and keyword logs are stored in RAM only and are cleared immediately when the device screen turns off or the active package changes.

---

## 17. Maintenance Plan
* **Android OS updates:** As Android releases new versions (up to Android 16), the Accessibility Node search strategy must be verified, as Google sometimes restricts access to standard Chrome view IDs.
* **Upstream Sync:** Update the keyword lists and browser package targets dynamically from the database.
* **Performance Checks:** Profile memory leaks weekly during local development builds using tools like LeakCanary.
