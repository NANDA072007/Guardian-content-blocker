package com.guardian.app.walls.wall2.router

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.event.EventDebouncer
import com.guardian.app.walls.wall2.event.ProtectionEvent
import com.guardian.app.walls.wall2.event.BrowserEvent
import com.guardian.app.walls.wall2.event.BrowserMetadata
import com.guardian.app.walls.wall2.event.Decision
import com.guardian.app.walls.wall2.monitor.*
import com.guardian.app.walls.wall2.util.TraversalLimits
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class AccessibilityEventRouter @Inject constructor(
    private val debouncer: EventDebouncer,
    private val browserMonitor: BrowserMonitor,
    private val settingsMonitor: SettingsMonitor,
    private val appMonitor: AppMonitor,
    private val searchMonitor: SearchMonitor,
    private val installerMonitor: InstallerMonitor,
    private val incognitoDetector: IncognitoDetector,
    private val healthMonitor: HealthMonitor
) {
    private var lastActivePackage: String? = null

    private val installerPackages = setOf(
        "com.android.vending",
        "com.google.android.finsky",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    fun routeEvent(event: AccessibilityEvent): ProtectionEvent? {
        if (event.packageName == null) return null

        val pkg = event.packageName.toString()
        lastActivePackage = pkg

        return when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event, pkg)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (!debouncer.shouldProcess("text_input", 2000L)) return null
                handleTextChanged(event, pkg)
            }
            else -> null
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent, pkg: String): ProtectionEvent? {
        if (!debouncer.shouldProcess("window_$pkg")) return null

        val rootNode: AccessibilityNodeInfo
        try {
            rootNode = event.source ?: return null
        } catch (_: IllegalStateException) {
            return null
        }

        return try {
            val totalNodes = countNodes(rootNode)
            if (totalNodes > TraversalLimits.MAX_NODES) return null

            when {
                settingsMonitor.canHandle(pkg) -> {
                    settingsMonitor.process(rootNode, pkg)
                }
                appMonitor.canHandle(pkg) -> {
                    val appEvent = appMonitor.process(rootNode, pkg)
                    if (appEvent != null) appEvent else checkIncognito(rootNode)
                }
                browserMonitor.canHandle(pkg) -> {
                    val browserEvent = browserMonitor.process(rootNode, pkg)
                    if (browserEvent != null) browserEvent else {
                        searchMonitor.process(rootNode)
                    }
                }
                pkg in installerPackages -> {
                    installerMonitor.process(rootNode)
                }
                else -> checkIncognito(rootNode)
            }
        } finally {
            try { rootNode.recycle() } catch (_: Exception) {}
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent, pkg: String): ProtectionEvent? {
        val texts = event.text ?: return null
        if (texts.isEmpty()) return null
        val fullText = texts.joinToString(" ").lowercase()
        return browserMonitor.processText(fullText, pkg)
    }

    private fun checkIncognito(rootNode: AccessibilityNodeInfo): ProtectionEvent? {
        if (!incognitoDetector.isIncognito(rootNode)) return null
        return BrowserEvent(
            sessionId = java.util.UUID.randomUUID().toString(),
            metadata = BrowserMetadata(
                url = null,
                domain = null,
                browserPackage = "incognito",
                extractionMethod = "incognito_detected"
            )
        )
    }

    fun getHealthEvent(): ProtectionEvent? = healthMonitor.check()

    fun getLastActivePackage(): String? = lastActivePackage

    private fun countNodes(node: AccessibilityNodeInfo): Int {
        var count = 0
        try {
            for (i in 0 until node.childCount) {
                if (count > TraversalLimits.MAX_NODES) return count
                val child = node.getChild(i)
                if (child != null) {
                    count += countNodes(child) + 1
                    child.recycle()
                }
            }
        } catch (_: Exception) {}
        return count
    }
}
