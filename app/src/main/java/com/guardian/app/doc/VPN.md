# Guardian: VPN/DNS Blocker Feature Blueprint
**Version:** 1.1  
**Status:** Under Design  
**Target Component:** Wall 1 (VPN/DNS Engine — `com.guardian.app.walls.wall1`)  

---

## 1. Mission
To inspect DNS traffic and block requests to known adult domains while remaining stable and lightweight.

---

## 2. Functional Requirements
* **Start VPN:** Configure the virtual network interface and start intercepting outbound port 53 packets.
* **Stop VPN:** Shut down the TUN file descriptor and clean up network resources when authenticated.
* **Auto Reconnect:** Re-establish the tunnel automatically when switching networks (Wi-Fi ↔ Mobile data).
* **Show Notification:** Display a persistent, non-dismissible system notification while active.
* **Filter DNS:** Inspect all DNS transaction requests routed through the virtual network adapter.
* **Block Domains:** Intercept requests matching blacklisted domain hashes and respond immediately with `NXDOMAIN` or `0.0.0.0`.
* **Forward Safe Requests:** Forward clean DNS requests to a secure upstream public resolver.
* **Log Blocked Requests:** Record block event counts and source labels in local storage.
* **Notify Protection Engine:** Broadcast matching events to the central orchestrator.
* **Work After Reboot:** Use broadcast receivers to restart the VPN foreground service upon system startup.
* **Handle Network Changes:** Listen to connectivity changes to prevent packet leaks.

---

## 3. Non-Functional Requirements
* **CPU Consumption:** VPN service must consume **< 5%** average CPU during active network query spikes.
* **RAM Footprint:** Maximum heap size must remain **< 60MB**.
* **Battery Efficiency:** Minimal packet processing outside active device interaction; low wake lock usage.
* **Startup Latency:** The VPN tunnel must establish in **< 2 seconds** from launch.
* **Zero Memory Leaks:** Avoid memory leak accumulations during long-running socket forwards.
* **Reliable Reconnect:** Network handovers must resolve and rebind sockets within **< 1.5 seconds**.
* **Crash Resistance:** Wrap low-level socket, stream, and checksum handlers to prevent service crashes.
* **OS Support:** Full compatibility with Android 10 through Android 16.

---

## 4. Constraints
* **Android VPNService API Limits:** Only one active VPN connection can exist globally. Third-party VPN apps will revoke our service.
* **Foreground Service Rules:** Strict enforcement of foreground service declarations (e.g., `specialUse` types on Android 14+).
* **Battery Optimization Restrictions:** Android's Doze mode and App Standby restrict background socket execution unless battery optimization is disabled.
* **OEM Customization Policies:** Aggressive RAM and power managers (e.g., Samsung Device Care, Xiaomi Security Center) terminate background processes.
* **No Root Access Required:** The entire package parsing, socket forwarding, and loopback must execute entirely in user space.
* **Offline-First Design:** Blocklist database checks and local resolver redirects must execute local lookups to avoid external service calls.

---

## 5. Research
* **How does Android's VpnService work?**  
  It routes outbound packet frames matching set IP ranges through a local TUN virtual interface. Reads and writes are executed asynchronously using standard `FileDescriptor` operations.
* **What DNS packet formats need to be handled?**  
  IPv4/IPv6 packet headers, UDP transport frames (port 53), and the RFC 1035 DNS query/response structures.
* **What happens when Wi-Fi changes?**  
  The system connectivity manager reports state shifts. Bounded sockets must be closed and re-bound to the active network interface to prevent packet drops.
* **How does Doze mode affect long-running services?**  
  It disables network access for background apps. We must request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to ensure persistent socket routing.
* **How do other VPN apps reconnect?**  
  They use network callbacks (`onCapabilitiesChanged`) to dynamically tear down and rebuild the socket pool as network validation states update.

---

## 6. Threat Analysis & Mitigations

| Threat | Mechanism | Mitigation Strategy |
|:---|:---|:---|
| **VPN Permission Revoked** | User activates another VPN or disables settings. | Handle `onRevoke()` callback by triggering `BlockOverlayManager` and alerting `GuardianCoreService`. |
| **Service Terminated by OS** | Low-memory killer cleans up background processes. | Mark the service as sticky (`START_STICKY`) and register an `AlarmManager` heartbeat receiver to trigger resurrection. |
| **Doze Mode Restriction** | Android shuts down networking during standby. | Prompt the user during onboarding to disable battery optimization for Guardian. |
| **DNS Request Timeout** | Upstream resolver drops packets or experiences lag. | Set connection timeouts to 2000ms. Inject a `Server Failure` response if upstream fails. |
| **TCP Port 853 DoT Bypass** | System bypasses port 53 using encrypted Private DNS. | Add routing rules in the VpnService configuration for known DoT/DoH IPs, and send TCP resets to reject port 853 and 443 bypass traffic. |

---

## 7. Architecture
The VPN subsystem is divided into isolated, single-responsibility modules:

* **VPN Service:** Manages foreground service notifications, binds to the Android system, and configures the TUN interface.
* **DNS Parser:** Decodes raw UDP byte packets and extracts QNAME query strings.
* **DNS Filter:** Queries local repositories to check if a domain hash is in the blocklist.
* **Packet Router:** Handles socket redirection, routing clean packets upstream, and dropping blocked packets.
* **Connection Manager:** Observes device networks and rebinds sockets on connection handoffs.
* **Notification Manager:** Updates the active foreground service notification state.
* **Recovery Manager:** Restores operations after unexpected crashes or network drops.
* **Logger:** Feeds block event counts and exceptions to diagnostic stores.

