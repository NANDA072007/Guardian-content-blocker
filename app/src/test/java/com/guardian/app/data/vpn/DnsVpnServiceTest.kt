package com.guardian.app.walls.wall1

import com.guardian.app.walls.wall1.pipeline.DnsParser
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsVpnServiceTest {

    private val dnsParser = DnsParser()

    @Test
    fun testExtractDomain_ValidDnsPacket() {
        // Construct a mock DNS UDP packet
        // IPv4 Header (20 bytes) + UDP Header (8 bytes) = 28 bytes offset
        // Transaction ID (2 bytes) + Flags (2 bytes) + Questions (2 bytes) + ... = 12 bytes DNS header
        val packet = ByteArray(100)
        
        // DNS Question payload starts at offset 28 + 12 = 40
        val dnsPayloadOffset = 28
        val questionOffset = dnsPayloadOffset + 12
        
        // "www.example.com"
        // length 3, 'w', 'w', 'w', length 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e', length 3, 'c', 'o', 'm', 0
        val domainBytes = byteArrayOf(
            3, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0
        )
        
        System.arraycopy(domainBytes, 0, packet, questionOffset, domainBytes.size)

        // Set QDCOUNT = 1 in DNS header (offset 28 + 4)
        packet[dnsPayloadOffset + 4] = 0x00.toByte()
        packet[dnsPayloadOffset + 5] = 0x01.toByte()
        
        val extracted = dnsParser.parseDomain(packet, dnsPayloadOffset, packet.size - dnsPayloadOffset)
        assertEquals("www.example.com", extracted)
    }

    @Test
    fun testExtractDomain_InvalidPacket_ReturnsEmpty() {
        val packet = ByteArray(10) // Too small
        try {
            dnsParser.parseDomain(packet, 28, packet.size - 28)
            org.junit.Assert.fail("Expected exception not thrown")
        } catch (e: Exception) {
            // Expected
        }
    }
}
