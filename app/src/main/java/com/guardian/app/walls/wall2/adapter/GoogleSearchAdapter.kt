package com.guardian.app.walls.wall2.adapter

import android.view.accessibility.AccessibilityNodeInfo

class GoogleSearchAdapter : SearchEngineAdapter {

    private val searchIndicators = listOf(
        "com.google.android.googlequicksearchbox",
        "com.android.chrome"
    )

    private val searchPageTexts = listOf(
        "search results",
        "results for",
        "google search",
        "did you mean"
    )

    override fun isSearchResultPage(rootNode: AccessibilityNodeInfo): Boolean {
        return nodeContainsText(rootNode, 0) { text ->
            searchPageTexts.any { text.contains(it) }
        }
    }

    override fun getSearchQuery(rootNode: AccessibilityNodeInfo): String? {
        return findSearchQuery(rootNode, 0)
    }

    override fun getEngineName(): String = "Google"

    private fun findSearchQuery(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 5) return null
        try {
            val text = node.text?.toString()
            if (text != null && text.length in 3..200) {
                val lower = text.lowercase()
                if (lower.startsWith("results for") || lower.contains("\"")) return text
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findSearchQuery(child, depth + 1)
                    if (result != null) return result
                    child.recycle()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun nodeContainsText(node: AccessibilityNodeInfo?, depth: Int, predicate: (String) -> Boolean): Boolean {
        if (node == null || depth > 10) return false
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null && predicate(text)) return true
            val desc = node.contentDescription?.toString()?.lowercase()
            if (desc != null && predicate(desc)) return true
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && nodeContainsText(child, depth + 1, predicate)) return true
            }
        } catch (_: Exception) {}
        return false
    }
}
