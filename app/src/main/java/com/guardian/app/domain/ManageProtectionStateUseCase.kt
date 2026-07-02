package com.guardian.app.domain

import com.guardian.app.core.ProtectionOrchestrator
import com.guardian.app.core.ProtectionStateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain use case to query and manipulate the status of our safety walls.
 * Delegates lifecycle execution commands directly to [ProtectionOrchestrator].
 */
@Singleton
class ManageProtectionStateUseCase @Inject constructor(
    private val protectionState: ProtectionStateManager,
    private val orchestrator: ProtectionOrchestrator
) {
    fun isWall1Enabled(): Boolean = protectionState.isWall1Enabled()
    fun isWall2Enabled(): Boolean = protectionState.isWall2Enabled()
    fun isSetupComplete(): Boolean = protectionState.isSetupComplete()
    fun isOemOptimizationAcknowledged(): Boolean = protectionState.isOemOptimizationAcknowledged()

    fun setSetupComplete(complete: Boolean) = protectionState.setSetupComplete(complete)

    fun setWall1Enabled(enabled: Boolean) {
        protectionState.setWall1Enabled(enabled)
    }

    fun setWall2Enabled(enabled: Boolean) {
        protectionState.setWall2Enabled(enabled)
    }

    fun setOemOptimizationAcknowledged(acknowledged: Boolean) {
        protectionState.setOemOptimizationAcknowledged(acknowledged)
    }

    fun startVpn() {
        orchestrator.startProtection()
    }

    fun stopVpn() {
        orchestrator.stopProtection()
    }
}
