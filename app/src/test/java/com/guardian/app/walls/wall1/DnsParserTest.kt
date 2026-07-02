package com.guardian.app.walls.wall1

import com.guardian.app.walls.wall1.pipeline.DnsParser
import com.guardian.app.walls.wall1.pipeline.DnsParseException
import org.junit.Assert.*
import org.junit.Test

class DnsParserTest {

    private val dnsParser = DnsParser()

    private fun createDnsQuery(qnameBytes: ByteArray): ByteArray {
        val payload = ByteArray(12 + qnameBytes.size + 4)
        // Tx ID
        payload[0] = 0x12.toByte()
        payload[1] = 0x34.toByte()
        // Flags (Query, Standard, RD = 1)
        payload[2] = 0x01.toByte()
        payload[3] = 0x00.toByte()
        // QDCOUNT = 1
        payload[4] = 0x00.toByte()
        payload[5] = 0x01.toByte()

        System.arraycopy(qnameBytes, 0, payload, 12, qnameBytes.size)

        // QTYPE = A (1)
        val offset = 12 + qnameBytes.size
        payload[offset] = 0x00.toByte()
        payload[offset + 1] = 0x01.toByte()
        // QCLASS = IN (1)
        payload[offset + 2] = 0x00.toByte()
        payload[offset + 3] = 0x01.toByte()

        return payload
    }

    @Test
    fun `parseDomain extracts simple domain name successfully`() {
        // google.com -> \u0006google\u0003com\u0000
        val qname = byteArrayOf(
            6, 'g'.toByte(), 'o'.toByte(), 'o'.toByte(), 'g'.toByte(), 'l'.toByte(), 'e'.toByte(),
            3, 'c'.toByte(), 'o'.toByte(), 'm'.toByte(),
            0
        )
        val packet = createDnsQuery(qname)
        val domain = dnsParser.parseDomain(packet, 0, packet.size)
        assertEquals("google.com", domain)
    }

    @Test
    fun `parseDomain throws if QR bit is response`() {
        val qname = byteArrayOf(
            6, 'g'.toByte(), 'o'.toByte(), 'o'.toByte(), 'g'.toByte(), 'l'.toByte(), 'e'.toByte(),
            3, 'c'.toByte(), 'o'.toByte(), 'm'.toByte(),
            0
        )
        val packet = createDnsQuery(qname)
        // Set QR bit to 1 (Response)
        packet[2] = (packet[2].toInt() or 0x80).toByte()

        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }

    @Test
    fun `parseDomain throws if question count is zero`() {
        val qname = byteArrayOf(
            6, 'g'.toByte(), 'o'.toByte(), 'o'.toByte(), 'g'.toByte(), 'l'.toByte(), 'e'.toByte(),
            3, 'c'.toByte(), 'o'.toByte(), 'm'.toByte(),
            0
        )
        val packet = createDnsQuery(qname)
        // Set QDCOUNT to 0
        packet[4] = 0
        packet[5] = 0

        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }

    @Test
    fun `parseDomain throws if recursion label limit exceeded`() {
        // 11 labels -> a.a.a.a.a.a.a.a.a.a.a.com (12 labels total)
        val labels = ByteArray(12 * 2)
        for (i in 0 until 11) {
            labels[i * 2] = 1
            labels[i * 2 + 1] = 'a'.toByte()
        }
        labels[22] = 3
        val comBytes = byteArrayOf('c'.toByte(), 'o'.toByte(), 'm'.toByte(), 0)
        
        val qname = ByteArray(labels.size + comBytes.size)
        System.arraycopy(labels, 0, qname, 0, labels.size)
        System.arraycopy(comBytes, 0, qname, labels.size, comBytes.size)

        val packet = createDnsQuery(qname)
        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }

    @Test
    fun `parseDomain throws if label length exceeds 63`() {
        // Label length 64
        val qname = ByteArray(66)
        qname[0] = 64
        for (i in 1..64) {
            qname[i] = 'a'.toByte()
        }
        qname[65] = 0

        val packet = createDnsQuery(qname)
        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }

    @Test
    fun `parseDomain detects circular compression pointer loops`() {
        // Prepare a packet with a pointer target pointing to itself
        // Offset 12 starts QNAME
        val packet = ByteArray(30)
        packet[4] = 0x00.toByte()
        packet[5] = 0x01.toByte() // QDCOUNT = 1
        
        // compression pointer at offset 12 pointing back to offset 12: 0xC0, 0x0C
        packet[12] = 0xC0.toByte()
        packet[13] = 0x0C.toByte()

        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }

    @Test
    fun `parseDomain throws if compression pointer is out of bounds`() {
        val packet = ByteArray(30)
        packet[4] = 0x00.toByte()
        packet[5] = 0x01.toByte() // QDCOUNT = 1
        
        // compression pointer at offset 12 pointing out of bounds (offset 50)
        packet[12] = 0xC0.toByte()
        packet[13] = 0x32.toByte()

        assertThrows(DnsParseException::class.java) {
            dnsParser.parseDomain(packet, 0, packet.size)
        }
    }
}
