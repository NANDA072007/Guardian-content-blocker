package com.guardian.app.walls.wall2.adapter

object ChromeProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary"
        ),
        compatibility = CompatibilityLevel.Supported,
        urlBarResourceIds = mapOf(
            "url_bar" to "com.android.chrome:id/url_bar",
            "location_bar" to "com.android.chrome:id/location_bar",
            "omnibox" to "com.android.chrome:id/omnibox_text_box",
            "tab_switcher" to "com.android.chrome:id/tab_switcher_url_bar"
        ),
        extractionStrategies = listOf(
            ExtractionStrategy.RESOURCE_ID,
            ExtractionStrategy.HINT_ATTRIBUTE,
            ExtractionStrategy.HEURISTIC
        ),
        knownIssues = listOf(
            "URL bar ID may change between Chrome versions",
            "Incognito mode may not expose URL bar"
        )
    )
}
