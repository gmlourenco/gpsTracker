package com.segurancarural.gpstracker.util

import android.util.Log
import com.segurancarural.gpstracker.BuildConfig

/**
 * Debug-only logging for development (Android Studio / adb logcat).
 * Release builds skip these calls entirely.
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}
