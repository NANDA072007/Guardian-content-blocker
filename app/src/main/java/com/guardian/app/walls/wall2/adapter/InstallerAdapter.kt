package com.guardian.app.walls.wall2.adapter

import android.view.accessibility.AccessibilityNodeInfo

interface InstallerAdapter {
    fun isInstallScreen(rootNode: AccessibilityNodeInfo): Boolean
    fun getTargetPackage(rootNode: AccessibilityNodeInfo): String?
    fun getInstallerName(): String
}
