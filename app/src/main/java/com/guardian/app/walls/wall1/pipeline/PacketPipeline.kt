package com.guardian.app.walls.wall1.pipeline

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.guardian.app.data.repository.GuardianRepository
import com.guardian.app.util.CryptoUtils
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class PacketPipeline(
    private val context: Context,
    private val vpnFd: ParcelFileDescriptor,
    private val protectSocketAction: (DatagramSocket) -> Boolean,
    private val repository: GuardianRepository,
    private val packetDecoder: PacketDecoder,
    private val dnsParser: DnsParser,
    private val responseWriter: ResponseWriter,
    private val ruleEngine: RuleEngine
) : PacketListener {

    private val isRunning = AtomicBoolean(false)
    private var tunReader: TunReader? = null
    private var outputStream: FileOutputStream? = null
    private var protectedSocket: DatagramSocket? = null
    private var receiveThread: Thread? = null

    // Map of DNS Transaction ID -> Original DecodedFrame query metadata
    private val pendingQueries = ConcurrentHashMap<Short, DecodedFrame>()

    // In-memory cache to prevent database bottlenecks
    private val dnsCache = object : LruCache<String, Boolean>(1000) {}

    companion object {
        private const val TAG = "PacketPipeline"
        
        val knownDohServers: Set<String> = setOf(
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220",
            "185.228.168.10", "185.228.169.11",
            "94.140.14.14", "94.140.15.15",
            "8.26.56.26", "8.20.247.20",
            "45.90.28.0", "45.90.30.0",
            "194.242.2.2", "194.242.2.3",
            "185.222.222.222", "45.11.45.11"
        )
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        outputStream = FileOutputStream(vpnFd.fileDescriptor)
        
        try {
            val socket = DatagramSocket()
            protectSocketAction(socket)
            socket.soTimeout = 2000 // 2000ms timeout for upstream DNS forward
            protectedSocket = socket
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize upstream DatagramSocket", e)
            isRunning.set(false)
            return
        }

        // Start upstream receiver thread
        receiveThread = Thread({
            val receiveBuffer = ByteArray(32767)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            
            while (isRunning.get()) {
                try {
                    val socket = protectedSocket ?: break
                    socket.receive(receivePacket)
                    val responsePayload = receivePacket.data.copyOf(receivePacket.length)
                    
                    if (responsePayload.size >= 2) {
                        val txId = (((responsePayload[0].toInt() and 0xFF) shl 8) or (responsePayload[1].toInt() and 0xFF)).toShort()
                        val originalFrame = pendingQueries.remove(txId)
                        val outStream = outputStream
                        
                        if (originalFrame != null && outStream != null) {
                            responseWriter.sendDnsResponse(outStream, originalFrame, responsePayload)
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error receiving DNS response from upstream", e)
                    }
                }
            }
        }, "UpstreamDnsReceiverThread").apply {
            start()
        }

        // Start reading raw packets from TUN
        tunReader = TunReader(vpnFd, this)
        tunReader?.start()
        Log.d(TAG, "Packet pipeline started successfully.")
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        tunReader?.stop()
        tunReader = null

        try {
            protectedSocket?.close()
        } catch (_: Exception) {}
        protectedSocket = null

        try {
            receiveThread?.interrupt()
        } catch (_: Exception) {}
        receiveThread = null

        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null

        pendingQueries.clear()
        Log.d(TAG, "Packet pipeline stopped.")
    }

    override fun onPacketReceived(packet: ByteArray) {
        val outStream = outputStream ?: return
        val socket = protectedSocket ?: return

        val decodedFrame = packetDecoder.decode(packet) ?: return

        // Drop non-TCP/UDP packets
        if (decodedFrame.protocol != 6 && decodedFrame.protocol != 17) return

        // 1. TCP RST checks for DoT/DoH secure bypass prevention
        if (decodedFrame.protocol == 6) {
            val destIpStr = decodedFrame.dstIp.joinToString(".") { (it.toInt() and 0xFF).toString() }
            if ((decodedFrame.dstPort == 443 || decodedFrame.dstPort == 853) && knownDohServers.contains(destIpStr)) {
                // If TCP SYN packet (SYN flag = 0x02) to known DoH resolver
                val ihl = decodedFrame.ipHeader.size
                if (packet.size >= ihl + 14) {
                    val tcpFlags = packet[ihl + 13].toInt() and 0xFF
                    if ((tcpFlags and 0x02) != 0) {
                        Log.d(TAG, "DoT/DoH bypass attempt detected to $destIpStr:${decodedFrame.dstPort}. Injecting TCP RST.")
                        responseWriter.sendTcpRst(outStream, packet, decodedFrame)
                    }
                }
            }
            return
        }

        // 2. UDP DNS interception (Port 53)
        if (decodedFrame.protocol == 17 && decodedFrame.dstPort == 53) {
            val payloadOffset = decodedFrame.payloadOffset
            val payloadLength = decodedFrame.payloadLength

            try {
                val domain = dnsParser.parseDomain(packet, payloadOffset, payloadLength)
                val hash = CryptoUtils.sha256(domain.toByteArray())

                // Check cache first to avoid RuleEngine traversal overhead
                var isBlocked = dnsCache.get(domain)
                if (isBlocked == null) {
                    isBlocked = domain.isNotEmpty() && ruleEngine.shouldBlock(domain)
                    dnsCache.put(domain, isBlocked)
                }

                val dnsPayload = ByteArray(payloadLength)
                System.arraycopy(packet, payloadOffset, dnsPayload, 0, payloadLength)

                if (isBlocked) {
                    Log.w(TAG, "BLOCKING query: $domain")
                    Thread {
                        repository.logBlockEventSync("DNS_VPN")
                    }.start()

                    val nxResponse = responseWriter.createNxDomainResponse(dnsPayload)
                    responseWriter.sendDnsResponse(outStream, decodedFrame, nxResponse)
                    return
                }

                // If allowed, forward query to upstream
                if (dnsPayload.size >= 2) {
                    val txId = (((dnsPayload[0].toInt() and 0xFF) shl 8) or (dnsPayload[1].toInt() and 0xFF)).toShort()
                    pendingQueries[txId] = decodedFrame

                    val dnsServerAddress = InetAddress.getByName("8.8.8.8")
                    val request = DatagramPacket(dnsPayload, dnsPayload.size, dnsServerAddress, 53)
                    socket.send(request)
                }
            } catch (e: DnsParseException) {
                Log.w(TAG, "Failed to parse DNS packet: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing DNS packet in pipeline", e)
            }
        }
    }

    override fun onError(e: Exception) {
        Log.e(TAG, "TUN read error encountered", e)
        // Pipeline failure: stop and notify
        stop()
        // Note: ProtectionOrchestrator will catch status change from VPN controller update
    }
}
