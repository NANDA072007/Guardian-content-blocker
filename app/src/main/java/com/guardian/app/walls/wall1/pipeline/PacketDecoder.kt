package com.guardian.app.walls.wall1.pipeline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PacketDecoder @Inject constructor() {

    fun decode(packet: ByteArray): DecodedFrame? {
        if (packet.isEmpty()) return null

        try {
            val ipVersion = (packet[0].toInt() shr 4) and 0x0F
            if (ipVersion == 4) {
                if (packet.size < 20) return null
                val ihl = (packet[0].toInt() and 0x0F) * 4
                if (packet.size < ihl) return null

                val protocol = packet[9].toInt() and 0xFF
                val srcIp = packet.copyOfRange(12, 16)
                val dstIp = packet.copyOfRange(16, 20)
                val ipHeader = packet.copyOfRange(0, ihl)

                if (protocol == 17) { // UDP
                    if (packet.size < ihl + 8) return null
                    val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
                    val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
                    val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
                    val payloadOffset = ihl + 8
                    val payloadLength = udpLen - 8

                    if (packet.size < payloadOffset + payloadLength || payloadLength < 0) return null
                    return DecodedFrame(
                        ipVersion = 4,
                        protocol = 17,
                        srcIp = srcIp,
                        dstIp = dstIp,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        payloadOffset = payloadOffset,
                        payloadLength = payloadLength,
                        ipHeader = ipHeader
                    )
                } else if (protocol == 6) { // TCP
                    if (packet.size < ihl + 20) return null
                    val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
                    val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
                    val dataOffset = (packet[ihl + 12].toInt() shr 4) and 0x0F
                    val tcpHeaderLength = dataOffset * 4
                    val payloadOffset = ihl + tcpHeaderLength
                    val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
                    val payloadLength = totalLength - payloadOffset

                    if (packet.size < payloadOffset + payloadLength || payloadLength < 0) return null
                    return DecodedFrame(
                        ipVersion = 4,
                        protocol = 6,
                        srcIp = srcIp,
                        dstIp = dstIp,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        payloadOffset = payloadOffset,
                        payloadLength = payloadLength,
                        ipHeader = ipHeader
                    )
                }
            } else if (ipVersion == 6) {
                if (packet.size < 40) return null
                val nextHeader = packet[6].toInt() and 0xFF
                val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
                val srcIp = packet.copyOfRange(8, 24)
                val dstIp = packet.copyOfRange(24, 40)
                val ipHeader = packet.copyOfRange(0, 40)

                if (nextHeader == 17) { // UDP
                    if (packet.size < 48) return null
                    val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
                    val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)
                    val udpLen = ((packet[44].toInt() and 0xFF) shl 8) or (packet[45].toInt() and 0xFF)
                    val payloadOffset = 48
                    val payloadLength = udpLen - 8

                    if (packet.size < payloadOffset + payloadLength || payloadLength < 0) return null
                    return DecodedFrame(
                        ipVersion = 6,
                        protocol = 17,
                        srcIp = srcIp,
                        dstIp = dstIp,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        payloadOffset = payloadOffset,
                        payloadLength = payloadLength,
                        ipHeader = ipHeader
                    )
                } else if (nextHeader == 6) { // TCP
                    if (packet.size < 60) return null
                    val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
                    val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)
                    val dataOffset = (packet[40 + 12].toInt() shr 4) and 0x0F
                    val tcpHeaderLength = dataOffset * 4
                    val payloadOffset = 40 + tcpHeaderLength
                    val payloadLength = payloadLen - tcpHeaderLength

                    if (packet.size < payloadOffset + payloadLength || payloadLength < 0) return null
                    return DecodedFrame(
                        ipVersion = 6,
                        protocol = 6,
                        srcIp = srcIp,
                        dstIp = dstIp,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        payloadOffset = payloadOffset,
                        payloadLength = payloadLength,
                        ipHeader = ipHeader
                    )
                }
            }
        } catch (_: Exception) {
            // Drop on any bounds / indexing errors
        }
        return null
    }
}
