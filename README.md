# Guardian — Open Source Accountability App

Guardian is a privacy-first, on-device accountability app that helps individuals break free from adult content addiction. It uses a **4-wall defense architecture** that cannot be bypassed, requires no cloud subscriptions, and is completely free.

## Architecture Overview

Guardian's defense is built on 4 independent walls. Each wall operates at a different OS layer so that disabling any one requires a different attack vector:

| Wall | Layer | Mechanism | Stops |
|------|-------|-----------|-------|
| Wall 1 | Network (VPN) | Local DNS sinkhole — intercepts DNS queries to `8.8.8.8:53`, null-routes known adult domains via a blocklist (SHA-256 hashed, binary search); TCP RST injection for 18 known DoH/DoT providers | DNS resolution of adult domains (limited — only 2 domains seeded by default) |
| Wall 2 | Accessibility | `AccessibilitySentry` foreground service (`onAccessibilityEvent`) scans node text across all apps via `BrowserMonitor` (100+ keywords, leetspeak normalization); `BlockOverlayManager` draws a full-screen DANGER overlay that consumes all touches (`onTouch=true`) + system keys (back/home/recent/volume) via `TYPE_ACCESSIBILITY_OVERLAY` | Typed keywords in any app (incognito, private browsing, 3rd-party keyboards, SMS, social media) |
| Wall 3 | Overlay | `BlockOverlayManager` shows a 45-second immersive DANGER overlay with countdown timer, then a positive reinforcement screen ("YOU DID IT! +1 Strength"), then redirects to home screen | Bypass attempts during block event |
| Wall 4 | Device Admin | `GuardianDeviceAdminReceiver` extends `DeviceAdminReceiver`; blocks uninstallation and force-stop via Android OS natively when active | App deletion, force-stop via Settings |

Supporting systems:
- **Auto-Resurrection Engine** (`ServiceResurrector`, `GuardianJobService`, `ServiceRestartReceiver`, `GuardianAlarmReceiver`, `WatchdogWorker`, `AccessibilityRecoveryWorker`) — multi-path health check that restarts any wall that Android's battery saver or OEM kills
- **Streak Tracker** — offline clean-day streak computed on-the-fly from Room/SQLite block-event history (last relapse detected as 3+ events in 10-min window); tamper-resistant because it is derived from immutable event data
- **Cooloff Timer** — SHA-256 gated 24-hour uninstall cooloff with rate-limiting penalties

## App Flow

### 1. First Launch → Onboarding (6 Steps)

Guardian walks the user through granting every permission it needs. No permission = no protection.

| Step | Permission | Why |
|------|-----------|-----|
| 1 | **Battery Optimization Exemption** | Prevents OEM battery saver from killing Guardian's foreground services |
| 2 | **Overlay Permission** (`SYSTEM_ALERT_WINDOW`) | Enables the block overlay that covers adult content |
| 3 | **Accessibility Service** | Lets `AccessibilitySentry` read screen content across all apps |
| 4 | **VPN** (`VpnService`) | Activates the local DNS sinkhole (Wall 1) |
| 5 | **Device Admin** | Prevents uninstallation without 24h cooloff (Wall 4) |
| 6 | **Guardian Code** | App generates an 8-char alphanumeric code (e.g., `AB3F-K7XN`). **Save this** — it's required to pause protection or start the 24h uninstall cooloff |

Once all 6 steps complete, the dashboard opens and all walls are active.

### 2. Dashboard (Daily Use)

- **Status ring** — shows clean-day streak with milestone badges (3=💪, 7=⚡, 14=🔥, 30=🏆, 90=👑)
- **Protection toggles** — each wall shows its status. Toggling a wall OFF triggers the cooloff check
- **Pause Protection** — enter your Guardian Code for a 5-minute pause (settings temporarily unlocked)
- **Emergency Mode** — immersive breathing exercise (4-7-8 method) to ride out urges
- **Support** — customer service form that submits diagnostics to a Google Sheet

### 3. Uninstall / Disable Flow

