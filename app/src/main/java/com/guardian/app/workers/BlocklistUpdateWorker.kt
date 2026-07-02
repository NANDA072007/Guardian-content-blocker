package com.guardian.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guardian.app.data.db.entities.DomainBlocklist
import com.guardian.app.data.db.GuardianDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class BlocklistUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("BlocklistUpdateWorker", "Starting weekly blocklist update...")
            
            // In a real scenario, this would be a URL to your hosted domains.csv.gz
            val updateUrl = URL("https://raw.githubusercontent.com/example/guardian-blocklist/main/domains.csv.gz")
            val connection = updateUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val gzipStream = GZIPInputStream(inputStream)
                val reader = BufferedReader(InputStreamReader(gzipStream))
                
                val db = GuardianDatabase.getInstance(context)
                val dao = db.domainDao()
                
                val domains = mutableListOf<DomainBlocklist>()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    val parts = line!!.split(",")
                    if (parts.size >= 2) {
                        domains.add(DomainBlocklist(parts[0], parts[1]))
                    }
                    
                    if (domains.size >= 10000) {
                        dao.insertDomains(domains)
                        domains.clear()
                    }
                }
                
                if (domains.isNotEmpty()) {
                    dao.insertDomains(domains)
                }
                
                reader.close()
                gzipStream.close()
                inputStream.close()
                
                Log.d("BlocklistUpdateWorker", "Weekly blocklist update completed successfully.")
                Result.success()
            } else {
                Log.w("BlocklistUpdateWorker", "Failed to fetch blocklist. HTTP Code: ${connection.responseCode}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("BlocklistUpdateWorker", "Error during blocklist update", e)
            Result.retry()
        }
    }
}
