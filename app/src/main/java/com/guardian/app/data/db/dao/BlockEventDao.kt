package com.guardian.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.guardian.app.data.db.entities.BlockEvent

@Dao
interface BlockEventDao {
    @Insert
    fun logBlockEvent(event: BlockEvent)

    @Query("SELECT MAX(timestamp) FROM block_events")
    fun getLastBlockTimestamp(): Long?

    @Query("SELECT MAX(e1.timestamp) FROM block_events e1 WHERE (SELECT COUNT(*) FROM block_events e2 WHERE e2.timestamp BETWEEN e1.timestamp AND e1.timestamp + 600000) >= 3")
    fun getLastRelapseTimestamp(): Long?

    @Query("SELECT CAST(julianday(date('now', 'localtime')) - julianday(date(:timestampMs/1000, 'unixepoch', 'localtime')) AS INTEGER)")
    fun getDaysSince(timestampMs: Long): Int
}
