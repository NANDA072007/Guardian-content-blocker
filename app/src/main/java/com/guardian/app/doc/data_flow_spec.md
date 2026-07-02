# Guardian: Wall 1 Packet Data Flow Specification
**Version:** 1.0  
**Status:** Active Draft  
**Target Component:** Wall 1 Packet Pipeline (`com.guardian.app.walls.wall1.pipeline`)  

---

## 1. Overview
This document specifies the exact contract for processing network packets captured by Guardian's virtual network interface (TUN). Every stage of the pipeline must be stateless, strictly typed, and isolated to prevent packet leakage or thread blocks.

---

## 2. Pipeline Sequence Diagram

```
[Application] (Sends DNS Query)
     │
     ▼
[Android Kernel Routing] (Redirects port 53 traffic)
     │
     ▼
[TUN File Descriptor]
     │
     ▼  (Read loop)
[TunReader] (Thread: Background Worker)
     │
     ▼  [Raw Byte Buffer]
[PacketDecoder] (Thread: Pipeline Thread)
     │
     ▼  [Decoded IP/UDP Frame]
[DnsParser] (Thread: Pipeline Thread)
     │
     ▼  [Extracted QNAME (Domain String)]
[RuleEngine] (Thread: Pipeline Thread / In-Memory Trie)
     │
     ├───────────────────────────┐
     │                           │
  ALLOWED?                      BLOCKED?
     ▼                           ▼
[Datagram Forwarder]       [ResponseWriter]
(Forwards upstream UDP)   (NXDOMAIN Injection)
     │                           │
     └─────────────┬─────────────┘
                   │
                   ▼  [Response IP Packet]
          [TUN File Descriptor]
                   │
                   ▼
          [Android Kernel]
                   │
                   ▼
         [Requesting App]
```

---

## 3. Component Specs

### 3.1 TunReader
* **Role:** Continuously pull bytes from the virtual interface.
* **Input:** `FileDescriptor` stream of the active TUN interface.
* **Output:** `ByteArray` (or `ByteBuffer`) containing raw IP frame bytes.
* **Threading Model:** Runs a blocking `while(isRunning)` loop on a dedicated background thread (`TunReaderThread`).
* **Failure Behavior:** If a read error occurs (e.g., FileDescriptor closed), shutdown the loop, transition `VpnState` to `ERROR`, and alert the `ProtectionOrchestrator`.
* **Timeout Behavior:** Non-blocking read or blocking read with instantaneous wakeup upon service termination.

### 3.2 PacketDecoder
* **Role:** Parse low-level IP and UDP layer headers to identify query formats.
* **Input:** Raw `ByteArray` from `TunReader`.
* **Output:** `DecodedFrame` struct:
  ```kotlin
  data class DecodedFrame(
      val ipVersion: Int, // 4 or 6
      val protocol: Int,  // TCP or UDP
      val srcIp: ByteArray,
      val dstIp: ByteArray,
      val srcPort: Int,
      val dstPort: Int,
      val payloadOffset: Int,
      val payloadLength: Int
  )
  ```
* **Threading Model:** Runs synchronously on the calling pipeline worker thread.
* **Failure Behavior:** If the IP version is not 4/6, or protocol is not UDP/TCP, drop the packet immediately (silent discard).
* **Timeout Behavior:** N/A (runs synchronous computation).

### 3.3 DnsParser
* **Role:** Validate DNS headers, protect against malicious input formats, and extract the requested QNAME domain.
* **Input:** Raw payload buffer and `DecodedFrame` info.
* **Output:** `String` representing the query domain (e.g., `www.example.com`).
* **Threading Model:** Runs synchronously on the calling pipeline worker thread.
* **Validation Controls:**
  * **Header Check:** Verify the QR bit is 0 (Query) and Question Count is > 0.
  * **Security Check:** Validate that QNAME labels do not exceed 10 segments to prevent recursion lockups.
  * **Compression Check:** Stop parsing if an label offset pointer (`0xC0`) points backward in a circular loop.
* **Failure Behavior:** If the packet violates any boundary check, throw a `DnsParseException` and discard the packet to prevent buffer overflow vulnerabilities.

### 3.4 RuleEngine
* **Role:** Determine if the domain matches any suffix/wildcard block entries.
* **Input:** Domain string.
* **Output:** `RuleDecision` enum (`ALLOW` or `BLOCK`).
* **Threading Model:** Executes synchronous, non-blocking lookups on the pipeline thread.
* **Database & Memory Policy:** **Never query the SQLite database directly inside the packet pipeline loop.** The `RuleEngine` queries a pre-loaded, in-memory **Trie / Reverse Trie** structure updated by the `ConfigurationManager` during boot or configuration updates.
* **Failure Behavior:** If the in-memory Trie is empty or uninitialized, fail-safe to `ALLOW` but log a critical system warning.

### 3.5 ResponseWriter
* **Role:** Synthesize and inject DNS responses back into the system loop.
* **Input:** `RuleDecision`, `DecodedFrame` query metadata, and raw request payload.
* **Output:** Formatted IPv4/IPv6 packet buffer written to the TUN FileDescriptor.
* **Injection Strategy:**
  * **BLOCK:** Create an `NXDOMAIN` response payload, swap source/destination IP addresses and ports, recalculate UDP and IP checksums, and write to the output stream.
  * **ALLOW (Fallback):** Delegate query to `DatagramForwarder` to execute standard UDP query forwarding to the upstream resolver.
* **Threading Model:** Runs synchronously on the pipeline thread.
* **Failure Behavior:** Catch writing `IOException` errors, log them, and continue processing next packets.

---

## 4. Timeout Contracts

* **Upstream Resolver Socket Forward:** Datagram sockets communicating with Google/Cloudflare resolvers must use a strict timeout of **2000ms**.
* **Database Suffix Build Timeout:** Loading the SQLite table into the in-memory Trie at startup must complete within **1000ms** to prevent delaying startup times.

---

## 5. Security Validation Boundary
Every buffer reading operation must check `IndexOutOfBounds` exceptions explicitly. No raw pointer math may be performed without checking length constraints against the declared IP packet headers. All domain strings extracted must undergo standard UTF-8 character verification.
