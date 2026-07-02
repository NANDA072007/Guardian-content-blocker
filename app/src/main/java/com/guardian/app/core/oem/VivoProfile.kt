package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class VivoProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? = null

    override fun openAutoStart(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        }
    }

    override fun manufacturerName(): String = "Vivo"

    override fun getManualInstructions(): String {
        return """
            1. Settings → Apps → Guardian → Battery
               → Unrestricted or No restrictions
            
            2. Settings → Battery → Battery Optimization
               → Guardian → Don't optimize
        """.trimIndent()
    }
}
