package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class OnePlusProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? = null

    override fun openAutoStart(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        }
    }

    override fun manufacturerName(): String = "OnePlus"

    override fun getManualInstructions(): String {
        return """
            OnePlus requires 2 steps:
            
            1. Settings → Battery
               → Battery Optimization
               → Find Guardian → Don't optimize
            
            2. Settings → Additional Settings
               → Battery → RAM Boost → OFF
               (This stops Smart Boost from clearing Guardian)
        """.trimIndent()
    }
}
