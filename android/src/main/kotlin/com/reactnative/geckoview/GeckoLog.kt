package com.reactnative.geckoview

import android.util.Log

/**
 * Release-safe logging facade.
 *
 * Debug logging is suppressed outside debug builds. Warnings/errors that must
 * remain visible should pass already-redacted messages.
 */
object GeckoLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) Log.w(tag, message) else Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) Log.e(tag, message) else Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
