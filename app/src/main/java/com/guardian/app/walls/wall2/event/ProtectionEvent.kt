package com.guardian.app.walls.wall2.event

import java.util.UUID

sealed interface ProtectionEvent {
    val eventId: UUID
    val sessionId: String
    val timestamp: Long
    val source: String
    val severity: Severity
    val metadata: EventMetadata
}

enum class Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

sealed interface EventMetadata

data class BrowserMetadata(
    val url: String?,
    val domain: String?,
    val browserPackage: String,
    val extractionMethod: String
) : EventMetadata

data class AppMetadata(
    val packageName: String,
    val label: String?,
    val category: String
) : EventMetadata

data class SettingsMetadata(
    val settingScreen: String,
    val detectedDanger: String
) : EventMetadata

data class SearchMetadata(
    val query: String,
    val engine: String
) : EventMetadata

data class InstallerMetadata(
    val installerPackage: String,
    val targetPackage: String?
) : EventMetadata

data class HealthMetadata(
    val score: Int,
    val components: Map<String, Boolean>
) : EventMetadata

data class BrowserUnsupportedMetadata(
    val browserPackage: String,
    val reason: String
) : EventMetadata

data class BrowserEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "BrowserMonitor",
    override val severity: Severity = Severity.HIGH,
    override val metadata: BrowserMetadata
) : ProtectionEvent

data class AppEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "AppMonitor",
    override val severity: Severity = Severity.HIGH,
    override val metadata: AppMetadata
) : ProtectionEvent

data class SettingsEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "SettingsMonitor",
    override val severity: Severity = Severity.CRITICAL,
    override val metadata: SettingsMetadata
) : ProtectionEvent

data class SearchEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "SearchMonitor",
    override val severity: Severity = Severity.MEDIUM,
    override val metadata: SearchMetadata
) : ProtectionEvent

data class InstallerEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "InstallerMonitor",
    override val severity: Severity = Severity.HIGH,
    override val metadata: InstallerMetadata
) : ProtectionEvent

data class HealthEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "HealthMonitor",
    override val severity: Severity = Severity.INFO,
    override val metadata: HealthMetadata
) : ProtectionEvent

data class BrowserUnsupportedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val sessionId: String,
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: String = "BrowserMonitor",
    override val severity: Severity = Severity.LOW,
    override val metadata: BrowserUnsupportedMetadata
) : ProtectionEvent
