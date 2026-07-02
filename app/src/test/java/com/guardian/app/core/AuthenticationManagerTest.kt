package com.guardian.app.core

import com.guardian.app.util.CryptoUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthenticationManagerTest {

    private lateinit var config: ConfigurationManager
    private lateinit var auth: AuthenticationManager

    @Before
    fun setup() {
        config = mockk(relaxed = true)
        auth = AuthenticationManager(config)
    }

    @Test
    fun `getMasterKeyHash returns null by default`() {
        every { config.getMasterKeyHash() } returns null
        assertNull(auth.getMasterKeyHash())
    }

    @Test
    fun `setMasterKeyHash delegates to config`() {
        auth.setMasterKeyHash("abc123")
        verify { config.setMasterKeyHash("abc123") }
    }

    @Test
    fun `getTrustedPersonContact returns null by default`() {
        every { config.getTrustedPersonContact() } returns null
        assertNull(auth.getTrustedPersonContact())
    }

    @Test
    fun `setTrustedPersonContact delegates to config`() {
        auth.setTrustedPersonContact("+1234567890")
        verify { config.setTrustedPersonContact("+1234567890") }
    }

    @Test
    fun `getUninstallUnlockTime returns 0 by default`() {
        every { config.getUninstallUnlockTime() } returns 0L
        assertEquals(0L, auth.getUninstallUnlockTime())
    }

    @Test
    fun `setUninstallUnlockTime delegates to config`() {
        auth.setUninstallUnlockTime(12345L)
        verify { config.setUninstallUnlockTime(12345L) }
    }

    @Test
    fun `verifyMasterKey returns false when no hash stored`() {
        every { config.getMasterKeyHash() } returns null
        assertFalse(auth.verifyMasterKey("123456"))
    }

    @Test
    fun `verifyMasterKey returns true for correct key`() {
        val correctPin = "123456"
        val hash = CryptoUtils.sha256(correctPin.toByteArray())
        every { config.getMasterKeyHash() } returns hash

        assertTrue(auth.verifyMasterKey(correctPin))
    }

    @Test
    fun `verifyMasterKey returns false for wrong key`() {
        val hash = CryptoUtils.sha256("123456".toByteArray())
        every { config.getMasterKeyHash() } returns hash

        assertFalse(auth.verifyMasterKey("999999"))
    }
}
