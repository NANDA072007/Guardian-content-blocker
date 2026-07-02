package com.guardian.app.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProtectionStateManagerTest {

    private lateinit var config: ConfigurationManager
    private lateinit var manager: ProtectionStateManager

    @Before
    fun setup() {
        config = mockk(relaxed = true)
        manager = ProtectionStateManager(config)
    }

    @Test
    fun `isSetupComplete returns false by default`() {
        every { config.isSetupComplete() } returns false
        assertFalse(manager.isSetupComplete())
    }

    @Test
    fun `setSetupComplete delegates to config`() {
        manager.setSetupComplete(true)
        verify { config.setSetupComplete(true) }
    }

    @Test
    fun `isWall1Enabled returns false by default`() {
        every { config.isWall1Enabled() } returns false
        assertFalse(manager.isWall1Enabled())
    }

    @Test
    fun `setWall1Enabled delegates to config`() {
        manager.setWall1Enabled(true)
        verify { config.setWall1Enabled(true) }
    }

    @Test
    fun `isWall2Enabled returns false by default`() {
        every { config.isWall2Enabled() } returns false
        assertFalse(manager.isWall2Enabled())
    }

    @Test
    fun `setWall2Enabled delegates to config`() {
        manager.setWall2Enabled(true)
        verify { config.setWall2Enabled(true) }
    }

    @Test
    fun `isOemOptimizationAcknowledged returns false by default`() {
        every { config.isOemOptimizationAcknowledged() } returns false
        assertFalse(manager.isOemOptimizationAcknowledged())
    }

    @Test
    fun `setOemOptimizationAcknowledged delegates to config`() {
        manager.setOemOptimizationAcknowledged(true)
        verify { config.setOemOptimizationAcknowledged(true) }
    }
}
