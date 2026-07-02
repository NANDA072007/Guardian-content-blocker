package com.guardian.app.domain

import com.guardian.app.core.CooldownTimerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PauseProtectionUseCaseTest {

    private lateinit var cooldownTimer: CooldownTimerManager
    private lateinit var useCase: PauseProtectionUseCase

    @Before
    fun setup() {
        cooldownTimer = mockk(relaxed = true)
        useCase = PauseProtectionUseCase(cooldownTimer)
    }

    @Test
    fun `execute sets pause with correct duration`() {
        val result = useCase.execute()

        assertEquals(PauseProtectionUseCase.PAUSE_DURATION_MS, result)
        verify { cooldownTimer.setProtectionPaused(any()) }
    }

    @Test
    fun `isPaused delegates to cooldownTimer`() {
        every { cooldownTimer.isProtectionPaused() } returns true
        assertTrue(useCase.isPaused())

        every { cooldownTimer.isProtectionPaused() } returns false
        assertFalse(useCase.isPaused())
    }

    @Test
    fun `getRemainingMs delegates to cooldownTimer`() {
        every { cooldownTimer.getRemainingPauseMs() } returns 120000L
        assertEquals(120000L, useCase.getRemainingMs())

        every { cooldownTimer.getRemainingPauseMs() } returns 0L
        assertEquals(0L, useCase.getRemainingMs())
    }
}
