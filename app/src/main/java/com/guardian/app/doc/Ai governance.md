# Guardian: AI Engineering Governance Protocol
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## 1. Why This Document Exists

AI coding assistants — whether Cursor, Claude, local Ollama models, or any other — are powerful but context-blind by default. They do not know that Guardian must be unkillable. They do not know we use zero paid APIs. They do not know that a memory leak in an AccessibilityService is a direct path to a user relapse.

Without governance, an AI assistant will confidently generate:
- Generic boilerplate that ignores Android's Low Memory Killer behavior
- Library suggestions that violate our zero-telemetry contract
- Service code that restarts "eventually" instead of "within 3 seconds"
- Firebase integrations that log more than we permit

This document defines the rules that make AI assistance an accelerator rather than a liability. Every AI-assisted coding session — in Cursor, via CLI, or through any other interface — must operate within this protocol.

---

## 2. The Source of Truth Hierarchy

Before generating any code, the AI must be oriented to the correct context. The hierarchy of truth is:

```
Level 1 (Architecture)    →  architecture_design.md
Level 2 (Strategy)        →  strategy_roadmap.md
Level 3 (Requirements)    →  functional-nonfunctional.md
Level 4 (Standards)       →  opencode.md (this file's sibling)
Level 5 (Governance)      →  ai_governance.md (this document)
```

**The AI must not make assumptions when requirements are unclear. It must ask.**

A five-second clarification question costs nothing. An assumption that introduces a bug in a background service can take hours to debug and represents a real failure risk for real users.

---

## 3. Pre-Generation Verification Checklist

Before the AI generates any code for Guardian, it must explicitly confirm:

```
[ ] I have reviewed architecture_design.md and understand the 4-Wall structure.
[ ] I understand the Unkillable Service requirement: START_STICKY, onDestroy broadcast, BOOT_COMPLETED receiver.
[ ] I will not suggest any library outside the approved stack in opencode.md.
[ ] I will not generate code that stores any URL, domain string, or ML capture to disk or cloud.
[ ] I will not suggest any paid API in the core blocking path.
[ ] I understand the 5% battery budget and will implement state-aware execution.
[ ] The code I generate is complete — no placeholders, no "// TODO: implement" in critical paths.
```

If an AI assistant cannot confirm these points (because context was not provided), the session must stop and context must be injected before proceeding.

---

## 4. Context Injection Templates

Use these templates when starting a new AI coding session in Cursor or via CLI.

### 4.1 General Session Opener

```
You are a senior Android engineer working on Guardian — a system-level content-blocking app.

Project constraints:
- Language: Kotlin (core services) + React Native (UI)
- Zero paid APIs in the core blocking path
- All services must be persistent: START_STICKY, onDestroy broadcast self-resurrect, BOOT_COMPLETED recovery
- Battery budget: all background services combined ≤ 5% per 24 hours
- Memory rule: no static context references, all bitmaps explicitly recycled, coroutine scopes tied to lifecycle
- Privacy: no URL strings, no ML captures, no user content is ever written to disk or cloud
- Approved libraries: Android Jetpack suite only (Room, WorkManager, EncryptedSharedPreferences, Hilt). OkHttp for networking. TFLite for ML. Firebase for accountability features only.

Architecture reference: [paste relevant section from architecture_design.md]
Current task: [describe the specific feature or bug]
```

### 4.2 Wall-Specific Context Templates

**For Wall 1 (DNS/VPN):**
```
We are implementing Wall 1 of Guardian: the DNS/VPN Engine.
Tech: Android VpnService API. Local TUN interface only — no remote server.
Blocklist: SQLite table of SHA-256 hashed domains, indexed. 100,000+ entries.
Blocked domains resolve to 0.0.0.0.
Lookup must complete in < 30ms (p95).
No DNS query content is ever logged — only a block count increment.
onRevoke() must detect VPN revocation and notify GuardianCoreService.
```

**For Wall 2 (Accessibility):**
```
We are implementing Wall 2 of Guardian: the Accessibility Sentry.
Tech: Android AccessibilityService.
Must monitor URL bars in: Chrome, Firefox, Samsung Browser, Opera, Brave.
Must block navigation to Accessibility Settings, Device Admin Settings, and App Info for Guardian.
Block response: immediately execute GLOBAL_ACTION_HOME and broadcast ACTION_SHOW_BLOCK_OVERLAY.
Interception must complete in < 200ms from AccessibilityEvent receipt.
Event processing must run off the accessibility thread to avoid ANR.
```

**For Wall 3 (ML Vision):**
```
We are implementing Wall 3 of Guardian: the Vision Sentry.
Tech: MediaProjection + TensorFlow Lite (MobileNetV2, quantized).
Capture interval: 7–10 seconds. Only when a browser is in the foreground.
Capture resolution: 540x960, downscaled to 224x224 for MobileNetV2 input.
Confidence threshold: 0.85 for adult_content class.
Memory contract: bitmap MUST be recycled in a finally block. No exceptions.
No captured image is written to disk or transmitted over the network.
```

**For Wall 4 (Device Admin):**
```
We are implementing Wall 4 of Guardian: the Policy Controller.
Tech: DevicePolicyManager + DeviceAdminReceiver.
Must prevent uninstallation and Force Stop without master password.
Must monitor AccessibilityService status every 60 seconds.
onDisableRequested must prompt for master password before allowing admin deactivation.
```

---

