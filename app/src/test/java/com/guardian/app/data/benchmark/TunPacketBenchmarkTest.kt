package com.guardian.app.data.benchmark

import com.guardian.app.walls.wall1.pipeline.DnsParser
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TunPacketBenchmarkTest {

    private val dnsParser = DnsParser()

    @Test
    fun benchmarkDnsExtractionUnderLoad() {
        // Construct a mock DNS UDP packet
        val packet = ByteArray(100)
        val dnsPayloadOffset = 28
        val questionOffset = dnsPayloadOffset + 12
        
        val domainBytes = byteArrayOf(
            3, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0
        )
        System.arraycopy(domainBytes, 0, packet, questionOffset, domainBytes.size)

        // Set QDCOUNT = 1 in DNS header
        packet[dnsPayloadOffset + 4] = 0x00.toByte()
        packet[dnsPayloadOffset + 5] = 0x01.toByte()

        val iterations = 1000
        val times = LongArray(iterations)

        for (i in 0 until iterations) {
            val time = measureTimeMillis {
                dnsParser.parseDomain(packet, dnsPayloadOffset, packet.size - dnsPayloadOffset)
            }
            times[i] = time
        }

        times.sort()
        val p95 = times[(iterations * 0.95).toInt()]
        
        println("1000 UDP parses completed.")
        println("p95 latency: $p95 ms")

        // Target < 30ms p95
        assertTrue("p95 latency should be under 30ms", p95 < 30)
    }
}
