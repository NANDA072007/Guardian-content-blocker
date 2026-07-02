package com.guardian.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_blocklist")
data class DomainBlocklist(
    @PrimaryKey
    val domainHash: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)
