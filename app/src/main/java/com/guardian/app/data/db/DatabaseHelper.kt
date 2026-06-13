package com.guardian.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.guardian.app.util.CryptoUtils

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 3
        private const val DATABASE_NAME = "guardian_db"

        private const val TABLE_BLOCKLIST = "domain_blocklist"
        private const val KEY_HASH = "domainHash"
        private const val KEY_CATEGORY = "category"

        private const val TABLE_BLOCK_EVENTS = "block_events"
        private const val KEY_ID = "id"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_WALL_SOURCE = "wall_source"

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

    }

    private val seedDomains = listOf(
        "pornhub.com" to "adult",
        "xvideos.com" to "adult",
        "xnxx.com" to "adult",
        "xhamster.com" to "adult",
        "redtube.com" to "adult",
        "youporn.com" to "adult",
        "tube8.com" to "adult",
        "adultfriendfinder.com" to "adult",
        "onlyfans.com" to "adult",
        "stripchat.com" to "adult",
        "chaturbate.com" to "adult",
        "cam4.com" to "adult",
        "livejasmin.com" to "adult",
        "flirt4free.com" to "adult",
        "omegle.com" to "adult"
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_BLOCKLIST($KEY_HASH TEXT PRIMARY KEY,$KEY_CATEGORY TEXT)")
        db.execSQL("CREATE TABLE $TABLE_BLOCK_EVENTS($KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,$KEY_TIMESTAMP INTEGER,$KEY_WALL_SOURCE TEXT)")
        seedBlocklist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_BLOCK_EVENTS($KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,$KEY_TIMESTAMP INTEGER,$KEY_WALL_SOURCE TEXT)")
                seedBlocklist(db)
            }
            2 -> {
                // v2 -> v3: recreate blocklist table with correct schema if needed
                db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCKLIST")
                db.execSQL("CREATE TABLE $TABLE_BLOCKLIST($KEY_HASH TEXT PRIMARY KEY,$KEY_CATEGORY TEXT)")
                seedBlocklist(db)
            }
            else -> {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCKLIST")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCK_EVENTS")
                onCreate(db)
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun seedBlocklist(db: SQLiteDatabase) {
        for ((domain, category) in seedDomains) {
            val hash = CryptoUtils.sha256(domain.toByteArray())
            val values = ContentValues().apply {
                put(KEY_HASH, hash)
                put(KEY_CATEGORY, category)
            }
            db.insertWithOnConflict(TABLE_BLOCKLIST, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun isDomainBlocked(hash: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(TABLE_BLOCKLIST, arrayOf(KEY_HASH), "$KEY_HASH = ?", arrayOf(hash), null, null, null, "1")
        return cursor.use { it.moveToFirst() }
    }

    fun insertDomain(hash: String, category: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_HASH, hash)
            put(KEY_CATEGORY, category)
        }
        db.insertWithOnConflict(TABLE_BLOCKLIST, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun logBlockEvent(wallSource: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_TIMESTAMP, System.currentTimeMillis())
            put(KEY_WALL_SOURCE, wallSource)
        }
        db.insert(TABLE_BLOCK_EVENTS, null, values)
    }

    fun getDaysCleanStreak(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT MAX($KEY_TIMESTAMP) FROM $TABLE_BLOCK_EVENTS", null)
        val lastBlockTimestamp = cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        
        val baselineTime = if (lastBlockTimestamp == 0L) {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.firstInstallTime
            } catch (e: Exception) {
                System.currentTimeMillis() // Fallback to 0 days
            }
        } else {
            lastBlockTimestamp
        }
        
        val diffInMillis = System.currentTimeMillis() - baselineTime
        return (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
    }
}
