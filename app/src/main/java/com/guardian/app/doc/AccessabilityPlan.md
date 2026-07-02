# Guardian Protection Platform — Wall 2 (Accessibility Sensor) Implementation Plan

**Architecture Version:** 1.0.0  
**Architecture Status:** ✅ APPROVED — Frozen  
**Review Confidence:** 9.9/10  
**Target Component:** Wall 2 — Accessibility Sensor (`com.guardian.app.walls.wall2`)

> **Implementation Rules:**
> 1. Frozen interfaces may not be modified by AI agents. If implementation is difficult, change the implementation, not the architecture.
> 2. No feature is merged without tests (unit + integration + manual).
> 3. V1 scope is strictly core blocking features. Support features (Protection Score, Audit Trail, Metrics, Compatibility Levels) go to V1.1 if they delay the blocker.  

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Frozen Contracts](#2-frozen-contracts)
3. [File Manifest](#3-file-manifest)
4. [Implementation Phases](#4-implementation-phases)
5. [Architecture Decisions](#5-architecture-decisions)
6. [Risk Register](#6-risk-register)
7. [Testing Strategy](#7-testing-strategy)

---

## 1. Architecture Overview

### Core Principle

The Guardian Protection Platform is an **event-driven** architecture. Every protection mechanism (VPN, Accessibility, future sensors) is a **sensor** that emits standardized `ProtectionEvent`s. A single `ProtectionPolicyEngine` evaluates events through `RuleProvider`s and the current `GuardianContext`, then produces a `Decision`. The `OverlayController` executes the decision.

### High-Level Flow

```
Protection Sensor (Wall 2 — Accessibility)
    │
    ▼
ProtectionEvent (immutable, typed metadata, eventId + sessionId)
    │
    ▼
ProtectionOrchestrator (thin: init / lifecycle / wiring / state sync)
    │
    ▼
ProtectionPolicyEngine
    │
    ├──► RuleProvider<T>[] (DomainRuleProvider, AppRuleProvider, …)
    ├──► CapabilityRegistry (CapabilityResult — not Boolean)
    ├──► GuardianContext (configuration + capabilities + state + session)
    ├──► MetricsRepository (telemetry — separate from config)
    └──► AuditTrail (local append-only event log)
    │
    ▼
Decision (Block / Warn / Ignore / Log / Observe / Recover)
    │
    ▼
OverlayController
    ├── OverlayQueue (priority-ordered + OverlayStrategy)
    └── BlockOverlayManager (renders — existing, refactored)
```

### Sensor Independence

Wall 2 (Accessibility) is one implementation of a `ProtectionSensor`. The core platform knows nothing about `AccessibilityService`, Android window nodes, or browser resource IDs. It only processes `ProtectionEvent`s. This allows future sensors (Device Owner, System Integrity, AI) to be added without modifying the core.

```
Guardian Protection Platform
│
├── VPN Sensor (Wall 1)
├── Accessibility Sensor (Wall 2) ← this plan
├── Device Security Sensor (Wall 3 — future)
├── System Integrity Sensor (Wall 4 — future)
└── AI Sensor (Wall 5 — future)
```

---

## 2. Frozen Contracts

These interfaces must not be modified without architecture review. Implementation classes are free to evolve.

### 2.1 ProtectionEvent

```kotlin
sealed interface ProtectionEvent {
    val eventId: UUID
    val sessionId: String
    val timestamp: Long
    val source: String
    val severity: Severity
    val metadata: EventMetadata
}

sealed interface EventMetadata

data class BrowserMetadata(
    val url: String?,
    val domain: String?,
    val browserPackage: String,
    val extractionMethod: String
) : EventMetadata

data class AppMetadata(
    val packageName: String,
    val label: String?,
    val category: String
) : EventMetadata

data class SettingsMetadata(
    val settingScreen: String,
    val detectedDanger: String
) : EventMetadata

data class SearchMetadata(
    val query: String,
    val engine: String
) : EventMetadata

data class InstallerMetadata(
    val installerPackage: String,
    val targetPackage: String?
) : EventMetadata

data class HealthMetadata(
    val score: Int,
    val components: Map<String, Boolean>
) : EventMetadata

enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
```

### 2.2 Decision

```kotlin
sealed class Decision {
    data object Block : Decision()
    data class Warn(val message: String) : Decision()
    data object Ignore : Decision()
    data object Log : Decision()
    data object Observe : Decision()
    data class Recover(val errorType: ErrorType) : Decision()
}

sealed class ErrorType {
    data object Recoverable : ErrorType()
    data object Fatal : ErrorType()
    data object Permission : ErrorType()
    data object Network : ErrorType()
    data object Unexpected : ErrorType()
}
```

### 2.3 RuleProvider

```kotlin
interface RuleProvider<T> {
    fun matches(target: T): Boolean
}

// Concrete typed providers (not frozen, but the pattern is):
interface DomainRuleProvider : RuleProvider<String>
interface AppRuleProvider : RuleProvider<AppSignature>
```

### 2.4 CapabilityRegistry

```kotlin
sealed class CapabilityResult {
    data object Supported : CapabilityResult()
    data class Unsupported(val reason: String) : CapabilityResult()
    data class Partial(val limitation: String) : CapabilityResult()
}

@Singleton
class CapabilityRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canExtractBrowserUrl(browserPackage: String): CapabilityResult
    fun canDrawOverlay(): CapabilityResult
    fun canReadInstaller(): CapabilityResult
    fun canDetectSplitScreen(): CapabilityResult
    // …
}
```

### 2.5 GuardianContext

```kotlin
data class GuardianContext(
    val configuration: GuardianConfiguration,
    val capabilities: CapabilitySnapshot,
    val protectionState: ProtectionState,
    val currentSession: SessionInfo
)

data class GuardianConfiguration(
    val wall1Enabled: Boolean,
    val wall2Enabled: Boolean,
    val setupComplete: Boolean,
    val protectionWeights: ProtectionWeights
)

data class CapabilitySnapshot(
    val browserExtraction: Map<String, CapabilityResult>,
    val overlay: CapabilityResult,
    val installerDetection: CapabilityResult,
    val splitScreenDetection: CapabilityResult,
    val timestamp: Long
)

data class ProtectionState(
    val sentryState: SentryState,
    val isPaused: Boolean,
    val pauseRemainingMs: Long
)

data class SessionInfo(
    val sessionId: String,
    val startedAt: Long,
    val eventCount: Long
)
```

### 2.6 BrowserAdapter

```kotlin
interface BrowserAdapter {
    fun extractUrl(
        rootNode: AccessibilityNodeInfo,
        profile: BrowserProfile
    ): String?

    fun getProfile(packageName: String): BrowserProfile?
}

data class BrowserProfile(
    val packageNames: Set<String>,
    val compatibility: CompatibilityLevel,
    val urlBarResourceIds: Map<String, String>,
    val extractionStrategies: List<ExtractionStrategy>,
    val knownIssues: List<String>
)

enum class CompatibilityLevel {
    Verified,    // Tested manually on this version
    Supported,   // Expected to work
    Experimental, // May work, known risks
    Broken       // Known to fail
}

enum class ExtractionStrategy {
    RESOURCE_ID,    // findByViewId
    TEXT_PATTERN,   // regex match on URL-like text nodes
    HINT_ATTRIBUTE, // contentDescription / hint text
    HEURISTIC       // last resort — search_contains("http") type logic
}
```

### 2.7 OverlayController

```kotlin
interface OverlayController {
    fun showBlock(event: ProtectionEvent, strategy: OverlayStrategy)
    fun dismissBlock()
    fun getQueueDepth(): Int
}

enum class OverlayStrategy {
    Replace,             // New overlay replaces current
    Queue,               // New overlay waits in priority queue
    Merge,               // Combine into one overlay
    IgnoreLowerPriority  // Only show if higher priority than current
}
```

### 2.8 SentryState (State Machine)

```kotlin
enum class SentryState {
    DISABLED,
    PERMISSION_GRANTED,
    STARTING,
    RUNNING,
    PAUSED,
    STOPPING,
    RESTARTING,
    ERROR,
    RECOVERING
}

// Legal transitions:
// DISABLED → PERMISSION_GRANTED (user enables a11y)
// PERMISSION_GRANTED → STARTING (service connected)
// STARTING → RUNNING (setup complete)
// RUNNING → PAUSED (user enters PIN)
// RUNNING → STOPPING (user disables via PIN)
// RUNNING → ERROR (system failure)
// PAUSED → RUNNING (pause expires)
// STOPPING → DISABLED (fully stopped)
// ERROR → RECOVERING (watchdog triggers)
// RECOVERING → RUNNING (recovery succeeded)
// RECOVERING → DISABLED (recovery failed, user must re-enable)
// RESTARTING → STARTING (explicit restart)
```

### 2.9 MetricsRepository

```kotlin
interface MetricsRepository {
    fun recordEventProcessingTime(ms: Long)
    fun recordOverlayLatency(ms: Long)
    fun recordTreeTraversal(nodes: Int, depth: Int, ms: Long)
    fun recordDecision(decision: Decision)
    fun recordDroppedEvent(category: String)
    fun recordRecovery()
    fun recordCacheHitRate(hits: Long, misses: Long)
    fun snapshot(): MetricsSnapshot
}

data class MetricsSnapshot(
    val avgEventLatencyMs: Double,
    val p95EventLatencyMs: Double,
    val avgTreeTraversalMs: Double,
    val avgTreeNodes: Double,
    val avgTreeDepth: Double,
    val maxQueueDepth: Int,
    val avgOverlayDurationMs: Double,
    val droppedEvents: Long,
    val recoveryCount: Long,
    val cacheHitRate: Double,
    val falsePositiveCount: Long,
    val memoryUsageMb: Long
)
```

### 2.10 AuditTrail

```kotlin
data class AuditEntry(
    val timestamp: Long,
    val eventId: UUID,
    val eventType: String,
    val decision: String,
    val source: String,
    val summary: String
)

interface AuditTrail {
    fun append(entry: AuditEntry)
    fun recent(count: Int): List<AuditEntry>
    fun clear()
    fun count(): Int
}
```

### 2.11 ProtectionScore

```kotlin
data class ProtectionWeights(
    val accessibility: Int = 20,
    val vpn: Int = 20,
    val overlay: Int = 15,
    val router: Int = 15,
    val heartbeat: Int = 10,
    val rules: Int = 20
) {
    fun total(): Int = accessibility + vpn + overlay + router + heartbeat + rules
}

data class ProtectionScore(
    val score: Int,
    val components: Map<String, Boolean>,
    val timestamp: Long
)
```

### 2.12 TraversalLimits

```kotlin
object TraversalLimits {
    const val MAX_DEPTH = 25
    const val MAX_NODES = 500
    const val MAX_TIME_MS = 50L
}
```

### 2.13 AppSignature

```kotlin
data class AppSignature(
    val packageName: String,
    val label: String?,
    val installer: String?,
    val category: AppCategory,
    val aliases: Set<String> = emptySet()
)

enum class AppCategory {
    KNOWN_ADULT, SUSPICIOUS, INSTALLER, SETTINGS
}
```

---

## 3. File Manifest

### 3.1 New Files

```
walls/wall2/
├── event/
│   ├── ProtectionEvent.kt       — sealed interface + typed metadata subtypes
│   ├── EventDebouncer.kt         — per-category debounce timers
│   └── Decision.kt               — sealed class + ErrorType sealed class
├── router/
│   └── AccessibilityEventRouter.kt — routes raw events to correct monitor
├── adapter/
│   ├── BrowserAdapter.kt         — interface + ExtractionStrategy enum
│   ├── BrowserProfile.kt         — data class + CompatibilityLevel enum
│   ├── ChromeProfile.kt          — Chrome-specific profile
│   ├── FirefoxProfile.kt         — Firefox-specific profile
│   ├── BraveProfile.kt           — Brave-specific profile
│   ├── SamsungProfile.kt         — Samsung Internet profile
│   ├── GenericProfile.kt         — fallback heuristic profile
│   ├── SearchEngineAdapter.kt    — interface
│   ├── GoogleSearchAdapter.kt    — Google search result detection
│   ├── InstallerAdapter.kt       — interface
│   ├── PlayStoreAdapter.kt       — Play Store installer detection
│   └── PackageInstallerAdapter.kt — AOSP package installer detection
├── monitor/
│   ├── BrowserMonitor.kt          — uses BrowserAdapter adapters
│   ├── AppMonitor.kt              — uses AppSignature matching
│   ├── SettingsMonitor.kt         — extracted from current AccessibilitySentry
│   ├── SearchMonitor.kt           — uses SearchEngineAdapter
│   ├── InstallerMonitor.kt        — uses InstallerAdapter
│   ├── HealthMonitor.kt           — computes ProtectionScore
│   └── IncognitoDetector.kt       — best-effort, never relied upon
├── model/
│   ├── AppSignature.kt            — data class + AppCategory enum
│   ├── GuardianContext.kt         — context value object
│   ├── WindowHierarchy.kt         — multi-window awareness
│   └── ProtectionScore.kt         — score data + ProtectionWeights
├── util/
│   ├── DomainNormalizer.kt        — normalize domain without hashing
│   └── TraversalLimits.kt         — constants for tree walk safety
└── service/
    └── AccessibilitySentry.kt     — rewritten (thin router, ~50 lines)

core/
├── ProtectionOrchestrator.kt      — thinned (init/lifecycle/wiring/sync)
├── ProtectionPolicyEngine.kt      — event + context → Decision
├── CapabilityRegistry.kt          — CapabilityResult-based queries
├── MetricsRepository.kt           — telemetry storage (separate from config)
├── AuditTrail.kt                  — local append-only event log
├── RuleProvider.kt                — generic interface
├── DomainRuleProvider.kt          — set-backed domain matching
├── AppRuleProvider.kt             — AppSignature matching
└── CapabilitySnapshot.kt          — immutable point-in-time capabilities

overlay/
└── OverlayController.kt           — includes OverlayQueue + OverlayStrategy
```

### 3.2 Modified Files

| File | Change |
|---|---|
| `walls/wall2/AccessibilitySentry.kt` | Strip to ~50 lines: receive event → forward to router |
| `core/ProtectionOrchestrator.kt` | Thin to init/lifecycle/wiring/state sync only |
| `core/ConfigurationManager.kt` | Add `ProtectionWeights`, remove metric keys |
| `core/SecurityManager.kt` | Add `SentryState` delegation |
| `config/BlocklistConfig.kt` | Add `AppSignature` entries, search engine packages |
| `ui/overlay/BlockOverlayManager.kt` | Refactor under `OverlayController` |

### 3.3 Unchanged Files

| File | Reason |
|---|---|
| `core/AccessibilityWatchdog.kt` | Already separate, works well |
| `workers/AccessibilityRecoveryWorker.kt` | Already works |
| `core/GuardianCoreService.kt` | Minimal change — only subscribe to `SentryState` |
| `core/ServiceResurrector.kt` | Unchanged |
| `res/xml/accessibility_service_config.xml` | Already correct |
| `ui/screens/DashboardScreen.kt` | Add Wall 2 status display (minimal) |
| `ui/screens/OnboardingScreen.kt` | Already works |
| `walls/wall1/*` | Unrelated to this plan |

---

## 4. Implementation Phases

### Phase 1 — Foundation (Priority: Critical)

**Goal:** Establish the event-driven core and thin `AccessibilitySentry`.

**Files to create:**
- `event/ProtectionEvent.kt`, `event/Decision.kt`
- `model/GuardianContext.kt`, `model/ProtectionScore.kt`, `model/AppSignature.kt`, `model/WindowHierarchy.kt`
- `util/TraversalLimits.kt`, `util/DomainNormalizer.kt`
- `core/ProtectionPolicyEngine.kt`
- `core/RuleProvider.kt`, `core/DomainRuleProvider.kt`, `core/AppRuleProvider.kt`
- `core/CapabilityRegistry.kt`, `core/CapabilitySnapshot.kt`
- `core/MetricsRepository.kt`, `core/AuditTrail.kt`
- `router/AccessibilityEventRouter.kt`
- `event/EventDebouncer.kt`

**Files to modify:**
- `service/AccessibilitySentry.kt` — strip to ~50 lines
- `core/ProtectionOrchestrator.kt` — thin down
- `core/ConfigurationManager.kt` — add `ProtectionWeights`, remove metrics
- `core/SecurityManager.kt` — add `SentryState` delegation

**Verification:** Project builds, `AccessibilitySentry` compiles as thin router, `ProtectionPolicyEngine` returns correct `Decision` for known event types.

---

### Phase 2 — Monitors (Priority: Must Have)

**Goal:** Implement all monitors emitting typed `ProtectionEvent`s.

**Files to create:**
- `monitor/BrowserMonitor.kt`
- `monitor/AppMonitor.kt`
- `monitor/SettingsMonitor.kt`
- `monitor/SearchMonitor.kt`
- `monitor/InstallerMonitor.kt`
- `monitor/HealthMonitor.kt`
- `monitor/IncognitoDetector.kt`

**Files to modify:**
- `config/BlocklistConfig.kt` — add `AppSignature` entries, search engine packages

**Verification:** Each monitor correctly emits the right `ProtectionEvent` subtype when fed mock `AccessibilityEvent`s.

---

### Phase 3 — Adapters (Priority: Must Have)

**Goal:** Implement browser, search engine, and installer adapters.

**Files to create:**
- `adapter/BrowserAdapter.kt`, `adapter/BrowserProfile.kt`
- `adapter/ChromeProfile.kt`, `adapter/FirefoxProfile.kt`
- `adapter/BraveProfile.kt`, `adapter/SamsungProfile.kt`
- `adapter/GenericProfile.kt`
- `adapter/SearchEngineAdapter.kt`, `adapter/GoogleSearchAdapter.kt`
- `adapter/InstallerAdapter.kt`, `adapter/PlayStoreAdapter.kt`
- `adapter/PackageInstallerAdapter.kt`

**Verification:** Each adapter returns correct URL/package data for known test cases.

---

### Phase 4 — Overlay (Priority: Must Have)

**Goal:** Build `OverlayController` with queue and refactor existing `BlockOverlayManager`.

**Files to create:**
- `overlay/OverlayController.kt`

**Files to modify:**
- `ui/overlay/BlockOverlayManager.kt` — refactor under `OverlayController`

**Verification:** Multiple simultaneous block events are queued and displayed in priority order. No overlay fights.

---

### Phase 5 — Integration (Priority: Must Have)

**Goal:** Wire all components together end-to-end.

**Tasks:**
1. Connect `AccessibilityEventRouter` → monitors → `ProtectionOrchestrator`
2. Wire `ProtectionPolicyEngine` with `GuardianContext` from `CapabilityRegistry` + `ConfigurationManager`
3. Connect `Decision` → `OverlayController`
4. Add `MetricsRepository` recording points to all monitors
5. Add `AuditTrail` append points

**Verification:** Full end-to-end test: `AccessibilityEvent` → `Decision` → overlay shown. Metrics recorded. Audit trail populated.

---

### Phase 6 — Tests (Priority: Should Have)

| Test File | Type | What It Verifies |
|---|---|---|
| `BrowserAdapterTest` | Unit | All adapters return expected URL bar IDs |
| `UrlExtractorTest` | Unit | Mock `AccessibilityNodeInfo` trees → correct URL extraction |
| `EventDebouncerTest` | Unit | Per-category timers fire independently |
| `AppMonitorTest` | Unit | `AppSignature` matching with aliases |
| `ProtectionPolicyEngineTest` | Unit | Event + context → correct Decision mapping |
| `DomainNormalizerTest` | Unit | Domain cleaning (www, scheme, TLD) |
| `SentryStateMachineTest` | Unit | All state transitions legal |
| `SettingsMonitorTest` | Unit | Settings text detection |
| `CapabilityRegistryTest` | Unit | Returns correct `CapabilityResult` per Android version |
| `AppSwitchToSettingsTest` | Integration | End-to-end settings blocking in < 100ms |
| `OverlayControllerTest` | Unit | Queue priority, strategy, show/hide lifecycle |
| `AuditTrailTest` | Unit | Append, recent, clear, count |

---

## 5. Architecture Decisions

### ADR-001: Event-Driven Over Monitor-Centric

**Decision:** Every monitor is a sensor that emits `ProtectionEvent`s. Monitors never decide what action to take.
**Rationale:** Separating observation from decision-making allows the `ProtectionPolicyEngine` to consider context (paused state, emergency override, severity) before acting. Monitors remain simple and testable.

### ADR-002: Typed Metadata Over Generic Maps

**Decision:** Each `ProtectionEvent` subtype carries a typed `EventMetadata` object rather than a `Map<String, String>`.
**Rationale:** Compile-time safety. No magic string keys. Each metadata type documents exactly what fields are available for that event category.

### ADR-003: Adapter Pattern for Browsers, Search Engines, Installers

**Decision:** External system integration uses the Adapter pattern with per-system profiles.
**Rationale:** Browser UI changes, search engine layout changes, and installer behavior changes are isolated to single profile updates. The `ExtractionStrategy` fallback chain (Primary → Fallback → Heuristic → Failure) ensures graceful degradation.

### ADR-004: RuleProvider Interface Over Concrete Implementation

**Decision:** Rule matching goes through `RuleProvider<T>` interface. The `ProtectionPolicyEngine` never knows about data structures.
**Rationale:** Swappable implementations (HashSet, Trie, BloomFilter, hybrid) without changing the engine. Testable with mock providers.

### ADR-005: MetricsRepository Separate From ConfigurationManager

**Decision:** Telemetry and configuration are stored in separate components.
**Rationale:** Configuration is user intent. Metrics are operational data. Mixing them creates coupling and makes it harder to reason about either in isolation.

### ADR-006: Accessibility is a Sensor, Not the Architecture

**Decision:** The core platform (`ProtectionOrchestrator` → `ProtectionPolicyEngine` → `Decision` → `OverlayController`) knows nothing about Android `AccessibilityService`.
**Rationale:** Future sensors (Device Owner, System Integrity, AI) can be added without modifying the core. The platform processes `ProtectionEvent`s, not Android API objects.

### ADR-007: CapabilityResult Over Boolean

**Decision:** `CapabilityRegistry` returns `CapabilityResult` (Supported / Unsupported / Partial) rather than Boolean.
**Rationale:** Android capabilities are rarely binary. "Partial" with a reason string enables meaningful diagnostics and graceful degradation.

### ADR-008: Explicit Traversal Limits

**Decision:** Accessibility node tree traversal enforces hard limits: depth ≤ 25, nodes ≤ 500, time ≤ 50ms.
**Rationale:** Android UI trees can be arbitrarily large. Unbounded traversal causes ANRs and battery drain. These limits prevent CPU spikes while covering the vast majority of real-world layouts.

### ADR-009: Audit Trail Is Local Only

**Decision:** The `AuditTrail` is an append-only local log. It is never transmitted.
**Rationale:** Privacy requirement. Users must be able to inspect Guardian's behavior locally without data leaving the device.

---

## 6. Risk Register

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Chrome updates break URL bar ID mapping | High: URL extraction fails silently | High | Adapter pattern isolates breakage. GenericProfile fallback. `BrowserUnsupportedEvent` emitted instead of crash. `CompatibilityLevel.Broken` marks affected versions. |
| OEM kills Accessibility process | High: protection stops | Medium | Multi-path resurrection (AlarmManager + JobService + WorkManager + self-restart). Dashboard shows red dot when dead. Recovery notification guides re-enable. |
| Android version changes Accessibility API | Medium: some features stop working | Medium | `CapabilityRegistry` checks API level before attempting operations. `CapabilityResult.Partial` reports exactly what is broken. |
| Incognito detection fails due to localization | Low: feature quietly skips detection | High | `IncognitoDetector` is `Observe`-only. Never triggers a block. Failure is silent and acceptable. |
| Split-screen produces interleaved events | Medium: wrong app gets blocked | Low | `WindowHierarchy` tracks window focus indexes. Events are routed to the correct window's monitor. |
| Overlay permission revoked | Medium: block screen not shown | Medium | `OverlayController` checks `CapabilityRegistry.canDrawOverlay()` before attempting. Emits `Recover(Permission)` decision. |
| Memory leak from stale AccessibilityNodeInfo | Medium: gradual memory growth | Low | All `recycle()` calls are in `finally` blocks. `TraversalLimits` prevent unbounded scans. `MetricsRepository` tracks memory usage. |
| UI tree exceeds traversal limits on complex pages | Low: rare missed detection | Medium | Limits (25 depth / 500 nodes / 50ms) cover >99% of real-world layouts. Missed detections are logged but not actionable. |

---

## 7. Testing Strategy

### Unit Tests (Fast, no Android dependency)

- Each `BrowserAdapter` profile returns correct resource IDs
- `DomainNormalizer` handles all URL formats (with/without scheme, www, m., subdomains)
- `ProtectionPolicyEngine` maps each event type to correct `Decision` given context
- `EventDebouncer` correctly rate-limits per category
- `SentryStateMachine` rejects illegal transitions
- `AppMonitor` matches `AppSignature` with aliases
- `AuditTrail` append/recent/clear/count

### Integration Tests (Mock Android APIs)

- `AccessibilityEventRouter` receives mock event → routes to correct monitor → emits correct `ProtectionEvent`
- `CapabilityRegistry` returns correct results for API level 29–36
- `OverlayController` with multiple simultaneous events → correct queue behavior
- `SettingsMonitor` detects dangerous settings screens and emits `SettingsEvent`

### Stress Tests (Manual, on device)

- Scroll a 1000-item Reddit feed in Chrome. Verify CPU < 5%.
- Switch between Chrome, settings, and home screen rapidly 50 times.
- Enter split-screen mode with Chrome and Settings side by side.
- Force-stop the app process. Verify recovery notification fires within 35 seconds.
- Open 5 blocked domains in rapid succession. Verify overlay queue handles correctly.

---

## Appendix: Current Code Structure Reference

For AI coding agents: the existing codebase follows these patterns:

- **DI:** Hilt `@Singleton` + `@Inject constructor` + `@AndroidEntryPoint` + `@ApplicationContext`
- **State:** `MutableStateFlow<T>` / `StateFlow<T>` for reactive observation
- **Communication:** Listener interfaces (e.g., `ConnectionMonitor.Listener`) + Intent-based for cross-component
- **Lifecycle:** `LifecycleService` with `START_STICKY`, foreground notification, `onDestroy` resurrection
- **Threading:** `HandlerThread` with dedicated priority, `mainHandler` for UI
- **Persistence:** `EncryptedStateStore` (EncryptedSharedPreferences) via `ConfigurationManager`
- **Time:** `TimeProvider` interface with `RealTimeProvider` (testable)
- **UI:** Programmatic (`LinearLayout` + `TextView`) in overlay, Compose in screens
- **Resurrection:** Multi-path: `AlarmManager` + `JobService` + `WorkManager` + self-start

### Key Existing Files to Reference

| File | Pattern to Follow |
|---|---|
| `walls/wall1/VpnController.kt` | Interface with `StateFlow`, `start`/`stop`/`restart`/`status`/`updateStatus` |
| `core/CooldownTimerManager.kt` | `TimeProvider` abstraction, dual time tracking |
| `core/ConnectionMonitor.kt` | Listener interface pattern |
| `ui/overlay/BlockOverlayManager.kt` | `HandlerThread`, programmatic window, synchronized rendering |
| `core/GuardianCoreService.kt` | Foreground service, `START_STICKY`, multi-path resurrection |
| `core/ServiceResurrector.kt` | Multiple resurrection paths with fallback |
| `config/BlocklistConfig.kt` | Object with typed sets for configuration |
