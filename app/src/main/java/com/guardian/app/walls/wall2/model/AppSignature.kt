package com.guardian.app.walls.wall2.model

data class AppSignature(
    val packageName: String,
    val label: String?,
    val installer: String?,
    val category: AppCategory,
    val aliases: Set<String> = emptySet()
) {
    fun matches(packageName: String): Boolean =
        this.packageName == packageName || packageName in aliases
}

enum class AppCategory {
    KNOWN_ADULT,
    SUSPICIOUS,
    INSTALLER,
    SETTINGS
}
