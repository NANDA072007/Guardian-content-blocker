package com.guardian.app.walls.wall2.core

import com.guardian.app.walls.wall2.adapter.CapabilityRegistry
import com.guardian.app.walls.wall2.adapter.MetricsRepository
import com.guardian.app.walls.wall2.adapter.OverlayController
import com.guardian.app.walls.wall2.adapter.OverlayStrategy
import com.guardian.app.walls.wall2.event.*
import com.guardian.app.walls.wall2.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionOrchestrator @Inject constructor(
    private val policyEngine: ProtectionPolicyEngine,
    private val capabilityRegistry: CapabilityRegistry,
    private val metricsRepository: MetricsRepository,
    private val overlayController: OverlayController
) {
    private val _sentryState = MutableStateFlow(SentryState.DISABLED)
    val sentryState: StateFlow<SentryState> = _sentryState.asStateFlow()

    private val sessionId: String = UUID.randomUUID().toString()
    private val sessionStartTime: Long = System.currentTimeMillis()
    private var eventCount: Long = 0

    fun onProtectionEvent(event: ProtectionEvent) {
        eventCount++

        val context = buildContext()
        val decision = policyEngine.evaluate(event, context)

        when (decision) {
            is Decision.Block -> {
                overlayController.showBlock(event, OverlayStrategy.Replace)
            }
            is Decision.Warn -> {
                overlayController.showBlock(event, OverlayStrategy.Queue)
            }
            is Decision.Observe -> {}
            is Decision.Log -> {}
            is Decision.Ignore -> {}
            is Decision.Recover -> {
                _sentryState.value = SentryState.RECOVERING
            }
        }
    }

    fun updateSentryState(state: SentryState) {
        _sentryState.value = state
    }

    private fun buildContext(): GuardianContext {
        val capabilities = capabilityRegistry.snapshot()

        return GuardianContext(
            configuration = GuardianConfiguration(
                wall1Enabled = false,
                wall2Enabled = _sentryState.value == SentryState.RUNNING,
                setupComplete = _sentryState.value != SentryState.DISABLED,
                protectionWeights = ProtectionWeights()
            ),
            capabilities = CapabilitySnapshot(
                browserExtraction = capabilities,
                overlay = capabilities["overlay"] ?: CapabilityResult.Unsupported("Unknown"),
                installerDetection = capabilities["installerDetection"] ?: CapabilityResult.Unsupported("Unknown"),
                splitScreenDetection = capabilities["splitScreen"] ?: CapabilityResult.Unsupported("Unknown"),
                timestamp = System.currentTimeMillis()
            ),
            protectionState = ProtectionState(
                sentryState = _sentryState.value,
                isPaused = _sentryState.value == SentryState.PAUSED,
                pauseRemainingMs = if (_sentryState.value == SentryState.PAUSED) 0L else 0L
            ),
            currentSession = SessionInfo(
                sessionId = sessionId,
                startedAt = sessionStartTime,
                eventCount = eventCount
            )
        )
    }
}
