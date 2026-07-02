package com.guardian.app.walls.wall2.model

import com.guardian.app.walls.wall2.event.SentryState

data class GuardianContext(
    val configuration: GuardianConfiguration,
    val capabilities: CapabilitySnapshot,
    val protectionState: ProtectionState,
    val currentSession: SessionInfo
)

data class GuardianConfiguration(
    val wall1Enabled: Boolean,
    val wall2Enabled: Boolean,
    val setupComplete: Boolean,
    val protectionWeights: ProtectionWeights
)

data class CapabilitySnapshot(
    val browserExtraction: Map<String, CapabilityResult>,
    val overlay: CapabilityResult,
    val installerDetection: CapabilityResult,
    val splitScreenDetection: CapabilityResult,
    val timestamp: Long
)

data class ProtectionState(
    val sentryState: SentryState,
    val isPaused: Boolean,
    val pauseRemainingMs: Long
)

data class SessionInfo(
    val sessionId: String,
    val startedAt: Long,
    val eventCount: Long
)

sealed class CapabilityResult {
    data object Supported : CapabilityResult()
    data class Unsupported(val reason: String) : CapabilityResult()
    data class Partial(val limitation: String) : CapabilityResult()
}
