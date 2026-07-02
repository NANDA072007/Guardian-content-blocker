package com.guardian.app.domain

import com.guardian.app.core.AuthenticationManager
import com.guardian.app.core.CooldownTimerManager
import com.guardian.app.util.CryptoUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InitiateCooloffUseCaseTest {

    private lateinit var auth: AuthenticationManager
    private lateinit var cooldownTimer: CooldownTimerManager
    private lateinit var useCase: InitiateCooloffUseCase

    private val testPin = "123456"
    private val testPinHash by lazy { CryptoUtils.sha256(testPin.toByteArray()) }

    @Before
    fun setup() {
        auth = mockk(relaxed = true)
        cooldownTimer = mockk(relaxed = true)
        useCase = InitiateCooloffUseCase(auth, cooldownTimer)

        every { auth.getMasterKeyHash() } returns testPinHash
        every { cooldownTimer.getFailedAttempts() } returns 0
        every { auth.getUninstallUnlockTime() } returns 0L
    }

    @Test
    fun `execute with correct code resets attempts and sets unlock time`() {
        val result = useCase.execute(testPin)

        assertTrue(result is InitiateCooloffUseCase.Result.Success)
        verify { cooldownTimer.resetFailedAttempts() }
        verify { auth.setUninstallUnlockTime(any()) }
    }

    @Test
    fun `execute with wrong code increments failed attempts`() {
        every { cooldownTimer.incrementFailedAttempts() } returns 1

        val result = useCase.execute("wrong")

        assertTrue(result is InitiateCooloffUseCase.Result.WrongCode)
        verify { cooldownTimer.incrementFailedAttempts() }
    }

    @Test
    fun `execute with 5th failed attempt applies 1 minute penalty`() {
        every { cooldownTimer.incrementFailedAttempts() } returns 5

        val result = useCase.execute("wrong")

        assertTrue(result is InitiateCooloffUseCase.Result.PenaltyActive)
        assertEquals(1L, (result as InitiateCooloffUseCase.Result.PenaltyActive).penaltyMinutes)
        verify { cooldownTimer.setCooloff(60_000L) }
    }

    @Test
    fun `execute with 6th failed attempt applies 5 minute penalty`() {
        every { cooldownTimer.incrementFailedAttempts() } returns 6

        val result = useCase.execute("wrong")

        assertTrue(result is InitiateCooloffUseCase.Result.PenaltyActive)
        assertEquals(5L, (result as InitiateCooloffUseCase.Result.PenaltyActive).penaltyMinutes)
        verify { cooldownTimer.setCooloff(300_000L) }
    }

    @Test
    fun `execute with 7th failed attempt applies 30 minute penalty`() {
        every { cooldownTimer.incrementFailedAttempts() } returns 7

        val result = useCase.execute("wrong")

        assertTrue(result is InitiateCooloffUseCase.Result.PenaltyActive)
        assertEquals(30L, (result as InitiateCooloffUseCase.Result.PenaltyActive).penaltyMinutes)
        verify { cooldownTimer.setCooloff(1_800_000L) }
    }

    @Test
    fun `execute with null stored hash returns WrongCode`() {
        every { auth.getMasterKeyHash() } returns null

        val result = useCase.execute(testPin)

        assertTrue(result is InitiateCooloffUseCase.Result.WrongCode)
    }

    @Test
    fun `isCooloffActive returns true when unlock time is in the future`() {
        every { auth.getUninstallUnlockTime() } returns System.currentTimeMillis() + 86_400_000L

        assertTrue(useCase.isCooloffActive())
    }

    @Test
    fun `isCooloffActive returns false when unlock time is 0`() {
        every { auth.getUninstallUnlockTime() } returns 0L

        assertFalse(useCase.isCooloffActive())
    }

    @Test
    fun `getRemainingMs returns 0 when no unlock time set`() {
        every { auth.getUninstallUnlockTime() } returns 0L

        assertEquals(0L, useCase.getRemainingMs())
    }

    @Test
    fun `execute when already cooling off returns AlreadyCoolingOff`() {
        every { auth.getMasterKeyHash() } returns testPinHash
        every { auth.getUninstallUnlockTime() } returns System.currentTimeMillis() + 86_400_000L

        val result = useCase.execute(testPin)

        assertTrue(result is InitiateCooloffUseCase.Result.AlreadyCoolingOff)
    }
}
