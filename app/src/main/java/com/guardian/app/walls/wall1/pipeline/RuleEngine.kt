package com.guardian.app.walls.wall1.pipeline

import android.util.Log
import com.guardian.app.data.repository.GuardianRepository
import com.guardian.app.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor(
    private val repository: GuardianRepository
) {
    private var blocklistLongs = LongArray(0)
    private var isInitialized = false

    init {
        // Asynchronously load the blocklist hashes from the database on startup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hashes = repository.getAllDomainHashes()
                loadBlocklist(hashes)
            } catch (e: Exception) {
                Log.e("RuleEngine", "Failed to load blocklist database", e)
            }
        }
    }

    @Synchronized
    fun loadBlocklist(hashes: List<String>) {
        val startTime = System.currentTimeMillis()
        val longs = LongArray(hashes.size)
        for (i in hashes.indices) {
            longs[i] = hexToLong(hashes[i])
        }
        longs.sort()
        blocklistLongs = longs
        isInitialized = true
        Log.d("RuleEngine", "Loaded ${hashes.size} domain hashes into in-memory array in ${System.currentTimeMillis() - startTime}ms")
    }

    /**
     * Determines whether the given domain matches any blocked rules.
     * Extracts parent suffixes and matches them against the in-memory blocklist.
     */
    fun shouldBlock(domain: String): Boolean {
        if (domain.isEmpty()) return false
        if (!isInitialized) {
            Log.w("RuleEngine", "RuleEngine not fully initialized; fail-safe ALLOW active.")
            return false
        }

        // Fast suffix-hashing check
        var currentSuffix = domain.lowercase().trim().removePrefix("www.").removePrefix("m.")
        while (currentSuffix.isNotEmpty()) {
            if (!currentSuffix.contains('.')) {
                break // Skip TLDs (e.g. "com", "org")
            }
            val hash = CryptoUtils.sha256(currentSuffix.toByteArray())
            val hashLong = hexToLong(hash)
            if (binarySearch(hashLong)) {
                return true
            }
            val nextDot = currentSuffix.indexOf('.')
            if (nextDot == -1) break
            currentSuffix = currentSuffix.substring(nextDot + 1)
        }
        return false
    }

    private fun binarySearch(value: Long): Boolean {
        var low = 0
        var high = blocklistLongs.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = blocklistLongs[mid]
            if (midVal < value) {
                low = mid + 1
            } else if (midVal > value) {
                high = mid - 1
            } else {
                return true // Found
            }
        }
        return false
    }

    private fun hexToLong(hex: String): Long {
        if (hex.length < 16) return 0L
        var result = 0L
        for (i in 0 until 16) {
            val digit = Character.digit(hex[i], 16)
            result = (result shl 4) or (digit.toLong() and 0x0F)
        }
        return result
    }
}
