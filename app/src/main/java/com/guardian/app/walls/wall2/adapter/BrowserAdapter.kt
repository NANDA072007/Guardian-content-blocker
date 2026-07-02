package com.guardian.app.walls.wall2.adapter

import android.view.accessibility.AccessibilityNodeInfo

interface BrowserAdapter {
    fun extractUrl(
        rootNode: AccessibilityNodeInfo,
        profile: BrowserProfile
    ): String?

    fun getProfile(packageName: String): BrowserProfile?
}

data class BrowserProfile(
    val packageNames: Set<String>,
    val compatibility: CompatibilityLevel,
    val urlBarResourceIds: Map<String, String>,
    val extractionStrategies: List<ExtractionStrategy>,
    val knownIssues: List<String> = emptyList()
)

enum class CompatibilityLevel {
    Verified,
    Supported,
    Experimental,
    Broken
}

enum class ExtractionStrategy {
    RESOURCE_ID,
    TEXT_PATTERN,
    HINT_ATTRIBUTE,
    HEURISTIC
}
