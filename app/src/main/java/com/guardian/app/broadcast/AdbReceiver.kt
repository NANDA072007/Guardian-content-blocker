package com.guardian.app.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.guardian.app.di.OverlayEntryPoint
import dagger.hilt.android.EntryPointAccessors

class AdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.hardware.usb.action.USB_STATE") {
            val connected = intent.getBooleanExtra("connected", false)
            Log.d("AdbReceiver", "USB State Changed. Connected: $connected")

            val entryPoint = EntryPointAccessors.fromApplication(context, OverlayEntryPoint::class.java)
            val securityManager = entryPoint.securityManager()
            if (connected && securityManager.isSetupComplete() && isAdbEnabled(context)) {
                Log.d("AdbReceiver", "ADB is enabled and USB connected. Triggering overlay.")
                entryPoint.blockOverlayManager().showOverlay()
            }
        }
    }

    private fun isAdbEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }
}
