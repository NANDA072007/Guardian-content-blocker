package com.guardian.app.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
    private var blockOverlayManager: com.guardian.app.ui.overlay.BlockOverlayManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Comprehensive browser URL bar IDs covering 15+ browsers
    private val browserUrlBarIds = mapOf(
        // Google Chrome & variants
        "com.android.chrome" to arrayOf("com.android.chrome:id/url_bar"),
        "com.chrome.beta" to arrayOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev" to arrayOf("com.chrome.dev:id/url_bar"),
        "com.chrome.canary" to arrayOf("com.chrome.canary:id/url_bar"),
        // Brave
        "com.brave.browser" to arrayOf("com.brave.browser:id/url_bar"),
        "com.brave.browser_beta" to arrayOf("com.brave.browser_beta:id/url_bar"),
        // DuckDuckGo
        "com.duckduckgo.mobile.android" to arrayOf(
            "com.duckduckgo.mobile.android:id/omnibar_text_input",
            "com.duckduckgo.mobile.android:id/omnibarTextInput"
        ),
        // Firefox & variants
        "org.mozilla.firefox" to arrayOf(
            "org.mozilla.firefox:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
        ),
        "org.mozilla.firefox_beta" to arrayOf(
            "org.mozilla.firefox_beta:id/url_bar",
            "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"
        ),
        "org.mozilla.fenix" to arrayOf(
            "org.mozilla.fenix:id/url_bar",
            "org.mozilla.fenix:id/mozac_browser_toolbar_url_view"
        ),
        "org.mozilla.focus" to arrayOf(
            "org.mozilla.focus:id/url_bar",
            "org.mozilla.focus:id/mozac_browser_toolbar_url_view"
        ),
        // Microsoft Edge
        "com.microsoft.emmx" to arrayOf(
            "com.microsoft.emmx:id/url_bar",
            "com.microsoft.emmx:id/url_bar_title"
        ),
        // Opera & Opera Mini
        "com.opera.browser" to arrayOf(
            "com.opera.browser:id/url_field",
            "com.opera.browser:id/url_bar_address_view"
        ),
        "com.opera.mini.native" to arrayOf(
            "com.opera.mini.native:id/url_field",
            "com.opera.mini.native:id/url_bar_address_view"
        ),
        "com.opera.gx" to arrayOf("com.opera.gx:id/url_field"),
        // Samsung Internet
        "com.sec.android.app.sbrowser" to arrayOf(
            "com.sec.android.app.sbrowser:id/url_bar_text",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text"
        ),
        // UC Browser
        "com.UCMobile.intl" to arrayOf("com.UCMobile.intl:id/address_bar_edit_text"),
        // Mi Browser (Xiaomi)
        "com.mi.globalbrowser" to arrayOf("com.mi.globalbrowser:id/url"),
        // Kiwi Browser
        "com.kiwibrowser.browser" to arrayOf("com.kiwibrowser.browser:id/url_bar"),
        // Vivaldi
        "com.vivaldi.browser" to arrayOf("com.vivaldi.browser:id/url_bar"),
        // Ecosia
        "com.ecosia.android" to arrayOf("com.ecosia.android:id/url_bar"),
        // Tor Browser
        "org.torproject.torbrowser" to arrayOf(
            "org.torproject.torbrowser:id/url_bar",
            "org.torproject.torbrowser:id/mozac_browser_toolbar_url_view"
        )
    )

    // Settings package names per OEM
    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",   // Samsung
        "com.miui.securitycenter",         // Xiaomi
        "com.coloros.safecenter",           // OPPO/Realme
        "com.oneplus.security",            // OnePlus
        "com.vivo.permissionmanager",      // Vivo
        "com.huawei.systemmanager"         // Huawei
    )

    // Production-grade keyword detection list
    // Category 1: Exact domain names (always block even if count = 1)
    private val instantBlockWords = setOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "tube8", "brazzers", "onlyfans", "stripchat", "chaturbate",
        "livejasmin", "cam4", "flirt4free", "spankbang", "hentai",
        "rule34", "e-hentai", "nhentai", "gelbooru", "danbooru",
        "xnxx", "bangbros", "naughtyamerica", "realitykings",
        "youjizz", "tnaflix", "beeg", "eporner", "ixxx"
    )

    // Category 2: Signal words (need count >= 2 to block, to prevent false positives)
    private val signalWords = setOf(
        "porn", "xxx", "nude", "naked", "nsfw", "hentai",
        "milf", "anal", "blowjob", "handjob", "creampie",
        "threesome", "orgasm", "masturbat", "dildo", "vibrator",
        "bondage", "fetish", "escort", "camgirl", "webcam",
        "stripclub", "lapdance", "erotic", "orgies",
        // Anti-evasion: common misspellings/leetspeak
        "p0rn", "pr0n", "p o r n", "pron", "prn",
        // Non-English common terms
        "bokep", "jav", "desi mms"
    )

    // Words that should NOT trigger blocking even if they contain signal substrings
    private val safeWords = setOf(
        "middlesex", "sussex", "essex", "sexism", "sexist",
        "bisexual", "homosexual", "heterosexual", "asexual",
        "unisex", "sextant", "sextet", "sexton",
        "anorexia", "dyslexia", "sexuality", "sexual health",
        "sexual harassment", "sexual assault", "sex education",
        "sex trafficking", "sexual orientation"
    )

    // Incognito / Private browsing triggers
    private val incognitoTriggerWords = setOf(
        "incognito tab", "new incognito", "private browsing",
        "inprivate", "secret mode", "private tab"
    )

    // Known popular VPN app packages to block
    private val vpnPackages = setOf(
        "com.nordvpn.android", "com.expressvpn.vpn", "com.surfshark.vpnclient.android",
        "com.jrzheng.supervpnfree", "free.vpn.unblock.proxy.turbovpn",
        "ch.protonvpn.android", "com.cloudflare.onedotonedotonedotone"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        dbHelper = DatabaseHelper.getInstance(this)
        securityManager = SecurityManager(this)
        blockOverlayManager = com.guardian.app.ui.overlay.BlockOverlayManager(this)

        // Bug 2 fix: Mark Wall 2 as enabled so Dashboard and Onboarding detect it
        securityManager.setWall2Enabled(true)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100 // Fast processing
        }
        serviceInfo = info
        Log.d("AccessibilitySentry", "Service Connected and Configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        val rootNode = rootInActiveWindow ?: return

        try {
            // Block 3rd-party VPN apps from launching to protect our DNS VPN
            if (pkg in vpnPackages) {
                Log.d("AccessibilitySentry", "Blocked 3rd-party VPN app: $pkg")
                performGlobalAction(GLOBAL_ACTION_HOME)
                triggerBlock()
                return
            }

            // Settings protection (covers all OEM settings apps)
            if (pkg in settingsPackages || pkg == "com.android.settings") {
                handleSettingsProtection(rootNode)
                return
            }

            // Check for URLs in supported browsers
            val viewIds = browserUrlBarIds[pkg]
            if (viewIds != null) {
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

            // Comprehensive screen content scanning (works on ALL apps & browsers)
            scope.launch {
                val allText = extractAllText(rootNode, 0).lowercase()
                if (allText.length < 5) return@launch // Skip trivially empty screens

                // Remove safe words from analysis to prevent false positives
                var cleanedText = allText
                for (safeWord in safeWords) {
                    cleanedText = cleanedText.replace(safeWord, "")
                }

                // Check for Incognito / Private browsing modes
                for (word in incognitoTriggerWords) {
                    if (cleanedText.contains(word)) {
                        Log.d("AccessibilitySentry", "Incognito mode detected: '$word' in app: $pkg")
                        dbHelper.logBlockEvent("WALL_2_INCOGNITO")
                        triggerBlock()
                        return@launch
                    }
                }

                // Check for instant-block words (single match = block)
                for (word in instantBlockWords) {
                    if (cleanedText.contains(word)) {
                        Log.d("AccessibilitySentry", "Instant-block word detected: '$word' in app: $pkg")
                        dbHelper.logBlockEvent("WALL_2_CONTENT")
                        triggerBlock()
                        return@launch
                    }
                }

                // Check for signal words (need 2+ matches)
                var signalCount = 0
                for (word in signalWords) {
                    if (cleanedText.contains(word)) {
                        signalCount++
                    }
                    if (signalCount >= 2) {
                        Log.d("AccessibilitySentry", "Signal threshold reached ($signalCount) in app: $pkg")
                        dbHelper.logBlockEvent("WALL_2_CONTENT")
                        triggerBlock()
                        return@launch
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AccessibilitySentry", "Error processing event", e)
        }
    }

    // Depth-limited text extraction to prevent StackOverflow on complex pages
    // Bug 4 fix: Only recycle child nodes (depth > 0), never the root node (depth == 0)
    // because the root node is still being used by onAccessibilityEvent() for URL scanning.
    private fun extractAllText(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > 15) return ""
        val sb = StringBuilder()
        try {
            node.text?.let { sb.append(it).append(" ") }
            node.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    sb.append(extractAllText(child, depth + 1))
                }
            }
        } catch (e: Exception) {
            // Silently handle stale node exceptions
        } finally {
            // Only recycle child nodes, not the root (which is owned by the caller)
            if (depth > 0) {
                try { node.recycle() } catch (_: Exception) {}
            }
        }
        return sb.toString()
    }

    // Bug 1 fix: This method is called from Dispatchers.IO coroutines.
    // performGlobalAction() MUST run on the main thread or it silently fails.
    // showOverlay() already posts internally, but we wrap everything for safety.
    private fun triggerBlock() {
        Log.d("AccessibilitySentry", "Triggering Block Overlay directly.")
        mainHandler.post {
            // Force Home screen so the blocked app loses focus
            performGlobalAction(GLOBAL_ACTION_HOME)
            // Show the DANGER ZONE overlay
            blockOverlayManager?.showOverlay()
        }
    }

    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        // 1. Check if user is on a legitimate setup screen
        val whitelistPhrases = listOf(
            "Guardian requires Device Admin to prevent uninstallation.",
            "Allow display over other apps",
            "Appear on top",
            "Accessibility",
            "Battery optimization",
            "Ignore battery optimizations"
        )

        var isWhitelisted = false
        for (phrase in whitelistPhrases) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(phrase)
            if (nodes.isNotEmpty()) {
                isWhitelisted = true
            }
            nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
            if (isWhitelisted) break
        }

        if (isWhitelisted) return

        // 2. Protection logic: kick out if they are on Guardian's settings, Private DNS, Developer Options, or VPN config
        val triggerWords = listOf(
            "Guardian", "AccessibilitySentry", "Device admin", "Deactivate", "Uninstall", "Force stop",
            "Private DNS", "Developer options", "USB debugging", "Wireless debugging", "VPN"
        )
        var triggered = false

        for (word in triggerWords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(word)
            if (nodes.isNotEmpty()) triggered = true
            nodes.forEach { try { it.recycle() } catch (_: Exception) {} }
            if (triggered) break
        }

        if (triggered) {
            val unlockTime = securityManager.getUninstallUnlockTime()
            val currentTime = System.currentTimeMillis()

            if (currentTime < unlockTime || unlockTime == 0L) {
                Log.d("AccessibilitySentry", "Self-Protection triggered! Kicking out of Settings.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                triggerBlock()
            }
        }
    }

    private fun checkAndBlockUrl(url: String) {
        val domain = extractDomain(url)
        val domainHash = CryptoUtils.sha256(domain.lowercase().toByteArray())

        if (dbHelper.isDomainBlocked(domainHash)) {
            Log.d("AccessibilitySentry", "Blocked domain detected: $domain")
            dbHelper.logBlockEvent("WALL_2")
            triggerBlock()
        }
    }

    private fun extractDomain(url: String): String {
        var domain = url.lowercase()
        if (domain.startsWith("http://")) domain = domain.substring(7)
        if (domain.startsWith("https://")) domain = domain.substring(8)

        val slashIndex = domain.indexOf('/')
        if (slashIndex != -1) domain = domain.substring(0, slashIndex)
        val questionIndex = domain.indexOf('?')
        if (questionIndex != -1) domain = domain.substring(0, questionIndex)
        val colonIndex = domain.indexOf(':')
        if (colonIndex != -1) domain = domain.substring(0, colonIndex)

        if (domain.startsWith("www.")) domain = domain.substring(4)
        if (domain.startsWith("m.")) domain = domain.substring(2)

        return domain
    }

    override fun onInterrupt() {
        Log.d("AccessibilitySentry", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        // Bug 2 fix: Mark Wall 2 as disabled when service is destroyed
        try {
            securityManager.setWall2Enabled(false)
        } catch (_: Exception) {}
    }
}