## 5. Code Review Rules for AI-Generated Output

All code from an AI assistant undergoes a Logic Stress Test before being accepted. The reviewer (Nanda) checks:

### 5.1 The Persistence Check
Does the service return `START_STICKY`? Does `onDestroy` send the restart broadcast? Is `BOOT_COMPLETED` handled? If any answer is no — reject and regenerate with explicit constraint.

### 5.2 The Memory Check
Are there any static Context references? Are Bitmaps recycled in finally blocks? Are coroutine scopes canceled in `onDestroy`? Any violation is a reject.

### 5.3 The Privacy Check
Does any log statement, database insert, or network call include URL strings, domain names, search terms, or ML capture data? Any violation is a hard reject — this is a user trust issue, not a code quality issue.

### 5.4 The Library Check
Does the generated code import any library not in the approved stack? Any unapproved import requires explicit justification and architecture document update before it can be accepted.

### 5.5 The Completeness Check
Are there any `// TODO`, `// implement this`, or placeholder comments in critical logic paths? Incomplete code is not accepted. AI must generate the complete implementation or clearly state it cannot.

---

## 6. AI Tool Usage Guidelines

### 6.1 Cursor (Primary IDE)

Cursor has access to the full project codebase. Use `@` references to inject specific documents:

```
@architecture_design.md @opencode.md

We are implementing the reboot recovery mechanism for GuardianCoreService.
Requirements:
- BroadcastReceiver registered for BOOT_COMPLETED and QUICKBOOT_POWERON
- Service must be fully active within 5 seconds of boot
- Must verify all active Walls and restart any that are inactive

Generate the complete ServiceRestartReceiver.kt and the relevant AndroidManifest.xml entries.
```

**Cursor Rules (`.cursorrules` file):** Maintain a `.cursorrules` file in the project root with Guardian's core constraints. This ensures every AI interaction in Cursor is preloaded with the correct context.

### 6.2 Local LLM (Ollama — Zero-Rupee Sessions)

For sensitive logic (Shared Key system, bypass detection, Device Admin), use local models via Ollama to avoid sending proprietary logic to public cloud APIs.

Recommended models:
- `deepseek-coder-v2` — Best for Kotlin service code
- `qwen2.5-coder` — Best for React Native UI code
- `llama3` — Best for architecture and logic discussions

Local session prompt format:
```
[SYSTEM]: You are a senior Android engineer. Follow these constraints absolutely:
1. Kotlin only for service code. No Java.
2. All services must be persistent (START_STICKY, onDestroy broadcast).
3. Zero paid APIs. Zero third-party analytics.
4. No static context references. No bitmap leaks.

[USER]: [Your specific task here]
```

### 6.3 What AI Must Never Do

| Action | Reason |
|---|---|
| Suggest Firebase Analytics for core feature tracking | Violates zero-telemetry contract |
| Use `GlobalScope` for coroutines in services | Lifecycle-unsafe; causes leaks |
| Store any URL or domain string in any log or DB | Violates privacy contract |
| Return `START_NOT_STICKY` from any service | Breaks persistence requirement |
| Use reflection on service classes | Breaks with ProGuard on release builds |
| Suggest a paid API as "temporary" | There is no temporary in Guardian's architecture |
| Generate placeholder logic for security-critical paths | Password handling, bypass detection, cooloff logic must be complete |

---

## 7. Failure Protocol

When AI-generated code causes a crash, ANR, or service failure:

### Step 1 — Immediate Rollback
Revert to the last Git commit tagged `[clean]`. Do not attempt to patch broken AI code without understanding the root cause first.

### Step 2 — Root Cause Analysis (RCA)
Document the failure in a brief RCA entry:

```
Date: [date]
Module: [class name]
Failure: [what happened — crash, ANR, memory leak, etc.]
Root Cause: [what the AI generated incorrectly]
Why AI Got It Wrong: [missing context? incorrect assumption? unknown Android behavior?]
Fix Applied: [what was changed]
```

### Step 3 — Protocol Update
If the failure reveals a pattern (e.g., "AI always forgets to handle `onRevoke()` in VpnService"), add a specific rule to this document and to the `.cursorrules` file to prevent recurrence.

The goal is a self-improving governance system. Every failure makes the next session safer.

---

## 8. Security & Privacy Guardrails

### Sensitive Logic — Local Models Only

The following modules must be developed using local Ollama models, never public cloud AI APIs:

- Master password generation and hashing logic
- Shared Key handoff flow
- 24-hour cooloff engine
- Bypass detection and anti-tamper logic
- Any code that handles `EncryptedSharedPreferences` keys

Sending this logic to a public LLM risks leaking our security architecture to a third party's training pipeline.

### Permission Additions

Any new `<uses-permission>` entry in `AndroidManifest.xml` generated by an AI must be:
1. Accompanied by a justification comment (see `opencode.md`)
2. Cross-referenced against the requirements documents to confirm it is needed
3. Reviewed manually before commit — no AI-suggested permission is committed without human review

---

## 9. The Standard We Hold

This protocol exists because the users who install Guardian are trusting it with something deeply personal. They are trusting that when they are weak at 1 AM, this app will hold the line for them.

That trust is not compatible with rushed code, untested assumptions, or memory leaks that cause the app to die when they need it most.

AI is a tool. A powerful one. But the judgment about whether the tool's output is trustworthy belongs to the engineer. This document is how that judgment is exercised systematically, not just instinctively.