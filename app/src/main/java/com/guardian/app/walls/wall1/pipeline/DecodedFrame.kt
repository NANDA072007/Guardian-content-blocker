package com.guardian.app.walls.wall1.pipeline

/**
 * Metadata representation of a decoded IP/UDP (or TCP) packet frame.
 */
data class DecodedFrame(
    val ipVersion: Int, // 4 or 6
    val protocol: Int,  // 17 for UDP, 6 for TCP
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
    val ipHeader: ByteArray // Copy of the original IP header for response mapping
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedFrame) return false
        if (ipVersion != other.ipVersion) return false
        if (protocol != other.protocol) return false
        if (!srcIp.contentEquals(other.srcIp)) return false
        if (!dstIp.contentEquals(other.dstIp)) return false
        if (srcPort != other.srcPort) return false
        if (dstPort != other.dstPort) return false
        if (payloadOffset != other.payloadOffset) return false
        if (payloadLength != other.payloadLength) return false
        if (!ipHeader.contentEquals(other.ipHeader)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ipVersion
        result = 31 * result + protocol
        result = 31 * result + srcIp.contentHashCode()
        result = 31 * result + dstIp.contentHashCode()
        result = 31 * result + srcPort
        result = 31 * result + dstPort
        result = 31 * result + payloadOffset
        result = 31 * result + payloadLength
        result = 31 * result + ipHeader.contentHashCode()
        return result
    }
}
