package com.guardian.app.util

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Crashlytics helper for logging non-fatal exceptions and custom keys.
 * Fatal crashes are automatically captured by Crashlytics.
 */
object CrashHelper {

    fun logException(e: Throwable, message: String? = null) {
        if (message != null) {
            Firebase.crashlytics.log(message)
        }
        Firebase.crashlytics.recordException(e)
    }

    fun log(message: String) {
        Firebase.crashlytics.log(message)
    }

    fun setCustomKey(key: String, value: String) {
        Firebase.crashlytics.setCustomKey(key, value)
    }

    fun setCustomKey(key: String, value: Long) {
        Firebase.crashlytics.setCustomKey(key, value)
    }

    fun setUserId(id: String) {
        Firebase.crashlytics.setUserId(id)
    }
}
