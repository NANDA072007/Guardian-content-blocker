package com.guardian.app.core.oem

import android.content.Context
import android.content.Intent

class DefaultProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? = null
    override fun openAutoStart(context: Context): Intent? = null
    override fun manufacturerName(): String = "Android"

    override fun getManualInstructions(): String {
        return """
            1. Settings → Apps → Guardian → Battery
               → Unrestricted or No restrictions
            
            2. Settings → Battery → Battery Optimization
               → Guardian → Don't optimize
        """.trimIndent()
    }
}
