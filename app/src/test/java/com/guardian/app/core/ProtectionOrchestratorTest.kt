package com.guardian.app.core

import android.content.Context
import android.content.Intent
import com.guardian.app.walls.wall1.ConnectionMonitor
import com.guardian.app.walls.wall1.VpnController
import com.guardian.app.walls.wall1.VpnState
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProtectionOrchestratorTest {

    private lateinit var context: Context
    private lateinit var config: ConfigurationManager
    private lateinit var vpnController: VpnController
    private lateinit var connectionMonitor: ConnectionMonitor
    private lateinit var orchestrator: ProtectionOrchestrator
    
    private val vpnStatusFlow = MutableStateFlow(VpnState.STOPPED)

    @Before
    fun setup() {
        mockkConstructor(Intent::class)
        // Intent.setAction() returns the Intent itself. We must return the mocked receiver (self).
        every { anyConstructed<Intent>().setAction(any()) } answers { self as Intent }


        context = mockk(relaxed = true)
        config = mockk(relaxed = true)
        vpnController = mockk(relaxed = true)
        connectionMonitor = mockk(relaxed = true)

        every { vpnController.status() } returns vpnStatusFlow

        orchestrator = ProtectionOrchestrator(context, config, vpnController, connectionMonitor)
    }

    @After
    fun tearDown() {
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `startProtection triggers settings update and starts engines`() {
        orchestrator.startProtection()

        verify { config.setWall1Enabled(true) }
        verify { vpnController.start() }
        verify { connectionMonitor.startMonitoring(any()) }
        assertEquals(VpnState.RUNNING, orchestrator.protectionState.value)
    }

    @Test
    fun `stopProtection stops monitoring and stops vpn`() {
        // Setup initial running state to avoid early exit guard
        orchestrator.updateState(VpnState.RUNNING)

        orchestrator.stopProtection()

        verify { config.setWall1Enabled(false) }
        verify { connectionMonitor.stopMonitoring() }
        verify { vpnController.stop() }
        assertEquals(VpnState.STOPPED, orchestrator.protectionState.value)
    }

    @Test
    fun `updateState propagates values correctly`() {
        orchestrator.updateState(VpnState.RECONNECTING)
        assertEquals(VpnState.RECONNECTING, orchestrator.protectionState.value)
        verify { vpnController.updateStatus(VpnState.RECONNECTING) }
    }
}
