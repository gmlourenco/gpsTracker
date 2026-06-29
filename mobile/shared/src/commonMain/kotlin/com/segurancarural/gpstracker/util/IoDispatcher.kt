package com.segurancarural.gpstracker.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-specific IO dispatcher.
 * - Android: Dispatchers.Default (optimized for blocking I/O)
 * - iOS: Dispatchers.Default (no IO dispatcher in Kotlin/Native)
 */
expect val ioDispatcher: CoroutineDispatcher
