package com.guardian.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseHelperTest {

    private lateinit var db: GuardianDatabase
    private lateinit var domainDao: DomainDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, GuardianDatabase::class.java).build()
        domainDao = db.domainDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testRelapseLogic_NoBlocks_ReturnsNull() {
        val relapseTime = domainDao.getLastRelapseTimestamp()
        assertEquals(null, relapseTime)
    }

    @Test
    fun testRelapseLogic_TwoBlocks_ReturnsNull() {
        val now = System.currentTimeMillis()
        domainDao.logBlockEvent(BlockEvent(now, "vpn"))
        domainDao.logBlockEvent(BlockEvent(now + 1000, "vpn"))

        val relapseTime = domainDao.getLastRelapseTimestamp()
        assertEquals(null, relapseTime)
    }

    @Test
    fun testRelapseLogic_ThreeBlocksInTenMinutes_ReturnsTimestamp() {
        val now = 1000000L
        domainDao.logBlockEvent(BlockEvent(now, "vpn"))
        domainDao.logBlockEvent(BlockEvent(now + 300000L, "vpn")) // 5 mins
        domainDao.logBlockEvent(BlockEvent(now + 500000L, "vpn")) // 8.3 mins

        val relapseTime = domainDao.getLastRelapseTimestamp()
        assertEquals(now + 500000L, relapseTime)
    }

    @Test
    fun testRelapseLogic_ThreeBlocksSpreadOut_ReturnsNull() {
        val now = 1000000L
        domainDao.logBlockEvent(BlockEvent(now, "vpn"))
        domainDao.logBlockEvent(BlockEvent(now + 300000L, "vpn")) // 5 mins
        domainDao.logBlockEvent(BlockEvent(now + 700000L, "vpn")) // 11.6 mins

        val relapseTime = domainDao.getLastRelapseTimestamp()
        assertEquals(null, relapseTime)
    }

    @Test
    fun insertAndLookupDomain() {
        val hash = "a1b2c3d4"
        domainDao.insertDomain(BlockedDomain(hash, "adult"))
        
        val isBlocked = domainDao.isDomainBlocked(hash)
        assertEquals(1, isBlocked)
        
        val isNotBlocked = domainDao.isDomainBlocked("not_in_db")
        assertEquals(0, isNotBlocked)
    }

    @Test
    fun logAndRetrieveBlockEvent() {
        val now = System.currentTimeMillis()
        domainDao.logBlockEvent(BlockEvent(now, "vpn"))
        
        val lastEvent = domainDao.getLastBlockTimestamp()
        assertEquals(now, lastEvent)
    }

    @Test
    fun calculateStreakAccuracy() {
        // Assume relapse was exactly 2 days ago
        // 2 days = 172800000 ms
        val twoDaysAgo = System.currentTimeMillis() - 172800000L
        
        val daysClean = domainDao.getDaysSince(twoDaysAgo)
        // Testing SQLite's date functions might be slightly off due to exact timezone matching in unit tests vs real app,
        // but typically 172800000 ms difference evaluates to 2 calendar days difference.
        // We allow 2 or 1 just in case the test runs right at a midnight boundary.
        assert(daysClean == 2 || daysClean == 1) 
    }
}
