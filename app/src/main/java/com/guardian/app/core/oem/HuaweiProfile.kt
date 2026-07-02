package com.guardian.app.core.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent

class HuaweiProfile : OemProfile {
    override fun openBatterySettings(context: Context): Intent? = null

    override fun openAutoStart(context: Context): Intent? {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        }
    }

    override fun manufacturerName(): String = "Huawei"

    override fun getManualInstructions(): String {
        return """
            1. Settings → Apps → Guardian → Battery
               → Unrestricted or No restrictions
            
            2. Settings → Battery → Battery Optimization
               → Guardian → Don't optimize
        """.trimIndent()
    }
}
