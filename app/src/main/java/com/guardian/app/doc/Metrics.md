# Guardian: Metrics & Analytics Framework
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## 1. Philosophy: Measure What Matters

Most apps measure engagement — time spent, sessions per day, DAU/MAU. Guardian's success is the **opposite** of engagement. A user who opens Guardian zero times in a day has had a perfect day.

This inverts the standard analytics playbook entirely. Our metrics must reflect a single truth: **is the user's life getting better?** Every number we track must answer that question — directly or indirectly.

We measure three categories:
1. **Technical Performance** — Is the product working as engineered?
2. **Behavioral Signal** — Is it working as a recovery tool?
3. **Business Health** — Is it sustainable as a company?

---

## 2. The North Star Metric

**30-Day Clean Streak Rate**

Definition: The percentage of users who, having reached Day 7 without a relapse event, go on to reach and maintain Day 30.

Target: ≥ 40%

Why this metric: Day 7 is the first significant commitment threshold. Users who make it to Day 7 have demonstrated genuine intent. The conversion from Day 7 → Day 30 is a direct measure of Guardian's ability to sustain identity change — not just provide a technical blocker.

A competitor's North Star might be "blocks per day." Ours is streaks per user. The difference reveals the product philosophy.

---

## 3. Technical Performance Metrics

### Wall 1: DNS/VPN Engine

| Metric | Definition | Target | Measurement Method |
|---|---|---|---|
| DNS Lookup Latency | Time from DNS query intercept to resolution response | p95 < 30ms | Instrumented SQLite query timer |
| Blocklist Coverage Rate | % of known adult domains in our blocklist vs. a reference list | > 95% | Monthly audit against Tranco/SFW reference list |
| False Positive Rate | % of clean domain queries that are incorrectly blocked | < 0.1% | Automated test suite with 10,000 clean domains |
| VPN Uptime | % of time the VPN interface is active during device-on hours | > 99.5% | Heartbeat log in Room DB |
| Blocklist Staleness | Days since last blocklist update | < 7 days | WorkManager task execution log |

### Wall 2: Accessibility Sentry

| Metric | Definition | Target | Measurement Method |
|---|---|---|---|
| Interception Latency | Time from AccessibilityEvent receipt to Block Overlay visible | p95 < 200ms | Instrumented timestamp delta |
| Settings Block Success Rate | % of attempts to reach Accessibility/Admin settings that are intercepted | 100% | QA test suite across 5 OEM devices |
| Browser Coverage | Number of monitored browser packages | ≥ 5 at launch | Package list in `AccessibilitySentryService.kt` |
| Event Processing Overhead | CPU % consumed by Accessibility event loop | < 1% sustained | Android Profiler — CPU tab |

### Wall 3: ML Vision Sentry

| Metric | Definition | Target | Measurement Method |
|---|---|---|---|
| Inference Latency | Time from screen capture to classification result | p95 < 500ms | TFLite benchmark on baseline device |
| Recall (Sensitivity) | % of actual adult content frames correctly flagged | > 90% | Test set of 100 labeled adult images |
| Precision | % of flagged frames that are actually adult content | > 80% | Test set of 100 labeled clean images |
| False Positive Rate | % of clean frames incorrectly blocked | < 5% | Test set of 200 clean images |
| Memory Footprint per Cycle | Peak RAM used during one capture+inference cycle | < 30MB | Android Profiler — Memory tab |
| State-Aware Idle Rate | % of time ML is correctly idle when browser is not foreground | > 99% | Foreground package monitor log |

**Note on Recall vs. Precision trade-off:** In Guardian's context, a false negative (missing adult content) is worse than a false positive (blocking clean content). We prioritize Recall. A user who sees a brief false block understands; a user who successfully views content due to a miss loses trust in the product permanently.

### Wall 4: Policy Controller

| Metric | Definition | Target | Measurement Method |
|---|---|---|---|
| Uninstall Block Rate | % of uninstall attempts blocked without password | 100% | QA test — attempt uninstall via Play Store, App Info, ADB |
| Force Stop Block Rate | % of Force Stop attempts blocked | 100% | QA test on 5 OEM devices |
| Admin Revocation Detection Time | Time from external DeviceAdmin revocation to Guardian alert | < 60 seconds | Instrumented test |

### System-Wide Persistence

| Metric | Definition | Target | Measurement Method |
|---|---|---|---|
| Reboot Recovery Time | Time from `BOOT_COMPLETED` to all Walls active | < 5 seconds | Automated reboot test with timestamp logging |
| Kill Recovery Time | Time from `am kill` to service restart | < 3 seconds | Instrumented test |
| Service Uptime (24h) | % of device-on time that GuardianCoreService is running | > 99.9% | Heartbeat log analysis |

---

## 4. Behavioral & Recovery Metrics

These metrics are the product's soul. Technical metrics tell us the walls are standing. Behavioral metrics tell us if the user is actually getting better.

