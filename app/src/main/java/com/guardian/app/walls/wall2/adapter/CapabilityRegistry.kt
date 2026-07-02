package com.guardian.app.walls.wall2.adapter

import android.content.Context
import android.os.Build
import com.guardian.app.walls.wall2.model.CapabilityResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canExtractBrowserUrl(browserPackage: String): CapabilityResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CapabilityResult.Partial("Depends on browser UI — check BrowserProfile compatibility")
        } else {
            CapabilityResult.Partial("Accessibility node traversal varies by Android version")
        }
    }

    fun canDrawOverlay(): CapabilityResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CapabilityResult.Supported
        } else {
            CapabilityResult.Partial("Requires SYSTEM_ALERT_WINDOW permission on Android 9 and below")
        }
    }

    fun canReadInstaller(): CapabilityResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CapabilityResult.Partial("Package installer UI detection varies by OEM")
        } else {
            CapabilityResult.Partial("Limited accessibility node access on older Android versions")
        }
    }

    fun canDetectSplitScreen(): CapabilityResult {
        return if (Build.VERSION.SDK_INT >= 31) {
            CapabilityResult.Supported
        } else {
            CapabilityResult.Unsupported("Split-screen detection requires Android 12+")
        }
    }

    fun isAccessibilityServiceEnabled(): CapabilityResult {
        return CapabilityResult.Supported
    }

    fun snapshot(): Map<String, CapabilityResult> = mapOf(
        "browserExtraction" to canExtractBrowserUrl("com.android.chrome"),
        "overlay" to canDrawOverlay(),
        "installerDetection" to canReadInstaller(),
        "splitScreen" to canDetectSplitScreen(),
        "accessibility" to isAccessibilityServiceEnabled()
    )
}
