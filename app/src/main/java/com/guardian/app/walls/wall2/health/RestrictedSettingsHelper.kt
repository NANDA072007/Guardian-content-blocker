package com.guardian.app.walls.wall2.health

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestrictedSettingsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "RestrictedSettings"

    fun isSideloaded(): Boolean {
        return try {
            val installer = context.packageManager.getInstallerPackageName(context.packageName)
            installer.isNullOrEmpty()
        } catch (_: Exception) {
            true
        }
    }

    fun isRestrictedSettingsBlocking(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (!isSideloaded()) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val ourService = "${context.packageName}/.walls.wall2.AccessibilitySentry"
        return !enabledServices.contains(ourService)
    }

    fun openRestrictedSettings(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getGuidanceText(): String {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                "Go to Settings → Accessibility → Guardian and enable the service."
            isRestrictedSettingsBlocking() ->
                "Your device blocks sideloaded apps from using Accessibility services.\n\n" +
                "1. Open Settings\n" +
                "2. Tap Apps → Guardian\n" +
                "3. Tap the ⋮ menu (top-right corner)\n" +
                "4. Tap \"Allow restricted settings\"\n" +
                "5. Go back to Settings → Accessibility → Guardian and toggle it ON"
            else ->
                "Go to Settings → Accessibility → Guardian and enable the service."
        }
    }
}
