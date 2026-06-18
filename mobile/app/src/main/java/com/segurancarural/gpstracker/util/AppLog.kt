package com.segurancarural.gpstracker.util

import android.util.Log
import com.segurancarural.gpstracker.BuildConfig

/**
 * Debug-only logging for development (Android Studio / adb logcat).
 * Release builds skip logcat but write to offline file.
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
        OfflineLogger.log("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
        OfflineLogger.log("INFO", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
        OfflineLogger.log("WARN", tag, message + (throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
        OfflineLogger.log("ERROR", tag, message + (throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
    }
}