### 4.1 Streak Analytics

| Metric | Definition | Target | Why It Matters |
|---|---|---|---|
| Day 1 Retention | % of users who are still active on Day 1 after install | > 70% | Measures onboarding quality |
| Day 7 Streak Rate | % of users who reach a 7-day clean streak | > 30% | First real commitment threshold |
| Day 30 Streak Rate | % of users who reach 30-day clean streak | > 15% | Primary success indicator |
| Streak Recovery Rate | % of users who start a new streak within 24h of a relapse | > 60% | Measures resilience, not perfection |
| Average Streak Length | Mean streak duration across all users | > 10 days | Overall product effectiveness |

### 4.2 Block & Relapse Patterns

| Metric | Definition | Insight |
|---|---|---|
| Peak Block Hour | Hour of day with highest block event frequency | Identifies "Danger Zones" for personalized notifications |
| Block-to-Relapse Ratio | % of block events that precede a streak reset within 10 minutes | Higher = blocks are not working; lower = friction is effective |
| Friction Success Rate | % of Block Screen sessions where user does NOT trigger another block within 30 minutes | Core measure of whether the 20-second timer is doing its job |
| Wall Source Distribution | % of blocks from Wall 1 vs. Wall 2 vs. Wall 3 | Reveals which content pathways users are most exposed to |

### 4.3 Emergency Mode Utilization

| Metric | Definition | Target | Insight |
|---|---|---|---|
| Emergency Mode Activation Rate | % of relapse events where user opens Emergency Mode | > 20% | Higher = user is choosing tools over impulse |
| Breathing Exercise Completion | % of Emergency Mode sessions where user completes full 4-7-8 cycle | > 50% | Engagement with recovery tool |
| Post-Emergency Relapse Rate | % of Emergency Mode sessions that end in a block event within 1 hour | < 15% | Effectiveness of the breathing intervention |

### 4.4 Daily Missions

| Metric | Definition | Target |
|---|---|---|
| Daily Mission Completion Rate | % of served missions marked complete | > 40% |
| Mission-to-Streak Correlation | Correlation between mission completion rate and streak length | Positive (r > 0.3) |
| Mission Category Breakdown | Which mission types (journaling, movement, breathing) are completed most | Informs mission library curation |

### 4.5 Accountability Loop

| Metric | Definition | Target |
|---|---|---|
| Trusted Person Setup Rate | % of users who complete the Shared Key handoff | > 60% |
| Weekly Report Open Rate | % of weekly summaries opened by the Trusted Person | > 40% |
| Alert Response Rate | % of burst block alerts that result in Trusted Person contacting the user within 1 hour | Tracked, no hard target — qualitative insight |

---

## 5. Business Health Metrics

| Metric | Definition | Target |
|---|---|---|
| Free → Pro Conversion Rate | % of free users who upgrade to Pro | > 8% |
| Monthly Churn Rate | % of Pro users who cancel in a given month | < 7% |
| Monthly Recurring Revenue (MRR) | Total active Pro subscriptions × $4.99 | Growing 15% MoM in first year |
| Average Revenue Per User (ARPU) | MRR / total active users | Tracked |
| Lifetime Value (LTV) | ARPU × average subscription duration | > $40 |
| Organic Install Rate | % of new installs from non-paid channels | > 90% |
| Play Store Conversion Rate | % of store listing visitors who install | > 20% |
| 30-Day User Retention | % of users still active 30 days post-install | > 35% |

---

## 6. Analytics Governance

### What We Track
- Block event counts (Wall source, time of day, hour)
- Streak state (streak length, relapse events, recovery)
- Feature utilization counts (Emergency Mode, Missions, Trusted Person setup)
- System health events (service restarts, bypass attempts — count only)

### What We Never Track
- Any URL, domain name, or search query
- Any content from ML screen captures
- Any user-typed text from any field
- Device location
- Any PII beyond an anonymous Firebase UID

### Data Architecture
- **On-Device First:** All behavioral analytics are aggregated locally in Room DB.
- **Cloud Sync:** Only pre-aggregated weekly summaries are pushed to Firestore. Raw events never leave the device.
- **Retention:** Local event data is purged after 90 days. Cloud summary data is retained for the duration of the account.

### Analytics Stack
- **Local:** Room DB (event logging) + on-device aggregation service
- **Cloud:** Firebase Analytics (anonymous, event-count only) + Firestore (accountability summaries)
- **No third-party SDKs:** No Mixpanel, Amplitude, Adjust, or any SDK with its own data collection. Guardian collects its own data or nothing.

---

## 7. Metric Review Cadence

| Frequency | Review Scope | Owner |
|---|---|---|
| Daily | Service uptime, crash rate, block latency | Engineer (Nanda) |
| Weekly | Streak metrics, Emergency Mode utilization, MRR | Founder |
| Monthly | Full metric review, cohort analysis, churn analysis | Founder |
| Per Release | All technical performance metrics (regression check) | Engineer |