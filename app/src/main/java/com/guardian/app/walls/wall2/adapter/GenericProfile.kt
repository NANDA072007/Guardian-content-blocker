package com.guardian.app.walls.wall2.adapter

object GenericProfile {
    fun create(): BrowserProfile = BrowserProfile(
        packageNames = setOf(
            "com.duckduckgo.mobile.android",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.microsoft.emmx",
            "com.kiwibrowser.browser",
            "com.vivaldi.browser",
            "com.ecosia.android"
        ),
        compatibility = CompatibilityLevel.Experimental,
        urlBarResourceIds = emptyMap(),
        extractionStrategies = listOf(
            ExtractionStrategy.TEXT_PATTERN,
            ExtractionStrategy.HINT_ATTRIBUTE,
            ExtractionStrategy.HEURISTIC
        ),
        knownIssues = listOf(
            "Generic profile uses heuristic matching — may produce false positives",
            "Browser UI changes may break text pattern matching"
        )
    )
}
