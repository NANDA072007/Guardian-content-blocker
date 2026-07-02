package com.guardian.app.walls.wall1

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors device connectivity and debounces transient network drops (e.g. Wi-Fi ↔ Mobile Data handover)
 * by validating connection status within a 1200ms grace window.
 */
@Singleton
class ConnectionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcher: CoroutineDispatcher
) {
    interface Listener {
        fun onConnectionLost()
        fun onConnectionValidated()
        fun onCaptivePortalDetected()
    }

    private var listener: Listener? = null
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var debounceJob: Job? = null

    @Synchronized
    fun startMonitoring(listener: Listener) {
        this.listener = listener
        if (networkCallback != null) return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // Start a 1200ms debounce job. If validated/portal capabilities arrive before it fires, it is cancelled.
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(1200)
                    withContext(Dispatchers.Main) {
                        this@ConnectionMonitor.listener?.onConnectionLost()
                    }
                }
            }

            override fun onAvailable(network: Network) {
                // Wait for capabilities changed verification
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                    // Cancel any active lost connection timer since we need captive portal login immediately
                    debounceJob?.cancel()
                    scope.launch(Dispatchers.Main) {
                        this@ConnectionMonitor.listener?.onCaptivePortalDetected()
                    }
                } else if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    // Valid connection established. Cancel any pending lost connection timer.
                    debounceJob?.cancel()
                    scope.launch(Dispatchers.Main) {
                        this@ConnectionMonitor.listener?.onConnectionValidated()
                    }
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun stopMonitoring() {
        debounceJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Callback might have already been unregistered or connectivity service died
            }
        }
        networkCallback = null
        listener = null
    }
}
