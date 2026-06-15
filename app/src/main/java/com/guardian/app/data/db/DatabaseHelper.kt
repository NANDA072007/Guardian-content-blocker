package com.guardian.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.guardian.app.util.CryptoUtils

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 4
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

    // ==========================================
    // PRODUCTION DOMAIN BLOCKLIST (200+ domains)
    // ==========================================
    private val seedDomains = listOf(
        // === TIER 1: Top 50 Most Visited Adult Sites ===
        "pornhub.com" to "adult",
        "xvideos.com" to "adult",
        "xnxx.com" to "adult",
        "xhamster.com" to "adult",
        "redtube.com" to "adult",
        "youporn.com" to "adult",
        "tube8.com" to "adult",
        "spankbang.com" to "adult",
        "beeg.com" to "adult",
        "eporner.com" to "adult",
        "youjizz.com" to "adult",
        "tnaflix.com" to "adult",
        "pornone.com" to "adult",
        "ixxx.com" to "adult",
        "txxx.com" to "adult",
        "hqporner.com" to "adult",
        "thumbzilla.com" to "adult",
        "porn.com" to "adult",
        "sex.com" to "adult",
        "porntrex.com" to "adult",
        "4tube.com" to "adult",
        "fux.com" to "adult",
        "pornpics.com" to "adult",
        "vporn.com" to "adult",
        "drtuber.com" to "adult",
        "porndig.com" to "adult",
        "motherless.com" to "adult",
        "lobstertube.com" to "adult",
        "pornmd.com" to "adult",
        "fuq.com" to "adult",
        "bellesa.co" to "adult",
        "playvids.com" to "adult",
        "pornrox.com" to "adult",
        "hotporndir.com" to "adult",
        "pornzog.com" to "adult",
        "porngo.com" to "adult",
        "pornhat.com" to "adult",
        "pornktube.com" to "adult",
        "3movs.com" to "adult",
        "anyporn.com" to "adult",
        "fapcat.com" to "adult",
        "fapster.xxx" to "adult",
        "megatube.xxx" to "adult",
        "tubegalore.com" to "adult",
        "pornhd.com" to "adult",
        "pornhd3x.tv" to "adult",
        "nuvid.com" to "adult",
        "porn300.com" to "adult",
        "porn00.org" to "adult",
        "zedporn.com" to "adult",

        // === TIER 2: Major Studios / Premium ===
        "brazzers.com" to "adult",
        "bangbros.com" to "adult",
        "naughtyamerica.com" to "adult",
        "realitykings.com" to "adult",
        "mofos.com" to "adult",
        "digitalplayground.com" to "adult",
        "babes.com" to "adult",
        "tushy.com" to "adult",
        "vixen.com" to "adult",
        "blacked.com" to "adult",
        "twistys.com" to "adult",
        "teamskeet.com" to "adult",
        "fakehub.com" to "adult",
        "faketaxi.com" to "adult",
        "wicked.com" to "adult",
        "kink.com" to "adult",
        "evilangel.com" to "adult",
        "julesjordan.com" to "adult",
        "adulttime.com" to "adult",
        "dorcelclub.com" to "adult",
        "girlsway.com" to "adult",
        "puremature.com" to "adult",
        "passion-hd.com" to "adult",

        // === TIER 3: Live Cam / Chat Sites ===
        "onlyfans.com" to "adult",
        "stripchat.com" to "adult",
        "chaturbate.com" to "adult",
        "cam4.com" to "adult",
        "livejasmin.com" to "adult",
        "flirt4free.com" to "adult",
        "myfreecams.com" to "adult",
        "bongacams.com" to "adult",
        "camsoda.com" to "adult",
        "streamate.com" to "adult",
        "imlive.com" to "adult",
        "xlovecam.com" to "adult",
        "jerkmate.com" to "adult",
        "fansly.com" to "adult",
        "manyvids.com" to "adult",
        "clips4sale.com" to "adult",
        "justfor.fans" to "adult",
        "loyalfans.com" to "adult",

        // === TIER 4: Hentai / Anime / Cartoon ===
        "hentaihaven.xxx" to "adult",
        "hanime.tv" to "adult",
        "nhentai.net" to "adult",
        "e-hentai.org" to "adult",
        "hentai2read.com" to "adult",
        "hitomi.la" to "adult",
        "rule34.xxx" to "adult",
        "gelbooru.com" to "adult",
        "danbooru.donmai.us" to "adult",
        "rule34video.com" to "adult",
        "multporn.net" to "adult",
        "tsumino.com" to "adult",
        "pururin.to" to "adult",
        "simply-hentai.com" to "adult",
        "hentaiworld.tv" to "adult",
        "fakku.net" to "adult",

        // === TIER 5: Dating / Hookup / Escort ===
        "adultfriendfinder.com" to "adult",
        "ashleymadison.com" to "adult",
        "fling.com" to "adult",
        "benaughty.com" to "adult",
        "seeking.com" to "adult",
        "whatsyourprice.com" to "adult",
        "skipthegames.com" to "adult",
        "megapersonals.com" to "adult",
        "bedpage.com" to "adult",
        "listcrawler.com" to "adult",
        "escortdirectory.com" to "adult",
        "eroticmonkey.ch" to "adult",
        "tryst.link" to "adult",

        // === TIER 6: Erotic Stories / Literotica ===
        "literotica.com" to "adult",
        "sexstories.com" to "adult",
        "asstr.org" to "adult",
        "chyoa.com" to "adult",
        "storiesonline.net" to "adult",
        "lushstories.com" to "adult",
        "bdsmlibrary.com" to "adult",
        "nifty.org" to "adult",
        "mcstories.com" to "adult",

        // === TIER 7: Image Boards / Forums ===
        "4chan.org" to "adult",
        "8kun.top" to "adult",
        "reddit.com/r/nsfw" to "adult",
        "reddit.com/r/gonewild" to "adult",
        "reddit.com/r/porn" to "adult",
        "imagefap.com" to "adult",
        "xpee.com" to "adult",
        "freeones.com" to "adult",
        "scrolller.com" to "adult",
        "coomer.su" to "adult",

        // === TIER 8: Proxy / Mirror Sites (Common evasion) ===
        "pornhub2.com" to "adult",
        "xvideos2.com" to "adult",
        "xnxx2.com" to "adult",
        "xhamster2.com" to "adult",
        "xhamster.desi" to "adult",
        "spankbang.party" to "adult",

        // === TIER 9: Regional / Localized Adult Sites ===
        "javhd.com" to "adult",
        "javfree.me" to "adult",
        "jav.guru" to "adult",
        "missav.com" to "adult",
        "thisav.com" to "adult",
        "avgle.com" to "adult",
        "desiporn.tube" to "adult",
        "desixx.net" to "adult",
        "masalaseen.com" to "adult",
        "pakistaniporn.tv" to "adult",
        "indianpornvideos.com" to "adult",

        // === TIER 10: Nude Leak / Celebrity ===
        "fapello.com" to "adult",
        "thefappening.pro" to "adult",
        "nudostar.com" to "adult",
        "celebjihad.com" to "adult",
        "aznude.com" to "adult",
        "ancensored.com" to "adult",
        "nudevista.com" to "adult",
        "pornpics.de" to "adult",

        // === TIER 11: Aggregators / Search ===
        "nudevista.com" to "adult",
        "pornmd.com" to "adult",
        "rexxx.com" to "adult",
        "findtubes.com" to "adult",
        "sxyprn.com" to "adult",
        "daftsex.com" to "adult",

        // === TIER 12: Known Risky Chat Platforms ===
        "omegle.com" to "adult",
        "chatroulette.com" to "adult",
        "chatrandom.com" to "adult",
        "dirtyroulette.com" to "adult",
        "emeraldchat.com" to "adult",
        "chathub.cam" to "adult",
        "camsurf.com" to "adult",
        "bazoocam.org" to "adult",
        "shagle.com" to "adult",
        "coomeet.com" to "adult",
        "tinychat.com" to "adult"
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_BLOCKLIST($KEY_HASH TEXT PRIMARY KEY,$KEY_CATEGORY TEXT)")
        db.execSQL("CREATE TABLE $TABLE_BLOCK_EVENTS($KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,$KEY_TIMESTAMP INTEGER,$KEY_WALL_SOURCE TEXT)")
        seedBlocklist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Always re-seed blocklist on upgrade to ensure latest domains are included
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOCKLIST")
        db.execSQL("CREATE TABLE $TABLE_BLOCKLIST($KEY_HASH TEXT PRIMARY KEY,$KEY_CATEGORY TEXT)")
        seedBlocklist(db)
        
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_BLOCK_EVENTS($KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,$KEY_TIMESTAMP INTEGER,$KEY_WALL_SOURCE TEXT)")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun seedBlocklist(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for ((domain, category) in seedDomains) {
                val hash = CryptoUtils.sha256(domain.toByteArray())
                val values = ContentValues().apply {
                    put(KEY_HASH, hash)
                    put(KEY_CATEGORY, category)
                }
                db.insertWithOnConflict(TABLE_BLOCKLIST, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
                System.currentTimeMillis()
            }
        } else {
            lastBlockTimestamp
        }

        val baselineCalendar = java.util.Calendar.getInstance().apply { timeInMillis = baselineTime }
        val currentCalendar = java.util.Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        
        // Zero out time components to compare calendar days
        baselineCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        baselineCalendar.set(java.util.Calendar.MINUTE, 0)
        baselineCalendar.set(java.util.Calendar.SECOND, 0)
        baselineCalendar.set(java.util.Calendar.MILLISECOND, 0)

        currentCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        currentCalendar.set(java.util.Calendar.MINUTE, 0)
        currentCalendar.set(java.util.Calendar.SECOND, 0)
        currentCalendar.set(java.util.Calendar.MILLISECOND, 0)

        val diffInMillis = currentCalendar.timeInMillis - baselineCalendar.timeInMillis
        val days = (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
        
        // Base streak starts at Day 1
        return days + 1
    }
}
