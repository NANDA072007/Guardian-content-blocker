# Guardian: Functional & Non-Functional Requirements
**Version:** 2.0  
**Status:** Active  
**Last Updated:** 2026  
**Owner:** Nanda (Founder & Lead Engineer)

---

## Part 1: Functional Requirements Document (FRD)

### Notation
- **SHALL** = Mandatory. Non-negotiable. No build is complete without this.
- **SHOULD** = Strongly recommended. Include unless technically blocked.
- **MAY** = Optional. Include if time permits.
- **AC** = Acceptance Criteria. The specific, testable condition that proves the requirement is met.

---

### 1. Guardian Core (Orchestration)

**F-0.1 — Persistent Foreground Service**
The system SHALL run as an Android `ForegroundService` with a persistent, non-dismissible notification at all times after setup is complete.
- **AC:** After completing onboarding, the Guardian notification is visible in the notification shade and cannot be swiped away.

**F-0.2 — Automatic Restart on Kill**
The system SHALL restart automatically if the process is killed by the OS or a third-party task killer.
- **AC:** After executing `adb shell am kill com.guardian`, the service restarts within 3 seconds.

**F-0.3 — Reboot Recovery**
The system SHALL automatically reactivate all protection walls within 5 seconds of a device reboot.
- **AC:** Device is rebooted; within 5 seconds of the home screen becoming visible, the Guardian foreground notification is present.

**F-0.4 — Wall Health Monitoring**
The Guardian Core SHALL continuously monitor the active status of all enabled Walls every 30 seconds. If a Wall is found inactive, it SHALL attempt to restart it and log the event.
- **AC:** Disabling a service via ADB triggers an automatic restart attempt within 30 seconds and a `wall_inactive` event is written to the block_events table.

---

### 2. Wall 1: DNS/VPN Engine

**F-1.1 — Local VPN Establishment**
The system SHALL establish a local VPN loopback interface using the Android `VpnService` API that intercepts all DNS queries on the device.
- **AC:** While Wall 1 is active, a browser attempting to load a blocked domain receives no response (page load fails silently).

**F-1.2 — Blocklist Domain Resolution**
The system SHALL resolve any domain present in the local blocklist to `0.0.0.0`, effectively preventing the content from loading.
- **AC:** Opening Chrome and navigating to a known blocked domain returns an ERR_NAME_NOT_RESOLVED error.

**F-1.3 — Lookup Latency**
All DNS blocklist lookups SHALL complete within 30ms.
- **AC:** Instrumented benchmark on SQLite lookup returns p95 latency ≤ 30ms on a Snapdragon 680-class device.

**F-1.4 — Blocklist Size**
The local blocklist SHALL contain a minimum of 100,000 adult domain hashes at app initialization.
- **AC:** `SELECT COUNT(*) FROM domain_blocklist` returns ≥ 100,000 on a fresh install.

**F-1.5 — Zero DNS Logging**
The system SHALL NOT log any specific domain name, URL, or search query. It SHALL only log an event count increment when a block occurs.
- **AC:** Code review confirms no domain string is written to any persistence layer or transmitted to any remote endpoint.

**F-1.6 — VPN Revocation Handling**
If the user manually revokes VPN permission via Android Settings, the system SHALL detect the revocation event, log it as a bypass attempt, and notify Guardian Core to escalate Wall 2 monitoring.
- **AC:** Revoking VPN permission via Settings triggers an `unauthorized_bypass` event in the block_events table within 5 seconds.

---

### 3. Wall 2: Accessibility Sentry

**F-2.1 — URL Monitoring in Browsers**
The system SHALL monitor the content of URL address bars and search input fields across all major browser applications.
- **AC:** Typing a blocked domain string in the Chrome address bar (without pressing Enter) triggers the Block Screen.

**F-2.2 — Block Screen Trigger Speed**
The Block Screen SHALL be displayed within 200ms of a blocked URL or keyword being detected in any monitored field.
- **AC:** Instrumented timestamp from AccessibilityEvent receipt to Block Overlay visibility is ≤ 200ms.

