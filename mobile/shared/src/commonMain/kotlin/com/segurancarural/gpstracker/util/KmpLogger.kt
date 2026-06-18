package com.segurancarural.gpstracker.util

/**
 * KMP Logger — expect declaration for multiplatform logging.
 *
 * Android: delegates to android.util.Log
 * iOS: delegates to NSLog / print
 *
 * This replaces direct android.util.Log usage in commonMain,
 * which is a hard blocker for iOS compilation.
 */
expect object KmpLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
