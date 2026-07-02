package com.guardian.app.engine

import android.content.Context
import android.util.Log
import com.guardian.app.data.db.entities.DomainBlocklist
import com.guardian.app.data.db.GuardianDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BlocklistEngine(private val context: Context) {

    suspend fun initializeBlocklistIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean("blocklist_initialized", false)
        
        if (!isInitialized) {
            Log.d("BlocklistEngine", "First launch detected. Unpacking default blocklist...")
            try {
                val db = GuardianDatabase.getInstance(context)
                val dao = db.domainDao()
                
                val inputStream = context.assets.open("domains.csv")
                val reader = BufferedReader(InputStreamReader(inputStream))
                
                val domains = mutableListOf<DomainBlocklist>()
                reader.useLines { lines ->
                    for (line in lines) {
                        val parts = line.split(",")
                        if (parts.size == 2) {
                            domains.add(DomainBlocklist(parts[0], parts[1]))
                        }
                        
                        // Batch insert every 10,000 records to keep memory low
                        if (domains.size >= 10000) {
                            dao.insertDomains(domains)
                            domains.clear()
                        }
                    }
                }
                
                // Insert remaining
                if (domains.isNotEmpty()) {
                    dao.insertDomains(domains)
                }
                
                reader.close()
                inputStream.close()
                
                prefs.edit().putBoolean("blocklist_initialized", true).apply()
                Log.d("BlocklistEngine", "Default blocklist successfully loaded into Room.")
            } catch (e: Exception) {
                Log.e("BlocklistEngine", "Failed to initialize blocklist from assets", e)
            }
        }
    }
}
