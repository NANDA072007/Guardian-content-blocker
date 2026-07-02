package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class SamsungProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        }
    }

    override fun openAutoStart(context: Context): Intent? = null

    override fun manufacturerName(): String = "Samsung"

    override fun getManualInstructions(): String {
        return """
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
    }
}
