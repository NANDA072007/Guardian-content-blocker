package com.guardian.app.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigurationManagerTest {

    private lateinit var store: EncryptedStateStore
    private lateinit var config: ConfigurationManager

    @Before
    fun setup() {
        store = mockk(relaxed = true)
        config = ConfigurationManager(store)
    }

    @Test
    fun `isSetupComplete delegates to store`() {
        every { store.getBoolean("setup_complete", false) } returns true
        assertTrue(config.isSetupComplete())
    }

    @Test
    fun `setSetupComplete delegates to store`() {
        config.setSetupComplete(true)
        verify { store.putBoolean("setup_complete", true) }
    }

    @Test
    fun `isWall1Enabled delegates to store`() {
        every { store.getBoolean("wall_1_enabled", false) } returns true
        assertTrue(config.isWall1Enabled())
    }

    @Test
    fun `setWall1Enabled delegates to store`() {
        config.setWall1Enabled(true)
        verify { store.putBoolean("wall_1_enabled", true) }
    }

    @Test
    fun `getMasterKeyHash delegates to store`() {
        every { store.getString("master_key_hash") } returns "hash123"
        assertEquals("hash123", config.getMasterKeyHash())
    }

    @Test
    fun `setMasterKeyHash delegates to store`() {
        config.setMasterKeyHash("hash123")
        verify { store.putString("master_key_hash", "hash123") }
    }
}
