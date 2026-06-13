package com.guardian.app.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.data.SecurityManager
import com.guardian.app.data.db.DatabaseHelper
import com.guardian.app.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AccessibilitySentry : AccessibilityService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var securityManager: SecurityManager

    private val browserUrlBarIds = mapOf(
        "com.android.chrome" to arrayOf("com.android.chrome:id/url_bar"),
        "com.brave.browser" to arrayOf("com.brave.browser:id/url_bar"),
        "com.duckduckgo.mobile.android" to arrayOf(
            "com.duckduckgo.mobile.android:id/omnibar_text_input"
        ),
        "org.mozilla.firefox" to arrayOf(
            "org.mozilla.firefox:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
        )
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        dbHelper = DatabaseHelper.getInstance(this)
        securityManager = SecurityManager(this)
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf(
                "com.android.chrome", 
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.duckduckgo.mobile.android",
                "com.android.settings"
            )
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d("AccessibilitySentry", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName ?: return
        val rootNode = rootInActiveWindow ?: return

        if (pkg == "com.android.settings") {
            handleSettingsProtection(rootNode)
            return
        }

        val viewIds = browserUrlBarIds[pkg] ?: return
        for (viewId in viewIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val urlText = node.text?.toString()
                node.recycle()
                if (!urlText.isNullOrEmpty()) {
                    scope.launch { checkAndBlockUrl(urlText) }
                }
            }
        }
    }

    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        // 1. Check if user is on a legitimate setup screen by looking for specific setup phrases
        val whitelistPhrases = listOf(
            "Guardian requires Device Admin to prevent uninstallation.", // Wall 4 setup
            "Allow display over other apps", // Stock Android overlay setup
            "Appear on top" // Samsung overlay setup
        )
        
        var isWhitelisted = false
        for (phrase in whitelistPhrases) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(phrase)
            if (nodes.isNotEmpty()) {
                isWhitelisted = true
            }
            nodes.forEach { it.recycle() }
            if (isWhitelisted) break
        }

        if (isWhitelisted) {
            return // Let them grant the permission!
        }

        // 2. Standard protection logic: kick out if they are on App Info or Accessibility disable screens
        val guardianNodes = rootNode.findAccessibilityNodeInfosByText("Guardian")
        val sentryNodes = rootNode.findAccessibilityNodeInfosByText("AccessibilitySentry")
        val triggered = guardianNodes.isNotEmpty() || sentryNodes.isNotEmpty()
        guardianNodes.forEach { it.recycle() }
        sentryNodes.forEach { it.recycle() }

        if (triggered) {
            val unlockTime = securityManager.getUninstallUnlockTime()
            val currentTime = System.currentTimeMillis()

            if (currentTime < unlockTime || unlockTime == 0L) {
                Log.d("AccessibilitySentry", "Self-Protection triggered! Kicking out of Settings.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                val intent = Intent("com.guardian.app.ACTION_SHOW_BLOCK_OVERLAY")
                sendBroadcast(intent)
            }
        }
    }

    private fun checkAndBlockUrl(url: String) {
        val domain = extractDomain(url)
        val domainHash = CryptoUtils.sha256(domain.lowercase().toByteArray())
        
        if (dbHelper.isDomainBlocked(domainHash)) {
            Log.d("AccessibilitySentry", "Blocked domain detected: $domain")
            dbHelper.logBlockEvent("WALL_2")
            val intent = Intent("com.guardian.app.ACTION_SHOW_BLOCK_OVERLAY")
            sendBroadcast(intent)
        }
    }

    private fun extractDomain(url: String): String {
        var domain = url.lowercase()
        if (domain.startsWith("http://")) domain = domain.substring(7)
        if (domain.startsWith("https://")) domain = domain.substring(8)
        if (domain.startsWith("www.")) domain = domain.substring(4)
        val slashIndex = domain.indexOf('/')
        if (slashIndex != -1) {
            domain = domain.substring(0, slashIndex)
        }
        val colonIndex = domain.indexOf(':')
        if (colonIndex != -1) {
            domain = domain.substring(0, colonIndex)
        }
        return domain
    }

    override fun onInterrupt() {
        Log.d("AccessibilitySentry", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
