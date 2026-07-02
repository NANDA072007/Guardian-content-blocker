package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class XiaomiProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettingsActivity"
            )
        }
    }

    override fun openAutoStart(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
    }

    override fun manufacturerName(): String = "Xiaomi"

    override fun getManualInstructions(): String {
        return """
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
    }
}
