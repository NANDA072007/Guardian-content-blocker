package com.guardian.app.walls.wall2.adapter

import android.view.accessibility.AccessibilityNodeInfo

interface SearchEngineAdapter {
    fun isSearchResultPage(rootNode: AccessibilityNodeInfo): Boolean
    fun getSearchQuery(rootNode: AccessibilityNodeInfo): String?
    fun getEngineName(): String
}
