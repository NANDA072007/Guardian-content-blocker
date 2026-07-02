package com.guardian.app.walls.wall2.adapter

import com.guardian.app.config.BlocklistConfig
import com.guardian.app.walls.wall2.model.AppSignature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuleProvider @Inject constructor() : RuleProvider<AppSignature> {

    private val knownApps: List<AppSignature> = BlocklistConfig.knownAdultApps

    override fun matches(target: AppSignature): Boolean {
        return knownApps.any { it.matches(target.packageName) }
    }

    fun findByPackageName(packageName: String): AppSignature? {
        return knownApps.firstOrNull { it.matches(packageName) }
    }
}
