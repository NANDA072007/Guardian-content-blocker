package com.guardian.app.util

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtils {
    private val secureRandom = SecureRandom()

    fun sha256(input: ByteArray): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateGuardianCode(): String {
        val charPool = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).map { charPool[secureRandom.nextInt(charPool.length)] }.joinToString("")
        return "${code.substring(0, 4)}-${code.substring(4)}"
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return MessageDigest.isEqual(a, b)
    }
}
