package com.guardian.app.walls.wall2.model

data class ProtectionWeights(
    val accessibility: Int = 20,
    val vpn: Int = 20,
    val overlay: Int = 15,
    val router: Int = 15,
    val heartbeat: Int = 10,
    val rules: Int = 20
) {
    fun total(): Int = accessibility + vpn + overlay + router + heartbeat + rules
}

data class ProtectionScore(
    val score: Int,
    val components: Map<String, Boolean>,
    val timestamp: Long = System.currentTimeMillis()
)
