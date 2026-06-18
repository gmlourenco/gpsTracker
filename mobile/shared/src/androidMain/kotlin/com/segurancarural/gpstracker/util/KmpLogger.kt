package com.segurancarural.gpstracker.util

import android.util.Log

/**
 * Android actual — delegates to android.util.Log.
 */
actual object KmpLogger {
    actual fun d(tag: String, message: String) { Log.d(tag, message) }
    actual fun i(tag: String, message: String) { Log.i(tag, message) }
    actual fun w(tag: String, message: String) { Log.w(tag, message) }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
