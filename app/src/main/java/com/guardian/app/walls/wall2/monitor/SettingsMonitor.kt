package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.event.*
import com.guardian.app.walls.wall2.util.TraversalLimits
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class SettingsMonitor @Inject constructor() {

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oneplus.security",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager"
    )

    private val dangerousPatterns = listOf(
        "use service",
        "accessibility",
        "allow guardian",
        "guardian permission",
        "device admin",
        "deactivate",
        "device administrator",
        "uninstall",
        "com.guardian.app"
    )

    fun canHandle(pkg: String): Boolean = pkg in settingsPackages

    fun process(rootNode: AccessibilityNodeInfo, pkg: String): ProtectionEvent? {
        val isDangerous = nodeContainsDangerousText(rootNode, 0)
        if (!isDangerous) return null

        val detectedDanger = findFirstDangerousText(rootNode, 0) ?: "unknown"

        return SettingsEvent(
            sessionId = java.util.UUID.randomUUID().toString(),
            metadata = SettingsMetadata(
                settingScreen = pkg,
                detectedDanger = detectedDanger
            )
        )
    }

    private fun nodeContainsDangerousText(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > TraversalLimits.MAX_DEPTH) return false
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null && dangerousPatterns.any { text.contains(it) }) return true
            val desc = node.contentDescription?.toString()?.lowercase()
            if (desc != null && dangerousPatterns.any { desc.contains(it) }) return true
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && nodeContainsDangerousText(child, depth + 1)) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun findFirstDangerousText(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > TraversalLimits.MAX_DEPTH) return null
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null) {
                val match = dangerousPatterns.firstOrNull { text.contains(it) }
                if (match != null) return match
            }
            val desc = node.contentDescription?.toString()?.lowercase()
            if (desc != null) {
                val match = dangerousPatterns.firstOrNull { desc.contains(it) }
                if (match != null) return match
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findFirstDangerousText(child, depth + 1)
                    if (result != null) return result
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
