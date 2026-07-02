package com.guardian.app.walls.wall1.pipeline

import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

class DnsParseException(message: String) : Exception(message)

@Singleton
class DnsParser @Inject constructor() {

    /**
     * Parses the DNS QNAME (Domain) from the given packet payload, enforcing security limits.
     * @throws DnsParseException if the packet violates format constraints or limits.
     */
    fun parseDomain(packet: ByteArray, payloadOffset: Int, payloadLength: Int): String {
        if (payloadLength < 12) {
            throw DnsParseException("DNS payload too short")
        }

        // Verify QR bit is 0 (Query)
        val qr = (packet[payloadOffset + 2].toInt() and 0x80) != 0
        if (qr) {
            throw DnsParseException("Not a DNS query")
        }

        // Verify question count is > 0
        val qdCount = ((packet[payloadOffset + 4].toInt() and 0xFF) shl 8) or (packet[payloadOffset + 5].toInt() and 0xFF)
        if (qdCount <= 0) {
            throw DnsParseException("No questions in DNS query")
        }

        val domain = StringBuilder()
        var currentOffset = payloadOffset + 12
        val visitedOffsets = mutableSetOf<Int>()
        var pointerFollowed = false
        var labelCount = 0
        var loopPreventer = 0

        while (loopPreventer++ < 100) {
            if (currentOffset >= payloadOffset + payloadLength) {
                throw DnsParseException("Index out of bounds while parsing DNS QNAME")
            }

            val len = packet[currentOffset].toInt() and 0xFF

            if (len == 0) {
                break
            }

            // Check for compression pointer (starts with 11xxxxxx)
            if ((len and 0xC0) == 0xC0) {
                if (currentOffset + 1 >= payloadOffset + payloadLength) {
                    throw DnsParseException("Invalid compression pointer boundaries")
                }
                val pointerOffset = ((len and 0x3F) shl 8) or (packet[currentOffset + 1].toInt() and 0xFF)
                
                // Compression pointer target must be within the DNS payload bounds
                if (pointerOffset < 12 || pointerOffset >= payloadLength) {
                    throw DnsParseException("Compression pointer target out of bounds: $pointerOffset")
                }

                // Prevent circular compression loops
                if (!visitedOffsets.add(pointerOffset)) {
                    throw DnsParseException("Circular compression loop detected")
                }

                if (!pointerFollowed) {
                    pointerFollowed = true
                }

                currentOffset = payloadOffset + pointerOffset
                continue
            }

            // Normal label
            if (len > 63) {
                throw DnsParseException("Label length exceeds 63 octets")
            }

            if (currentOffset + 1 + len > payloadOffset + payloadLength) {
                throw DnsParseException("Label boundaries exceed packet length")
            }

            if (domain.isNotEmpty()) {
                domain.append(".")
            }

            val labelBytes = ByteArray(len)
            System.arraycopy(packet, currentOffset + 1, labelBytes, 0, len)
            
            // UTF-8 Validation: check if the string can be parsed cleanly
            val labelStr = String(labelBytes, StandardCharsets.UTF_8)
            if (labelStr.contains('\uFFFD')) {
                throw DnsParseException("Malformed non-UTF-8 characters in domain")
            }
            
            domain.append(labelStr)
            
            currentOffset += 1 + len
            labelCount++

            if (labelCount > 10) {
                throw DnsParseException("Recursion limit exceeded: too many domain labels")
            }
        }

        if (loopPreventer >= 100) {
            throw DnsParseException("Loop prevention triggered: parsing QNAME took too many steps")
        }

        return domain.toString()
    }
}
