package com.guardian.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityManagerTest {

    private lateinit var securityManager: SecurityManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        securityManager = SecurityManager(context)
        // Reset state for testing
        securityManager.setSetupComplete(false)
        securityManager.setWall1Enabled(false)
        securityManager.setWall2Enabled(false)
        securityManager.resetFailedAttempts()
    }

    @Test
    fun testSetupComplete() {
        assertFalse(securityManager.isSetupComplete())
        securityManager.setSetupComplete(true)
        assertTrue(securityManager.isSetupComplete())
    }

    @Test
    fun testWallsToggle() {
        assertFalse(securityManager.isWall1Enabled())
        assertFalse(securityManager.isWall2Enabled())

        securityManager.setWall1Enabled(true)
        assertTrue(securityManager.isWall1Enabled())
        assertFalse(securityManager.isWall2Enabled())

        securityManager.setWall2Enabled(true)
        assertTrue(securityManager.isWall1Enabled())
        assertTrue(securityManager.isWall2Enabled())
    }

    @Test
    fun testFailedAttemptsAndCooloff() {
        assertEquals(0, securityManager.getFailedAttempts())
        
        val count = securityManager.incrementFailedAttempts()
        assertEquals(1, count)
        assertEquals(1, securityManager.getFailedAttempts())
        
        securityManager.setCooloff(5000L) // 5 seconds
        val remaining = securityManager.getRemainingCooloffMs()
        assertTrue("Remaining cooloff should be close to 5000ms", remaining > 4000L && remaining <= 5000L)
        
        securityManager.resetFailedAttempts()
        assertEquals(0, securityManager.getFailedAttempts())
        assertEquals(0L, securityManager.getRemainingCooloffMs())
    }
}
