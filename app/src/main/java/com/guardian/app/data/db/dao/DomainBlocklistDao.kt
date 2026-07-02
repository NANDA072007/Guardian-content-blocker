package com.guardian.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.guardian.app.data.db.entities.DomainBlocklist

@Dao
interface DomainBlocklistDao {
    @Query("SELECT COUNT(*) FROM domain_blocklist WHERE domainHash = :hash")
    fun isDomainBlocked(hash: String): Int

    @Query("SELECT domainHash FROM domain_blocklist")
    fun getAllDomainHashes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDomains(domains: List<DomainBlocklist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDomain(domain: DomainBlocklist)
}
