package com.guardian.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val wallSource: String,
    val triggerType: String = "UNKNOWN",
    val hourOfDay: Int = 0
)
