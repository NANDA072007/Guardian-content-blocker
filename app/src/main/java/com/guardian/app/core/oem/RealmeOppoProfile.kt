package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class RealmeOppoProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? = null

    override fun openAutoStart(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        }
    }

    override fun manufacturerName(): String = "OPPO / Realme"

    override fun getManualInstructions(): String {
        return """
            Realme/OPPO requires 3 steps:
            
            1. Settings → Battery → App Quick Freeze
               → Make sure Guardian is NOT listed here
            
            2. Settings → Battery → More
               → Intelligent Control → OFF
            
            3. Settings → Apps → Guardian → Battery
               → Allow background activity
        """.trimIndent()
    }
}
