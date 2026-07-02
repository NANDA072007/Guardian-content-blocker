package com.guardian.app.walls.wall2.adapter

import com.guardian.app.data.repository.GuardianRepository
import com.guardian.app.util.CryptoUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainRuleProvider @Inject constructor(
    private val repository: GuardianRepository
) : RuleProvider<String> {

    override fun matches(target: String): Boolean {
        val hash = CryptoUtils.sha256(target.lowercase().toByteArray())
        return repository.isDomainBlockedSync(hash)
    }

    fun matchesSubdomain(domain: String): Boolean {
        val hash = CryptoUtils.sha256(domain.lowercase().toByteArray())
        return repository.isDomainBlockedSync(hash)
    }
}
