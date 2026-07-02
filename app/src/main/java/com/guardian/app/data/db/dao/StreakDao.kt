package com.guardian.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.guardian.app.data.db.entities.StreakLog

@Dao
interface StreakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStreak(streakLog: StreakLog)

    @Query("SELECT * FROM streak_log WHERE date = :date LIMIT 1")
    fun getStreakByDate(date: String): StreakLog?

    @Query("SELECT SUM(isClean) FROM streak_log")
    fun getTotalCleanDays(): Int
}
