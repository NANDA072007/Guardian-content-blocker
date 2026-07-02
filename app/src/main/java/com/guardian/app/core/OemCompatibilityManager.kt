package com.guardian.app.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object OemCompatibilityManager {
    
    private val manufacturer = Build.MANUFACTURER.lowercase()
    
    fun getDeepLinkIntent(): Intent? {
        return when {
            // Samsung: Opens directly to Guardian's battery settings
            manufacturer.contains("samsung") -> Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
            
            // Xiaomi: Opens MIUI autostart settings
            manufacturer.contains("xiaomi") || 
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            
            // OnePlus
            manufacturer.contains("oneplus") -> Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            }
            
            // Realme/OPPO
            manufacturer.contains("realme") || 
            manufacturer.contains("oppo") -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            
            // Huawei
            manufacturer.contains("huawei") || 
            manufacturer.contains("honor") -> Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            
            // Vivo
            manufacturer.contains("vivo") -> Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            
            // Stock Android or unknown
            else -> null
        }
    }
    
    fun getManualInstructions(): String {
        return when {
            manufacturer.contains("samsung") -> """
                Samsung requires 3 separate steps:
                
                1. Settings → Device Care → Battery
                   → tap ⋮ menu → Automation
                   → Turn OFF "Auto optimize daily"
                
                2. Settings → Battery → Background usage limits
                   → Find Guardian → tap → "Never sleeping"
                
                3. Settings → Apps → Guardian → Battery
                   → Select "Unrestricted"
                   
                All 3 must be done. One is not enough.
            """.trimIndent()
            
            manufacturer.contains("xiaomi") || 
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") -> """
                Xiaomi/POCO has 4 separate places. Do all 4:
                
                1. Security App → Permissions → Autostart
                   → Find Guardian → Enable
                   (This is the most important one)
                
                2. Settings → Apps → Manage apps → Guardian
                   → Battery Saver → No restrictions
                
                3. Settings → Battery & Performance
                   → Choose apps → Guardian → No restrictions
                
                4. Security App → Battery → Power Saving
                   → Guardian → No restrictions
            """.trimIndent()
            
            manufacturer.contains("oneplus") -> """
                OnePlus requires 2 steps:
                
                1. Settings → Battery
                   → Battery Optimization
                   → Find Guardian → Don't optimize
                
                2. Settings → Additional Settings
                   → Battery → RAM Boost → OFF
                   (This stops Smart Boost from clearing Guardian)
            """.trimIndent()
            
            manufacturer.contains("realme") || 
            manufacturer.contains("oppo") -> """
                Realme/OPPO requires 3 steps:
                
                1. Settings → Battery → App Quick Freeze
                   → Make sure Guardian is NOT listed here
                
                2. Settings → Battery → More
                   → Intelligent Control → OFF
                
                3. Settings → Apps → Guardian → Battery
                   → Allow background activity
            """.trimIndent()
            
            else -> """
                1. Settings → Apps → Guardian → Battery
                   → Unrestricted or No restrictions
                
                2. Settings → Battery → Battery Optimization
                   → Guardian → Don't optimize
            """.trimIndent()
        }
    }
    
    fun isBatteryOptimizationExempted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    // Request the system-level exemption programmatically
    // This survives OEM resets — manual settings do not
    fun requestSystemExemption(context: Context) {
        if (!isBatteryOptimizationExempted(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
