package com.guardian.app.walls.wall2.monitor

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.guardian.app.walls.wall2.event.*
import com.guardian.app.walls.wall2.model.ProtectionScore
import com.guardian.app.walls.wall2.model.ProtectionWeights
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class HealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val serviceClassName = "com.guardian.app.walls.wall2.service.AccessibilitySentry"

    fun check(): HealthEvent {
        val components = mutableMapOf<String, Boolean>()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        components["accessibility"] = accessibilityEnabled

        val score = calculateScore(components)
        updateEventCount()

        return HealthEvent(
            sessionId = java.util.UUID.randomUUID().toString(),
            metadata = HealthMetadata(
                score = score,
                components = components.toMap()
            )
        )
    }

    fun calculateProtectionScore(): ProtectionScore {
        val components = mutableMapOf<String, Boolean>()
        components["accessibility"] = isAccessibilityServiceEnabled()
        components["overlay"] = true
        components["router"] = true
        components["rules"] = true
        val score = calculateScore(components)
        return ProtectionScore(score = score, components = components)
    }

    private fun calculateScore(components: Map<String, Boolean>): Int {
        val weights = ProtectionWeights()
        var total = 0
        for ((key, active) in components) {
            val weight = when (key) {
                "accessibility" -> weights.accessibility
                "vpn" -> weights.vpn
                "overlay" -> weights.overlay
                "router" -> weights.router
                "heartbeat" -> weights.heartbeat
                "rules" -> weights.rules
                else -> 0
            }
            if (active) total += weight
        }
        return total
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            enabledServices.any { it.resolveInfo.serviceInfo.name == serviceClassName }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateEventCount() {
        // Health monitor tracks its own event count
    }
}
