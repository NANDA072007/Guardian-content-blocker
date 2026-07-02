package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.adapter.InstallerAdapter
import com.guardian.app.walls.wall2.adapter.PlayStoreAdapter
import com.guardian.app.walls.wall2.adapter.PackageInstallerAdapter
import com.guardian.app.walls.wall2.adapter.DomainRuleProvider
import com.guardian.app.walls.wall2.event.*
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class InstallerMonitor @Inject constructor(
    private val domainRuleProvider: DomainRuleProvider
) {

    private val adapters: List<InstallerAdapter> = listOf(
        PlayStoreAdapter(),
        PackageInstallerAdapter()
    )

    fun process(rootNode: AccessibilityNodeInfo): ProtectionEvent? {
        for (adapter in adapters) {
            if (!adapter.isInstallScreen(rootNode)) continue

            val target = adapter.getTargetPackage(rootNode)
            return InstallerEvent(
                sessionId = java.util.UUID.randomUUID().toString(),
                metadata = InstallerMetadata(
                    installerPackage = adapter.getInstallerName(),
                    targetPackage = target
                )
            )
        }
        return null
    }
}
