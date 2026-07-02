package com.guardian.app.walls.wall2.adapter

import android.view.accessibility.AccessibilityNodeInfo

class PlayStoreAdapter : InstallerAdapter {

    private val playStorePackages = setOf(
        "com.android.vending",
        "com.google.android.finsky"
    )

    override fun isInstallScreen(rootNode: AccessibilityNodeInfo): Boolean {
        return nodeContainsText(rootNode, 0) { text ->
            text.contains("install") || text.contains("update") || text.contains("get")
        }
    }

    override fun getTargetPackage(rootNode: AccessibilityNodeInfo): String? {
        return findPackageName(rootNode, 0)
    }

    override fun getInstallerName(): String = "GooglePlay"

    private fun findPackageName(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 5) return null
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null && text.matches(Regex("^[a-z0-9.]+\\.[a-z0-9.]+$"))) return text
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findPackageName(child, depth + 1)
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
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && nodeContainsText(child, depth + 1, predicate)) return true
            }
        } catch (_: Exception) {}
        return false
    }
}