1. User navigates to Settings → Disable Guardian
2. Enters their **Guardian Code**
3. If correct, a **24-hour cooloff** starts (countdown shown on screen)
4. After 24 hours, user can remove Device Admin privileges
5. Only then can the app be uninstalled

Wrong code attempts trigger escalating penalties: 1 min lockout after 5 failures, 5 min after 6, 30 min after 7+.

### 4. Customer Service

Users can submit a report with:
- **Category**: Bug Report / Feature Request / Suggestion / Other
- **Severity**: Minor / Major / Critical (only shown for Bug Report)
- **Device Info**: 15 fields collected automatically (OS version, API level, device model, manufacturer, app version, build number, VPN state, accessibility state, device admin state, health level, battery optimization status, restricted settings blocking, overlay permission, uptime hours, last recovery time)
- Cooldown: 30 seconds between submissions
- Data is POSTed to a Google Form — no server infrastructure

## Usage Guide

### Installation
1. Download the latest APK from Releases
2. `Settings → Install unknown apps → Allow`
3. Open Guardian → tap "I Understand"

### Daily Habits
- **Morning**: Check dashboard — if the ring is still green, all walls are active
- **Urge**: Open Emergency Mode → breathe through the 4-7-8 cycle → leave the room
- **Relapse**: Don't uninstall. Use the 5-min pause with your Guardian Code if you need to disable protections temporarily

### Important Notes
- **Write down your Guardian Code.** It cannot be recovered. If you lose it, you cannot pause or uninstall.
- **OEM AutoStart**: Xiaomi/Oppo/Vivo/etc. users must enable AutoStart in system settings for Guardian, otherwise the OS may kill background services.
- **Restricted Settings (Android 13+)**: If you sideloaded the APK, Android may block Accessibility. The dashboard shows a blue banner to guide you to "Allow restricted settings".

## How to Promote Guardian

### Target Audience
1. **Individuals** struggling with porn addiction who can't afford $10-15/mo for Covenant Eyes / Ever Accountable / Canopy
2. **Recovery communities** — Reddit (r/pornfree, r/nofap, r/REDDITORSINRECOVERY), Discord servers, 12-step groups
3. **Religious / faith-based groups** — LDS (Fortify), Catholic (Covenant Eyes users looking for free alternative), Muslim accountability groups
4. **Parents** of teens who want a free device-level accountability tool (though marketed as "self-accountability" to avoid the stigma)

### Messaging Pillars
1. **"It's free and always will be"** — biggest differentiator from Covenant Eyes ($15/mo)
2. **"Unbypassable by design"** — the 4-wall architecture is harder to defeat than any other free tool
3. **"Zero cloud, zero tracking"** — all processing on-device, no account, no data collection
4. **"Your code, your lock"** — the Guardian Code puts you (not a 3rd party) in control

### Distribution Channels

| Channel | Approach |
|---------|----------|
| **Reddit** | Post in r/pornfree, r/nofap, r/androidapps. Avoid direct links — share the GitHub repo. Title format: "I built a free, open-source, unbypassable porn blocker for Android" |
| **GitHub** | Add `porn-blocker`, `accountability-app`, `parental-controls`, `dns-filter`, `android-security` tags. Encourage star/watch for visibility |
| **YouTube** | Publish a 3-minute setup walkthrough. Embed the video in the README |
| **Recovery blogs** | Guest post: "Why I stopped paying $15/mo for Covenant Eyes and built an open-source alternative" |
| **F-Droid** | Submit to F-Droid for discoverability in the free-software ecosystem |
| **XDA Developers** | Post on XDA forums — the technical audience can validate and vouch for the architecture |

### Quick Win Tactics
- Create a `gh-pages` or Vercel landing page with a one-click APK download link
- Add a **"Share Guardian"** button to the dashboard that generates a share text: *"I'm using Guardian — a free, private, unbypassable porn blocker. Download: [link]"*
- Submit to **Product Hunt** (category: Android Apps, Open Source)
- Add a **Lite version** bumper video for TikTok/Reels showing the danger-zone overlay in action

## License
MIT — free forever.
