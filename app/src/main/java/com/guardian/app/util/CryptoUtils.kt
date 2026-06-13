package com.guardian.app.util

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtils {
    private val secureRandom = SecureRandom()

    fun sha256(input: ByteArray): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateUnlockCode(): String {
        return String.format("%06d", secureRandom.nextInt(900000) + 100000)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }
}
