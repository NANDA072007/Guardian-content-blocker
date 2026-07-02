package com.guardian.app.walls.wall1.pipeline

import android.util.Log
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseWriter @Inject constructor() {

    /**
     * Crafts an NXDOMAIN DNS response payload based on the query payload.
     */
    fun createNxDomainResponse(requestPayload: ByteArray): ByteArray {
        val response = requestPayload.copyOf()
        if (response.size >= 4) {
            // Set QR bit = 1 (Response)
            response[2] = (response[2].toInt() or 0x80).toByte()
            // Set RA bit = 1 (Recursion Available) and RCODE = 3 (NXDOMAIN)
            response[3] = (response[3].toInt() or 0x80).toByte()
            response[3] = ((response[3].toInt() and 0xF0) or 0x03).toByte()
        }
        return response
    }

    /**
     * Swaps IP/UDP headers of the request, attaches the response payload, and writes to TUN.
     */
    fun sendDnsResponse(outputStream: OutputStream, decodedFrame: DecodedFrame, responsePayload: ByteArray) {
        try {
            val ipHeaderLen = decodedFrame.ipHeader.size
            val responseIpPacket = ByteArray(ipHeaderLen + responsePayload.size)
            System.arraycopy(decodedFrame.ipHeader, 0, responseIpPacket, 0, ipHeaderLen)
            System.arraycopy(responsePayload, 0, responseIpPacket, ipHeaderLen, responsePayload.size)

            if (decodedFrame.ipVersion == 4) {
                // Swap IPv4 Source and Dest IP
                for (i in 0..3) {
                    val temp = responseIpPacket[12 + i]
                    responseIpPacket[12 + i] = responseIpPacket[16 + i]
                    responseIpPacket[16 + i] = temp
                }

                // Swap UDP Source and Dest Port
                for (i in 0..1) {
                    val temp = responseIpPacket[ipHeaderLen + i]
                    responseIpPacket[ipHeaderLen + i] = responseIpPacket[ipHeaderLen + 2 + i]
                    responseIpPacket[ipHeaderLen + 2 + i] = temp
                }

                // Update IP Total Length
                val totalLen = responseIpPacket.size
                responseIpPacket[2] = (totalLen shr 8).toByte()
                responseIpPacket[3] = totalLen.toByte()

                // Update UDP Length
                val udpLen = 8 + responsePayload.size
                responseIpPacket[ipHeaderLen + 4] = (udpLen shr 8).toByte()
                responseIpPacket[ipHeaderLen + 5] = udpLen.toByte()

                // Clear UDP Checksum (0 means no checksum/ignored in UDP)
                responseIpPacket[ipHeaderLen + 6] = 0
                responseIpPacket[ipHeaderLen + 7] = 0

                // Clear IP Checksum
                responseIpPacket[10] = 0
                responseIpPacket[11] = 0

                // Recalculate IP Checksum
                var sum = 0
                for (i in 0 until ipHeaderLen step 2) {
                    val word = ((responseIpPacket[i].toInt() and 0xFF) shl 8) or (responseIpPacket[i + 1].toInt() and 0xFF)
                    sum += word
                }
                while ((sum shr 16) > 0) {
                    sum = (sum and 0xFFFF) + (sum shr 16)
                }
                sum = sum.inv() and 0xFFFF
                responseIpPacket[10] = (sum shr 8).toByte()
                responseIpPacket[11] = sum.toByte()
            } else if (decodedFrame.ipVersion == 6) {
                // Swap IPv6 Source and Dest IP
                for (i in 0..15) {
                    val temp = responseIpPacket[8 + i]
                    responseIpPacket[8 + i] = responseIpPacket[24 + i]
                    responseIpPacket[24 + i] = temp
                }

                // Swap UDP Source and Dest Port
                for (i in 0..1) {
                    val temp = responseIpPacket[40 + i]
                    responseIpPacket[40 + i] = responseIpPacket[42 + i]
                    responseIpPacket[42 + i] = temp
                }

                // Update IPv6 Payload Length
                val payloadLen = 8 + responsePayload.size
                responseIpPacket[4] = (payloadLen shr 8).toByte()
                responseIpPacket[5] = payloadLen.toByte()

                // Update UDP Length
                responseIpPacket[44] = (payloadLen shr 8).toByte()
                responseIpPacket[45] = payloadLen.toByte()

                // Clear UDP Checksum
                responseIpPacket[46] = 0
                responseIpPacket[47] = 0
            }

            outputStream.write(responseIpPacket)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e("ResponseWriter", "Failed to write DNS response to TUN", e)
        }
    }

    /**
     * Constructs and sends a TCP RST packet back to the client to terminate bypassed secure queries.
     */
    fun sendTcpRst(outputStream: OutputStream, originalPacket: ByteArray, decodedFrame: DecodedFrame) {
        if (decodedFrame.ipVersion != 4) return // IPv4 TCP RST support

        try {
            val ihl = decodedFrame.ipHeader.size
            val srcIp = originalPacket.copyOfRange(12, 16)
            val dstIp = originalPacket.copyOfRange(16, 20)
            val srcPort = decodedFrame.srcPort
            val dstPort = decodedFrame.dstPort
            
            val seqNum = ((originalPacket[ihl + 4].toInt() and 0xFF).toLong() shl 24) or
                         ((originalPacket[ihl + 5].toInt() and 0xFF).toLong() shl 16) or
                         ((originalPacket[ihl + 6].toInt() and 0xFF).toLong() shl 8) or
                         (originalPacket[ihl + 7].toInt() and 0xFF).toLong()

            // Build IP header (20 bytes)
            val ipLen = 20
            val tcpLen = 20
            val ipHeader = ByteArray(ipLen)
            ipHeader[0] = 0x45
            val totalLen = ipLen + tcpLen
            ipHeader[2] = (totalLen shr 8).toByte()
            ipHeader[3] = totalLen.toByte()
            ipHeader[8] = 64
            ipHeader[9] = 6 // TCP Protocol
            
            // Swap IPs
            System.arraycopy(dstIp, 0, ipHeader, 12, 4)
            System.arraycopy(srcIp, 0, ipHeader, 16, 4)

            // Build TCP header (20 bytes)
            val tcpHeader = ByteArray(tcpLen)
            tcpHeader[0] = (dstPort shr 8).toByte()
            tcpHeader[1] = dstPort.toByte()
            tcpHeader[2] = (srcPort shr 8).toByte()
            tcpHeader[3] = srcPort.toByte()
            val ackNum = seqNum + 1
            tcpHeader[8] = (ackNum shr 24).toByte()
            tcpHeader[9] = (ackNum shr 16).toByte()
            tcpHeader[10] = (ackNum shr 8).toByte()
            tcpHeader[11] = ackNum.toByte()
            tcpHeader[12] = 0x50 // Data Offset (5 = 20 bytes)
            tcpHeader[13] = 0x14 // Flags: RST-ACK

            // Calculate TCP checksum with pseudo-header
            val pseudoLen = 12 + tcpLen
            val pseudoBuf = ByteArray(pseudoLen)
            System.arraycopy(dstIp, 0, pseudoBuf, 0, 4)
            System.arraycopy(srcIp, 0, pseudoBuf, 4, 4)
            pseudoBuf[8] = 0
            pseudoBuf[9] = 6
            pseudoBuf[10] = (tcpLen shr 8).toByte()
            pseudoBuf[11] = tcpLen.toByte()

            var sum = 0L
            for (i in 0 until pseudoLen step 2) {
                val word = ((pseudoBuf[i].toInt() and 0xFF) shl 8) or (pseudoBuf[i + 1].toInt() and 0xFF)
                sum += word
            }
            for (i in 0 until tcpLen step 2) {
                val word = ((tcpHeader[i].toInt() and 0xFF) shl 8) or (tcpHeader[i + 1].toInt() and 0xFF)
                sum += word
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            val checksum = (sum.inv() and 0xFFFF).toInt()
            tcpHeader[16] = (checksum shr 8).toByte()
            tcpHeader[17] = checksum.toByte()

            // Calculate IP checksum
            var ipSum = 0L
            for (i in 0 until ipLen step 2) {
                val word = ((ipHeader[i].toInt() and 0xFF) shl 8) or (ipHeader[i + 1].toInt() and 0xFF)
                ipSum += word
            }
            while ((ipSum shr 16) > 0) {
                ipSum = (ipSum and 0xFFFF) + (ipSum shr 16)
            }
            val ipChecksum = (ipSum.inv() and 0xFFFF).toInt()
            ipHeader[10] = (ipChecksum shr 8).toByte()
            ipHeader[11] = ipChecksum.toByte()

            // Combine and write RST packet
            val rstPacket = ByteArray(totalLen)
            System.arraycopy(ipHeader, 0, rstPacket, 0, ipLen)
            System.arraycopy(tcpHeader, 0, rstPacket, ipLen, tcpLen)

            outputStream.write(rstPacket)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e("ResponseWriter", "Failed to write TCP RST to TUN", e)
        }
    }
}
