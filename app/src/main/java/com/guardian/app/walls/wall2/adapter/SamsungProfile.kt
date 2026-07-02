package com.guardian.app.walls.wall2.adapter

object SamsungProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "com.sec.android.app.sbrowser"
        ),
        compatibility = CompatibilityLevel.Experimental,
        urlBarResourceIds = mapOf(
            "default" to "com.sec.android.app.sbrowser:id/location_bar"
        ),
        extractionStrategies = listOf(
            ExtractionStrategy.RESOURCE_ID,
            ExtractionStrategy.HINT_ATTRIBUTE
        ),
        knownIssues = listOf(
            "Samsung Internet uses custom UI components that vary by One UI version",
            "Resource IDs may differ between Samsung firmware versions"
        )
    )
}