**F-2.3 — Settings Self-Protection**
If the user navigates to any of the following Settings sub-pages, the system SHALL immediately execute `GLOBAL_ACTION_HOME` and log the bypass attempt:
- Accessibility Settings → Guardian
- Device Admin Settings → Guardian
- App Info → Guardian (Force Stop / Clear Data)
- **AC:** Navigating to Accessibility Settings and tapping Guardian redirects to the home screen within 200ms.

**F-2.4 — Multi-Browser Support**
Wall 2 SHALL monitor the following packages at minimum:
`com.android.chrome`, `org.mozilla.firefox`, `com.sec.android.app.sbrowser`, `com.opera.browser`, `com.brave.browser`
- **AC:** Triggered block screen appears when a blocked URL is entered in each listed browser.

---

### 4. Wall 3: ML Vision Sentry

**F-3.1 — State-Aware Screen Capture**
The system SHALL capture device screen content every 7–10 seconds ONLY when a browser or flagged application is in the foreground.
- **AC:** CPU profiler confirms VisionSentryService has near-zero activity when a non-browser app (e.g., Calculator) is in the foreground.

**F-3.2 — On-Device ML Inference**
All captured images SHALL be analyzed using a TensorFlow Lite model running entirely on-device. No image data SHALL be transmitted over the network.
- **AC:** Network traffic monitor confirms zero outbound traffic from VisionSentryService during active inference cycles.

**F-3.3 — Detection Threshold**
The Block Screen SHALL be triggered when the ML model outputs an `adult_content` confidence score greater than 0.85.
- **AC:** Test set of 50 known adult images triggers the Block Screen; test set of 50 clean images does not. Pass rate ≥ 90% on each set.

**F-3.4 — Inference Latency**
ML inference SHALL complete within 500ms of image capture on a baseline device (Snapdragon 680).
- **AC:** TFLite benchmark tool reports p95 inference time ≤ 500ms.

**F-3.5 — Memory Cleanup**
The captured image Bitmap SHALL be explicitly cleared from memory immediately after inference completes, regardless of the inference result.
- **AC:** Memory profiler confirms no retained Bitmap objects in VisionSentryService heap between capture cycles.

---

### 5. Wall 4: Policy Controller

**F-4.1 — Device Administrator Privileges**
The system SHALL request Device Administrator privileges during onboarding and SHALL require these privileges to remain active for all Pro features to function.
- **AC:** Navigating to App Info and tapping "Force Stop" returns: "This action is not available. Contact your device administrator."

**F-4.2 — Uninstall Prevention**
The system SHALL prevent standard uninstallation without entry of the correct master password.
- **AC:** Attempting to uninstall Guardian via Play Store or App Info without the master password fails with a system-level error.

**F-4.3 — Anti-Tamper Monitoring**
Wall 4 SHALL verify the active status of Wall 2 (AccessibilityService) every 60 seconds. If Wall 2 is found disabled, Wall 4 SHALL log the event and alert Guardian Core.
- **AC:** Disabling AccessibilityService via ADB triggers an `anti_tamper_detected` event within 60 seconds.

---

### 6. Block Screen & Friction Logic

**F-6.1 — System-Level Overlay**
The Block Screen SHALL be displayed as a system-level window overlay (`TYPE_APPLICATION_OVERLAY`) that renders above all other applications, including the launcher.
- **AC:** The Block Screen is visible even when the Recent Apps button is pressed during the timer.

**F-6.2 — Timer Non-Dismissibility**
The Block Screen timer countdown SHALL NOT be dismissible via the Back button, Home button, or Recent Apps button during its duration.
- **AC:** Pressing Back, Home, and Recent Apps 10 times during the 20-second timer does not dismiss the Block Screen.

**F-6.3 — Default Timer Duration**
The default countdown timer SHALL be 20 seconds.
- **AC:** Timer visible on Block Screen counts from 20 to 0 and then allows dismissal.

**F-6.4 — Streak Display**
The Block Screen SHALL display the user's current streak day count prominently.
- **AC:** A user on Day 14 sees "Day 14" displayed on the Block Screen.

**F-6.5 — Dynamic Messaging**
The Block Screen SHALL display contextual motivational copy based on the current time of day.
- **AC:** A block event occurring between 10 PM and 2 AM displays a "Danger Zone" specific message.

