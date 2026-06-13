# Guardian: Strategic Development Roadmap
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## 1. Executive Vision

Guardian is not a content blocker. It is a **Commitment Device** — a psychological and technical contract between a user's rational future self and their impulsive present self.

The core insight driving Guardian is this: willpower is a depletable resource. Guardian replaces willpower with architecture. It builds an environment where the *right* choice is the only accessible choice, drawing from behavioral economics (loss aversion, social accountability, friction design) and Android system-level engineering.

**The Mission:** Give individuals who struggle with compulsive digital behavior a tool that is genuinely harder to circumvent than their worst impulse — and pair that technical lock with a recovery system that builds identity, not shame.

**Target Outcome for the User:** A 30-day streak of clean device usage, supported by four layers of technical defense and one layer of human accountability.

---

## 2. Strategic Pillars

| Pillar | Description |
|---|---|
| **Unkillable Core** | The app must survive Force Stop, reboot, ADB, and Safe Mode attempts |
| **Zero-Rupee Build** | 100% open-source stack. No paid APIs in the critical path |
| **Privacy by Design** | All ML inference and screenshot analysis happens on-device, never in the cloud |
| **Behavioral Science First** | Every UI decision is justified by a psychological principle, not aesthetics |
| **Accountability Loop** | A trusted human (not an algorithm) is always in the accountability chain |

---

## 3. Phased Development Plan

### Phase 1 — The Core Shield *(Months 1–2)*
**Objective:** Ship a working, unkillable foundation. Nothing fancy. Just walls that hold.

**Deliverables:**

- `GuardianCoreService.kt` — Persistent ForegroundService with `START_STICKY` and `BOOT_COMPLETED` BroadcastReceiver
- Wall 2: `AccessibilitySentryService.kt` — URL detection in Chrome, Firefox, Samsung Browser; self-protection against Settings navigation
- Wall 4: `DeviceAdminReceiver.kt` — Prevents uninstallation without master password
- Onboarding Flow — Sequential permission granting (Accessibility → Device Admin → Notification)
- Block Screen v1 — Full-screen overlay with 20-second hard timer, streak display, and motivational copy
- `EncryptedSharedPreferences` setup — Stores master key and setup state

**Phase 1 Exit Criteria:**
- [ ] App survives Force Stop and restarts within 3 seconds
- [ ] App restarts within 5 seconds of device reboot
- [ ] Block screen cannot be dismissed via Back or Home during timer
- [ ] User cannot access Accessibility Settings while Guardian is active

---

### Phase 2 — Network & Visual Intelligence *(Months 3–4)*
**Objective:** Close the gaps that Wall 2 misses. Catch incognito tabs, direct IP access, and visual content.

**Deliverables:**

- Wall 1: `DnsVpnService.kt` — Local VPN loopback using `VpnService` API; SQLite-backed domain blocklist with 100,000+ hashed entries; DNS sinkholing to `0.0.0.0`
- Wall 3: `VisionSentryService.kt` — `MediaProjection` capture at 7–10 second intervals; TensorFlow Lite inference using quantized MobileNetV2; broadcast to block overlay on detection
- State-Aware ML — ML sentry activates only when a browser or flagged app is in the foreground (battery optimization)
- Block Screen v2 — Dynamic messaging based on time of day; danger zone detection (10 PM – 2 AM)
- Blocklist Update Worker — `WorkManager` task for weekly OTA blocklist updates via OkHttp

**Phase 2 Exit Criteria:**
- [ ] DNS blocklist lookup resolves in < 30ms
- [ ] ML inference completes in < 500ms
- [ ] No ML screenshot is written to disk or gallery
- [ ] Battery consumption stays under 5% per 24-hour cycle

---

### Phase 3 — Accountability & Identity *(Months 5–6)*
**Objective:** Transform Guardian from a blocker into a recovery system. Introduce the human layer.

**Deliverables:**

