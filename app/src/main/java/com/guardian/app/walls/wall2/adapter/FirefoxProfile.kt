package com.guardian.app.walls.wall2.adapter

object FirefoxProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "org.mozilla.klar"
        ),
        compatibility = CompatibilityLevel.Experimental,
        urlBarResourceIds = mapOf(
            "default" to "org.mozilla.firefox:id/url_bar"
        ),
        extractionStrategies = listOf(
            ExtractionStrategy.RESOURCE_ID,
            ExtractionStrategy.TEXT_PATTERN
        ),
        knownIssues = listOf(
            "Firefox uses dynamic content descriptions that may not contain URLs",
            "Fenix (Firefox Daylight) changed the URL bar architecture"
        )
    )
}
