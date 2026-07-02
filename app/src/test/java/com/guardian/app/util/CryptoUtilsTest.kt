package com.guardian.app.util

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CryptoUtilsTest {

    @Test
    fun testSha256() {
        // "test" in SHA-256 is 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        val input = "test".toByteArray(StandardCharsets.UTF_8)
        val expectedHash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        
        val actualHash = CryptoUtils.sha256(input)
        assertEquals("SHA-256 hash does not match expected output", expectedHash, actualHash)
    }

    @Test
    fun testGenerateGuardianCode() {
        val code = CryptoUtils.generateGuardianCode()
        
        assertNotNull("Generated code should not be null", code)
        assertEquals("Generated code should be exactly 9 characters (XXXX-XXXX)", 9, code.length)
        
        val validFormat = "^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$".toRegex()
        assertTrue("Code should match XXXX-XXXX format with valid chars", code.matches(validFormat))
    }

    @Test
    fun testGenerateGuardianCode_randomness() {
        val codes = (1..100).map { CryptoUtils.generateGuardianCode() }.toSet()
        
        assertEquals("100 generated codes should all be unique", 100, codes.size)
    }

    @Test
    fun testConstantTimeEquals() {
        val stringA = "my_secure_password".toByteArray(StandardCharsets.UTF_8)
        val stringB = "my_secure_password".toByteArray(StandardCharsets.UTF_8)
        val stringC = "different_password".toByteArray(StandardCharsets.UTF_8)
        
        assertTrue("Identical byte arrays should return true", CryptoUtils.constantTimeEquals(stringA, stringB))
        assertFalse("Different byte arrays should return false", CryptoUtils.constantTimeEquals(stringA, stringC))
    }
}
