package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.adapter.AppRuleProvider
import com.guardian.app.walls.wall2.event.*
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class AppMonitor @Inject constructor(
    private val appRuleProvider: AppRuleProvider
) {
    fun canHandle(pkg: String): Boolean = appRuleProvider.findByPackageName(pkg) != null

    fun process(rootNode: AccessibilityNodeInfo, pkg: String): ProtectionEvent? {
        val signature = appRuleProvider.findByPackageName(pkg) ?: return null

        return AppEvent(
            sessionId = java.util.UUID.randomUUID().toString(),
            metadata = AppMetadata(
                packageName = pkg,
                label = signature.label,
                category = signature.category.name
            )
        )
    }
}
