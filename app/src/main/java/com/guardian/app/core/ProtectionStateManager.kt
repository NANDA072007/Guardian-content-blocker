package com.guardian.app.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionStateManager @Inject constructor(
    private val config: ConfigurationManager
) {

    fun isSetupComplete(): Boolean = config.isSetupComplete()
    fun setSetupComplete(isComplete: Boolean) = config.setSetupComplete(isComplete)

    fun isWall1Enabled(): Boolean = config.isWall1Enabled()
    fun setWall1Enabled(enabled: Boolean) = config.setWall1Enabled(enabled)

    fun isWall2Enabled(): Boolean = config.isWall2Enabled()
    fun setWall2Enabled(enabled: Boolean) = config.setWall2Enabled(enabled)

    fun isOemOptimizationAcknowledged(): Boolean = config.isOemOptimizationAcknowledged()
    fun setOemOptimizationAcknowledged(acknowledged: Boolean) = config.setOemOptimizationAcknowledged(acknowledged)
}

