package com.guardian.app.core.oem

import android.content.Context
import android.content.Intent

/**
 * Interface representing custom vendor profiles for battery settings and auto-start requirements.
 */
interface OemProfile {
    fun openBatterySettings(context: Context): Intent?
    fun openAutoStart(context: Context): Intent?
    fun manufacturerName(): String
    fun getManualInstructions(): String
}