---

## 8. Interfaces
```kotlin
interface VpnController {
    fun start()
    fun stop()
    fun restart()
    fun status(): VpnStatus
}

interface DomainFilter {
    fun shouldBlock(domain: String): Boolean
}

interface PacketForwarder {
    fun forwardUpstream(packet: ByteArray, length: Int)
    fun injectResponse(response: ByteArray)
}
```

---

## 9. Data Flow
```
Browser/App -> DNS Request -> VpnService -> DNS Parser -> Domain Filter -> Protection Engine
                                                                                │
                                                                       Allowed or Blocked?
                                                                        ┌──────┴──────┐
                                                                      ALLOW         BLOCK
                                                                        │             │
                                                                    Forward to    Spoof response
                                                                    Upstream DNS  with 0.0.0.0
                                                                        │             │
                                                                        ▼             ▼
                                                                     Inject Response back
                                                                      to Client Socket
```

---

## 10. State Management
Rather than relying on loosely structured boolean flags, the VPN service acts on a strict lifecycle state model:
* **Idle:** Service is stopped; no TUN interface allocated.
* **Starting:** Service is starting; allocating TUN file descriptor and starting worker threads.
* **Running:** Tunnel is active; intercepting and routing packets.
* **Stopping:** Service is tearing down; closing descriptors and freeing threads.
* **Recovering:** Network or process failure occurred; rebuilding socket connections.
* **Error:** Fatal initialization failure occurred; displaying warning notifications.
* **Permission Missing:** BIND_VPN_SERVICE permission is not granted.

---

## 11. Error Handling

| Expected Failure | Detection Method | Recovery / Action | User Message |
|:---|:---|:---|:---|
| **Permission Denied** | VpnService.prepare() returns intent | Launch settings Activity to request permission | "Guardian requires VPN permission to block adult websites." |
| **DNS Server Offline** | SocketTimeoutException | Fallback to secondary resolver (e.g., Google 8.8.8.4) | None (silent fallback) |
| **Socket Closed** | IOException during read() | Re-initialize the Datagram socket pool | None (automatic reconnect) |
| **Packet Malformed** | IndexOutOfBoundsException | Discard bad bytes and continue loop | None (discard bad frames) |
| **Memory Pressure** | Runtime.getRuntime().freeMemory() low | Flush internal DNS cache | None (auto cache flush) |

---

## 12. Instrumentation & Metrics
We track these metrics to evaluate performance and debug issues:
* `vpn_startup_time_ms`: Time taken to build the TUN interface.
* `reconnect_event_count`: Number of network handovers handled.
* `total_dns_queries`: Total count of processed packets.
* `blocked_dns_queries`: Number of blocked domains.
* `average_lookup_latency_ms`: Speed of local database queries.
* `malformed_packet_count`: Number of dropped bad packets.
* `heap_memory_usage_bytes`: Current memory allocations.

---

## 13. Logging Policy
* **Log Rules:** Log startup, shutdown, errors, block events, and network updates.
* **Privacy Restriction:** Never write full packet payloads, query strings, or IP addresses of clean queries to storage.

---

## 14. Unit Testing
* **DNS Header Extraction:** Mock binary packet arrays and verify transaction ID and flag extraction.
* **Domain Hashing & Matching:** Verify the SHA-256 generation matches the stored database hashes.
* **Checksum Integrity:** Test the checksum math for modified packets against standard RFC benchmarks.

---

## 15. Integration Testing
* **VPN Service + Parser:** Bind mock packet files to the input stream and assert correct domain parsing.
* **Database + Filter:** Assert that querying blocked domains triggers block events, while clean queries pass through to the mock forwarder.

---

## 16. Stress Testing
* ** Handoff Stress:** Toggle airplane mode, Wi-Fi, and mobile connections 100 times to verify there are no socket leaks.
* **Query Stress:** Send **10,000 requests** sequentially to measure queue depth and verify the app does not crash from memory exhaustion.
* **Persistence Test:** Run the VPN continuously for **24 hours** in standby to verify memory stability.

---

## 17. Performance Profiling
* **Memory Tracking:** Monitor heap profiles to catch memory leaks in the socket reader loops.
* **Latency Profiling:** Measure the round-trip speed of queries. Target local database checks must stay under 5ms.
* **Thread Count Audits:** Verify that starting/stopping the service does not leak background threads.

---

## 18. Security Review
* **No Packet Leaking:** Use packet logging to verify that no DNS packets bypass the TUN interface during network handovers.
* **Zero Decryption:** The service only monitors DNS configurations (port 53) and does not decrypt SSL/TLS HTTPS payloads, keeping user credentials secure.

---

## 19. Documentation
* **Supported Protocols:** IPv4 and IPv6 UDP DNS.
* **Known Limitations:** Does not intercept native DoH/DoT queries if Private DNS is set to strict mode.
* **Bypass Recovery:** The recovery notification directs the user to open settings if the VPN fails.

---

## 20. Maintenance Plan
* **API Changes:** Inspect changes to foreground service declarations in Android 15/16.
* **Database Updates:** Keep the OTA database update worker schedule active using WorkManager.
* **Telemetry Review:** Review local crash metrics weekly during development to address edge cases.
