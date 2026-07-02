package com.guardian.app.walls.wall2.health

import android.view.accessibility.AccessibilityEvent

sealed interface HealthSignal {
    val timestamp: Long

    data class SystemPermission(
        override val timestamp: Long,
        val serviceListed: Boolean,
        val serviceEnabled: Boolean,
        val correctPackage: Boolean,
        val correctServiceName: Boolean
    ) : HealthSignal

    data class ServiceConnected(
        override val timestamp: Long,
        val connected: Boolean,
        val connectionCount: Int,
        val lastInterruptTime: Long,
        val interruptCount: Int
    ) : HealthSignal

    data class EventHeartbeat(
        override val timestamp: Long,
        val eventType: Int,
        val packageName: String?,
        val hasSource: Boolean,
        val hasText: Boolean,
        val eventCount: Long,
        val eventsPerMinute: Double
    ) : HealthSignal

    data class ForegroundApp(
        override val timestamp: Long,
        val accessibilityPackage: String?,
        val usageStatsPackage: String?,
        val match: Boolean
    ) : HealthSignal

    data class ActiveWindow(
        override val timestamp: Long,
        val rootAvailable: Boolean,
        val childCount: Int,
        val childrenAccessible: Boolean,
        val hasTextContent: Boolean,
        val hasContentDescription: Boolean
    ) : HealthSignal

    data class UrlExtraction(
        override val timestamp: Long,
        val attemptCount: Long,
        val successCount: Long,
        val lastSuccessTime: Long,
        val lastUrl: String?,
        val extractionMethod: String?
    ) : HealthSignal

    data class BrowserCompatibility(
        override val timestamp: Long,
        val chromeAccessible: Boolean,
        val firefoxAccessible: Boolean,
        val samsungAccessible: Boolean,
        val genericAccessible: Boolean
    ) : HealthSignal

    data class OverlayCapability(
        override val timestamp: Long,
        val overlayShown: Boolean,
        val systemAlertWindowGranted: Boolean,
        val lastShowTime: Long,
        val showSuccessCount: Long,
        val showFailCount: Long
    ) : HealthSignal

    data class OemStatus(
        override val timestamp: Long,
        val manufacturer: String,
        val autoStartEnabled: Boolean?,
        val batteryOptimizationExempt: Boolean?,
        val backgroundRestricted: Boolean?,
        val deviceManufacturer: String
    ) : HealthSignal

    data class SelfTestResult(
        override val timestamp: Long,
        val passed: Boolean,
        val chromeDetected: Boolean,
        val urlExtracted: Boolean,
        val activeWindowReadable: Boolean,
        val recentEventsReceived: Boolean,
        val overallHealthy: Boolean
    ) : HealthSignal
}
