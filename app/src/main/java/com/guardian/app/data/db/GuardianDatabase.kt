package com.guardian.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guardian.app.data.db.dao.BlockEventDao
import com.guardian.app.data.db.dao.DomainBlocklistDao
import com.guardian.app.data.db.dao.StreakDao
import com.guardian.app.data.db.entities.BlockEvent
import com.guardian.app.data.db.entities.DomainBlocklist
import com.guardian.app.data.db.entities.StreakLog
import com.guardian.app.util.CryptoUtils
import java.util.concurrent.Executors

@Database(entities = [DomainBlocklist::class, BlockEvent::class, StreakLog::class], version = 2, exportSchema = false)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun domainDao(): DomainBlocklistDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: GuardianDatabase? = null

        fun getInstance(context: Context): GuardianDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian_room_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Executors.newSingleThreadExecutor().execute {
                val database = INSTANCE
                if (database != null) {
                    val seedDomains = arrayOf(
                        arrayOf("pornhub.com", "adult"),
                        arrayOf("xvideos.com", "adult")
                    )

                    val blockedDomains = mutableListOf<DomainBlocklist>()
                    for (entry in seedDomains) {
                        val domainHash = CryptoUtils.sha256(entry[0].toByteArray())
                        blockedDomains.add(DomainBlocklist(domainHash, entry[1]))
                    }
                    database.domainDao().insertDomains(blockedDomains)
                }
            }
        }
    }
}
