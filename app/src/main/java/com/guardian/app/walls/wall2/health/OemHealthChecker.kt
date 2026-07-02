package com.guardian.app.walls.wall2.health

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OemHealthChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "OemHealth"
    private val manufacturer = Build.MANUFACTURER.lowercase()

    fun check(): HealthSignal.OemStatus {
        val autoStart = when {
            manufacturer.contains("xiaomi") -> checkXiaomiAutoStart()
            manufacturer.contains("samsung") -> checkSamsungAutoStart()
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> checkColorOSAutoStart()
            manufacturer.contains("vivo") -> checkVivoAutoStart()
            manufacturer.contains("oneplus") -> checkOnePlusAutoStart()
            else -> null
        }

        val batteryExempt = checkBatteryOptimization()
        val backgroundRestricted = checkBackgroundRestriction()

        val signal = HealthSignal.OemStatus(
            timestamp = System.currentTimeMillis(),
            manufacturer = manufacturer,
            autoStartEnabled = autoStart,
            batteryOptimizationExempt = batteryExempt,
            backgroundRestricted = backgroundRestricted,
            deviceManufacturer = Build.MANUFACTURER
        )
        return signal
    }

    private fun checkXiaomiAutoStart(): Boolean? {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.miui.securitycenter")
            if (intent != null) null else null
        } catch (_: Exception) { null }
    }

    private fun checkSamsungAutoStart(): Boolean? = null

    private fun checkColorOSAutoStart(): Boolean? = null

    private fun checkVivoAutoStart(): Boolean? = null

    private fun checkOnePlusAutoStart(): Boolean? = null

    private fun checkBatteryOptimization(): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        } catch (_: Exception) { false }
    }

    private fun checkBackgroundRestriction(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                usageStatsManager != null
            } else false
        } catch (_: Exception) { false }
    }

    fun getDeviceTips(): List<String> {
        val tips = mutableListOf<String>()
        when {
            manufacturer.contains("xiaomi") -> {
                tips.add("Xiaomi: Enable Autostart in Settings → Apps → Guardian → Autostart")
                tips.add("Xiaomi: Lock Guardian in recent apps (pull down on app preview)")
                tips.add("Xiaomi: Disable battery restriction in Settings → Apps → Guardian → Battery saver")
            }
            manufacturer.contains("samsung") -> {
                tips.add("Samsung: Disable 'Put unused apps to sleep' in Device Care → Battery")
                tips.add("Samsung: Set Guardian to 'Unrestricted' in Settings → Apps → Guardian → Battery")
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                tips.add("ColorOS: Enable 'Startup manager' for Guardian in Settings → Apps")
                tips.add("ColorOS: Disable 'Auto-optimize' for Guardian in Battery settings")
            }
            manufacturer.contains("vivo") -> {
                tips.add("Vivo: Add Guardian to 'Protected apps' list in Settings → Battery")
                tips.add("Vivo: Disable 'Background power consumption limit' for Guardian")
            }
            manufacturer.contains("oneplus") -> {
                tips.add("OnePlus: Set 'Battery optimization' to 'Don't optimize' for Guardian")
                tips.add("OnePlus: Enable 'Quick launch' for Guardian in recent apps settings")
            }
        }

        if (!checkBatteryOptimization()) {
            tips.add("Disable battery optimization for Guardian in Settings → Apps → Guardian → Battery")
        }

        return tips
    }
}