---

### 7. Accountability & Shared Key System

**F-7.1 — Password Generation**
Upon onboarding, the system SHALL generate a random 12-character alphanumeric master password.
- **AC:** Generated password contains at minimum 1 uppercase, 1 lowercase, 1 digit, and is exactly 12 characters.

**F-7.2 — Password Share & Delete**
The system SHALL provide a mechanism to share the master password to a Trusted Person via WhatsApp or SMS Intent. After the share action is confirmed, the password SHALL be deleted from the user's view and only the hash stored.
- **AC:** After sharing, navigating back to the password screen shows "Password secured — only your Trusted Person has it." The plaintext is not accessible.

**F-7.3 — 24-Hour Cooloff**
If a user requests to disable any protection Wall, the system SHALL initiate a non-cancelable 24-hour countdown before the unlock is permitted.
- **AC:** Requesting an unlock initiates a 24-hour timer. Manually changing the device clock does not accelerate the timer.

**F-7.4 — Cooloff Persistence**
The 24-hour cooloff countdown SHALL persist through device reboots.
- **AC:** A cooloff timer with 12 hours remaining, when the device is rebooted, resumes with the correct remaining time.

**F-7.5 — Trusted Person Notification on Unlock Request**
When a user initiates a 24-hour unlock request, the system SHALL send an automated FCM notification to the Trusted Person's registered device.
- **AC:** Initiating an unlock request results in the Trusted Person receiving a notification within 60 seconds.

---

### 8. Streak Engine

**F-8.1 — Daily Streak Increment**
The streak counter SHALL increment by 1 for every 24-hour period in which no relapse event is recorded.
- **AC:** A user with 0 blocks for 3 consecutive days has a streak of 3.

**F-8.2 — Relapse Detection**
The system SHALL detect a relapse event when 3 or more block triggers occur within any 10-minute window and reset the streak to 0.
- **AC:** Triggering 3 blocks within 10 minutes results in streak = 0 and a relapse notification.

**F-8.3 — Burst Alert**
When a relapse event is detected, the system SHALL send an FCM notification to the Trusted Person within 60 seconds.
- **AC:** Triggering a relapse event results in the Trusted Person receiving an FCM notification within 60 seconds.

---

### 9. Emergency Mode

**F-9.1 — Emergency Mode Access**
Emergency Mode SHALL be accessible via a clearly visible button on the main dashboard.
- **AC:** Emergency Mode button is visible on the dashboard without scrolling.

**F-9.2 — Breathing Exercise**
Emergency Mode SHALL include a 4-7-8 breathing animation with visual guidance (inhale for 4 seconds, hold for 7, exhale for 8).
- **AC:** Animation cycles correctly through 4-7-8 timing with visible phase labels.

**F-9.3 — Movement Timer**
Emergency Mode SHALL include a 10-minute movement timer with a simple prompt to perform physical activity.
- **AC:** Tapping "Start Movement" begins a 10-minute countdown.

**F-9.4 — One-Tap Contact**
Emergency Mode SHALL provide a button to call or WhatsApp message the Trusted Person with a single tap.
- **AC:** Tapping the contact button opens WhatsApp with the Trusted Person's number pre-filled, or initiates a phone call.

---

## Part 2: Non-Functional Requirements Document (NFRD)

### 1. Performance

**NF-1.1 — Wall 2 Interception Latency**
The Accessibility Sentry SHALL detect and display the Block Screen within **200ms** of a blocked URL or keyword being detected.
- *Measurement:* Instrumented timestamp from `onAccessibilityEvent` receipt to `WindowManager.addView()` completion.

**NF-1.2 — Wall 3 Inference Latency**
The ML Vision Sentry SHALL complete image capture, downscaling, and TFLite inference within **500ms**.
- *Measurement:* TFLite benchmark tool on Snapdragon 680 device.

**NF-1.3 — Wall 1 DNS Lookup**
DNS blocklist lookup SHALL complete within **30ms**.
- *Measurement:* Instrumented SQLite query execution time, p95 over 1000 queries.

**NF-1.4 — Battery Consumption**
All Guardian background services combined SHALL consume no more than **5% of total battery per 24-hour cycle** on a device with average usage.
- *Measurement:* Android Battery Historian; 24-hour test with screen-on time of 4 hours.

