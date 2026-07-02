package com.guardian.app.walls.wall2.adapter

object BraveProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "com.brave.browser",
            "com.brave.browser_beta"
        ),
        compatibility = CompatibilityLevel.Supported,
        urlBarResourceIds = mapOf(
            "default" to "com.brave.browser:id/url_bar",
            "location_bar" to "com.brave.browser:id/location_bar"
        ),
        extractionStrategies = listOf(
            ExtractionStrategy.RESOURCE_ID,
            ExtractionStrategy.HINT_ATTRIBUTE,
            ExtractionStrategy.HEURISTIC
        ),
        knownIssues = listOf(
            "Brave shares Chrome's rendering engine — URL bar behavior similar to Chrome"
        )
    )
}
