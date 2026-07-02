package com.guardian.app.core

import com.guardian.app.util.TimeProvider
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CooldownTimerManagerTest {

    private lateinit var config: ConfigurationManager
    private lateinit var timeProvider: TimeProvider
    private lateinit var manager: CooldownTimerManager

    @Before
    fun setup() {
        config = mockk(relaxed = true)
        timeProvider = mockk(relaxed = true)
        manager = CooldownTimerManager(config, timeProvider)
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_000L
    }

    @Test
    fun `getFailedAttempts returns 0 by default`() {
        every { config.getFailedAttempts() } returns 0
        assertEquals(0, manager.getFailedAttempts())
    }

    @Test
    fun `incrementFailedAttempts increases count`() {
        every { config.getFailedAttempts() } returns 3
        val result = manager.incrementFailedAttempts()

        assertEquals(4, result)
        verify { config.setFailedAttempts(4) }
    }

    @Test
    fun `resetFailedAttempts clears all state`() {
        manager.resetFailedAttempts()

        verify { config.setFailedAttempts(0) }
        verify { config.setCooloffEndTime(0L) }
        verify { config.setCooloffStartUptime(0L) }
        verify { config.setCooloffDuration(0L) }
    }

    @Test
    fun `setCooloff stores correct values`() {
        every { timeProvider.elapsedRealtime() } returns 1000L
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_000L

        manager.setCooloff(60_000L)

        verify { config.setCooloffEndTime(1_700_000_060_000L) }
        verify { config.setCooloffStartUptime(1000L) }
        verify { config.setCooloffDuration(60_000L) }
    }

    @Test
    fun `getRemainingCooloffMs returns 0 when no cooloff set`() {
        every { config.getCooloffEndTime() } returns 0L
        every { config.getCooloffStartUptime() } returns 0L
        every { config.getCooloffDuration() } returns 0L

        assertEquals(0L, manager.getRemainingCooloffMs())
    }

    @Test
    fun `getRemainingCooloffMs returns positive when cooloff active`() {
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_000L
        every { config.getCooloffEndTime() } returns 1_700_000_060_000L
        every { config.getCooloffStartUptime() } returns 1000L
        every { config.getCooloffDuration() } returns 60_000L
        every { timeProvider.elapsedRealtime() } returns 2000L

        val remaining = manager.getRemainingCooloffMs()
        assertTrue("Cooloff should have positive remaining time", remaining > 0)
    }

    @Test
    fun `isProtectionPaused returns false when not paused`() {
        every { config.getProtectionPausedUntil() } returns 0L
        assertFalse(manager.isProtectionPaused())
    }

    @Test
    fun `isProtectionPaused returns true when pause is active`() {
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_000L
        every { config.getProtectionPausedUntil() } returns 1_700_000_300_000L
        assertTrue(manager.isProtectionPaused())
    }

    @Test
    fun `isProtectionPaused returns false and clears when expired`() {
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_001L
        every { config.getProtectionPausedUntil() } returns 1_700_000_000_000L
        assertFalse(manager.isProtectionPaused())
        verify { config.setProtectionPausedUntil(0L) }
    }

    @Test
    fun `getRemainingPauseMs returns 0 when not paused`() {
        every { config.getProtectionPausedUntil() } returns 0L
        assertEquals(0L, manager.getRemainingPauseMs())
    }

    @Test
    fun `getRemainingPauseMs returns positive when paused`() {
        every { timeProvider.currentTimeMillis() } returns 1_700_000_000_000L
        every { config.getProtectionPausedUntil() } returns 1_700_000_300_000L
        val remaining = manager.getRemainingPauseMs()
        assertEquals(300_000L, remaining)
    }
}
