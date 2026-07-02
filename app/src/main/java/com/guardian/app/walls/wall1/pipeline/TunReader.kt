package com.guardian.app.walls.wall1.pipeline

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

interface PacketListener {
    fun onPacketReceived(packet: ByteArray)
    fun onError(e: Exception)
}

class TunReader(
    private val vpnFd: ParcelFileDescriptor,
    private val listener: PacketListener
) {
    private val isRunning = AtomicBoolean(false)
    private var readThread: Thread? = null

    fun start() {
        if (isRunning.getAndSet(true)) return

        readThread = Thread({
            val inputStream = FileInputStream(vpnFd.fileDescriptor)
            val buffer = ByteArray(32767)
            
            while (isRunning.get()) {
                try {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        val packet = buffer.copyOf(length)
                        listener.onPacketReceived(packet)
                    } else if (length < 0) {
                        throw IOException("TUN FileDescriptor reached EOF")
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e("TunReader", "Error reading from TUN descriptor", e)
                        listener.onError(e)
                    }
                    break
                }
            }
        }, "TunReaderThread").apply {
            priority = Thread.MAX_PRIORITY // High priority for read thread to avoid packet dropping
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        readThread?.interrupt()
        readThread = null
    }
}
