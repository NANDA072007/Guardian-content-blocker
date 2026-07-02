package com.guardian.app.walls.wall2.adapter

object DuckDuckGoProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "com.duckduckgo.mobile.android"
        ),
        compatibility = CompatibilityLevel.Supported,
        urlBarResourceIds = mapOf(
            "omnibar" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "url_bar" to "com.duckduckgo.mobile.android:id/url_bar",
            "browser_bar" to "com.duckduckgo.mobile.android:id/addressBarTitle"
        ),
        extractionStrategies = listOf(
            ExtractionStrategy.RESOURCE_ID,
            ExtractionStrategy.HINT_ATTRIBUTE,
            ExtractionStrategy.TEXT_PATTERN,
            ExtractionStrategy.HEURISTIC
        ),
        knownIssues = listOf(
            "DuckDuckGo fire button clears all tabs, incognito-like by default"
        )
    )
}
