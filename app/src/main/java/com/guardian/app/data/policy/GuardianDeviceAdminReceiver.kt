package com.guardian.app.data.policy

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "Device Admin Enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.d("DeviceAdmin", "Device Admin Disable Requested")
        // We can return a warning message here
        return "Disabling Guardian will stop all protections. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "Device Admin Disabled")
    }
}