**NF-1.5 — Memory Footprint**
The Guardian process SHALL maintain a RAM footprint of **< 100MB** in background state.
- *Measurement:* Android Profiler — Memory tab; measured after 30 minutes of background operation.

---

### 2. Reliability

**NF-2.1 — Service Uptime**
The Guardian Core ForegroundService SHALL maintain **99.9% uptime** during active device usage hours (device is on and not in deep sleep).
- *Measurement:* Logged heartbeat events in Room DB; calculated as: (expected heartbeats - missed heartbeats) / expected heartbeats.

**NF-2.2 — Reboot Recovery Time**
All active Walls SHALL be fully operational within **5 seconds** of the device completing a reboot.
- *Measurement:* Timestamp of `BOOT_COMPLETED` broadcast vs. timestamp of first Wall health-check passing.

**NF-2.3 — Offline Operation**
All four Walls SHALL function entirely without an internet connection. Cloud features (Trusted Person dashboard, FCM alerts) MAY be delayed but SHALL NOT affect local blocking.
- *Measurement:* Enable Airplane Mode; verify all Walls block correctly for 24 hours.

---

### 3. Security

**NF-3.1 — Encrypted Local Storage**
All sensitive data (master key hash, Trusted Person contact, cooloff timestamps) SHALL be stored in Android `EncryptedSharedPreferences` backed by the hardware keystore where available.
- *Measurement:* Code review confirms no sensitive value is stored in unencrypted `SharedPreferences` or plain SQLite.

**NF-3.2 — No Screenshot Persistence**
ML Vision Sentry captures SHALL exist only in-memory and SHALL NEVER be written to device storage, gallery, or any cloud endpoint.
- *Measurement:* File system scan after 1 hour of active Vision Sentry operation; confirm zero image files created by Guardian's UID.

**NF-3.3 — Bypass Detection Logging**
All detected bypass attempts (settings navigation, ADB force-stop, VPN revocation) SHALL be logged to the `block_events` table with timestamp and type.
- *Measurement:* Each bypass scenario triggers a verifiable log entry.

---

### 4. Privacy

**NF-4.1 — Zero URL Logging**
The DNS filter and Accessibility Sentry SHALL log only event counts, never specific URLs, domain names, search terms, or user-typed content.
- *Measurement:* Full database schema review confirms no text-content columns on block event tables.

**NF-4.2 — Anonymous Cloud Identity**
Firebase user identity SHALL use Anonymous Authentication only. No email, phone number, or device identifier SHALL be transmitted to Firebase.
- *Measurement:* Firebase console confirms all users have UID type "anonymous."

**NF-4.3 — Local ML Processing**
TensorFlow Lite inference SHALL be fully local. The app SHALL NOT call any remote vision API.
- *Measurement:* Network traffic capture during Vision Sentry operation confirms zero outbound requests from the inference pipeline.

---

### 5. Usability & Psychology

**NF-5.1 — Intentional Friction**
UI flows for disabling features SHALL require a minimum of 3 deliberate user actions before any protection Wall can be modified. Friction is a design feature, not a bug.
- *Measurement:* User journey map; count from "I want to disable Wall X" to "Wall X is disabled" ≥ 3 taps, not counting password entry.

**NF-5.2 — Shame-Free Copy**
All in-app text, notifications, and Block Screen messaging SHALL use supportive, identity-reinforcing language. No language SHALL imply failure, weakness, or judgment.
- *Measurement:* UX copy review; all flagged "shame" language variants replaced before release.

**NF-5.3 — Emergency Mode Accessibility**
Emergency Mode SHALL be usable with one hand in portrait mode and SHALL comply with WCAG 2.1 AA contrast standards for all text and interactive elements.
- *Measurement:* Accessibility scanner (Android Accessibility Suite) passes on Emergency Mode screen.

**NF-5.4 — Cold Start Time**
The Guardian application SHALL reach an interactive dashboard state within **3 seconds** of a cold launch on a mid-range device.
- *Measurement:* Android App Startup Metrics — Time to Initial Display (TTID) ≤ 3000ms.