package com.guardian.app.ui.customer

sealed class Category(val displayName: String) {
    data object Bug : Category("Bug Report")
    data object Feature : Category("Feature Request")
    data object Suggestion : Category("Suggestion")
    data object Other : Category("Other")

    companion object {
        val entries = listOf(Bug, Feature, Suggestion, Other)
    }
}

enum class Severity(val displayName: String) {
    MINOR("Minor"),
    MAJOR("Major"),
    CRITICAL("Critical")
}

data class CustomerReport(
    val category: Category = Category.Bug,
    val severity: Severity = Severity.MAJOR,
    val description: String = "",
    val stepsToReproduce: String = "",
    val expectedBehavior: String = "",
    val actualBehavior: String = "",
    val deviceInfo: DeviceInfo? = null,
    val recentEvents: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class DeviceInfo(
    val osVersion: String,
    val apiLevel: Int,
    val device: String,
    val manufacturer: String,
    val appVersion: String,
    val buildNumber: Int,
    val vpnState: String,
    val accessibilityState: String,
    val adminState: String,
    val healthLevel: String,
    val batteryOptimizationIgnored: Boolean,
    val restrictedSettingsBlocking: Boolean,
    val overlayPermissionGranted: Boolean,
    val uptimeHours: Long,
    val lastRecovery: String?
) {
    fun toFormattedString(): String = buildString {
        appendLine("=== Device Info ===")
        appendLine("App: Guardian v$appVersion (build $buildNumber)")
        appendLine("OS: Android $osVersion (API $apiLevel)")
        appendLine("Device: $manufacturer $device")
        appendLine()
        appendLine("=== Protection ===")
        appendLine("VPN: $vpnState")
        appendLine("Accessibility: $accessibilityState")
        appendLine("Device Admin: $adminState")
        appendLine("Health: $healthLevel")
        appendLine("Battery Optimization Ignored: $batteryOptimizationIgnored")
        appendLine("Restricted Settings Blocking: $restrictedSettingsBlocking")
        appendLine("Overlay Permission: $overlayPermissionGranted")
        appendLine("Uptime: ${uptimeHours}h")
        if (lastRecovery != null) {
            appendLine()
            appendLine("=== Last Recovery ===")
            appendLine(lastRecovery)
        }
    }
}
