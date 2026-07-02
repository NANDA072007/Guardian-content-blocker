package com.guardian.app.walls.wall2.core

import com.guardian.app.walls.wall2.adapter.DomainRuleProvider
import com.guardian.app.walls.wall2.adapter.AuditTrail
import com.guardian.app.walls.wall2.adapter.AuditEntry
import com.guardian.app.walls.wall2.adapter.MetricsRepository
import com.guardian.app.walls.wall2.event.*
import com.guardian.app.walls.wall2.model.GuardianContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProtectionPolicyEngine @Inject constructor(
    private val domainRuleProvider: DomainRuleProvider,
    private val auditTrail: AuditTrail,
    private val metricsRepository: MetricsRepository
) {
    fun evaluate(event: ProtectionEvent, context: GuardianContext): Decision {
        val startTime = System.nanoTime()

        val decision = when (event) {
            is BrowserEvent -> handleBrowserEvent(event, context)
            is AppEvent -> handleAppEvent(event, context)
            is SearchEvent -> handleSearchEvent(event, context)
            is InstallerEvent -> handleInstallerEvent(event, context)
            is SettingsEvent -> handleSettingsEvent(event, context)
            is HealthEvent -> Decision.Observe
            is BrowserUnsupportedEvent -> Decision.Ignore
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        metricsRepository.recordEventProcessingTime(elapsedMs)
        metricsRepository.recordDecision(decision)
        auditTrail.append(
            AuditEntry(
                timestamp = System.currentTimeMillis(),
                eventId = event.eventId,
                eventType = event.source,
                decision = decision.toString(),
                source = event.source,
                summary = "Severity: ${event.severity}, Decision: $decision"
            )
        )

        return decision
    }

    private fun handleBrowserEvent(event: BrowserEvent, context: GuardianContext): Decision {
        if (context.protectionState.isPaused) return Decision.Ignore

        val domain = event.metadata.domain
        if (domain != null && domainRuleProvider.matches(domain)) {
            return Decision.Block
        }

        // Text-input events: blocked word already matched by BrowserMonitor
        // extractionMethod = "text_input_substring" | "text_input_word"
        val extraction = event.metadata.extractionMethod
        if (extraction.startsWith("text_input")) {
            return Decision.Block
        }

        // Blanket fallback: browser page with canvas/WebView-rendered content
        // where accessibility node text is empty — treat as block
        if (extraction == "browser_blanket") {
            return Decision.Block
        }

        return Decision.Observe
    }

    private fun handleAppEvent(event: AppEvent, context: GuardianContext): Decision {
        if (context.protectionState.isPaused) return Decision.Ignore
        return if (event.metadata.category == "KNOWN_ADULT") {
            Decision.Block
        } else {
            Decision.Observe
        }
    }

    private fun handleSearchEvent(event: SearchEvent, context: GuardianContext): Decision {
        if (context.protectionState.isPaused) return Decision.Ignore
        // Block outright — the user searched for explicitly blocked terms
        return Decision.Block
    }

    private fun handleSettingsEvent(event: SettingsEvent, context: GuardianContext): Decision {
        if (context.protectionState.isPaused) return Decision.Ignore
        // Only block when the settings screen shows Guardian-related dangerous controls
        val danger = event.metadata.detectedDanger.lowercase()
        val isGuardianThreat = danger.contains("accessibility") ||
                danger.contains("device admin") ||
                danger.contains("deactivate") ||
                danger.contains("uninstall") ||
                danger.contains("com.guardian.app") ||
                danger.contains("allow guardian") ||
                danger.contains("guardian permission")
        return if (isGuardianThreat) Decision.Block else Decision.Observe
    }

    private fun handleInstallerEvent(event: InstallerEvent, context: GuardianContext): Decision {
        if (context.protectionState.isPaused) return Decision.Ignore
        val target = event.metadata.targetPackage ?: return Decision.Ignore
        return if (domainRuleProvider.matches(target)) {
            Decision.Block
        } else {
            Decision.Observe
        }
    }
}
