package com.guardian.app.walls.wall2.health

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.adapter.ChromeProfile
import com.guardian.app.walls.wall2.adapter.FirefoxProfile
import com.guardian.app.walls.wall2.adapter.SamsungProfile
import com.guardian.app.walls.wall2.util.TraversalLimits
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class SelfTestRoutine @Inject constructor() {
    private val chromeProfile = ChromeProfile.create()
    private val ffProfile = FirefoxProfile.create()
    private val samsungProfile = SamsungProfile.create()
    private val allBrowserPackages = chromeProfile.packageNames +
            ffProfile.packageNames + samsungProfile.packageNames

    private var lastRootNode: AccessibilityNodeInfo? = null
    private var lastEventTime: Long = 0
    private var lastEventPackage: String? = null
    private var browserPackagesFound = mutableSetOf<String>()

    fun feedRootNode(node: AccessibilityNodeInfo?) {
        if (lastRootNode != null && lastRootNode != node) {
            try { lastRootNode?.recycle() } catch (_: Exception) {}
        }
        lastRootNode = node
    }

    fun feedEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventTime = System.currentTimeMillis()
        lastEventPackage = event.packageName?.toString()
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg in allBrowserPackages) {
            browserPackagesFound.add(pkg)
        }
    }

    fun runDiagnostics(): AccessibilityHealthEngine.SelfTestDetails {
        val chromeDetected = browserPackagesFound.any { it in chromeProfile.packageNames }
        val ffDetected = browserPackagesFound.any { it in ffProfile.packageNames }
        val samsungDetected = browserPackagesFound.any { it in samsungProfile.packageNames }

        val recentEvents = (System.currentTimeMillis() - lastEventTime) < 120_000L
        val packageAvailable = lastEventPackage != null

        val rootAvailable = lastRootNode != null
        val childrenReadable = if (rootAvailable && lastRootNode != null) {
            try {
                val node = lastRootNode!!
                var count = 0
                for (i in 0 until minOf(node.childCount, TraversalLimits.MAX_DEPTH)) {
                    val child = node.getChild(i)
                    if (child != null) {
                        count++
                        child.recycle()
                    }
                }
                count > 0
            } catch (_: Exception) { false }
        } else false

        return AccessibilityHealthEngine.SelfTestDetails(
            chromeDetected = chromeDetected || ffDetected || samsungDetected,
            urlExtracted = false,
            activeWindowReadable = childrenReadable,
            recentEventsReceived = recentEvents
        )
    }
}
