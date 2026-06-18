package com.segurancarural.gpstracker.util

import platform.Foundation.NSLog

/**
 * iOS actual — delegates to NSLog.
 */
actual object KmpLogger {
    actual fun d(tag: String, message: String) { NSLog("D/$tag: $message") }
    actual fun i(tag: String, message: String) { NSLog("I/$tag: $message") }
    actual fun w(tag: String, message: String) { NSLog("W/$tag: $message") }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        NSLog("E/$tag: $message${throwable?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }
}