- Shared Key System — One-time password generation, WhatsApp/SMS sharing via Intent, deletion from user view post-share
- 24-Hour Cool-Off Engine — `WorkManager` task persisted in Room DB; immune to manual clock manipulation via network time cross-check
- Streak Engine — Daily reset logic, loss-aversion messaging, relapse detection (3 blocks in 10 minutes = streak reset)
- Daily Missions — Curated recovery action list (journaling, breathing, movement); randomized daily selection; completion tracking
- Emergency Mode — 4-7-8 breathing animation; 10-minute movement timer; one-tap call/text to Trusted Person
- Cloud Accountability — Firebase Anonymous Auth; Firestore weekly summary push; FCM alert on 3-block burst event
- Trusted Person Dashboard — Simple read-only web view of the user's weekly stats

**Phase 3 Exit Criteria:**
- [ ] Password sharing flow completes and password is deleted from user view
- [ ] 24-hour countdown persists through reboot
- [ ] Streak resets correctly on relapse event
- [ ] Trusted Person receives FCM notification within 60 seconds of a burst event

---

### Phase 4 — Hardening & Scale *(Months 7+)*
**Objective:** Close every remaining bypass vector and begin ecosystem expansion.

**Deliverables:**

- ADB Detection — Block USB debugging connections while Hard Lock is active
- Safe Mode Defense — Detect Safe Mode boot and restrict functionality; alert Trusted Person
- Developer Options Block — Detect and intercept navigation to Developer Options settings page
- Chrome Extension — Guardian for Browser with cross-device streak sync
- Community Layer — Anonymous peer accountability network ("Emergency Call" feature)
- Guardian Pro Backend — Subscription management, Trusted Person portal, cross-device sync

**Phase 4 Exit Criteria:**
- [ ] ADB cannot disable any Guardian service while Hard Lock is active
- [ ] Zero successful uninstalls without master password across 10 test devices
- [ ] Chrome Extension correctly syncs streak with Android app

---

## 4. Technology Decisions (Rationale)

| Decision | Choice | Rationale |
|---|---|---|
| **Core Language** | Kotlin | Native Android API access required for VpnService, AccessibilityService, DevicePolicyManager. Flutter cannot interface with these at the depth Guardian requires. |
| **UI Layer** | React Native | Faster UI iteration without sacrificing native service layer. Business logic stays in Kotlin. |
| **ML Runtime** | TensorFlow Lite | On-device inference, no network dependency, quantized models fit within memory budget |
| **Local DB** | Room + SQLite | Type-safe, Jetpack-native, no external dependency |
| **Cloud** | Firebase Free Tier | Only used for accountability features, never for core blocking logic |
| **Background Tasks** | WorkManager | Handles doze mode, battery optimization, and persistence across reboots |

---

## 5. Key Performance Indicators (KPIs)

| Metric | Target | Measurement |
|---|---|---|
| 30-Day Streak Retention | > 40% of users who reach Day 7 reach Day 30 | Firebase event tracking |
| Bypass Failure Rate | 100% (zero successful uninstalls without password) | Uninstall attempt logs |
| Wall 2 Interception Speed | < 200ms | Instrumented AccessibilityService logs |
| Wall 3 Inference Latency | < 500ms | TFLite benchmark on mid-range device |
| Battery Consumption | < 5% per 24 hours | Android Battery Historian |
| Reboot Recovery Time | < 5 seconds | Automated reboot test suite |

---

## 6. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Android OS update breaks AccessibilityService behavior | High | Critical | Monitor Android release notes; maintain compatibility test matrix |
| OEM battery optimization kills ForegroundService (Samsung, Xiaomi) | High | Critical | Device-specific whitelisting guide; test on 5+ OEM devices |
| TFLite model false positive rate too high | Medium | High | Tune confidence threshold per device; allow user-reported false positives |
| Google Play policy rejection (DeviceAdmin + VPN) | Medium | Critical | Pre-submission policy review; frame as parental control / self-discipline tool |
| Firebase free tier limits exceeded at scale | Low | Medium | Migrate accountability cloud to self-hosted Supabase at 10K users |