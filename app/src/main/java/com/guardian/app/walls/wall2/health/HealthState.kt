package com.guardian.app.walls.wall2.health

enum class HealthLevel {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    RECOVERING,
    UNAVAILABLE
}

data class HealthState(
    val level: HealthLevel,
    val score: Float,
    val failingChecks: List<String>,
    val lastCheckedAt: Long,
    val degradedSince: Long? = null,
    val recoveryAttempts: Int = 0
) {
    companion object {
        val UNKNOWN = HealthState(
            level = HealthLevel.UNKNOWN,
            score = 0f,
            failingChecks = emptyList(),
            lastCheckedAt = 0L
        )

        val UNAVAILABLE = HealthState(
            level = HealthLevel.UNAVAILABLE,
            score = 0f,
            failingChecks = listOf("Service not connected"),
            lastCheckedAt = System.currentTimeMillis()
        )
    }
}
