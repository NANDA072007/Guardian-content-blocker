package com.guardian.app.walls.wall1

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var listener: ConnectionMonitor.Listener
    private lateinit var connectionMonitor: ConnectionMonitor
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkConstructor(NetworkRequest.Builder::class)
        val mockRequest = mockk<NetworkRequest>()
        
        // Mock the builder pattern methods for NetworkRequest.Builder returning self (the builder instance)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } answers { self as NetworkRequest.Builder }

        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockRequest

        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        connectionMonitor = ConnectionMonitor(context, testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkConstructor(NetworkRequest.Builder::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `startMonitoring registers network callback`() {
        connectionMonitor.startMonitoring(listener)
        verify { connectivityManager.registerNetworkCallback(any(), any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `stopMonitoring unregisters network callback`() {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit

        connectionMonitor.startMonitoring(listener)
        connectionMonitor.stopMonitoring()

        verify { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
    }

    @Test
    fun `onLost invokes listener after 1200ms when not cancelled`() = runTest(testDispatcher) {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit

        connectionMonitor.startMonitoring(listener)
        val mockNetwork = mockk<Network>()

        // Trigger onLost
        callbackSlot.captured.onLost(mockNetwork)

        // Advance time before delay finishes
        advanceTimeBy(1000)
        verify(exactly = 0) { listener.onConnectionLost() }

        // Advance time past 1200ms grace window
        advanceTimeBy(300)
        verify(exactly = 1) { listener.onConnectionLost() }
    }

    @Test
    fun `onCapabilitiesChanged with validated network cancels lost timer`() = runTest(testDispatcher) {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit

        connectionMonitor.startMonitoring(listener)
        val mockNetwork = mockk<Network>()

        // Trigger onLost
        callbackSlot.captured.onLost(mockNetwork)

        // Trigger capabilities changed back to validated before 1200ms grace window
        advanceTimeBy(1000)
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns false

        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, capabilities)

        // Advance time past 1200ms
        advanceTimeBy(300)

        // Verification: onConnectionLost should NEVER be called, onConnectionValidated called
        verify(exactly = 0) { listener.onConnectionLost() }
        verify(exactly = 1) { listener.onConnectionValidated() }
    }

    @Test
    fun `onCapabilitiesChanged with captive portal cancels lost timer`() = runTest(testDispatcher) {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit

        connectionMonitor.startMonitoring(listener)
        val mockNetwork = mockk<Network>()

        // Trigger onLost
        callbackSlot.captured.onLost(mockNetwork)

        // Trigger capabilities changed captive portal
        advanceTimeBy(500)
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns true

        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, capabilities)

        // Advance time past 1200ms
        advanceTimeBy(1000)

        // Verification: onConnectionLost should NEVER be called, onCaptivePortalDetected called
        verify(exactly = 0) { listener.onConnectionLost() }
        verify(exactly = 1) { listener.onCaptivePortalDetected() }
    }
}
