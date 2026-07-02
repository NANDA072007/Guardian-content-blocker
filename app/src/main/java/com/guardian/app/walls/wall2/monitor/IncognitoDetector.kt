package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class IncognitoDetector @Inject constructor() {

    // Browser UI strings and content descriptions that indicate private/incognito browsing.
    // Covers: Chrome, Brave, Firefox, Samsung, DuckDuckGo, Edge, Opera
    private val incognitoPatterns = listOf(
        // English — Chrome / generic
        "incognito",
        "private browsing",
        "private tab",
        "inprivate",            // Microsoft Edge
        "secret tab",           // Samsung Internet
        "anonymous",

        // Chrome-specific
        "you've gone incognito",
        "you're incognito",
        "now you can browse privately",

        // Firefox
        "private window",
        "private mode",
        "you're in a private window",

        // Samsung Internet
        "secret mode",
        "secret mode on",

        // Brave
        "private browsing with tor",
        "private window with tor",
        "new private tab",

        // DuckDuckGo — always private by design, but fire button clears state
        "clear all tabs and data",
        "fire button",

        // Opera
        "private",

        // Opera / Yandex / UC
        "navegación privada",    // Spanish
        "navigation privée",     // French
        "privatbrowsing",        // German / Nordic
        "navigazione privata",   // Italian
        "navegação privativa"    // Portuguese
    )

    /**
     * Returns true if the current window's accessibility tree contains any UI text
     * that strongly indicates an incognito or private browsing session.
     */
    fun isIncognito(rootNode: AccessibilityNodeInfo): Boolean {
        return nodeContainsPattern(rootNode, depth = 0)
    }

    private fun nodeContainsPattern(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 6) return false
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null && incognitoPatterns.any { text.contains(it) }) return true

            val desc = node.contentDescription?.toString()?.lowercase()
            if (desc != null && incognitoPatterns.any { desc.contains(it) }) return true

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && nodeContainsPattern(child, depth + 1)) {
                    child.recycle()
                    return true
                }
                try { child?.recycle() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return false
    }
}
