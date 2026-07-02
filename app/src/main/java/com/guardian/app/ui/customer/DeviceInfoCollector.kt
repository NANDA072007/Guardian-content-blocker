package com.guardian.app.ui.customer

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.guardian.app.BuildConfig
import com.guardian.app.broadcast.GuardianDeviceAdminReceiver
import com.guardian.app.walls.wall2.health.RestrictedSettingsHelper

object DeviceInfoCollector {

    suspend fun collect(context: Context, restrictedHelper: RestrictedSettingsHelper? = null): DeviceInfo {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, GuardianDeviceAdminReceiver::class.java)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager

        val a11yRunning = am?.let { manager ->
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
                .any { it.resolveInfo.serviceInfo.name.contains("AccessibilitySentry") }
        } ?: false

        val rh = restrictedHelper ?: RestrictedSettingsHelper(context.applicationContext)

        return DeviceInfo(
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            device = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE,
            vpnState = if (isVpnRunning(context)) "Running" else "Stopped",
            accessibilityState = if (a11yRunning) "Running" else "Stopped",
            adminState = if (dpm.isAdminActive(componentName)) "Enabled" else "Disabled",
            healthLevel = "Unknown",
            batteryOptimizationIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
            restrictedSettingsBlocking = rh.isRestrictedSettingsBlocking(),
            overlayPermissionGranted = Settings.canDrawOverlays(context),
            uptimeHours = (System.currentTimeMillis() - getAppStartTime()) / 3_600_000,
            lastRecovery = null
        )
    }

    private fun isVpnRunning(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ?: false
        } else {
            @Suppress("DEPRECATION")
            false
        }
    }

    private var appStartTime = System.currentTimeMillis()
    fun markAppStart() { appStartTime = System.currentTimeMillis() }
    private fun getAppStartTime(): Long = appStartTime
}
