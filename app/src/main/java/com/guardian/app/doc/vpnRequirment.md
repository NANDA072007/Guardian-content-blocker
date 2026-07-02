# Guardian: VPN/DNS Blocker Verification Checklist
**Version:** 1.0  
**Status:** Active Tracking  
**File Reference:** [VPN.md](file:///d:/Android%20projects/guardian/app/src/main/java/com/guardian/app/doc/VPN.md)  

Use this checklist to track the development, optimization, and verification of the Wall 1 (VPN/DNS Engine) subsystem.

---

## 1. Core Interception & Parsing
- [ ] **Establish TUN Interface:** Creates local virtual network adapter (`VpnService.Builder`) in under 2 seconds.
- [ ] **DNS Only Routing:** Configures split-tunneling routing rules to route port 53 traffic into the TUN interface while leaving general web traffic (HTTP/HTTPS) untouched.
- [ ] **IPv4 Parser:** Extract IP and UDP headers from incoming packets correctly.
- [ ] **IPv6 Parser:** Support IPv6 DNS queries (routing to local TUN and parsing IPv6/UDP headers).
- [ ] **DNS Query Extractor (QNAME):** Recursively parses DNS question sections, decodes label-length domain notations, and reconstructs clean domain strings (e.g., `www.example.com`).

---

## 2. Filtering & Sinkholing (Blocking)
- [ ] **SHA-256 Hashing:** Hashes extracted domain names using SHA-256 before database lookups to protect user privacy.
- [ ] **Fast Blocklist Lookup:** Queries the Room database for matching hashes. Includes an LRU in-memory cache to ensure lookup completes in under 5ms.
- [ ] **NXDOMAIN Injection:** Instantly constructs spoofed DNS responses with an `NXDOMAIN` (non-existent domain) error code and injects them back into the TUN interface for blocked queries.
- [ ] **Upstream Redirection:** Opens raw Datagram sockets to a secure upstream resolver (like Cloudflare `1.1.1.1` or Google `8.8.8.8`) for clean queries.
- [ ] **Upstream Response Forwarding:** Reads responses from upstream and routes them back to the client application via the TUN interface.

---

## 3. Threat Mitigation & Anti-Bypass
- [ ] **Private DNS Bypass Prevention (DoT/DoH):** Redirects outgoing TCP connections on ports 853 and 443 for known secure resolvers into the TUN interface and drops them (TCP resets), forcing the OS to fall back to port 53.
- [ ] **Static IP Override:** Intercepts DNS queries even if the user manually overrides DNS settings on their Wi-Fi connection.
- [ ] **Revocation Guard:** Detects `onRevoke()` (when another VPN starts) and calls `BlockOverlayManager` to display the lockout screen.

---

## 4. Connectivity & Network Handover
- [ ] **Network Change Handover:** Rebinds active UDP sockets to the newly available physical interface during a Wi-Fi ↔ Mobile Data transition.
- [ ] **No Flashing Overlay:** The "Reconnecting..." overlay does not display during minor connection switches that resolve in under 1 second.
- [ ] **Captive Portal Detection:** Detects `NET_CAPABILITY_CAPTIVE_PORTAL` networks, pauses VPN interception temporarily to allow portal logins, and resumes once internet access is validated.

---

## 5. Persistence & Lifecycle
- [ ] **Persistent Service:** Service runs as a low-importance foreground service with a persistent notification.
- [ ] **Task Removal Survival:** Starts itself back up immediately via `onTaskRemoved()` when swiped away from the Recents menu.
- [ ] **Self-Resurrection Broadcast:** Triggers a restart broadcast in `onDestroy()` before system termination.
- [ ] **AlarmManager Heartbeat:** Sets a recurring alarm to wake up and restart the service if killed by the OS.
- [ ] **JobService Failsafe:** Schedules a JobService check that runs periodically to resurrect the service.

---

## 6. Performance & Logging
- [ ] **CPU Threshold:** Stays below 5% CPU usage under a packet load of 100 queries/second.
- [ ] **RAM Threshold:** Heap usage remains under 60MB under load.
- [ ] **DNS Resolution Latency:** Clean domain forwarding completes in under 30ms.
- [ ] **No Leak logs:** No plain domain names or user IP addresses are written to persistent logs.

---

## Verification Status Summary
| Component | Status | Tested On | Notes |
|:---|:---|:---|:---|
| **1. Interception & Parsing** | ⚠️ Incomplete | Android Emulator | Requires IPv6 parsing verification. |
| **2. Filtering & Sinkholing** | ⚠️ Incomplete | Android Emulator | Local Room blocklist database is configured but lookup performance is not profiled. |
| **3. Threat Mitigation** | ❌ Missing | None | Private DNS bypass (Port 853 blocking) is not implemented. |
| **4. Connectivity & Handover** | ❌ Broken | Xiaomi MIUI | Reconnecting overlay flashes constantly during Wi-Fi swaps. |
| **5. Persistence & Lifecycle** | ⚠️ Incomplete | Xiaomi MIUI | Aggressive battery savers terminate the service; user onboarding prompts are missing. |
| **6. Performance & Logging** | ⚠️ Incomplete | None | No profiling done under high-load stress testing. |
